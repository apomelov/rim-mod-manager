package rimmods.library

import kotlinx.serialization.Serializable
import rimmods.scan.Mod
import rimmods.scan.PackageId

@Serializable
data class ModEntry(
    val packageId: PackageId,
    val name: String,
    /** In the library but not on disk. */
    val missing: Boolean = false,
    /** Marked as a translation: shown only with "show hidden". */
    val hidden: Boolean = false,
    /** Marked translations of this mod that are on disk. */
    val translations: List<ModEntry> = emptyList(),
    /** An icon can be fetched from `/api/mods/{packageId}/icon`. */
    val hasIcon: Boolean = false,
    /** Languages the mod ships (Languages folders), e.g. `Russian`. */
    val languages: List<String> = emptyList(),
)

@Serializable
data class FolderView(
    val name: String,
    val expanded: Boolean = false,
    val folders: List<FolderView> = emptyList(),
    val mods: List<ModEntry> = emptyList(),
)

@Serializable
data class LibraryView(
    /** Virtual folder, shown last: mods on disk neither placed in the tree nor shown under an original. */
    val sortout: List<ModEntry>,
    val folders: List<FolderView>,
    /** Core and the DLCs found in the game folder, in release order: shown first, not movable. */
    val game: List<ModEntry> = emptyList(),
)

/**
 * Joins the disk scan with the user's library into what the tree UI shows. Pure; writes nothing.
 * Folders and mods are sorted by name, case-insensitively; the stored order is ignored.
 */
object LibrarySync {
    /** [game]: Core and the DLCs, shown above the folders as they come (release order). */
    fun view(library: Library, mods: List<Mod>, game: List<Mod> = emptyList()): LibraryView {
        val onDisk = mods.associateBy { it.packageId }
        val isTranslation = library.translations.keys

        // original on disk → its marked translations on disk
        val translationsOf: Map<PackageId, List<PackageId>> = library.translations
            .filterKeys { it in onDisk }
            .flatMap { (translation, originals) -> originals.filter { it in onDisk }.distinct().map { it to translation } }
            .groupBy({ it.first }, { it.second })
        val shownUnderOriginal = translationsOf.values.flatten().toSet()

        val byName = compareBy<ModEntry>({ it.name.lowercase() }, { it.packageId.value })

        fun entry(id: PackageId, withTranslations: Boolean = true): ModEntry = ModEntry(
            packageId = id,
            name = onDisk[id]?.about?.name ?: id.value,
            missing = id !in onDisk,
            hidden = id in isTranslation,
            translations = if (withTranslations) {
                translationsOf[id].orEmpty().map { entry(it, withTranslations = false) }.sortedWith(byName)
            } else {
                emptyList()
            },
            hasIcon = onDisk[id]?.icon != null,
            languages = onDisk[id]?.languages.orEmpty(),
        )

        val folderOrder = compareBy<FolderView>({ it.name.lowercase() }, { it.name })
        val placed = mutableSetOf<PackageId>()
        fun folder(f: Folder): FolderView = FolderView(
            name = f.name,
            expanded = f.expanded,
            folders = f.folders.map(::folder).sortedWith(folderOrder),
            mods = f.mods.also { placed += it }.filter { it !in shownUnderOriginal }.map { entry(it) }.sortedWith(byName),
        )
        val folders = library.folders.map(::folder).sortedWith(folderOrder)

        val sortout = onDisk.keys
            .filter { it !in placed && it !in shownUnderOriginal }
            .map { entry(it) }
            .sortedWith(byName)
        val gameEntries = game.map { ModEntry(it.packageId, it.about.name, hasIcon = it.icon != null) }
        return LibraryView(sortout, folders, gameEntries)
    }
}
