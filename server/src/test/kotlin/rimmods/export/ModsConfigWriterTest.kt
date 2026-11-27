package rimmods.export

import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import rimmods.scan.ModsConfigReader
import rimmods.scan.PackageId

class ModsConfigWriterTest {
    @TempDir
    lateinit var dir: Path

    private val original = """
        <?xml version="1.0" ?>
        <ModsConfigData>
          <version>1.6.4871 rev598</version>
          <activeMods>
            <li>brrainz.harmony</li>
            <li>ludeon.rimworld</li>
          </activeMods>
          <knownExpansions>
            <li>ludeon.rimworld.royalty</li>
            <li>ludeon.rimworld.biotech</li>
          </knownExpansions>
          <somethingElse attr="x">kept</somethingElse>
        </ModsConfigData>
    """.trimIndent()

    private val now = LocalDateTime.of(2026, 11, 27, 10, 15, 30)

    private fun write(mods: List<String>, at: LocalDateTime = now): Path {
        val file = dir.resolve("ModsConfig.xml")
        ModsConfigWriter.write(file, mods.map(PackageId::of), dir.resolve("backups"), at)
        return file
    }

    @Test
    fun `replaces the active mods, lowercased, and keeps everything else`() {
        dir.resolve("ModsConfig.xml").writeText(original)
        val file = write(listOf("Ludeon.RimWorld", "Some.Mod"))

        val read = ModsConfigReader.read(file)
        assertEquals("1.6.4871 rev598", read.version)
        assertEquals(listOf("ludeon.rimworld", "some.mod"), read.activeMods.map { it.value })
        assertEquals(listOf("ludeon.rimworld.royalty", "ludeon.rimworld.biotech"), read.knownExpansions.map { it.value })
        val text = file.readText()
        assertTrue("<li>some.mod</li>" in text, text)
        assertTrue("""<somethingElse attr="x">kept</somethingElse>""" in text, text)
        assertFalse("brrainz.harmony" in text, text)
        assertTrue(text.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"), text)
    }

    @Test
    fun `backs up the previous file, byte for byte, under a timestamped name`() {
        dir.resolve("ModsConfig.xml").writeText(original)
        write(listOf("ludeon.rimworld"))
        write(listOf("ludeon.rimworld", "a.mod")) // same second: a second backup, not an overwrite

        val backups = dir.resolve("backups").listDirectoryEntries().map { it.name }.sorted()
        assertEquals(listOf("ModsConfig-2026-11-27T10-15-30-2.xml", "ModsConfig-2026-11-27T10-15-30.xml"), backups)
        assertEquals(original, dir.resolve("backups").resolve("ModsConfig-2026-11-27T10-15-30.xml").readText())
    }

    @Test
    fun `a missing or unreadable file is an error and nothing is written`() {
        assertFailsWith<ModsConfigWriteException> { write(listOf("ludeon.rimworld")) }
        assertFalse(dir.resolve("ModsConfig.xml").exists())

        dir.resolve("ModsConfig.xml").writeText("<ModsConfigData><activeMods>")
        assertFailsWith<ModsConfigWriteException> { write(listOf("ludeon.rimworld")) }
        assertEquals("<ModsConfigData><activeMods>", dir.resolve("ModsConfig.xml").readText())
        assertFalse(dir.resolve("backups").exists())
    }

    @Test
    fun `a file without version is refused (the game would reset the list)`() {
        dir.resolve("ModsConfig.xml").writeText("<ModsConfigData><activeMods/></ModsConfigData>")
        assertFailsWith<ModsConfigWriteException> { write(listOf("ludeon.rimworld")) }
    }
}
