package rimmods.api

import rimmods.Settings
import java.util.concurrent.ConcurrentHashMap
import rimmods.scan.GameScanner
import rimmods.translations.ModKeySets
import rimmods.scan.GameVersion
import rimmods.scan.Mod
import rimmods.scan.ModScanner
import rimmods.scan.ModsConfigReader
import rimmods.scan.PackageId
import rimmods.scan.ScanResult

/** The latest disk scan. Rescanned when the library is (re)loaded; detail and image requests use it as is. */
class AppState(private val settings: Settings) {
    /** [game]: Core and the DLCs from the game folder; [scan]: the Workshop mods folder. */
    data class Snapshot(val gameVersion: GameVersion, val scan: ScanResult, val game: List<Mod> = emptyList()) {
        val byId: Map<PackageId, Mod> = (game + scan.mods).associateBy { it.packageId }

        /** Translation keys per mod, read on first use (seconds for a few hundred mods) and kept until the next rescan. */
        val keys: MutableMap<PackageId, ModKeySets> = ConcurrentHashMap()
    }

    @Volatile
    private var snapshot: Snapshot? = null

    fun rescan(): Snapshot {
        val config = ModsConfigReader.read(settings.modsConfigFile)
        val scan = ModScanner(config.gameVersion).scan(settings.modsDir)
        val game = GameScanner.scan(settings.gameDir, config.gameVersion)
        return Snapshot(config.gameVersion, scan, game).also { snapshot = it }
    }

    fun current(): Snapshot = snapshot ?: rescan()
}
