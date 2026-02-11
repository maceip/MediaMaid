package ai.musicconverter.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicFileScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Cache previous scan results for incremental delta scanning
    @Volatile
    private var cachedPaths: Set<String> = emptySet()
    @Volatile
    private var cachedFiles: List<MusicFile> = emptyList()
    @Volatile
    private var lastScanEpoch: Long = 0L  // seconds since epoch of last full scan

    suspend fun scanForMusicFiles(includeConverted: Boolean = false): List<MusicFile> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val musicFiles = mutableListOf<MusicFile>()

        try {
            if (lastScanEpoch > 0L && cachedFiles.isNotEmpty()) {
                // Incremental: query only files modified since last scan, merge with cache
                val delta = scanWithMediaStore(modifiedSince = lastScanEpoch - 5)
                val deltaPaths = delta.mapTo(HashSet()) { it.path }
                // Keep cached files that weren't updated, add all delta files
                musicFiles.addAll(cachedFiles.filter { it.path !in deltaPaths })
                musicFiles.addAll(delta)
                Timber.d("Incremental scan: ${delta.size} changed, ${musicFiles.size} total")
            } else {
                // Full scan
                musicFiles.addAll(scanWithMediaStore())
                Timber.d("Full MediaStore scan: ${musicFiles.size} files")
            }
        } catch (e: Exception) {
            Timber.e(e, "MediaStore scan failed, falling back to file system scan")
        }

        // Filesystem fallback only when MediaStore found nothing convertible
        if (musicFiles.none { it.needsConversion }) {
            try {
                musicFiles.addAll(scanFileSystemParallel())
            } catch (e: Exception) {
                Timber.e(e, "File system scan failed")
            }
        }

        // Build deduplicated result and update cache
        val result = musicFiles
            .distinctBy { it.path }
            .let { list -> if (includeConverted) list else list.filter { it.needsConversion } }
            .sortedByDescending { it.lastModified }

        cachedPaths = result.mapTo(HashSet()) { it.path }
        cachedFiles = result
        lastScanEpoch = System.currentTimeMillis() / 1000

        Timber.d("Scan completed in ${System.currentTimeMillis() - startMs}ms, ${result.size} files")
        result
    }

    /**
     * Queries MediaStore for audio files. Trust MediaStore as authoritative —
     * no File.exists() calls needed (MediaStore is the OS file index).
     *
     * @param modifiedSince If > 0, only returns files modified after this epoch (seconds).
     *                      Used for incremental delta scans on pull-to-refresh.
     */
    private fun scanWithMediaStore(modifiedSince: Long = 0L): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK
        )

        val selection = if (modifiedSince > 0L) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        val selectionArgs = if (modifiedSince > 0L) arrayOf(modifiedSince.toString()) else null
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            // O(1) extension lookup
            val supported = MusicFile.SUPPORTED_EXTENSIONS.toHashSet()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val path = cursor.getString(dataColumn) ?: continue

                // Quick extension filter — no filesystem I/O
                val dotIdx = path.lastIndexOf('.')
                if (dotIdx < 0) continue
                val ext = path.substring(dotIdx + 1).lowercase()
                if (ext !in supported) continue

                // Trust MediaStore — skip File.exists(). MediaStore is the OS
                // file index; if it has an entry, the file is there. Stale entries
                // (deleted files) are cleaned up by the OS media scanner.

                val size = cursor.getLong(sizeColumn)
                val lastModified = cursor.getLong(dateColumn) * 1000
                val duration = cursor.getLong(durationColumn)
                val artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" }
                val album = cursor.getString(albumColumn)?.takeIf { it != "<unknown>" }
                val albumId = cursor.getLong(albumIdColumn).takeIf { it > 0 }
                val trackRaw = cursor.getInt(trackColumn)
                val trackNumber = if (trackRaw > 0) trackRaw % 1000 else null

                musicFiles.add(
                    MusicFile(
                        id = id.toString(),
                        name = name.substringBeforeLast('.', name),
                        path = path,
                        extension = ext,
                        size = size,
                        lastModified = lastModified,
                        duration = duration,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        trackNumber = trackNumber
                    )
                )
            }
        }

        return musicFiles
    }

    companion object {
        private const val FS_MAX_DEPTH = 3        // don't descend too deep
        private const val FS_MAX_FILES = 500       // cap filesystem fallback results
    }

    /**
     * Filesystem fallback: parallel scan of standard music directories only.
     * Deliberately excludes the storage root — on a 512GB device this would
     * walk millions of entries (photos, app data, etc.) for minimal gain.
     */
    private suspend fun scanFileSystemParallel(): List<MusicFile> = coroutineScope {
        val directories = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
        ).filter { it.exists() && it.isDirectory }

        val results = ConcurrentLinkedQueue<MusicFile>()

        directories.map { dir ->
            async(Dispatchers.IO) {
                scanDirectory(dir, results)
            }
        }.awaitAll()

        results.toList()
    }

    private fun scanDirectory(directory: File, results: ConcurrentLinkedQueue<MusicFile>, depth: Int = 0) {
        if (depth > FS_MAX_DEPTH) return
        if (results.size >= FS_MAX_FILES) return  // stop early if cap reached

        try {
            val files = directory.listFiles() ?: return
            for (file in files) {
                if (results.size >= FS_MAX_FILES) return
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
