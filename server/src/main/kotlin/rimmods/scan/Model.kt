package rimmods.scan

import kotlinx.serialization.Serializable

/** A mod's identity. RimWorld treats packageIds case-insensitively and lowercases them internally. */
@Serializable
@JvmInline
value class PackageId private constructor(val value: String) {
    override fun toString() = value

    val isLudeon: Boolean get() = value == CORE || value.startsWith("$CORE.")

    companion object {
        private const val CORE = "ludeon.rimworld"
        fun of(raw: String): PackageId = PackageId(raw.trim().lowercase())

        /** Core and the DLCs in release order. */
        val CORE_AND_DLCS: List<PackageId> by lazy {
            listOf("", ".royalty", ".ideology", ".biotech", ".anomaly", ".odyssey").map { of(CORE + it) }
        }
    }
}

/** Game version as used in `…ByVersion` keys and `supportedVersions`: `major.minor`, e.g. `1.6`. */
@Serializable
@JvmInline
value class GameVersion private constructor(val value: String) {
    override fun toString() = value

    companion object {
        private val PREFIX = Regex("""^\s*(\d+)\.(\d+)""")

        /** Accepts `1.6`, `1.6.4871 rev598` and the like. */
        fun parse(raw: String): GameVersion {
            val m = PREFIX.find(raw) ?: throw IllegalArgumentException("Not a game version: '$raw'")
            return GameVersion("${m.groupValues[1]}.${m.groupValues[2]}")
        }
    }
}

@Serializable
data class Dependency(
    val packageId: PackageId,
    val displayName: String? = null,
    val workshopId: String? = null,
    /** 1.6+: any of these also satisfies the dependency. */
    val alternatives: List<PackageId> = emptyList(),
)

/** What About.xml says about a mod, with `…ByVersion` already resolved for one game version. */
@Serializable
data class AboutInfo(
    val packageId: PackageId,
    /** packageId as written by the author, for display. */
    val packageIdOriginal: String,
    val name: String,
    val authors: List<String>,
    val description: String,
    val supportedVersions: List<String>,
    val dependencies: List<Dependency>,
    val loadAfter: List<PackageId>,
    val loadBefore: List<PackageId>,
    val forceLoadAfter: List<PackageId>,
    val forceLoadBefore: List<PackageId>,
    val incompatibleWith: List<PackageId>,
    val url: String? = null,
    val modVersion: String? = null,
    /** Icon texture path without extension, relative to a `Textures` folder. */
    val modIconPath: String? = null,
)

/** A mod found on disk. */
@Serializable
data class Mod(
    val about: AboutInfo,
    val workshopId: String?,
    /** Absolute path of the mod's folder. */
    val folder: String,
    /** Resolved image files (absolute paths), see docs/about-xml.md "Images". */
    val preview: String? = null,
    val icon: String? = null,
) {
    val packageId: PackageId get() = about.packageId
}

@Serializable
data class ScanProblem(
    val folder: String,
    val message: String,
    /** A folder holding only `.dds` textures: leftovers of a removed mod that the user may delete from the app. */
    val texturesOnly: Boolean = false,
)

@Serializable
data class ScanResult(val mods: List<Mod>, val problems: List<ScanProblem>)
