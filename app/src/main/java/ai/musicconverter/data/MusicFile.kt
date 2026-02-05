package ai.musicconverter.data

import java.io.File

data class MusicFile(
    val id: String,
    val name: String,
    val path: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val duration: Long = 0L,
    val artist: String? = null,
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f
) {
    val file: File get() = File(path)

    val displayDuration: String
        get() {
            if (duration <= 0) return "--:--"
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val displayTrack: String
        get() {
            val num = trackNumber ?: return ""
            val total = totalTracks
            return if (total != null && total > 0) "$num of $total" else "$num"
        }

    val displaySize: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", kb)
            }
        }

    val needsConversion: Boolean
        get() = extension.lowercase() !in listOf("mp3", "m4a", "aac")

    companion object {
        // Formats supported by Android MediaCodec (used by Media3 Transformer)
        // Note: theora removed (video codec), wma has limited support
        val SUPPORTED_EXTENSIONS = listOf(
            "mp3",   // MPEG Audio Layer III - universal
            "ogg",   // Ogg Vorbis - universal since API 21
            "m4a",   // AAC in MP4 container - universal
            "aac",   // Raw AAC - universal
            "flac",  // Free Lossless Audio - universal since API 21
            "wav",   // PCM audio - universal
            "opus",  // Opus in Ogg - universal since API 21
            "webm",  // WebM (Vorbis/Opus audio) - universal since API 21
            "3gp",   // 3GPP (AMR audio) - universal
            "amr"    // AMR-NB/WB - universal
        )

        fun fromFile(file: File): MusicFile? {
            val ext = file.extension.lowercase()
            if (ext !in SUPPORTED_EXTENSIONS) return null

            return MusicFile(
                id = file.absolutePath.hashCode().toString(),
                name = file.nameWithoutExtension,
                path = file.absolutePath,
                extension = ext,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }
    }
}

enum class ConversionStatus {
    IDLE,
    SCANNING,
    CONVERTING,
    COMPLETED,
    ERROR
}
