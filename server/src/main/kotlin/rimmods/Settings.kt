package rimmods

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** App settings from `settings.yaml` (docs/user-files.md). */
data class Settings(
    val configDir: Path,
    val modsDir: Path,
    val modsConfigFile: Path,
    val port: Int,
) {
    val libraryFile: Path get() = configDir.resolve("library.yaml")

    companion object {
        /** `-Drmm.configDir` overrides the config folder, e.g. to develop against copies. */
        fun load(
            configDir: Path = System.getProperty("rmm.configDir")?.let(::Path) ?: defaultConfigDir(),
            home: String = System.getProperty("user.home"),
        ): Settings {
            val file = configDir.resolve("settings.yaml")
            val dto = if (file.exists() && file.readText().isNotBlank()) {
                yaml.decodeFromString(SettingsDto.serializer(), file.readText())
            } else {
                SettingsDto()
            }
            return Settings(
                configDir = configDir,
                modsDir = Path(expandHome(dto.modsDir, home)),
                modsConfigFile = Path(expandHome(dto.modsConfigFile, home)),
                port = dto.port,
            )
        }

        fun defaultConfigDir(): Path {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.config"
            return Path(base, "rimmodmanager")
        }

        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))

        private fun expandHome(path: String, home: String) =
            if (path == "~" || path.startsWith("~/")) home + path.removePrefix("~") else path
    }
}

@Serializable
private data class SettingsDto(
    val modsDir: String = "~/.steam/steam/steamapps/workshop/content/294100",
    val modsConfigFile: String = "~/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config/ModsConfig.xml",
    val port: Int = 8765,
)
