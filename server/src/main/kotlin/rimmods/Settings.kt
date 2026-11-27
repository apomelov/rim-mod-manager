package rimmods

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import rimmods.setup.Host
import rimmods.setup.Os
import rimmods.setup.RealHost

class SettingsException(message: String) : Exception(message)

/** App settings from `settings.yaml` (docs/paths.md). */
data class Settings(
    /** The app's config folder: settings.yaml, the default library.yaml, backups. */
    val configDir: Path,
    /** The game's content folder (Data/, Mods/); Core and the DLCs are scanned from it. Null: not scanned. */
    val gameDir: Path?,
    /** The game's config folder, holding ModsConfig.xml. */
    val gameConfigDir: Path,
    val workshopDir: Path? = null,
    val localModsDir: Path? = null,
    val libraryFile: Path = configDir.resolve("library.yaml"),
    val port: Int = DEFAULT_PORT,
) {
    val modsConfigFile: Path get() = gameConfigDir.resolve("ModsConfig.xml")
    val backupDir: Path get() = configDir.resolve("backups")

    /** Mods folders in priority order: a local copy wins over a Workshop one, as in the game. */
    val modsDirs: List<Path> get() = listOfNotNull(localModsDir, workshopDir)

    companion object {
        const val DEFAULT_PORT = 8765

        fun file(configDir: Path): Path = configDir.resolve("settings.yaml")

        /** The settings, or null when there is no settings file yet (the welcome page writes it). */
        fun load(configDir: Path, home: String = System.getProperty("user.home")): Settings? {
            val file = file(configDir)
            if (!file.exists() || file.readText().isBlank()) return null
            val dto = try {
                yaml.decodeFromString(SettingsDto.serializer(), file.readText())
            } catch (e: Exception) {
                throw SettingsException("$file: ${e.message}")
            }
            fun path(s: String?) = s?.takeIf { it.isNotBlank() }?.let { Path(expandHome(it, home)) }
            val gameDir = path(dto.gameDir)
            val gameConfigDir = path(dto.gameConfigDir) ?: path(dto.modsConfigFile)?.parent
                ?: throw SettingsException("$file: gameConfigDir is missing")
            return Settings(
                configDir = configDir,
                gameDir = gameDir,
                gameConfigDir = gameConfigDir,
                workshopDir = path(dto.workshopDir) ?: path(dto.modsDir),
                localModsDir = path(dto.localModsDir) ?: gameDir?.resolve("Mods")?.takeIf { it.isDirectory() },
                libraryFile = path(dto.libraryFile) ?: configDir.resolve("library.yaml"),
                port = dto.port ?: DEFAULT_PORT,
            )
        }

        /** The port to listen on, also before the app is configured. */
        fun port(configDir: Path): Int = runCatching { load(configDir)?.port }.getOrNull() ?: DEFAULT_PORT

        /** Writes settings.yaml (atomically), with absolute paths. */
        fun save(s: Settings) {
            val dto = SettingsDto(
                gameDir = s.gameDir?.toString(),
                gameConfigDir = s.gameConfigDir.toString(),
                workshopDir = s.workshopDir?.toString(),
                localModsDir = s.localModsDir?.toString(),
                libraryFile = s.libraryFile.toString(),
                port = s.port,
            )
            s.configDir.createDirectories()
            val tmp = Files.createTempFile(s.configDir, "settings", ".tmp")
            try {
                tmp.writeText(writer.encodeToString(SettingsDto.serializer(), dto) + "\n")
                Files.move(tmp, file(s.configDir), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(tmp)
            }
        }

        /** The app's config folder for this OS; `-Drmm.configDir` overrides it. */
        fun defaultConfigDir(host: Host = RealHost): Path {
            System.getProperty("rmm.configDir")?.let { return Path(it) }
            return when (host.os) {
                Os.WINDOWS -> (host.env("APPDATA")?.let(::Path) ?: host.home.resolve("AppData/Roaming")).resolve("rimmodmanager")
                Os.MAC -> host.home.resolve("Library/Application Support/rimmodmanager")
                Os.LINUX -> (host.env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::Path) ?: host.home.resolve(".config"))
                    .resolve("rimmodmanager")
            }
        }

        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))
        private val writer = Yaml(
            configuration = YamlConfiguration(encodeDefaults = false, singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous),
        )

        private fun expandHome(path: String, home: String) =
            if (path == "~" || path.startsWith("~/") || path.startsWith("~\\")) home + path.removePrefix("~") else path
    }
}

@Serializable
private data class SettingsDto(
    val gameDir: String? = null,
    val gameConfigDir: String? = null,
    val workshopDir: String? = null,
    val localModsDir: String? = null,
    val libraryFile: String? = null,
    val port: Int? = null,
    /** Old name of [workshopDir]. */
    val modsDir: String? = null,
    /** Old: the ModsConfig.xml file itself; its folder is [gameConfigDir]. */
    val modsConfigFile: String? = null,
)
