package rimmods.export

import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeText
import org.w3c.dom.Element
import org.w3c.dom.Node
import rimmods.scan.PackageId
import rimmods.scan.Xml
import rimmods.scan.child
import rimmods.scan.childText

class ModsConfigWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Writes the active mod list into the game's `ModsConfig.xml`, keeping everything else in the file. */
object ModsConfigWriter {
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")

    /**
     * Backs the current file up into [backupDir] (`ModsConfig-<time>.xml`), then replaces `<activeMods>` with [mods]
     * (lowercased, in order) atomically. The file must exist and have a `<version>`: without one the game would reset
     * the list on start. Returns the backup.
     */
    fun write(file: Path, mods: List<PackageId>, backupDir: Path, now: LocalDateTime = LocalDateTime.now()): Path {
        if (!file.exists()) throw ModsConfigWriteException("$file does not exist")
        val root = try {
            file.inputStream().buffered().use { Xml.parseRoot(it) }
        } catch (e: Exception) {
            throw ModsConfigWriteException("Cannot read $file: ${e.message}", e)
        }
        if (root.childText("version") == null) throw ModsConfigWriteException("$file has no <version>")

        val doc = root.ownerDocument.apply { xmlStandalone = true } // no standalone="no" in the declaration
        val active = root.child("activeMods") ?: doc.createElement("activeMods").also { root.appendChild(it) }
        while (active.firstChild != null) active.removeChild(active.firstChild)
        for (id in mods) active.appendChild(doc.createElement("li").apply { textContent = id.value })
        stripWhitespace(root)

        val xml = StringWriter().also { out ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
            }.transform(DOMSource(doc), StreamResult(out))
        }.toString()

        val backup = backup(file, backupDir, now)
        val tmp = Files.createTempFile(file.parent, "ModsConfig", ".tmp")
        try {
            tmp.writeText(xml)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
        return backup
    }

    private fun backup(file: Path, backupDir: Path, now: LocalDateTime): Path {
        backupDir.createDirectories()
        val base = "ModsConfig-${STAMP.format(now)}"
        val target = generateSequence(1) { it + 1 }
            .map { n -> backupDir.resolve(if (n == 1) "$base.xml" else "$base-$n.xml") }
            .first { !it.exists() }
        return Files.copy(file, target)
    }

    /** Drops whitespace-only text nodes so the transformer re-indents cleanly. */
    private fun stripWhitespace(e: Element) {
        var n: Node? = e.firstChild
        while (n != null) {
            val next = n.nextSibling
            when {
                n.nodeType == Node.TEXT_NODE && n.textContent.isBlank() -> e.removeChild(n)
                n is Element -> stripWhitespace(n)
            }
            n = next
        }
    }
}
