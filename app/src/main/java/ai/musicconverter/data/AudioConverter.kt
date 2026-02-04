package ai.musicconverter.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Audio converter using Android's Media3 Transformer library.
 *
 * Features:
 * - Converts to AAC (M4A) - universally supported, hardware accelerated
 * - Configurable output location:
 *   - Music/Converted folder (for music app discovery) when deleting originals
 *   - Same folder as original (when keeping originals)
 * - Notifies MediaStore so Spotify, YouTube Music, etc. can find files
 * - Preserves metadata including cover art (Media3 copies metadata by default)
 *
 * Cover Art Handling:
 * - Media3 Transformer preserves embedded artwork from source files
 * - M4A files store cover art in the 'covr' atom (MP4 metadata)
 * - MediaStore indexes artwork automatically for music apps
 */
@Singleton
@UnstableApi
class AudioConverter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class ConversionResult {
        data class Success(val outputPath: String) : ConversionResult()
        data class Error(val message: String) : ConversionResult()
        data object Cancelled : ConversionResult()
    }

    /**
     * Get the Music/Converted output directory.
     * Creates the folder if it doesn't exist.
     */
    private fun getMusicConvertedDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val convertedDir = File(musicDir, CONVERTED_FOLDER_NAME)
        if (!convertedDir.exists()) {
            convertedDir.mkdirs()
        }
        return convertedDir
    }

    /**
     * Convert audio file to AAC format in M4A container.
     *
     * @param inputPath Path to the input audio file
     * @param saveToMusicFolder If true, saves to Music/Converted/ for music app discovery.
     *                          If false, saves next to the original file.
     * @param onProgress Progress callback (0.0 to 1.0)
     */
    suspend fun convertToAac(
        inputPath: String,
        saveToMusicFolder: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): ConversionResult = withContext(Dispatchers.Main) {
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            return@withContext ConversionResult.Error("Input file does not exist")
        }

        // Determine output directory based on preference
        val outputDir = if (saveToMusicFolder) {
            getMusicConvertedDirectory()
        } else {
            // Save next to original file
            inputFile.parentFile ?: return@withContext ConversionResult.Error("Cannot determine output directory")
        }

        val outputFile = File(outputDir, "${inputFile.nameWithoutExtension}.m4a")

        // Handle filename conflicts
        val finalOutputFile = getUniqueFile(outputFile)

        // Delete output file if it already exists
        if (finalOutputFile.exists()) {
            finalOutputFile.delete()
        }

        Timber.d("Converting: $inputPath -> ${finalOutputFile.absolutePath}")
        Timber.d("Output location: ${if (saveToMusicFolder) "Music/Converted" else "next to original"}")

        try {
            val result = convertWithTransformer(
                inputUri = inputFile.toUri(),
                outputFile = finalOutputFile,
                onProgress = onProgress
            )

            // If successful, notify MediaStore (always, so file is discoverable)
            if (result is ConversionResult.Success) {
                notifyMediaStore(finalOutputFile, saveToMusicFolder)
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Conversion failed")
            finalOutputFile.delete()
            ConversionResult.Error(e.message ?: "Unknown conversion error")
        }
    }

    /**
     * Generate unique filename if file already exists.
     */
    private fun getUniqueFile(file: File): File {
        if (!file.exists()) return file

        val parent = file.parentFile
        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension

        var counter = 1
        var newFile: File
        do {
            newFile = File(parent, "${nameWithoutExt}_$counter.$ext")
            counter++
        } while (newFile.exists())

        return newFile
    }

    /**
     * Notify MediaStore about the new file so music apps can discover it.
     * This makes the file appear in Spotify local files, YouTube Music, etc.
     */
    private suspend fun notifyMediaStore(file: File, isInMusicFolder: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isInMusicFolder) {
                // Android 10+ with Music folder - Use MediaStore API
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$CONVERTED_FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }

                // The file already exists, so we just need to scan it
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4")
                ) { path, uri ->
                    Timber.d("MediaStore scan complete: $path -> $uri")
                }
            } else {
                // Use MediaScannerConnection for all other cases
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4")
                ) { path, uri ->
                    Timber.d("MediaScanner indexed: $path -> $uri")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify MediaStore")
        }
    }

    private suspend fun convertWithTransformer(
        inputUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): ConversionResult = suspendCancellableCoroutine { continuation ->

        val progressHolder = ProgressHolder()

        val transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult
                ) {
                    Timber.d("Conversion completed: ${outputFile.absolutePath}")
                    Timber.d("Export result - audio: ${exportResult.audioEncoderName}, duration: ${exportResult.durationMs}ms")
                    if (continuation.isActive) {
                        continuation.resume(ConversionResult.Success(outputFile.absolutePath))
                    }
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Timber.e(exportException, "Conversion error")
                    outputFile.delete()
                    if (continuation.isActive) {
                        continuation.resume(
                            ConversionResult.Error(
                                exportException.message ?: "Conversion failed"
                            )
                        )
                    }
                }
            })
            .build()

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true) // Audio only
            .build()

        // Start the transformation
        transformer.start(editedMediaItem, outputFile.absolutePath)

        // Monitor progress
        val progressJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            while (continuation.isActive) {
                val progressState = transformer.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress / 100f)
                }
                delay(100)
            }
        }

        continuation.invokeOnCancellation {
            progressJob.cancel()
            transformer.cancel()
            outputFile.delete()
        }
    }

    /**
     * Get the duration of a media file in milliseconds.
     */
    fun getMediaDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to get media duration")
            0L
        }
    }

    companion object {
        const val OUTPUT_EXTENSION = "m4a"
        const val OUTPUT_MIME_TYPE = MimeTypes.AUDIO_AAC
        const val CONVERTED_FOLDER_NAME = "Converted"
    }
}
