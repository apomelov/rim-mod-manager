package rimmods.scan

import org.w3c.dom.Element
import java.io.InputStream

class AboutXmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Parses `About/About.xml` as described in docs/about-xml.md. */
object AboutXmlParser {
    private val WORKSHOP_ID = Regex("""(\d{4,})\D*$""")

    fun parse(input: InputStream, gameVersion: GameVersion): AboutInfo {
        val root = try {
            Xml.parseRoot(input)
        } catch (e: Exception) {
            throw AboutXmlException("Malformed XML: ${e.message}", e)
        }
        if (!root.tagName.equals("ModMetaData", ignoreCase = true)) {
            throw AboutXmlException("Unexpected root element <${root.tagName}>")
        }
        val packageIdRaw = root.childText("packageId") ?: throw AboutXmlException("Missing <packageId>")
        val packageId = PackageId.of(packageIdRaw)

        return AboutInfo(
            packageId = packageId,
            packageIdOriginal = packageIdRaw,
            name = root.childText("name") ?: packageIdRaw,
            authors = authors(root),
            description = root.childText("description") ?: "",
            supportedVersions = root.child("supportedVersions")?.liTexts() ?: emptyList(),
            dependencies = root.versioned("modDependencies", gameVersion)?.liElements()?.mapNotNull(::dependency)
                ?: emptyList(),
            loadAfter = root.versionedIds("loadAfter", gameVersion),
            loadBefore = root.versionedIds("loadBefore", gameVersion),
            forceLoadAfter = root.versionedIds("forceLoadAfter", gameVersion),
            forceLoadBefore = root.versionedIds("forceLoadBefore", gameVersion),
            incompatibleWith = root.versionedIds("incompatibleWith", gameVersion),
            url = root.childText("url"),
            modVersion = root.childText("modVersion"),
            modIconPath = root.childText("modIconPath"),
        )
    }

    /** Extracts a Workshop id from `steam://url/CommunityFilePage/123` or `…/filedetails/?id=123`. */
    fun workshopIdFromUrl(url: String?): String? = url?.let { WORKSHOP_ID.find(it.trim())?.groupValues?.get(1) }

    private fun authors(root: Element): List<String> {
        val single = root.childText("author")?.split(',')?.map { it.trim() } ?: emptyList()
        val list = root.child("authors")?.liTexts() ?: emptyList()
        return (single + list).filter { it.isNotEmpty() }.distinct()
    }

    private fun dependency(li: Element): Dependency? {
        val id = li.childText("packageId") ?: return null
        return Dependency(
            packageId = PackageId.of(id),
            displayName = li.childText("displayName"),
            workshopId = workshopIdFromUrl(li.childText("steamWorkshopUrl")),
            alternatives = li.child("alternativePackageIds")?.liTexts()?.map(PackageId::of) ?: emptyList(),
        )
    }

    /** `<nameByVersion><vX.Y>` for the game version if present, otherwise plain `<name>`. */
    private fun Element.versioned(name: String, gameVersion: GameVersion): Element? =
        child("${name}ByVersion")?.child("v$gameVersion") ?: child(name)

    private fun Element.versionedIds(name: String, gameVersion: GameVersion): List<PackageId> =
        versioned(name, gameVersion)?.liTexts()?.map(PackageId::of) ?: emptyList()
}
