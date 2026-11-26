package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class ModsConfigReaderTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `reads version, active mods and expansions`() {
        val file = dir.resolve("ModsConfig.xml")
        file.writeText(
            """
            <?xml version="1.0" ?>
            <ModsConfigData>
              <version>1.6.4871 rev598</version>
              <activeMods><li>brrainz.harmony</li><li>Ludeon.RimWorld</li></activeMods>
              <knownExpansions><li>ludeon.rimworld.royalty</li></knownExpansions>
            </ModsConfigData>
            """.trimIndent(),
        )
        val config = ModsConfigReader.read(file)
        assertEquals("1.6.4871 rev598", config.version)
        assertEquals(GameVersion.parse("1.6"), config.gameVersion)
        assertEquals(listOf("brrainz.harmony", "ludeon.rimworld"), config.activeMods.map { it.value })
        assertEquals(listOf("ludeon.rimworld.royalty"), config.knownExpansions.map { it.value })
    }

    @Test
    fun `game version parsing`() {
        assertEquals("1.6", GameVersion.parse("1.6").value)
        assertEquals("1.6", GameVersion.parse("1.6.4871 rev598").value)
    }
}
