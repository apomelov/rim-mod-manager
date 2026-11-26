package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.inputStream

/** The game's `ModsConfig.xml`: the active mod list RimWorld (and RimSort) read. */
data class ModsConfig(
    val version: String,
    val gameVersion: GameVersion,
    val activeMods: List<PackageId>,
    val knownExpansions: List<PackageId>,
)

object ModsConfigReader {
    fun read(file: Path): ModsConfig {
        val root = file.inputStream().buffered().use { Xml.parseRoot(it) }
        val version = root.childText("version") ?: throw IllegalArgumentException("No <version> in $file")
        return ModsConfig(
            version = version,
            gameVersion = GameVersion.parse(version),
            activeMods = root.child("activeMods")?.liTexts()?.map(PackageId::of) ?: emptyList(),
            knownExpansions = root.child("knownExpansions")?.liTexts()?.map(PackageId::of) ?: emptyList(),
        )
    }
}
