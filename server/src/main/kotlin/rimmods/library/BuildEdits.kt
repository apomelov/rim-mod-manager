package rimmods.library

import rimmods.scan.PackageId

/** Edits of builds (the tabs over the tree). Pure functions, like [LibraryEdits]. Builds are addressed by name. */
object BuildEdits {

    /** The open tab: [Library.currentBuild] if it exists, else the first build, else none. */
    fun current(library: Library): Build? =
        library.builds.firstOrNull { it.name == library.currentBuild } ?: library.builds.firstOrNull()

    fun setCurrent(library: Library, name: String): Library {
        require(library, name)
        return library.copy(currentBuild = name)
    }

    /** Adds an empty build at the end and opens it. */
    fun create(library: Library, name: String): Library {
        val clean = freeName(library, name)
        return library.copy(builds = library.builds + Build(clean), currentBuild = clean)
    }

    fun rename(library: Library, name: String, newName: String): Library {
        require(library, name)
        if (newName.trim() == name) return library
        val clean = freeName(library, newName)
        return library.copy(
            builds = library.builds.map { if (it.name == name) it.copy(name = clean) else it },
            currentBuild = if (library.currentBuild == name) clean else library.currentBuild,
        )
    }

    /** Copies a build right after itself as "X copy" (or "X copy 2", …) and opens the copy. */
    fun duplicate(library: Library, name: String): Library {
        val build = require(library, name)
        val taken = library.builds.map { it.name }.toSet()
        val copyName = generateSequence(2) { it + 1 }.map { "$name copy $it" }
            .let { sequenceOf("$name copy") + it }
            .first { it !in taken }
        val at = library.builds.indexOf(build) + 1
        val builds = library.builds.toMutableList().also { it.add(at, build.copy(name = copyName)) }
        return library.copy(builds = builds, currentBuild = copyName)
    }

    /** Deletes a build. If it was open, its right neighbour opens (else the left one). */
    fun delete(library: Library, name: String): Library {
        val build = require(library, name)
        val i = library.builds.indexOf(build)
        val rest = library.builds - build
        val wasCurrent = current(library) == build
        val next = if (wasCurrent) (rest.getOrNull(i) ?: rest.lastOrNull())?.name else current(library)?.name
        return library.copy(builds = rest, currentBuild = next)
    }

    /** Selects (adds once, at the end) or deselects [mods] in a build. */
    fun select(library: Library, name: String, mods: List<PackageId>, selected: Boolean): Library {
        require(library, name)
        return library.copy(
            builds = library.builds.map { b ->
                if (b.name != name) b
                else if (selected) b.copy(mods = (b.mods + mods).distinct())
                else b.copy(mods = b.mods - mods.toSet())
            },
        )
    }

    private fun require(library: Library, name: String): Build =
        library.builds.firstOrNull { it.name == name } ?: throw LibraryEditException("No build '$name'")

    private fun freeName(library: Library, name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) throw LibraryEditException("Build name is empty")
        if (library.builds.any { it.name == clean }) throw LibraryEditException("A build named '$clean' already exists")
        return clean
    }
}
