package ai.musicconverter.ui

import ai.musicconverter.data.MusicFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the selection → pendingCount → convertFiles interaction.
 *
 * This replicates the exact logic from MusicConverterScreen and the ViewModel's
 * convertFiles() filter to catch mismatches between what the dialog shows and
 * what actually gets converted.
 *
 * Known past bug: deselect all → select one mp3 → convert → dialog said "0 tracks"
 * because pendingCount filtered by needsConversion but the user expected their
 * selection to be respected.
 */
class SelectionConversionTest {

    private lateinit var musicFiles: List<MusicFile>
    private lateinit var checkedFiles: MutableMap<String, Boolean>

    private fun file(id: String, ext: String, name: String = "track_$id") = MusicFile(
        id = id, name = name, path = "/music/$name.$ext",
        extension = ext, size = 1024, lastModified = 0
    )

    /** Replicates MusicConverterScreen's pendingCount calculation */
    private fun pendingCount(): Int = musicFiles.count {
        it.needsConversion && !it.isConverting && (checkedFiles[it.id] ?: true)
    }

    /** Replicates the dialog's selected IDs sent to ViewModel */
    private fun selectedIds(): Set<String> = checkedFiles.filter { it.value }.keys

    /** Replicates ViewModel.convertFiles() filter */
    private fun filesToConvert(fileIds: Set<String>): List<MusicFile> =
        musicFiles.filter { it.id in fileIds && it.needsConversion && !it.isConverting }

    @Before
    fun setup() {
        musicFiles = listOf(
            file("1", "flac"),  // needs conversion
            file("2", "mp3"),   // does NOT need conversion
            file("3", "ogg"),   // needs conversion
            file("4", "m4a"),   // does NOT need conversion
            file("5", "wav"),   // needs conversion
        )
        checkedFiles = mutableMapOf()

        // Replicate CondensedMediaList defaulting: all files start checked
        musicFiles.forEach { if (it.id !in checkedFiles) checkedFiles[it.id] = true }
    }

    // ── Default state ────────────────────────────────────────

    @Test
    fun `default state counts all convertible files`() {
        // flac + ogg + wav = 3
        assertEquals(3, pendingCount())
    }

    @Test
    fun `default selectedIds includes all files`() {
        assertEquals(5, selectedIds().size)
    }

    @Test
    fun `default filesToConvert matches pendingCount`() {
        val ids = selectedIds()
        assertEquals(pendingCount(), filesToConvert(ids).size)
    }

    // ── Deselect all → select one convertible ────────────────

    @Test
    fun `deselect all then select flac shows 1 pending`() {
        // Deselect all
        musicFiles.forEach { checkedFiles[it.id] = false }

        // Select only the flac file
        checkedFiles["1"] = true

        assertEquals(1, pendingCount())
        assertEquals(1, filesToConvert(selectedIds()).size)
    }

    // ── Deselect all → select one non-convertible (THE BUG) ──

    @Test
    fun `deselect all then select mp3 shows 0 pending`() {
        musicFiles.forEach { checkedFiles[it.id] = false }
        checkedFiles["2"] = true

        // mp3 doesn't need conversion, so pending is 0
        assertEquals(0, pendingCount())
        // This is the bug scenario: user selected 1 file but dialog shows "0"
        // The fix: don't show the dialog when pendingCount == 0
    }

    @Test
    fun `pendingCount and filesToConvert always agree`() {
        // For any selection state, these two must match
        musicFiles.forEach { checkedFiles[it.id] = false }
        checkedFiles["2"] = true  // mp3

        assertEquals(pendingCount(), filesToConvert(selectedIds()).size)
    }

    // ── Mixed selection ──────────────────────────────────────

    @Test
    fun `mixed selection counts only convertible checked files`() {
        musicFiles.forEach { checkedFiles[it.id] = false }
        checkedFiles["1"] = true   // flac - convertible
        checkedFiles["2"] = true   // mp3 - not convertible
        checkedFiles["3"] = true   // ogg - convertible

        assertEquals(2, pendingCount())
        assertEquals(2, filesToConvert(selectedIds()).size)
    }

    // ── Files not in checkedFiles default to true ────────────

    @Test
    fun `files absent from checkedFiles default to checked via ternary`() {
        // Simulate new files appearing that haven't been defaulted yet
        val freshChecked = mutableMapOf<String, Boolean>()
        val count = musicFiles.count {
            it.needsConversion && !it.isConverting && (freshChecked[it.id] ?: true)
        }
        // All convertible files should be counted (flac + ogg + wav = 3)
        assertEquals(3, count)
    }

    @Test
    fun `partially populated checkedFiles - unchecked files not counted`() {
        // Only file "1" explicitly unchecked, others not in map (default true)
        val partial = mutableMapOf("1" to false)
        val count = musicFiles.count {
            it.needsConversion && !it.isConverting && (partial[it.id] ?: true)
        }
        // ogg + wav = 2 (flac is unchecked, mp3/m4a don't need conversion)
        assertEquals(2, count)
    }

    // ── Converting files are excluded ────────────────────────

    @Test
    fun `files currently converting are excluded from pendingCount`() {
        val filesWithConverting = musicFiles.map {
            if (it.id == "1") it.copy(isConverting = true) else it
        }
        musicFiles = filesWithConverting

        // flac is converting so only ogg + wav = 2
        assertEquals(2, pendingCount())
    }

    @Test
    fun `filesToConvert excludes currently converting files`() {
        val filesWithConverting = musicFiles.map {
            if (it.id == "1") it.copy(isConverting = true) else it
        }
        musicFiles = filesWithConverting

        val result = filesToConvert(selectedIds())
        assertEquals(2, result.size)
        assertTrue(result.none { it.id == "1" })
    }

    // ── Tab filtering interaction ────────────────────────────

    @Test
    fun `convert tab only shows convertible files for selection`() {
        // Tab 1 filters: files.filter { it.needsConversion }
        val convertTabFiles = musicFiles.filter { it.needsConversion }
        assertEquals(3, convertTabFiles.size)
        assertTrue(convertTabFiles.all { it.needsConversion })
    }

    // ── Edge cases ───────────────────────────────────────────

    @Test
    fun `empty file list produces zero counts`() {
        musicFiles = emptyList()
        assertEquals(0, pendingCount())
        assertTrue(filesToConvert(selectedIds()).isEmpty())
    }

    @Test
    fun `all files already converted produces zero pending`() {
        musicFiles = listOf(file("1", "mp3"), file("2", "m4a"), file("3", "aac"))
        musicFiles.forEach { checkedFiles[it.id] = true }
        assertEquals(0, pendingCount())
    }
}
