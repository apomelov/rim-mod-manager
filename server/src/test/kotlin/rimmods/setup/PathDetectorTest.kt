package rimmods.setup

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class PathDetectorTest {
    @TempDir
    lateinit var tmp: Path

    private val home get() = tmp.resolve("home")

    private class FakeHost(
        override val os: Os,
        override val home: Path,
        override val root: Path,
        val vars: Map<String, String> = emptyMap(),
        val reg: Map<Pair<String, String>, String> = emptyMap(),
        val regSub: Map<Pair<String, String>, List<String>> = emptyMap(),
    ) : Host {
        override fun env(name: String) = vars[name]
        override fun registry(key: String, value: String) = reg[key to value]
        override fun registrySubkeys(key: String, value: String) = regSub[key to value].orEmpty()
    }

    private fun host(os: Os, vars: Map<String, String> = emptyMap(), reg: Map<Pair<String, String>, String> = emptyMap(), regSub: Map<Pair<String, String>, List<String>> = emptyMap()) =
        FakeHost(os, home, tmp.resolve("root"), vars, reg, regSub)

    private fun dir(p: Path): Path = p.createDirectories()

    /** A game content folder: Data/Core/About/About.xml and Mods/. */
    private fun game(at: Path): Path {
        dir(at.resolve("Data/Core/About")).resolve("About.xml").writeText("<ModMetaData><packageId>Ludeon.RimWorld</packageId></ModMetaData>")
        dir(at.resolve("Mods"))
        return at
    }

    private fun steam(at: Path, libraries: List<Pair<Path, Boolean>> = emptyList()): Path {
        dir(at.resolve("steamapps"))
        if (libraries.isNotEmpty()) {
            val entries = libraries.withIndex().joinToString("\n") { (i, l) ->
                val apps = if (l.second) "\"294100\" \"1\"" else "\"228980\" \"1\""
                "\"$i\" { \"path\" \"${l.first.toString().replace("\\", "\\\\")}\" \"apps\" { $apps } }"
            }
            dir(at.resolve("config")).resolve("libraryfolders.vdf").writeText("\"libraryfolders\" { $entries }")
        }
        return at
    }

    private fun modsConfig(dir: Path) = dir(dir).also { it.resolve("ModsConfig.xml").writeText("<ModsConfigData/>") }

    private val unity = "Ludeon Studios/RimWorld by Ludeon Studios/Config"

    @Test
    fun `Linux - Steam library from libraryfolders, symlinked roots seen once`() {
        val real = steam(home.resolve(".steam/debian-installation"))
        Files.createSymbolicLink(home.resolve(".steam/steam"), real)
        val library = dir(tmp.resolve("games/SteamLibrary"))
        steam(real, listOf(real to false, library to true))
        val gameDir = game(library.resolve("steamapps/common/RimWorld"))
        val workshop = dir(library.resolve("steamapps/workshop/content/294100"))
        val config = modsConfig(home.resolve(".config/unity3d/$unity"))

        assertEquals(
            DetectedPaths(gameDir.toRealPath(), config.toRealPath(), workshop.toRealPath(), gameDir.resolve("Mods").toRealPath()),
            PathDetector.detect(host(Os.LINUX)),
        )
    }

    @Test
    fun `Linux - Proton config wins, then Flatpak, then the native one`() {
        val root = steam(home.resolve(".local/share/Steam"))
        game(root.resolve("steamapps/common/RimWorld"))
        modsConfig(home.resolve(".config/unity3d/$unity"))
        val flatpak = modsConfig(home.resolve(".var/app/com.valvesoftware.Steam/.config/unity3d/$unity"))
        assertEquals(flatpak.toRealPath(), PathDetector.detect(host(Os.LINUX)).gameConfigDir)

        val proton = modsConfig(root.resolve("steamapps/compatdata/294100/pfx/drive_c/users/steamuser/AppData/LocalLow/$unity"))
        assertEquals(proton.toRealPath(), PathDetector.detect(host(Os.LINUX)).gameConfigDir)
    }

    @Test
    fun `a config folder holding ModsConfig is preferred over an empty one`() {
        dir(home.resolve(".var/app/com.valvesoftware.Steam/.config/unity3d/$unity")) // empty
        val native = modsConfig(home.resolve(".config/unity3d/$unity"))
        assertEquals(native.toRealPath(), PathDetector.detect(host(Os.LINUX)).gameConfigDir)
    }

    @Test
    fun `Windows - Steam from the registry, config under the user profile`() {
        val root = steam(tmp.resolve("C/Program Files (x86)/Steam"))
        val gameDir = game(root.resolve("steamapps/common/RimWorld"))
        val profile = tmp.resolve("C/Users/me")
        val config = modsConfig(profile.resolve("AppData/LocalLow/$unity"))
        val h = host(Os.WINDOWS, vars = mapOf("USERPROFILE" to profile.toString()), reg = mapOf(("HKLM\\SOFTWARE\\Wow6432Node\\Valve\\Steam" to "InstallPath") to root.toString()))
        val found = PathDetector.detect(h)
        assertEquals(gameDir.toRealPath(), found.gameDir)
        assertEquals(config.toRealPath(), found.gameConfigDir)
        assertNull(found.workshopDir)
    }

    @Test
    fun `Windows - without the registry, Program Files (x86) is tried`() {
        val programFiles = tmp.resolve("C/Program Files (x86)")
        val gameDir = game(steam(programFiles.resolve("Steam")).resolve("steamapps/common/RimWorld"))
        assertEquals(gameDir.toRealPath(), PathDetector.detect(host(Os.WINDOWS, vars = mapOf("ProgramFiles(x86)" to programFiles.toString()))).gameDir)
    }

    @Test
    fun `macOS - content inside the app bundle, or next to it`() {
        val common = steam(home.resolve("Library/Application Support/Steam")).resolve("steamapps/common/RimWorld")
        val bundle = game(dir(common.resolve("RimWorldMac.app")))
        dir(bundle.resolve("Contents"))
        val config = modsConfig(home.resolve("Library/Application Support/RimWorld/Config"))
        val found = PathDetector.detect(host(Os.MAC))
        assertEquals(bundle.toRealPath(), found.gameDir)
        assertEquals(config.toRealPath(), found.gameConfigDir)

        val sibling = tmp.resolve("sibling")
        dir(sibling.resolve("RimWorld.app/Contents"))
        game(sibling)
        assertEquals(sibling, PathDetector.contentFolder(sibling))
    }

    @Test
    fun `macOS - a GOG copy in Applications when Steam has none`() {
        val app = game(dir(tmp.resolve("root/Applications/RimWorld.app")))
        assertEquals(app.toRealPath(), PathDetector.detect(host(Os.MAC)).gameDir)
    }

    @Test
    fun `GOG via Heroic, the GOG registry, or a shallow search`() {
        val heroicGame = game(tmp.resolve("heroic/RimWorld"))
        dir(home.resolve(".config/heroic/gog_store")).resolve("installed.json")
            .writeText("""{"installed":[{"appName":"x","install_path":"${heroicGame.toString().replace("\\", "\\\\")}"}]}""")
        assertEquals(heroicGame.toRealPath(), PathDetector.detect(host(Os.LINUX)).gameDir)

        val gog = game(tmp.resolve("gog/RimWorld"))
        val win = host(Os.WINDOWS, regSub = mapOf(("HKLM\\SOFTWARE\\WOW6432Node\\GOG.com\\Games" to "path") to listOf(gog.toString())))
        assertEquals(gog.toRealPath(), PathDetector.detect(win).gameDir)
    }

    @Test
    fun `shallow search finds a game two levels down`() {
        val deep = game(home.resolve("Games/Strategy/RimWorld"))
        assertEquals(deep.toRealPath(), PathDetector.detect(host(Os.LINUX)).gameDir)
    }

    @Test
    fun `nothing installed, nothing found`() {
        assertEquals(DetectedPaths(), PathDetector.detect(host(Os.LINUX)))
    }
}
