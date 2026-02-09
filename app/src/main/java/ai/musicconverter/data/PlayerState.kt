package ai.musicconverter.data

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentAlbum: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    val hasMedia: Boolean = false
) {
    val displayPosition: String
        get() {
            if (duration <= 0) return "--:--"
            val totalSeconds = (position / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val displayDuration: String
        get() {
            if (duration <= 0) return "--:--"
            val totalSeconds = (duration / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}
