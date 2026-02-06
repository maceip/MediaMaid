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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MusicConverterUiState(
    val musicFiles: List<MusicFile> = emptyList(),
    val status: ConversionStatus = ConversionStatus.IDLE,
    val currentConvertingId: String? = null,
    val error: String? = null,
    val hasStoragePermission: Boolean = false,
    val searchQuery: String = "",
    // Batch conversion progress
    val totalToConvert: Int = 0,
    val convertedCount: Int = 0,
    val isBatchConverting: Boolean = false
) {
    val filteredFiles: List<MusicFile>
        get() = if (searchQuery.isBlank()) musicFiles
        else musicFiles.filter { file ->
            file.name.contains(searchQuery, ignoreCase = true) ||
            (file.artist?.contains(searchQuery, ignoreCase = true) == true)
        }
}

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

    // Keep Original Files: OFF = delete originals (default), ON = keep originals
    val keepOriginalEnabled: StateFlow<Boolean> = preferencesManager.keepOriginalFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Batching constants - prevent overwhelming the system
    private companion object {
        const val MAX_CONCURRENT_CONVERSIONS = 3
        const val BATCH_SIZE = 10
        const val ENQUEUE_DELAY_MS = 50L
        const val UI_UPDATE_DEBOUNCE_MS = 100L
    }

    // Track files being converted by path for O(1) lookups
    private val convertingFiles = mutableSetOf<String>()

    // Job for batch conversion - can be cancelled
    private var batchConversionJob: Job? = null

    // Pending UI updates - debounced
    private var pendingUiUpdate: Job? = null

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

    /**
     * Convert a single file - used for individual item conversion
     */
    fun convertFile(musicFile: MusicFile) {
        if (convertingFiles.contains(musicFile.path)) {
            Timber.d("File already being converted: ${musicFile.path}")
            return
        }

        enqueueConversion(musicFile)
        scheduleUiUpdate()
    }

    /**
     * Convert selected files by their IDs
     */
    fun convertSelectedFiles(selectedIds: List<String>) {
        val filesToConvert = _uiState.value.musicFiles
            .filter { it.id in selectedIds && it.needsConversion && !convertingFiles.contains(it.path) }
        startBatchConversion(filesToConvert)
    }

    /**
     * Convert all files with proper batching to prevent crashes.
     */
    fun convertAllFiles() {
        val filesToConvert = _uiState.value.musicFiles
            .filter { it.needsConversion && !convertingFiles.contains(it.path) }
        startBatchConversion(filesToConvert)
    }

    private fun startBatchConversion(filesToConvert: List<MusicFile>) {
        if (filesToConvert.isEmpty()) {
            Timber.d("No files to convert")
            return
        }

        Timber.d("Starting batch conversion of ${filesToConvert.size} files")

        _uiState.update {
            it.copy(
                isBatchConverting = true,
                totalToConvert = filesToConvert.size,
                convertedCount = 0,
                status = ConversionStatus.CONVERTING
            )
        }

        batchConversionJob = viewModelScope.launch {
            var enqueued = 0

            for (file in filesToConvert) {
                if (!isActive) {
                    Timber.d("Batch conversion cancelled")
                    break
                }

                // Wait if we have too many concurrent jobs
                while (isActive && getActiveConversionCount() >= MAX_CONCURRENT_CONVERSIONS) {
                    delay(200)
                }

                if (!isActive) break

                enqueueConversion(file)
                enqueued++

                // Small delay between enqueues to prevent overwhelming WorkManager
                if (enqueued % BATCH_SIZE == 0) {
                    scheduleUiUpdate()
                    delay(ENQUEUE_DELAY_MS)
                }
            }

            Timber.d("Batch conversion enqueued $enqueued files")
            scheduleUiUpdate()
        }
    }

    /**
     * Enqueue a single file for conversion
     */
    private fun enqueueConversion(musicFile: MusicFile) {
        val keepOriginal = keepOriginalEnabled.value
        val deleteOriginal = !keepOriginal
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

        convertingFiles.add(musicFile.path)
    }

    /**
     * Get count of currently active (running + enqueued) conversions
     */
    private fun getActiveConversionCount(): Int {
        return try {
            workManager.getWorkInfosByTag(ConversionWorker.WORK_TAG)
                .get()
                .count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        } catch (e: Exception) {
            Timber.e(e, "Error getting active conversion count")
            0
        }
    }

    /**
     * Schedule a debounced UI update to prevent rapid-fire state changes
     */
    private fun scheduleUiUpdate() {
        pendingUiUpdate?.cancel()
        pendingUiUpdate = viewModelScope.launch {
            delay(UI_UPDATE_DEBOUNCE_MS)
            updateUiFromConvertingSet()
        }
    }

    /**
     * Update UI state based on the converting files set
     */
    private fun updateUiFromConvertingSet() {
        _uiState.update { state ->
            state.copy(
                musicFiles = state.musicFiles.map { file ->
                    val isConverting = convertingFiles.contains(file.path)
                    if (file.isConverting != isConverting) {
                        file.copy(isConverting = isConverting)
                    } else {
                        file
                    }
                },
                status = if (convertingFiles.isNotEmpty()) ConversionStatus.CONVERTING else ConversionStatus.IDLE
            )
        }
    }

    private fun observeWorkProgress() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(ConversionWorker.WORK_TAG)
                .collect { workInfos ->
                    // Process in batches and debounce to avoid UI storms
                    processWorkInfosBatched(workInfos)
                }
        }
    }

    /**
     * Process work infos efficiently without causing UI storms
     */
    private fun processWorkInfosBatched(workInfos: List<WorkInfo>) {
        var hasChanges = false
        val completedPaths = mutableListOf<String>()
        val failedPaths = mutableMapOf<String, String>()

        for (workInfo in workInfos) {
            val inputPath = workInfo.outputData.getString(ConversionWorker.KEY_INPUT_PATH)
                ?: workInfo.progress.getString(ConversionWorker.KEY_INPUT_PATH)
                ?: continue

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    if (convertingFiles.remove(inputPath)) {
                        completedPaths.add(inputPath)
                        hasChanges = true
                    }
                }
                WorkInfo.State.FAILED -> {
                    if (convertingFiles.remove(inputPath)) {
                        val error = workInfo.outputData.getString(ConversionWorker.KEY_ERROR) ?: "Unknown error"
                        failedPaths[inputPath] = error
                        hasChanges = true
                    }
                }
                WorkInfo.State.CANCELLED -> {
                    if (convertingFiles.remove(inputPath)) {
                        hasChanges = true
                    }
                }
                else -> { /* RUNNING, ENQUEUED, BLOCKED - no state change needed */ }
            }
        }

        if (hasChanges) {
            _uiState.update { state ->
                val updatedFiles = state.musicFiles
                    .filter { it.path !in completedPaths }
                    .map { file ->
                        if (file.path in failedPaths) {
                            file.copy(isConverting = false, conversionProgress = 0f)
                        } else {
                            file
                        }
                    }

                val newConvertedCount = state.convertedCount + completedPaths.size
                val stillConverting = convertingFiles.isNotEmpty()

                state.copy(
                    musicFiles = updatedFiles,
                    convertedCount = newConvertedCount,
                    isBatchConverting = stillConverting && state.isBatchConverting,
                    status = when {
                        failedPaths.isNotEmpty() -> ConversionStatus.ERROR
                        !stillConverting && state.isBatchConverting -> ConversionStatus.COMPLETED
                        stillConverting -> ConversionStatus.CONVERTING
                        else -> ConversionStatus.IDLE
                    },
                    error = failedPaths.values.firstOrNull(),
                    currentConvertingId = null
                )
            }

            if (completedPaths.isNotEmpty()) {
                Timber.d("Completed ${completedPaths.size} conversions")
            }
            if (failedPaths.isNotEmpty()) {
                Timber.e("Failed ${failedPaths.size} conversions")
            }
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

    fun toggleKeepOriginal() {
        viewModelScope.launch {
            val currentEnabled = keepOriginalEnabled.value
            preferencesManager.setKeepOriginalFiles(!currentEnabled)
        }
    }

    fun cancelAllConversions() {
        // Cancel the batch job first
        batchConversionJob?.cancel()
        batchConversionJob = null

        // Cancel all WorkManager work
        workManager.cancelAllWorkByTag(ConversionWorker.WORK_TAG)

        // Clear our tracking set
        convertingFiles.clear()

        // Reset UI state
        _uiState.update { state ->
            state.copy(
                musicFiles = state.musicFiles.map { file ->
                    if (file.isConverting) {
                        file.copy(isConverting = false, conversionProgress = 0f)
                    } else file
                },
                status = ConversionStatus.IDLE,
                isBatchConverting = false,
                totalToConvert = 0,
                convertedCount = 0,
                currentConvertingId = null
            )
        }

        Timber.d("Cancelled all conversions")
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, status = ConversionStatus.IDLE) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
