package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ModScannerTest {
    @TempDir
    lateinit var dir: Path

    private val scanner = ModScanner(GameVersion.parse("1.6"))

    private fun mod(folder: String, packageId: String?, publishedFileId: String? = null) {
        val about = dir.resolve(folder).resolve("About").createDirectories()
        val idTag = packageId?.let { "<packageId>$it</packageId>" } ?: ""
        about.resolve("About.xml").writeText("<ModMetaData>$idTag<name>$folder</name></ModMetaData>")
        publishedFileId?.let { about.resolve("PublishedFileId.txt").writeText(it) }
    }

    @Test
    fun `scans every mod folder`() {
        mod("111", "a.one", "111")
        mod("222", "a.two", "222")
        val result = scanner.scan(dir)
        assertEquals(listOf("a.one", "a.two"), result.mods.map { it.packageId.value })
        assertEquals(emptyList(), result.problems)
    }

    @Test
    fun `workshop id comes from PublishedFileId, else from a numeric folder name`() {
        mod("111", "a.one", " 999\n")
        mod("222", "a.two")
        mod("local", "a.three")
        val ids = scanner.scan(dir).mods.associate { it.packageId.value to it.workshopId }
        assertEquals(mapOf("a.one" to "999", "a.two" to "222", "a.three" to null), ids)
    }

    @Test
    fun `bad mods become problems and do not stop the scan`() {
        mod("111", "a.one")
        mod("222", null) // no packageId
        dir.resolve("333").createDirectories() // no About.xml
        dir.resolve("stray.txt").writeText("not a mod")
        val result = scanner.scan(dir)
        assertEquals(listOf("a.one"), result.mods.map { it.packageId.value })
        assertEquals(2, result.problems.size)
        assertTrue(result.problems.any { it.folder.endsWith("222") && "packageId" in it.message })
        assertTrue(result.problems.any { it.folder.endsWith("333") && "About.xml" in it.message })
    }

    @Test
    fun `a folder with only dds textures left is flagged as removable leftovers`() {
        val textures = dir.resolve("444").resolve("1.6").resolve("Textures").createDirectories()
        textures.resolve("a.dds").writeText("dds")
        textures.resolve("B.DDS").writeText("dds")
        val problem = scanner.scan(dir).problems.single()
        assertTrue(problem.texturesOnly)
        assertTrue(".dds" in problem.message, problem.message)
    }

    @Test
    fun `a folder without About xml but with other files is not removable`() {
        val textures = dir.resolve("555").resolve("Textures").createDirectories()
        textures.resolve("a.dds").writeText("dds")
        textures.resolve("a.png").writeText("png")
        dir.resolve("666").createDirectories() // empty
        val problems = scanner.scan(dir).problems
        assertEquals(listOf(false, false), problems.map { it.texturesOnly })
        assertTrue(problems.all { "About.xml" in it.message && "removed mod" in it.message }, problems.toString())
    }

    @Test
    fun `scan resolves preview and icon`() {
        mod("111", "a.one")
        val about = dir.resolve("111").resolve("About")
        about.resolve("Preview.png").writeText("png")
        about.resolve("ModIcon.png").writeText("png")
        val m = scanner.scan(dir).mods.single()
        assertEquals(about.resolve("Preview.png").toString(), m.preview)
        assertEquals(about.resolve("ModIcon.png").toString(), m.icon)
    }

    @Test
    fun `duplicate packageIds keep the first and report all`() {
        mod("111", "Same.Id")
        mod("222", "same.id")
        val result = scanner.scan(dir)
        assertEquals(listOf(dir.resolve("111").toString()), result.mods.map { it.folder })
        assertEquals(2, result.problems.count { "Duplicate" in it.message })
    }

    @Test
    fun `several mods folders - the first one's copy wins, the duplicate is reported, a missing folder too`() {
        mod("local/A", "a.one")
        mod("workshop/111", "a.one")
        mod("workshop/222", "a.two")
        val result = scanner.scan(listOf(dir.resolve("local"), dir.resolve("workshop"), dir.resolve("gone")))
        assertEquals(listOf("a.one", "a.two"), result.mods.map { it.packageId.value })
        assertTrue(result.mods.first().folder.endsWith("local/A"))
        assertTrue(result.problems.any { "Duplicate" in it.message && it.folder.endsWith("workshop/111") })
        assertTrue(result.problems.any { it.folder.endsWith("gone") && "not found" in it.message })
    }
}
