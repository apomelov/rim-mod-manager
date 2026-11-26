package rimmods.scan

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Finds a mod's preview and icon images (docs/about-xml.md, "Images"). */
object ImageResolver {

    fun preview(modFolder: Path): Path? = aboutFile(modFolder, "preview.png")

    /** Icons for all mods that have one. Needs the whole set: an icon may live in a dependency. */
    fun icons(mods: List<Mod>): Map<PackageId, Path> {
        val byId = mods.associateBy { it.packageId }
        val result = mutableMapOf<PackageId, Path>()
        for (mod in mods) {
            val folder = Path.of(mod.folder)
            val iconPath = mod.about.modIconPath
            val icon = iconPath?.let { texture(folder, it) }
                ?: iconPath?.let { path ->
                    transitiveDependencies(mod, byId).firstNotNullOfOrNull { texture(Path.of(it.folder), path) }
                }
                ?: aboutFile(folder, "modicon.png")
            if (icon != null) result[mod.packageId] = icon
        }
        return result
    }

    /** Dependencies on disk, nearest first (breadth-first), without the mod itself. */
    private fun transitiveDependencies(mod: Mod, byId: Map<PackageId, Mod>): Sequence<Mod> = sequence {
        val seen = mutableSetOf(mod.packageId)
        val queue = ArrayDeque(listOf(mod))
        while (queue.isNotEmpty()) {
            for (dep in queue.removeFirst().about.dependencies) {
                val found = (listOf(dep.packageId) + dep.alternatives).firstNotNullOfOrNull { byId[it] } ?: continue
                if (seen.add(found.packageId)) {
                    yield(found)
                    queue.addLast(found)
                }
            }
        }
    }

    private fun aboutFile(modFolder: Path, lowerName: String): Path? {
        val about = modFolder.resolve("About")
        if (!about.isDirectory()) return null
        return about.listDirectoryEntries().firstOrNull { it.isRegularFile() && it.name.lowercase() == lowerName }
    }

    /** `<any>/Textures/<path>.png` inside [modFolder], case-insensitive; the shortest match wins. */
    private fun texture(modFolder: Path, texturePath: String): Path? {
        if (!modFolder.isDirectory()) return null
        val suffix = "/textures/" + texturePath.trim().trim('/').lowercase() + ".png"
        return Files.walk(modFolder).use { paths ->
            paths.filter { it.isRegularFile() && ("/" + modFolder.relativize(it).toString().lowercase()).endsWith(suffix) }
                .toList()
                .minByOrNull { it.nameCount }
        }
    }
}
