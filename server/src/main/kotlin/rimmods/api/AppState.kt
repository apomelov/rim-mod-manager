package rimmods.api

import rimmods.Settings
import rimmods.scan.GameVersion
import rimmods.scan.Mod
import rimmods.scan.ModScanner
import rimmods.scan.ModsConfigReader
import rimmods.scan.PackageId
import rimmods.scan.ScanResult

/** The latest disk scan. Rescanned when the library is (re)loaded; detail and image requests use it as is. */
class AppState(private val settings: Settings) {
    data class Snapshot(val gameVersion: GameVersion, val scan: ScanResult) {
        val byId: Map<PackageId, Mod> = scan.mods.associateBy { it.packageId }
    }

    @Volatile
    private var snapshot: Snapshot? = null

    fun rescan(): Snapshot {
        val config = ModsConfigReader.read(settings.modsConfigFile)
        return Snapshot(config.gameVersion, ModScanner(config.gameVersion).scan(settings.modsDir)).also { snapshot = it }
    }

    fun current(): Snapshot = snapshot ?: rescan()
}
