package rimmods.scan

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The languages a mod ships: the folders inside any `Languages` folder in the mod, at its root or in a load folder
 * (`Common/`, `1.6/`, conditional ones like `Biotech/`, nested up to two levels). Version folders (`1.5`, `v1.5`)
 * count only for the game version. An approximation of the game's load folders: LoadFolders.xml is not read.
 */
/** What a mod folder holds: its languages and its own content folders (`Defs`, `Assemblies`, …). */
data class ModContent(val languages: List<String>, val content: List<String>)

object Languages {
    /** Folders of a mod's own content. Their text is English (the game's base language), so having any means English. */
    private val CONTENT = listOf("Assemblies", "Defs", "Patches", "Sounds", "Textures")

    private val VERSION_DIR = Regex("""v?(\d+\.\d+)""", RegexOption.IGNORE_CASE)

    /** Folder names the game or modders use for the same language. */
    private val ALIASES = mapOf(
        "TraditionalChinese" to "ChineseTraditional",
        "SimplifiedChinese" to "ChineseSimplified",
        "PortugueseBrazil" to "PortugueseBrazilian",
        "KoreanFastChannel" to "Korean",
    )

    fun scan(modFolder: Path, gameVersion: GameVersion): ModContent {
        val found = sortedSetOf<String>()
        val content = sortedSetOf<String>()
        fun visit(dir: Path, depth: Int) {
            for (child in dir.listDirectoryEntries().filter { it.isDirectory() && !Files.isSymbolicLink(it) }) {
                val name = child.name
                val contentName = CONTENT.firstOrNull { it.equals(name, ignoreCase = true) }
                when {
                    name.equals("Languages", ignoreCase = true) ->
                        child.listDirectoryEntries().filter { it.isDirectory() }.forEach { found += normalise(it.name) }
                    contentName != null -> content += contentName
                    depth >= 2 -> {}
                    VERSION_DIR.matchEntire(name)?.let { it.groupValues[1] != gameVersion.toString() } == true -> {}
                    else -> visit(child, depth + 1)
                }
            }
        }
        if (modFolder.isDirectory()) visit(modFolder, 0)
        if (content.isNotEmpty()) found += "English"
        return ModContent(found.toList(), content.toList())
    }

    /** `Russian (Русский)` → `Russian`; known aliases → the game's name. */
    fun normalise(folder: String): String {
        val base = folder.substringBefore(" (").trim()
        return ALIASES[base] ?: base
    }
}
