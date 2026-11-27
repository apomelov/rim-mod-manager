package rimmods.api

import java.nio.file.Path
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
class NotConfiguredException : Exception("Not set up yet: open the app to choose the game and mods folders")

/** The settings (null until the welcome page saves them) and the latest disk scan. */
class AppState(val configDir: Path, settings: Settings?) {
    constructor(settings: Settings) : this(settings.configDir, settings)

    @Volatile
    private var settingsOrNull: Settings? = settings

    fun settingsOrNull(): Settings? = settingsOrNull

    fun settings(): Settings = settingsOrNull ?: throw NotConfiguredException()

    /** New settings (from the welcome page); the next request rescans. */
    fun configure(s: Settings) {
        settingsOrNull = s
        snapshot = null
    }

    /** [game]: Core and the DLCs from the game folder; [scan]: the Workshop mods folder. */
    data class Snapshot(val gameVersion: GameVersion, val scan: ScanResult, val game: List<Mod> = emptyList()) {
        val byId: Map<PackageId, Mod> = (game + scan.mods).associateBy { it.packageId }

        /** Translation keys per mod, read on first use (seconds for a few hundred mods) and kept until the next rescan. */
        val keys: MutableMap<PackageId, ModKeySets> = ConcurrentHashMap()
    }

    @Volatile
    private var snapshot: Snapshot? = null

    fun rescan(): Snapshot {
        val settings = settings()
        val config = ModsConfigReader.read(settings.modsConfigFile)
        val scan = ModScanner(config.gameVersion).scan(settings.modsDirs)
        val game = GameScanner.scan(settings.gameDir, config.gameVersion)
        return Snapshot(config.gameVersion, scan, game).also { snapshot = it }
    }

    fun current(): Snapshot = snapshot ?: rescan()
}
