package rimmods.library

import rimmods.scan.PackageId

class LibraryEditException(message: String) : Exception(message)

/** A folder's position in the tree: the names from the top. Written as `QoL/base`; the empty path is the root. */
@JvmInline
value class FolderPath(val names: List<String>) {
    val isRoot: Boolean get() = names.isEmpty()
    val parent: FolderPath get() = FolderPath(names.dropLast(1))
    val name: String get() = names.last()
    fun child(name: String) = FolderPath(names + name)
    fun isSameOrInside(other: FolderPath) = names.size >= other.names.size && names.take(other.names.size) == other.names
    override fun toString() = names.joinToString("/")

    companion object {
        val ROOT = FolderPath(emptyList())
        fun parse(s: String?): FolderPath = FolderPath(s.orEmpty().split('/').map { it.trim() }.filter { it.isNotEmpty() })
    }
}

/**
 * Edits of the user's library. Pure functions: each returns a new [Library] or throws [LibraryEditException].
 * Folders are addressed by [FolderPath]; with duplicate sibling names (possible in a hand-edited file) the first wins.
 */
object LibraryEdits {

    /** Moves [mods] into the folder [to] (null = Sortout, i.e. out of the tree). Stored order is irrelevant: the view sorts. */
    fun moveMods(library: Library, mods: List<PackageId>, to: FolderPath?): Library {
        if (to != null) requireFolder(library, to, allowRoot = false)
        val moving = mods.distinct()
        val removed = library.copy(folders = removeMods(library.folders, moving.toSet()))
        if (to == null) return removed
        return removed.copy(
            folders = updateFolder(removed.folders, to) { it.copy(mods = it.mods + moving) },
        )
    }

    /** Moves a folder (with its contents) under [toParent]. */
    fun moveFolder(library: Library, from: FolderPath, toParent: FolderPath): Library {
        val folder = requireFolder(library, from, allowRoot = false)!!
        requireFolder(library, toParent, allowRoot = true)
        if (toParent.isSameOrInside(from)) throw LibraryEditException("Cannot move '$from' into itself")
        if (isPredefined(from) && toParent != FolderPath.ROOT) {
            throw LibraryEditException("Predefined folder '$from' stays at the top level")
        }
        val without = removeFolder(library.folders, from)
        val siblings = childrenOf(without, toParent)
        if (siblings.any { it.name == folder.name }) throw LibraryEditException("'$toParent' already has a folder named '${folder.name}'")
        return library.copy(folders = updateChildren(without, toParent) { it + folder })
    }

    /** A top-level folder named like one of [LibraryStore.PREDEFINED_FOLDERS]; it can be reordered but not nested. */
    fun isPredefined(path: FolderPath): Boolean =
        path.parent == FolderPath.ROOT && path.name in LibraryStore.PREDEFINED_FOLDERS

    fun createFolder(library: Library, parent: FolderPath, name: String): Library {
        requireFolder(library, parent, allowRoot = true)
        val clean = validName(name)
        if (childrenOf(library.folders, parent).any { it.name == clean }) {
            throw LibraryEditException("'${parent.toString().ifEmpty { "/" }}' already has a folder named '$clean'")
        }
        return library.copy(folders = updateChildren(library.folders, parent) { it + Folder(clean) })
    }

    fun renameFolder(library: Library, path: FolderPath, newName: String): Library {
        requireFolder(library, path, allowRoot = false)
        val clean = validName(newName)
        if (clean == path.name) return library
        if (childrenOf(library.folders, path.parent).any { it.name == clean }) {
            throw LibraryEditException("A folder named '$clean' already exists next to '$path'")
        }
        return library.copy(folders = updateFolder(library.folders, path) { it.copy(name = clean) })
    }

    /**
     * Deletes a folder. A top-level folder takes its whole subtree with it, so its mods go back to Sortout. A nested
     * folder's mods and subfolders move up into its parent; a subfolder named like one already there is merged into it.
     */
    fun deleteFolder(library: Library, path: FolderPath): Library {
        val folder = requireFolder(library, path, allowRoot = false)!!
        val without = library.copy(folders = removeFolder(library.folders, path))
        if (path.parent.isRoot) return without
        return without.copy(
            folders = updateFolder(without.folders, path.parent) { parent ->
                parent.copy(mods = parent.mods + folder.mods, folders = merge(parent.folders, folder.folders))
            },
        )
    }

    private fun merge(into: List<Folder>, incoming: List<Folder>): List<Folder> =
        incoming.fold(into) { acc, f ->
            val i = acc.indexOfFirst { it.name == f.name }
            if (i < 0) {
                acc + f
            } else {
                acc.toMutableList().also { it[i] = it[i].copy(mods = it[i].mods + f.mods, folders = merge(it[i].folders, f.folders)) }
            }
        }

    fun setExpanded(library: Library, path: FolderPath, expanded: Boolean): Library {
        requireFolder(library, path, allowRoot = false)
        return library.copy(folders = updateFolder(library.folders, path) { it.copy(expanded = expanded) })
    }

    /** Marks [translation] as a translation of [originals] (replacing an earlier mark). */
    fun markTranslation(library: Library, translation: PackageId, originals: List<PackageId>): Library {
        if (translation in originals) throw LibraryEditException("A mod cannot translate itself")
        originals.firstOrNull { it in library.translations }?.let {
            throw LibraryEditException("'$it' is itself a translation and cannot be an original")
        }
        library.translations.entries.firstOrNull { (t, o) -> t != translation && translation in o }?.let {
            throw LibraryEditException("'$translation' has a translation ('${it.key}') and cannot itself be a translation")
        }
        return library.copy(translations = library.translations + (translation to originals.distinct()))
    }

    fun unmarkTranslation(library: Library, translation: PackageId): Library =
        library.copy(translations = library.translations - translation)

    // --- tree helpers ---

    private fun validName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) throw LibraryEditException("Folder name is empty")
        if ('/' in clean) throw LibraryEditException("Folder name cannot contain '/'")
        return clean
    }

    /** The folder at [path] (null for the root), or an error if there is none. */
    private fun requireFolder(library: Library, path: FolderPath, allowRoot: Boolean): Folder? {
        if (path.isRoot) {
            if (allowRoot) return null
            throw LibraryEditException("The root is not a folder")
        }
        var level = library.folders
        var found: Folder? = null
        for (name in path.names) {
            found = level.firstOrNull { it.name == name } ?: throw LibraryEditException("No folder '$path'")
            level = found.folders
        }
        return found
    }

    private fun childrenOf(folders: List<Folder>, parent: FolderPath): List<Folder> =
        if (parent.isRoot) folders else childrenOf(folders.first { it.name == parent.names.first() }.folders, FolderPath(parent.names.drop(1)))

    private fun updateChildren(folders: List<Folder>, parent: FolderPath, f: (List<Folder>) -> List<Folder>): List<Folder> =
        if (parent.isRoot) f(folders) else updateFolder(folders, parent) { it.copy(folders = f(it.folders)) }

    private fun updateFolder(folders: List<Folder>, path: FolderPath, f: (Folder) -> Folder): List<Folder> {
        val i = folders.indexOfFirst { it.name == path.names.first() }
        val target = folders[i]
        val updated = if (path.names.size == 1) f(target) else target.copy(folders = updateFolder(target.folders, FolderPath(path.names.drop(1)), f))
        return folders.toMutableList().also { it[i] = updated }
    }

    private fun removeFolder(folders: List<Folder>, path: FolderPath): List<Folder> =
        updateChildren(folders, path.parent) { siblings ->
            val i = siblings.indexOfFirst { it.name == path.name }
            siblings.filterIndexed { j, _ -> j != i }
        }

    private fun removeMods(folders: List<Folder>, mods: Set<PackageId>): List<Folder> =
        folders.map { it.copy(folders = removeMods(it.folders, mods), mods = it.mods.filter { m -> m !in mods }) }
}
