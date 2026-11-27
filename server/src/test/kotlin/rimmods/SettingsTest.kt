package rimmods

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class SettingsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `defaults when the file is missing, with home expanded`() {
        val s = Settings.load(dir, home = "/home/u")
        assertEquals(Path("/home/u/.steam/steam/steamapps/workshop/content/294100"), s.modsDir)
        assertEquals(
            Path("/home/u/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config/ModsConfig.xml"),
            s.modsConfigFile,
        )
        assertEquals(8765, s.port)
        assertEquals(Path("/home/u/.steam/steam/steamapps/common/RimWorld"), s.gameDir)
        assertEquals(dir.resolve("library.yaml"), s.libraryFile)
        assertEquals(dir.resolve("backups"), s.backupDir)
    }

    @Test
    fun `values from the file, missing keys default`() {
        dir.resolve("settings.yaml").writeText("modsDir: ~/mods\nport: 9000\ngameDir: /games/rw\n")
        val s = Settings.load(dir, home = "/home/u")
        assertEquals(Path("/home/u/mods"), s.modsDir)
        assertEquals(Path("/games/rw"), s.gameDir)
        assertEquals(9000, s.port)
        assertEquals(Path("/home/u/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config/ModsConfig.xml"), s.modsConfigFile)
    }

    @Test
    fun `absolute paths are kept`() {
        dir.resolve("settings.yaml").writeText("modsDir: /data/mods\n")
        assertEquals(Path("/data/mods"), Settings.load(dir, home = "/home/u").modsDir)
    }
}
