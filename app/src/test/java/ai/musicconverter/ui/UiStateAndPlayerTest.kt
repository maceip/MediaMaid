package ai.musicconverter.ui

import ai.musicconverter.data.MusicFile
import ai.musicconverter.data.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateAndPlayerTest {

    private fun file(id: String, name: String, artist: String? = null) = MusicFile(
        id = id, name = name, path = "/music/$name.flac",
        extension = "flac", size = 1024, lastModified = 0, artist = artist
    )

    // ── filteredFiles ────────────────────────────────────────

    @Test
    fun `empty search returns all files`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(file("1", "Alpha"), file("2", "Beta")),
            searchQuery = ""
        )
        assertEquals(2, state.filteredFiles.size)
    }

    @Test
    fun `blank search returns all files`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(file("1", "Alpha"), file("2", "Beta")),
            searchQuery = "   "
        )
        assertEquals(2, state.filteredFiles.size)
    }

    @Test
    fun `search filters by name case insensitive`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(file("1", "Alpha"), file("2", "Beta"), file("3", "Gamma")),
            searchQuery = "beta"
        )
        assertEquals(1, state.filteredFiles.size)
        assertEquals("Beta", state.filteredFiles[0].name)
    }

    @Test
    fun `search filters by artist`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(
                file("1", "Track1", artist = "Beatles"),
                file("2", "Track2", artist = "Stones"),
                file("3", "Track3", artist = null)
            ),
            searchQuery = "beatles"
        )
        assertEquals(1, state.filteredFiles.size)
        assertEquals("Track1", state.filteredFiles[0].name)
    }

    @Test
    fun `search matches name OR artist`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(
                file("1", "Yesterday", artist = "Beatles"),
                file("2", "Beatlemania", artist = "Documentary"),
            ),
            searchQuery = "beatle"
        )
        assertEquals(2, state.filteredFiles.size)
    }

    @Test
    fun `search with no matches returns empty`() {
        val state = MusicConverterUiState(
            musicFiles = listOf(file("1", "Alpha"), file("2", "Beta")),
            searchQuery = "zzz"
        )
        assertTrue(state.filteredFiles.isEmpty())
    }

    // ── PlayerState displayPosition ──────────────────────────

    @Test
    fun `displayPosition formats correctly`() {
        val state = PlayerState(position = 90_000L, duration = 300_000L)
        assertEquals("1:30", state.displayPosition)
    }

    @Test
    fun `displayPosition shows placeholder when no duration`() {
        assertEquals("--:--", PlayerState(position = 5000L, duration = 0L).displayPosition)
    }

    @Test
    fun `displayPosition handles zero position`() {
        assertEquals("0:00", PlayerState(position = 0L, duration = 100_000L).displayPosition)
    }

    // ── PlayerState displayDuration ──────────────────────────

    @Test
    fun `displayDuration formats correctly`() {
        assertEquals("5:00", PlayerState(duration = 300_000L).displayDuration)
    }

    @Test
    fun `displayDuration placeholder for zero`() {
        assertEquals("--:--", PlayerState(duration = 0L).displayDuration)
    }

    // ── PlayerState progress ─────────────────────────────────

    @Test
    fun `progress computes fraction`() {
        val state = PlayerState(position = 150_000L, duration = 300_000L)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun `progress is zero when no duration`() {
        assertEquals(0f, PlayerState(position = 100L, duration = 0L).progress, 0.001f)
    }

    @Test
    fun `progress clamped to 1`() {
        val state = PlayerState(position = 500_000L, duration = 300_000L)
        assertEquals(1f, state.progress, 0.001f)
    }
}
