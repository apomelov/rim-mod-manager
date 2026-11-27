package rimmods.scan

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.name

class LeftoverException(message: String) : Exception(message)

/**
 * Folders left in the mods folder after unsubscribing. Steam removes only the files it downloaded, so textures
 * generated next to them (`.dds`, e.g. by RimSort's texture optimisation) stay behind.
 */
object Leftovers {
    const val TEXTURES_ONLY_MESSAGE =
        "Only .dds textures left: leftovers of a removed mod (Steam does not remove textures generated " +
            "after download, e.g. by RimSort's texture optimisation). Safe to delete."

    /** At least one file, and every file is a `.dds`. A symbolic link anywhere makes it false. */
    fun isTexturesOnly(folder: Path): Boolean = Files.walk(folder).use { paths ->
        var any = false
        for (p in paths) {
            if (p == folder) continue
            if (Files.isSymbolicLink(p)) return false
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue
            if (!p.name.lowercase().endsWith(".dds")) return false
            any = true
        }
        any
    }

    /**
     * Deletes [path] if it is, right now, a real folder directly inside one of [modsDirs], with only `.dds` textures.
     * Anything else is refused with [LeftoverException].
     */
    fun delete(modsDirs: List<Path>, path: String) {
        val folder = runCatching { Path.of(path).normalize() }.getOrNull()
        val inModsDir = folder != null && folder.isAbsolute && modsDirs.any { folder.parent == it.normalize() }
        if (!inModsDir) throw LeftoverException("'$path' is not a folder directly inside a mods folder")
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) throw LeftoverException("No folder '$path'")
        if (!isTexturesOnly(folder!!)) throw LeftoverException("'$path' is not a folder with only .dds textures")
        Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
