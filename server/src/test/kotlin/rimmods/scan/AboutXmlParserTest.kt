package rimmods.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AboutXmlParserTest {
    private val v16 = GameVersion.parse("1.6")

    private fun parse(xml: String, version: GameVersion = v16) = AboutXmlParser.parse(xml.byteInputStream(), version)

    @Test
    fun `reads the basic fields`() {
        val info = parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ModMetaData>
              <packageId>Author.SomeMod</packageId>
              <name>Some Mod</name>
              <author>Author</author>
              <description>  Does things.  </description>
              <supportedVersions><li>1.5</li><li>1.6</li></supportedVersions>
              <url>https://example.org</url>
              <modVersion IgnoreIfNoMatchingField="True">2.1.0</modVersion>
              <modIconPath IgnoreIfNoMatchingField="True">UI/Logo</modIconPath>
            </ModMetaData>
            """.trimIndent(),
        )
        assertEquals(PackageId.of("author.somemod"), info.packageId)
        assertEquals("Author.SomeMod", info.packageIdOriginal)
        assertEquals("Some Mod", info.name)
        assertEquals(listOf("Author"), info.authors)
        assertEquals("Does things.", info.description)
        assertEquals(listOf("1.5", "1.6"), info.supportedVersions)
        assertEquals("https://example.org", info.url)
        assertEquals("2.1.0", info.modVersion)
        assertEquals("UI/Logo", info.modIconPath)
    }

    @Test
    fun `packageIds are lowercased everywhere`() {
        val info = parse(
            """
            <ModMetaData>
              <packageId>A.B</packageId>
              <modDependencies><li><packageId>Brrainz.Harmony</packageId></li></modDependencies>
              <loadAfter><li>Ludeon.RimWorld</li></loadAfter>
            </ModMetaData>
            """,
        )
        assertEquals("a.b", info.packageId.value)
        assertEquals("brrainz.harmony", info.dependencies.single().packageId.value)
        assertEquals("ludeon.rimworld", info.loadAfter.single().value)
    }

    @Test
    fun `element names are case-insensitive`() {
        val info = parse("<modMetaData><PACKAGEID>a.b</PACKAGEID><Name>X</Name></modMetaData>")
        assertEquals("a.b", info.packageId.value)
        assertEquals("X", info.name)
    }

    @Test
    fun `tolerates a UTF-8 BOM`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            """<?xml version="1.0" encoding="utf-8"?><ModMetaData><packageId>a.b</packageId></ModMetaData>""".toByteArray()
        assertEquals("a.b", AboutXmlParser.parse(bytes.inputStream(), v16).packageId.value)
    }

    @Test
    fun `name falls back to packageId`() {
        assertEquals("A.B", parse("<ModMetaData><packageId>A.B</packageId></ModMetaData>").name)
    }

    @Test
    fun `author is comma-separated and merges with authors list`() {
        val info = parse(
            """
            <ModMetaData><packageId>a.b</packageId>
              <author>One, Two</author>
              <authors><li>Two</li><li>Three</li></authors>
            </ModMetaData>
            """,
        )
        assertEquals(listOf("One", "Two", "Three"), info.authors)
    }

    @Test
    fun `dependency items carry display name, workshop id and alternatives`() {
        val dep = parse(
            """
            <ModMetaData><packageId>a.b</packageId>
              <modDependencies><li>
                <packageId>erdelf.HumanoidAlienRaces</packageId>
                <displayName>HAR</displayName>
                <steamWorkshopUrl>steam://url/CommunityFilePage/839005762</steamWorkshopUrl>
                <alternativePackageIds IgnoreIfNoMatchingField="True"><li>erdelf.HumanoidAlienRaces.dev</li></alternativePackageIds>
              </li></modDependencies>
            </ModMetaData>
            """,
        ).dependencies.single()
        assertEquals("erdelf.humanoidalienraces", dep.packageId.value)
        assertEquals("HAR", dep.displayName)
        assertEquals("839005762", dep.workshopId)
        assertEquals(listOf(PackageId.of("erdelf.humanoidalienraces.dev")), dep.alternatives)
    }

    @Test
    fun `dependency without packageId is skipped`() {
        val info = parse(
            "<ModMetaData><packageId>a.b</packageId><modDependencies><li><displayName>X</displayName></li></modDependencies></ModMetaData>",
        )
        assertEquals(emptyList(), info.dependencies)
    }

    @Test
    fun `ByVersion for the game version overrides the plain element`() {
        val xml = """
            <ModMetaData><packageId>a.b</packageId>
              <modDependencies><li><packageId>plain.dep</packageId></li></modDependencies>
              <modDependenciesByVersion>
                <v1.5><li><packageId>old.dep</packageId></li></v1.5>
                <v1.6><li><packageId>new.dep</packageId></li></v1.6>
              </modDependenciesByVersion>
              <loadAfter><li>plain.after</li></loadAfter>
              <loadAfterByVersion><v1.6><li>new.after</li></v1.6></loadAfterByVersion>
            </ModMetaData>
        """
        val info16 = parse(xml)
        assertEquals(listOf("new.dep"), info16.dependencies.map { it.packageId.value })
        assertEquals(listOf("new.after"), info16.loadAfter.map { it.value })

        val info17 = parse(xml, GameVersion.parse("1.7"))
        assertEquals(listOf("plain.dep"), info17.dependencies.map { it.packageId.value })
        assertEquals(listOf("plain.after"), info17.loadAfter.map { it.value })
    }

    @Test
    fun `an empty ByVersion entry still overrides`() {
        val info = parse(
            """
            <ModMetaData><packageId>a.b</packageId>
              <modDependencies><li><packageId>plain.dep</packageId></li></modDependencies>
              <modDependenciesByVersion><v1.6></v1.6></modDependenciesByVersion>
            </ModMetaData>
            """,
        )
        assertEquals(emptyList(), info.dependencies)
    }

    @Test
    fun `reads load order and incompatibility lists, forceLoad has no ByVersion variant`() {
        val info = parse(
            """
            <ModMetaData><packageId>a.b</packageId>
              <loadBefore><li>x.before</li></loadBefore>
              <forceLoadAfter><li>x.fafter</li></forceLoadAfter>
              <forceLoadAfterByVersion><v1.6><li>x.ignored</li></v1.6></forceLoadAfterByVersion>
              <forceLoadBefore><li>x.fbefore</li></forceLoadBefore>
              <incompatibleWith><li>x.incompat</li></incompatibleWith>
            </ModMetaData>
            """,
        )
        assertEquals(listOf("x.before"), info.loadBefore.map { it.value })
        assertEquals(listOf("x.fafter"), info.forceLoadAfter.map { it.value })
        assertEquals(listOf("x.fbefore"), info.forceLoadBefore.map { it.value })
        assertEquals(listOf("x.incompat"), info.incompatibleWith.map { it.value })
    }

    @Test
    fun `missing packageId is an error`() {
        assertFailsWith<AboutXmlException> { parse("<ModMetaData><name>X</name></ModMetaData>") }
    }

    @Test
    fun `malformed XML is an error`() {
        assertFailsWith<AboutXmlException> { parse("<ModMetaData><packageId>a.b</ModMetaData>") }
    }

    @Test
    fun `wrong root is an error`() {
        assertFailsWith<AboutXmlException> { parse("<Defs><packageId>a.b</packageId></Defs>") }
    }

    @Test
    fun `workshop id from urls`() {
        assertEquals("2009463077", AboutXmlParser.workshopIdFromUrl("steam://url/CommunityFilePage/2009463077"))
        assertEquals("2009463077", AboutXmlParser.workshopIdFromUrl("https://steamcommunity.com/sharedfiles/filedetails/?id=2009463077"))
        assertNull(AboutXmlParser.workshopIdFromUrl("https://github.com/x/y"))
        assertNull(AboutXmlParser.workshopIdFromUrl(null))
    }
}
