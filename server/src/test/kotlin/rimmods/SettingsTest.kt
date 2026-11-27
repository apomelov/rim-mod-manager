package rimmods

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import rimmods.setup.Host
import rimmods.setup.Os

class SettingsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `no settings file means not set up`() {
        assertNull(Settings.load(dir, home = "/home/u"))
        assertEquals(8765, Settings.port(dir))
    }

    @Test
    fun `all keys, home expanded, library defaults next to the settings`() {
        dir.resolve("settings.yaml").writeText(
            """
            gameDir: ~/RimWorld
            gameConfigDir: /cfg/Config
            workshopDir: ~/ws
            localModsDir: /local/Mods
            port: 9000
            """.trimIndent(),
        )
        val s = Settings.load(dir, home = "/home/u")!!
        assertEquals(Path("/home/u/RimWorld"), s.gameDir)
        assertEquals(Path("/cfg/Config/ModsConfig.xml"), s.modsConfigFile)
        assertEquals(listOf(Path("/local/Mods"), Path("/home/u/ws")), s.modsDirs)
        assertEquals(dir.resolve("library.yaml"), s.libraryFile)
        assertEquals(dir.resolve("backups"), s.backupDir)
        assertEquals(9000, s.port)
    }

    @Test
    fun `old keys are still read, local Mods defaults to the game's`() {
        val game = dir.resolve("game").also { it.resolve("Mods").createDirectories() }
        dir.resolve("settings.yaml").writeText("gameDir: $game\nmodsDir: /ws\nmodsConfigFile: /cfg/Config/ModsConfig.xml\n")
        val s = Settings.load(dir, home = "/home/u")!!
        assertEquals(Path("/cfg/Config"), s.gameConfigDir)
        assertEquals(listOf(game.resolve("Mods"), Path("/ws")), s.modsDirs)
    }

    @Test
    fun `a file without the game config folder is an error`() {
        dir.resolve("settings.yaml").writeText("gameDir: /g\n")
        assertFailsWith<SettingsException> { Settings.load(dir, home = "/home/u") }
    }

    @Test
    fun `save then load round-trips`() {
        val s = Settings(dir, Path("/g"), Path("/c"), workshopDir = null, localModsDir = Path("/m"), libraryFile = Path("/lib/library.yaml"), port = 9001)
        Settings.save(s)
        assertEquals(s, Settings.load(dir, home = "/home/u"))
    }

    @Test
    fun `app config folder per OS`() {
        fun host(os: Os, vars: Map<String, String> = emptyMap()) = object : Host {
            override val os = os
            override val home = Path("/home/u")
            override val root = Path("/")
            override fun env(name: String) = vars[name]
            override fun registry(key: String, value: String): String? = null
            override fun registrySubkeys(key: String, value: String) = emptyList<String>()
        }
        assertEquals(Path("/home/u/.config/rimmodmanager"), Settings.defaultConfigDir(host(Os.LINUX)))
        assertEquals(Path("/xdg/rimmodmanager"), Settings.defaultConfigDir(host(Os.LINUX, mapOf("XDG_CONFIG_HOME" to "/xdg"))))
        assertEquals(Path("/appdata/rimmodmanager"), Settings.defaultConfigDir(host(Os.WINDOWS, mapOf("APPDATA" to "/appdata"))))
        assertEquals(Path("/home/u/Library/Application Support/rimmodmanager"), Settings.defaultConfigDir(host(Os.MAC)))
    }
}
