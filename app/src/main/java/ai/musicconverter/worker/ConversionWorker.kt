package ai.musicconverter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ai.musicconverter.data.AudioConverter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

@UnstableApi
@HiltWorker
class ConversionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val converter: AudioConverter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val inputPath = inputData.getString(KEY_INPUT_PATH)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No input path provided"))

        val deleteOriginal = inputData.getBoolean(KEY_DELETE_ORIGINAL, true) // Default: delete original
        val saveToMusicFolder = inputData.getBoolean(KEY_SAVE_TO_MUSIC_FOLDER, true) // Default: save to Music folder

        Timber.d("Starting conversion: $inputPath -> AAC (M4A)")
        Timber.d("Delete original: $deleteOriginal, Save to Music folder: $saveToMusicFolder")

        setProgress(workDataOf(KEY_PROGRESS to 0f))

        // Media3 Transformer converts to AAC (most universal format supported by MediaCodec)
        val result = converter.convertToAac(
            inputPath = inputPath,
            saveToMusicFolder = saveToMusicFolder
        ) { progress ->
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        }

        return when (result) {
            is AudioConverter.ConversionResult.Success -> {
                Timber.d("Conversion completed: ${result.outputPath}")

                if (deleteOriginal) {
                    val originalFile = File(inputPath)
                    if (originalFile.exists()) {
                        originalFile.delete()
                        Timber.d("Deleted original file: $inputPath")
                    }
                }

                Result.success(
                    workDataOf(
                        KEY_OUTPUT_PATH to result.outputPath,
                        KEY_INPUT_PATH to inputPath
                    )
                )
            }
            is AudioConverter.ConversionResult.Error -> {
                Timber.e("Conversion failed: ${result.message}")
                Result.failure(workDataOf(KEY_ERROR to result.message, KEY_INPUT_PATH to inputPath))
            }
            AudioConverter.ConversionResult.Cancelled -> {
                Timber.d("Conversion cancelled")
                Result.failure(workDataOf(KEY_ERROR to "Cancelled", KEY_INPUT_PATH to inputPath))
            }
        }
    }

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_OUTPUT_FORMAT = "output_format"
        const val KEY_DELETE_ORIGINAL = "delete_original"
        const val KEY_SAVE_TO_MUSIC_FOLDER = "save_to_music_folder"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        // AAC is the output format - MP3 encoding not supported by MediaCodec
        const val FORMAT_AAC = "aac"

        const val WORK_TAG = "conversion_work"
    }
}
