package rimmods.setup

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class PathChecksTest {
    @TempDir
    lateinit var dir: Path

    private fun valid(): SetupPaths {
        val game = dir.resolve("game")
        game.resolve("Data/Core/About").createDirectories().resolve("About.xml").writeText("<ModMetaData/>")
        game.resolve("Version.txt").writeText("1.6.4871 rev598\n")
        val config = dir.resolve("Config").createDirectories()
        config.resolve("ModsConfig.xml").writeText("<ModsConfigData/>")
        return SetupPaths(game.toString(), config.toString(), "", "", dir.resolve("cfg/library.yaml").toString())
    }

    @Test
    fun `valid paths pass, optional mods folders may be empty, a new library will be created`() {
        val c = PathChecks.check(valid())
        assertTrue(c.ok, c.toString())
        assertEquals("RimWorld 1.6.4871 rev598", c.gameDir.message)
        assertEquals("Will be created", c.libraryFile.message)
    }

    @Test
    fun `each wrong path says why`() {
        val c = PathChecks.check(
            valid().copy(gameDir = dir.toString(), gameConfigDir = "", workshopDir = dir.resolve("nope").toString(), libraryFile = dir.toString()),
        )
        assertFalse(c.ok)
        assertTrue("Data/Core" in c.gameDir.message)
        assertEquals(Check(false, "Required"), c.gameConfigDir)
        assertEquals(Check(false, "Not a folder"), c.workshopDir)
        assertFalse(c.libraryFile.ok)
    }

    @Test
    fun `a config folder without ModsConfig asks to start the game once`() {
        val c = PathChecks.check(valid().copy(gameConfigDir = dir.resolve("empty").createDirectories().toString()))
        assertTrue("start the game once" in c.gameConfigDir.message)
    }
}
