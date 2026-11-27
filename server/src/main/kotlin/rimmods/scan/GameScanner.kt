package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Core and the DLCs, from the game's `Data` folder (`Data/<name>/About/About.xml`). Read only. */
object GameScanner {
    /**
     * The game's own icon, used for Core (and as the app's favicon): `<x>_Data/Resources/UnityPlayer.png`, which only
     * Linux builds have (`RimWorldLinux_Data` today, likely `RimWorld_Data` from 1.7).
     */
    private fun icon(gameDir: Path): String? = runCatching {
        gameDir.listDirectoryEntries("*_Data").map { it.resolve("Resources/UnityPlayer.png") }.firstOrNull { it.isRegularFile() }?.toString()
    }.getOrNull()

    fun scan(gameDir: Path?, gameVersion: GameVersion): List<Mod> {
        val data = gameDir?.resolve("Data")?.takeIf { it.isDirectory() } ?: return emptyList()
        val icon = icon(gameDir)
        val core = PackageId.CORE_AND_DLCS.first()
        return ModScanner(gameVersion).scan(data).mods
            .filter { it.packageId.isLudeon }
            .map { m ->
                // Their About.xml has no <name>; the parser then falls back to the packageId.
                val name = if (m.about.name == m.about.packageIdOriginal) Path.of(m.folder).name else m.about.name
                m.copy(about = m.about.copy(name = name), icon = if (m.packageId == core) icon ?: m.icon else m.icon)
            }
            .sortedBy { PackageId.CORE_AND_DLCS.indexOf(it.packageId).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
}
