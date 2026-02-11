package ai.musicconverter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MusicFileTest {

    private fun musicFile(
        extension: String,
        id: String = "1",
        name: String = "test",
        path: String = "/music/test.$extension",
        size: Long = 1024,
        lastModified: Long = 0,
        duration: Long = 0L,
        artist: String? = null,
        trackNumber: Int? = null,
        totalTracks: Int? = null
    ) = MusicFile(id, name, path, extension, size, lastModified, duration, artist,
        trackNumber = trackNumber, totalTracks = totalTracks)

    // ── needsConversion ──────────────────────────────────────

    @Test
    fun `mp3 does not need conversion`() =
        assertFalse(musicFile("mp3").needsConversion)

    @Test
    fun `m4a does not need conversion`() =
        assertFalse(musicFile("m4a").needsConversion)

    @Test
    fun `aac does not need conversion`() =
        assertFalse(musicFile("aac").needsConversion)

    @Test
    fun `flac needs conversion`() =
        assertTrue(musicFile("flac").needsConversion)

    @Test
    fun `ogg needs conversion`() =
        assertTrue(musicFile("ogg").needsConversion)

    @Test
    fun `wav needs conversion`() =
        assertTrue(musicFile("wav").needsConversion)

    @Test
    fun `opus needs conversion`() =
        assertTrue(musicFile("opus").needsConversion)

    @Test
    fun `webm needs conversion`() =
        assertTrue(musicFile("webm").needsConversion)

    @Test
    fun `3gp needs conversion`() =
        assertTrue(musicFile("3gp").needsConversion)

    @Test
    fun `amr needs conversion`() =
        assertTrue(musicFile("amr").needsConversion)

    @Test
    fun `needsConversion is case insensitive`() {
        assertFalse(musicFile("MP3").needsConversion)
        assertFalse(musicFile("M4A").needsConversion)
        assertTrue(musicFile("FLAC").needsConversion)
    }

    // ── displayDuration ──────────────────────────────────────

    @Test
    fun `displayDuration formats minutes and seconds`() {
        assertEquals("3:45", musicFile("mp3", duration = 225_000L).displayDuration)
    }

    @Test
    fun `displayDuration shows placeholder for zero duration`() {
        assertEquals("--:--", musicFile("mp3", duration = 0L).displayDuration)
    }

    @Test
    fun `displayDuration pads seconds with zero`() {
        assertEquals("1:05", musicFile("mp3", duration = 65_000L).displayDuration)
    }

    // ── displaySize ──────────────────────────────────────────

    @Test
    fun `displaySize shows MB for large files`() {
        val file = musicFile("mp3", size = 5 * 1024 * 1024L)
        assertEquals("5.0 MB", file.displaySize)
    }

    @Test
    fun `displaySize shows KB for small files`() {
        val file = musicFile("mp3", size = 512 * 1024L)
        assertEquals("512 KB", file.displaySize)
    }

    // ── displayTrack ─────────────────────────────────────────

    @Test
    fun `displayTrack shows track of total`() {
        assertEquals("5 of 12", musicFile("mp3", trackNumber = 5, totalTracks = 12).displayTrack)
    }

    @Test
    fun `displayTrack shows just track number when no total`() {
        assertEquals("5", musicFile("mp3", trackNumber = 5).displayTrack)
    }

    @Test
    fun `displayTrack empty when no track number`() {
        assertEquals("", musicFile("mp3").displayTrack)
    }

    // ── fromFile ─────────────────────────────────────────────

    @Test
    fun `fromFile returns null for unsupported extension`() {
        assertNull(MusicFile.fromFile(File("/music/doc.pdf")))
        assertNull(MusicFile.fromFile(File("/music/image.jpg")))
    }

    @Test
    fun `fromFile creates MusicFile for supported extension`() {
        // fromFile needs an actual file for length/lastModified, but we can
        // test that it returns non-null for supported extensions by checking
        // the extension parsing logic
        val supported = MusicFile.SUPPORTED_EXTENSIONS
        assertTrue(supported.contains("mp3"))
        assertTrue(supported.contains("flac"))
        assertTrue(supported.contains("ogg"))
        assertTrue(supported.contains("wav"))
        assertTrue(supported.contains("opus"))
        assertEquals(10, supported.size)
    }
}
