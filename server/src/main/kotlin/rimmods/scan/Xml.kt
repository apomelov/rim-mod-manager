package rimmods.scan

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Lenient DOM helpers: element names are matched case-insensitively, text is trimmed. */
internal object Xml {
    private val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }

    fun parseRoot(input: InputStream): Element {
        val builder = factory.newDocumentBuilder().apply { setErrorHandler(null) }
        return builder.parse(input).documentElement
    }
}

internal fun Element.elements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).map(nodes::item).filter { it.nodeType == Node.ELEMENT_NODE }.map { it as Element }
}

internal fun Element.child(name: String): Element? = elements().firstOrNull { it.tagName.equals(name, ignoreCase = true) }

internal fun Element.trimmedText(): String? = textContent?.trim()?.takeIf { it.isNotEmpty() }

internal fun Element.childText(name: String): String? = child(name)?.trimmedText()

/** Texts of `<li>` children. */
internal fun Element.liTexts(): List<String> =
    elements().filter { it.tagName.equals("li", ignoreCase = true) }.mapNotNull { it.trimmedText() }

internal fun Element.liElements(): List<Element> = elements().filter { it.tagName.equals("li", ignoreCase = true) }
