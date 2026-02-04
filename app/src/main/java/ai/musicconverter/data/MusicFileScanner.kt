package ai.musicconverter.data

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicFileScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun scanForMusicFiles(): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()

        // Try MediaStore first for better performance
        try {
            musicFiles.addAll(scanWithMediaStore())
        } catch (e: Exception) {
            Timber.e(e, "MediaStore scan failed, falling back to file system scan")
        }

        // If MediaStore didn't find files that need conversion, scan file system
        if (musicFiles.none { it.needsConversion }) {
            try {
                musicFiles.addAll(scanFileSystem())
            } catch (e: Exception) {
                Timber.e(e, "File system scan failed")
            }
        }

        // Remove duplicates and sort by last modified (most recent first)
        musicFiles
            .distinctBy { it.path }
            .filter { it.needsConversion }
            .sortedByDescending { it.lastModified }
    }

    private fun scanWithMediaStore(): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val path = cursor.getString(dataColumn) ?: continue
                val size = cursor.getLong(sizeColumn)
                val lastModified = cursor.getLong(dateColumn) * 1000

                val file = File(path)
                if (!file.exists()) continue

                val ext = file.extension.lowercase()
                if (ext !in MusicFile.SUPPORTED_EXTENSIONS) continue

                musicFiles.add(
                    MusicFile(
                        id = id.toString(),
                        name = file.nameWithoutExtension,
                        path = path,
                        extension = ext,
                        size = size,
                        lastModified = lastModified
                    )
                )
            }
        }

        return musicFiles
    }

    private fun scanFileSystem(): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()
        val directories = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
            Environment.getExternalStorageDirectory()
        )

        for (dir in directories) {
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, musicFiles)
            }
        }

        return musicFiles
    }

    private fun scanDirectory(directory: File, results: MutableList<MusicFile>, depth: Int = 0) {
        if (depth > 5) return // Limit recursion depth

        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    scanDirectory(file, results, depth + 1)
                } else if (file.isFile) {
                    MusicFile.fromFile(file)?.let { results.add(it) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning directory: ${directory.absolutePath}")
        }
    }
}
