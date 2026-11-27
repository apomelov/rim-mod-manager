package rimmods.setup

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class Os {
    LINUX, WINDOWS, MAC;

    companion object {
        fun current(): Os = System.getProperty("os.name").lowercase().let {
            when {
                it.startsWith("windows") -> WINDOWS
                it.startsWith("mac") -> MAC
                else -> LINUX
            }
        }
    }
}

/** Everything detection looks at besides the file system; tests replace it. */
interface Host {
    val os: Os
    val home: Path

    /** Where absolute system folders like `/Applications` are looked up (the file system root). */
    val root: Path
    fun env(name: String): String?

    /** A Windows registry value, e.g. `registry("HKCU\\Software\\Valve\\Steam", "SteamPath")`. */
    fun registry(key: String, value: String): String?

    /** Value [value] of every subkey of [key] (Windows). */
    fun registrySubkeys(key: String, value: String): List<String>
}

data class DetectedPaths(
    val gameDir: Path? = null,
    val gameConfigDir: Path? = null,
    val workshopDir: Path? = null,
    val localModsDir: Path? = null,
)

/** Finds the game, its config and the mods folders, as specified in docs/paths.md. Read only. */
object PathDetector {
    const val APP_ID = "294100"
    private const val UNITY_CONFIG = "Ludeon Studios/RimWorld by Ludeon Studios/Config"

    fun detect(host: Host): DetectedPaths {
        val steamRoots = steamRoots(host)
        var gameDir: Path? = null
        var workshopDir: Path? = null
        for (steam in steamRoots) {
            val library = libraryWithRimWorld(steam) ?: steam
            gameDir = contentFolder(library.resolve("steamapps/common/RimWorld")) ?: continue
            workshopDir = library.resolve("steamapps/workshop/content/$APP_ID").takeIf { it.isDirectory() }
            break
        }
        if (gameDir == null) gameDir = nonSteamGame(host)
        return DetectedPaths(
            gameDir = gameDir?.let(::real),
            gameConfigDir = gameConfigDir(host, steamRoots),
            workshopDir = workshopDir?.let(::real),
            localModsDir = gameDir?.resolve("Mods")?.takeIf { it.isDirectory() }?.let(::real),
        )
    }

    /** The folder holding `Data/Core/About/About.xml`: [dir] itself, or for a macOS `.app` inside it, the bundle or its parent. */
    fun contentFolder(dir: Path): Path? {
        if (!dir.isDirectory()) return null
        if (isContent(dir)) return dir
        val bundles = children(dir).filter { it.isDirectory() && it.name.endsWith(".app", ignoreCase = true) }
            .sortedBy { listOf("RimWorld.app", "RimWorldMac.app").indexOf(it.name).let { i -> if (i < 0) 99 else i } }
        return bundles.firstOrNull { isContent(it) }
    }

    fun isContent(dir: Path) = dir.resolve("Data/Core/About/About.xml").isRegularFile()

    private fun steamRoots(host: Host): List<Path> {
        val h = host.home
        val candidates: List<Path?> = when (host.os) {
            Os.LINUX -> listOf(
                h.resolve(".steam/debian-installation"),
                h.resolve(".steam/steam"),
                h.resolve(".local/share/Steam"),
                h.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"),
                h.resolve("snap/steam/common/.local/share/Steam"),
            )
            Os.WINDOWS -> listOf(
                host.registry("HKCU\\Software\\Valve\\Steam", "SteamPath")?.let(::Path),
                host.registry("HKLM\\SOFTWARE\\Wow6432Node\\Valve\\Steam", "InstallPath")?.let(::Path),
                host.registry("HKLM\\SOFTWARE\\Valve\\Steam", "InstallPath")?.let(::Path),
                host.env("ProgramFiles(x86)")?.let { Path(it, "Steam") },
            )
            Os.MAC -> listOf(h.resolve("Library/Application Support/Steam"))
        }
        return candidates.filterNotNull()
            .filter { it.isDirectory() && (it.resolve("steamapps").isDirectory() || it.resolve("config/libraryfolders.vdf").isRegularFile()) }
            .map(::real)
            .distinct()
    }

    /** The Steam library whose `apps` list RimWorld, from `libraryfolders.vdf`. */
    private fun libraryWithRimWorld(steam: Path): Path? {
        val file = listOf("config/libraryfolders.vdf", "steamapps/libraryfolders.vdf").map(steam::resolve).firstOrNull { it.isRegularFile() }
            ?: return null
        val folders = runCatching { Vdf.parse(file.readText())["libraryfolders"] as? Vdf.Block }.getOrNull() ?: return null
        return folders.entries.asSequence()
            .mapNotNull { it.second as? Vdf.Block }
            .filter { (it["apps"] as? Vdf.Block)?.get(APP_ID) != null }
            .mapNotNull { (it["path"] as? Vdf.Text)?.value?.let(::Path) }
            .firstOrNull { it.isDirectory() }
    }

    /** GOG, Heroic, standalone copies: only tried when Steam has no RimWorld. */
    private fun nonSteamGame(host: Host): Path? {
        val h = host.home
        val heroic = when (host.os) {
            Os.WINDOWS -> host.env("APPDATA")?.let { Path(it, "heroic/gog_store/installed.json") }
            Os.LINUX -> h.resolve(".config/heroic/gog_store/installed.json")
            Os.MAC -> null
        }
        val direct: List<Path> = buildList {
            if (host.os == Os.WINDOWS) {
                addAll(host.registrySubkeys("HKLM\\SOFTWARE\\WOW6432Node\\GOG.com\\Games", "path").map(::Path))
            }
            heroic?.let { addAll(heroicInstalls(it)) }
        }
        direct.firstNotNullOfOrNull(::contentFolder)?.let { return it }

        val searched = when (host.os) {
            Os.WINDOWS -> listOfNotNull(host.env("SystemDrive")?.let { Path("$it\\", "GOG Games") }, h.resolve("GOG Games"), h.resolve("Games"))
            Os.LINUX -> listOf(h.resolve("GOG Games"), h.resolve("Games"))
            Os.MAC -> listOf(host.root.resolve("Applications"), h.resolve("Applications"))
        }
        for (base in searched.filter { it.isDirectory() }) {
            contentFolder(base)?.let { return it }
            for (child in children(base).filter { it.isDirectory() && !Files.isSymbolicLink(it) }) {
                contentFolder(child)?.let { return it }
                if (host.os == Os.MAC) continue
                for (grandchild in children(child).filter { it.isDirectory() && !Files.isSymbolicLink(it) }) {
                    contentFolder(grandchild)?.let { return it }
                }
            }
        }
        return null
    }

    private fun heroicInstalls(file: Path): List<Path> = runCatching {
        val installed = Json.parseToJsonElement(file.readText()) as JsonObject
        (installed["installed"] as JsonArray).mapNotNull { (it as JsonObject)["install_path"]?.jsonPrimitive?.contentOrNull?.let(::Path) }
    }.getOrDefault(emptyList())

    /** The first existing candidate that holds ModsConfig.xml, else the first existing one. */
    private fun gameConfigDir(host: Host, steamRoots: List<Path>): Path? {
        val h = host.home
        val candidates: List<Path> = when (host.os) {
            Os.LINUX -> steamRoots.map { it.resolve("steamapps/compatdata/$APP_ID/pfx/drive_c/users/steamuser/AppData/LocalLow/$UNITY_CONFIG") } +
                listOfNotNull(
                    h.resolve(".var/app/com.valvesoftware.Steam/.config/unity3d/$UNITY_CONFIG"),
                    host.env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { Path(it, "unity3d/$UNITY_CONFIG") },
                    h.resolve(".config/unity3d/$UNITY_CONFIG"),
                )
            Os.WINDOWS -> listOf((host.env("USERPROFILE")?.let(::Path) ?: h).resolve("AppData/LocalLow/$UNITY_CONFIG"))
            Os.MAC -> h.resolve("Library/Application Support").let { support ->
                children(support).filter { it.name.equals("RimWorld", ignoreCase = true) }.map { it.resolve("Config") }
            }
        }
        val existing = candidates.filter { it.isDirectory() }
        return (existing.firstOrNull { it.resolve("ModsConfig.xml").isRegularFile() } ?: existing.firstOrNull())?.let(::real)
    }

    private fun children(dir: Path): List<Path> =
        if (dir.isDirectory()) runCatching { dir.listDirectoryEntries().sortedBy { it.name }.take(500) }.getOrDefault(emptyList()) else emptyList()

    private fun real(p: Path): Path = runCatching { p.toRealPath() }.getOrDefault(p)
}
