package rimmods.translations

import kotlin.test.Test
import kotlin.test.assertEquals
import rimmods.scan.AboutInfo
import rimmods.scan.Dependency
import rimmods.scan.Mod
import rimmods.scan.PackageId

class TranslationFinderTest {
    private fun id(s: String) = PackageId.of(s)

    private class Spec(val mod: Mod, val keys: ModKeySets)

    private fun keys(prefix: String, n: Int) = (1..n).map { "def:$prefix$it" }.toSet()

    private fun mod(
        packageId: String,
        provided: Set<String> = emptySet(),
        translated: Set<String> = emptySet(),
        languages: List<String> = listOf("English"),
        content: List<String> = listOf("Defs"),
        deps: List<String> = emptyList(),
        description: String = "",
        workshopId: String? = null,
    ) = Spec(
        Mod(
            about = AboutInfo(
                packageId = id(packageId), packageIdOriginal = packageId, name = packageId, authors = emptyList(),
                description = description, supportedVersions = emptyList(), dependencies = deps.map { Dependency(id(it)) },
                loadAfter = emptyList(), loadBefore = emptyList(), forceLoadAfter = emptyList(), forceLoadBefore = emptyList(),
                incompatibleWith = emptyList(),
            ),
            workshopId = workshopId,
            folder = "/mods/$packageId",
            languages = languages,
            content = content,
        ),
        ModKeySets(provided, translated),
    )

    private fun ru(packageId: String, translated: Set<String>, deps: List<String> = emptyList(), description: String = "") =
        mod(packageId, translated = translated, languages = listOf("Russian"), content = emptyList(), deps = deps, description = description)

    private fun find(vararg specs: Spec, sortout: Set<String>? = null): List<TranslationSuggestion> {
        val byId = specs.associateBy { it.mod.packageId }
        return TranslationFinder.find(
            mods = specs.map { it.mod },
            sortout = sortout?.map(::id)?.toSet() ?: byId.keys,
        ) { byId.getValue(it.packageId).keys }
    }

    private fun TranslationSuggestion.pairs() = originals.map { "${translation.value}->${it.packageId.value}" }

    @Test
    fun `a pure translation is matched to the mod defining the keys it translates`() {
        val r = find(
            mod("lib", provided = keys("x", 50) + "def:o1"),
            mod("orig", provided = keys("o", 20)),
            ru("orig.ru", translated = keys("o", 20)),
        )
        assertEquals(listOf("orig.ru->orig"), r.single().pairs())
        assertEquals(20, r.single().originals.single().sharedKeys)
        assertEquals(20 to 20, r.single().originals.single().let { it.translatedKeys to it.originalKeys })
    }

    @Test
    fun `a pack translation gets every module, libraries sharing a key or two are left out`() {
        val r = find(
            mod("core", provided = keys("c", 100)),
            mod("big", provided = keys("b", 100)),
            mod("small", provided = keys("s", 10)), // 8 of 108 keys: under 10 %, but most of its own
            mod("library", provided = keys("l", 400) + keys("c", 2) + keys("x", 9)),
            ru("pack.ru", translated = keys("b", 100) + keys("s", 8) + keys("x", 9)), // library: 9 keys, too few either way
        )
        assertEquals(listOf("pack.ru->big", "pack.ru->small"), r.single().pairs())
        assertEquals(listOf(100 to 100, 8 to 10), r.single().originals.map { it.translatedKeys to it.originalKeys })
    }

    @Test
    fun `a library sharing many keys but a small part of both is left out`() {
        val r = find(
            mod("orig", provided = keys("o", 500)),
            mod("vfe.core", provided = keys("v", 1000)),
            ru("orig.ru", translated = keys("o", 500) + keys("v", 40)),
        )
        assertEquals(listOf("orig.ru->orig"), r.single().pairs())
    }

    @Test
    fun `a tiny translation needs only as many keys as it has`() {
        val r = find(mod("orig", provided = keys("o", 2)), ru("orig.ru", translated = keys("o", 2)))
        assertEquals(listOf("orig.ru->orig"), r.single().pairs())
    }

    @Test
    fun `a tie goes to the mod named by a dependency or a Workshop link`() {
        val r = find(
            mod("bundle", provided = keys("p", 10)),
            mod("portal", provided = keys("p", 10)),
            ru("portal.ru", translated = keys("p", 10), deps = listOf("portal")),
        )
        assertEquals(listOf("portal.ru->portal"), r.single().pairs())
        assertEquals(true, r.single().originals.single().dependency)

        val linked = find(
            mod("bundle", provided = keys("p", 10)),
            mod("portal", provided = keys("p", 10), workshopId = "123456789"),
            ru("portal.ru", translated = keys("p", 10), description = "Original: https://steamcommunity.com/sharedfiles/filedetails/?id=123456789"),
        )
        assertEquals(listOf("portal.ru->portal"), linked.single().pairs())
        assertEquals(true, linked.single().originals.single().link)
    }

    @Test
    fun `two translations of one mod are two suggestions, matched ones first by shared keys, unmatched last`() {
        val r = find(
            mod("orig", provided = keys("o", 30)),
            ru("a.ru", translated = keys("o", 10)),
            ru("b.ru", translated = keys("o", 30)),
            ru("lonely.ru", translated = keys("z", 5)),
        )
        assertEquals(listOf("b.ru", "a.ru", "lonely.ru"), r.map { it.translation.value })
        assertEquals(emptyList(), r.last().originals)
    }

    @Test
    fun `only Sortout mods that translate something are candidates`() {
        val r = find(
            mod("orig", provided = keys("o", 10)),
            ru("placed.ru", translated = keys("o", 10)),
            mod("english.only", content = emptyList()), // English-only Languages: no translation keys
            sortout = setOf("orig", "english.only"),
        )
        assertEquals(emptyList(), r)
    }

    @Test
    fun `translation keys make a candidate even without a language for this game version`() {
        val old = mod("old.ru", translated = keys("o", 10), languages = emptyList(), content = emptyList())
        val r = find(mod("orig", provided = keys("o", 10)), old)
        assertEquals(listOf("old.ru->orig"), r.single().pairs())
    }

    @Test
    fun `a mod with content is a candidate only when one mod it depends on covers most of what it translates`() {
        val fork = mod("orig.fork", provided = keys("o", 10), translated = keys("o", 10), languages = listOf("English", "Russian"), deps = listOf("orig"))
        val plain = mod("other", provided = keys("q", 10), translated = keys("o", 10), languages = listOf("English", "Russian"))
        val r = find(mod("orig", provided = keys("o", 10)), fork, plain)
        assertEquals(listOf("orig.fork->orig"), r.flatMap { it.pairs() })
    }
}
