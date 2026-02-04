package ai.musicconverter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import ai.musicconverter.data.ConversionStatus
import ai.musicconverter.data.MusicFile
import ai.musicconverter.data.MusicFileScanner
import ai.musicconverter.data.PreferencesManager
import ai.musicconverter.service.AutoConvertService
import ai.musicconverter.worker.ConversionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MusicConverterUiState(
    val musicFiles: List<MusicFile> = emptyList(),
    val status: ConversionStatus = ConversionStatus.IDLE,
    val currentConvertingId: String? = null,
    val error: String? = null,
    val hasStoragePermission: Boolean = false
)

@HiltViewModel
class MusicConverterViewModel @Inject constructor(
    application: Application,
    private val scanner: MusicFileScanner,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(MusicConverterUiState())
    val uiState: StateFlow<MusicConverterUiState> = _uiState.asStateFlow()

    val autoConvertEnabled: StateFlow<Boolean> = preferencesManager.autoConvertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val deleteOriginalEnabled: StateFlow<Boolean> = preferencesManager.deleteOriginalAfterConversion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true) // Default: delete original

    init {
        observeWorkProgress()
    }

    fun setStoragePermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = granted) }
        if (granted) {
            scanForFiles()
        }
    }

    fun scanForFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ConversionStatus.SCANNING, error = null) }

            try {
                val files = scanner.scanForMusicFiles()
                Timber.d("Found ${files.size} files needing conversion")

                _uiState.update {
                    it.copy(
                        musicFiles = files,
                        status = ConversionStatus.IDLE
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scanning for files")
                _uiState.update {
                    it.copy(
                        status = ConversionStatus.ERROR,
                        error = e.message ?: "Failed to scan for files"
                    )
                }
            }
        }
    }

    fun convertFile(musicFile: MusicFile) {
        val deleteOriginal = deleteOriginalEnabled.value
        // If deleting original, save to Music/Converted folder for music app discovery
        // If keeping original, save converted file next to original
        val saveToMusicFolder = deleteOriginal

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(
                workDataOf(
                    ConversionWorker.KEY_INPUT_PATH to musicFile.path,
                    ConversionWorker.KEY_OUTPUT_FORMAT to ConversionWorker.FORMAT_AAC,
                    ConversionWorker.KEY_DELETE_ORIGINAL to deleteOriginal,
                    ConversionWorker.KEY_SAVE_TO_MUSIC_FOLDER to saveToMusicFolder
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(ConversionWorker.WORK_TAG)
            .addTag("file_${musicFile.id}")
            .build()

        workManager.enqueueUniqueWork(
            "convert_${musicFile.id}",
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        _uiState.update { state ->
            state.copy(
                musicFiles = state.musicFiles.map {
                    if (it.id == musicFile.id) it.copy(isConverting = true) else it
                },
                currentConvertingId = musicFile.id,
                status = ConversionStatus.CONVERTING
            )
        }
    }

    fun convertAllFiles() {
        val filesToConvert = _uiState.value.musicFiles.filter { it.needsConversion && !it.isConverting }

        filesToConvert.forEach { convertFile(it) }
    }

    private fun observeWorkProgress() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(ConversionWorker.WORK_TAG)
                .collect { workInfos ->
                    workInfos.forEach { workInfo ->
                        handleWorkInfoUpdate(workInfo)
                    }
                }
        }
    }

    private fun handleWorkInfoUpdate(workInfo: WorkInfo) {
        val inputPath = workInfo.outputData.getString(ConversionWorker.KEY_INPUT_PATH)
        val progress = workInfo.progress.getFloat(ConversionWorker.KEY_PROGRESS, 0f)

        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                _uiState.update { state ->
                    state.copy(
                        musicFiles = state.musicFiles.map { file ->
                            if (file.path == inputPath) {
                                file.copy(isConverting = true, conversionProgress = progress)
                            } else file
                        }
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val completedPath = workInfo.outputData.getString(ConversionWorker.KEY_INPUT_PATH)
                Timber.d("Conversion succeeded for: $completedPath")

                _uiState.update { state ->
                    state.copy(
                        musicFiles = state.musicFiles.filter { it.path != completedPath },
                        status = if (state.musicFiles.size <= 1) ConversionStatus.COMPLETED else state.status,
                        currentConvertingId = null
                    )
                }
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(ConversionWorker.KEY_ERROR)
                Timber.e("Conversion failed: $error")

                _uiState.update { state ->
                    state.copy(
                        musicFiles = state.musicFiles.map { file ->
                            if (file.path == inputPath) {
                                file.copy(isConverting = false, conversionProgress = 0f)
                            } else file
                        },
                        status = ConversionStatus.ERROR,
                        error = error,
                        currentConvertingId = null
                    )
                }
            }
            WorkInfo.State.CANCELLED -> {
                _uiState.update { state ->
                    state.copy(
                        musicFiles = state.musicFiles.map { file ->
                            if (file.path == inputPath) {
                                file.copy(isConverting = false, conversionProgress = 0f)
                            } else file
                        },
                        currentConvertingId = null
                    )
                }
            }
            else -> { /* ENQUEUED, BLOCKED */ }
        }
    }

    fun toggleAutoConvert() {
        viewModelScope.launch {
            val currentEnabled = autoConvertEnabled.value
            val newEnabled = !currentEnabled

            preferencesManager.setAutoConvertEnabled(newEnabled)

            if (newEnabled) {
                AutoConvertService.start(getApplication())
            } else {
                AutoConvertService.stop(getApplication())
            }
        }
    }

    fun toggleDeleteOriginal() {
        viewModelScope.launch {
            val currentEnabled = deleteOriginalEnabled.value
            preferencesManager.setDeleteOriginalAfterConversion(!currentEnabled)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, status = ConversionStatus.IDLE) }
    }
}
