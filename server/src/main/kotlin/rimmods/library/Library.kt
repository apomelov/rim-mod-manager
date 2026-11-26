package rimmods.library

import rimmods.scan.PackageId

/** All user decisions, as held in memory. See docs/user-files.md. */
data class Library(
    val folders: List<Folder> = emptyList(),
    /** Mods marked as translations → the mods they translate. */
    val translations: Map<PackageId, List<PackageId>> = emptyMap(),
    val builds: List<Build> = emptyList(),
)

data class Folder(
    val name: String,
    val expanded: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val mods: List<PackageId> = emptyList(),
)

data class Build(val name: String, val mods: List<PackageId> = emptyList())

/** A library plus what was wrong with the file it came from. Problems are reported, never fixed on disk silently. */
data class LoadedLibrary(val library: Library, val problems: List<String>)

class LibraryFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
