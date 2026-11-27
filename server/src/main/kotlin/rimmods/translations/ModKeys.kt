package rimmods.translations

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.inputStream
import org.w3c.dom.Element
import rimmods.scan.Xml
import rimmods.scan.childText
import rimmods.scan.elements

/**
 * The keys a translation is matched on (docs/translations.md): `def:<defName>` and `key:<Keyed key>`.
 * All folders of the mod are read, whatever the game version; unreadable files are skipped.
 */
object ModKeys {
    /** What a mod defines: defNames in its Defs, keys in its English Keyed files. */
    fun provided(modFolder: Path): Set<String> {
        val out = HashSet<String>()
        for ((rel, file) in xmlFiles(modFolder)) {
            when {
                "defs" in rel.dropLast(1) -> root(file)?.elements()?.forEach { def -> def.childText("defName")?.let { out += "def:$it" } }
                isLanguage(rel, english = true) && "keyed" in rel -> root(file)?.elements()?.forEach { out += "key:${it.tagName}" }
            }
        }
        return out
    }

    /** What a mod translates: defNames in DefInjected and keys in Keyed, in its non-English languages. */
    fun translated(modFolder: Path): Set<String> {
        val out = HashSet<String>()
        for ((rel, file) in xmlFiles(modFolder)) {
            if (!isLanguage(rel, english = false)) continue
            when {
                "definjected" in rel -> root(file)?.elements()?.forEach { out += "def:${it.tagName.substringBefore('.')}" }
                "keyed" in rel -> root(file)?.elements()?.forEach { out += "key:${it.tagName}" }
            }
        }
        return out
    }

    /** `…/Languages/<language>/…` with that language English (or not). Path segments are lowercased. */
    private fun isLanguage(rel: List<String>, english: Boolean): Boolean {
        val i = rel.indexOf("languages")
        if (i < 0 || i + 1 >= rel.size - 1) return false
        return rel[i + 1].startsWith("english") == english
    }

    /** XML files with their path relative to the mod, as lowercased segments. */
    private fun xmlFiles(modFolder: Path): List<Pair<List<String>, Path>> {
        if (!modFolder.isDirectory()) return emptyList()
        return Files.walk(modFolder).use { paths ->
            paths.filter { it.isRegularFile() && it.extension.equals("xml", ignoreCase = true) }
                .map { modFolder.relativize(it).map { s -> s.toString().lowercase() } to it }
                .toList()
        }
    }

    private fun root(file: Path): Element? = try {
        file.inputStream().buffered().use { Xml.parseRoot(it) }
    } catch (e: Exception) {
        null
    }
}
