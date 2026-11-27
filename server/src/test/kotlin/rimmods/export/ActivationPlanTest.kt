package rimmods.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import rimmods.scan.AboutInfo
import rimmods.scan.Dependency
import rimmods.scan.Mod
import rimmods.scan.PackageId

class ActivationPlanTest {
    private fun id(s: String) = PackageId.of(s)
    private fun ids(vararg s: String) = s.map(PackageId::of)

    private fun dep(packageId: String, vararg alternatives: String) =
        Dependency(id(packageId), displayName = "$packageId name", workshopId = "123", alternatives = ids(*alternatives))

    private fun mod(
        packageId: String,
        name: String = packageId,
        deps: List<Dependency> = emptyList(),
        loadAfter: List<String> = emptyList(),
        loadBefore: List<String> = emptyList(),
    ) = Mod(
        about = AboutInfo(
            packageId = id(packageId), packageIdOriginal = packageId, name = name, authors = emptyList(),
            description = "", supportedVersions = emptyList(), dependencies = deps,
            loadAfter = ids(*loadAfter.toTypedArray()), loadBefore = ids(*loadBefore.toTypedArray()),
            forceLoadAfter = emptyList(), forceLoadBefore = emptyList(), incompatibleWith = emptyList(),
        ),
        workshopId = null,
        folder = "/mods/$packageId",
    )

    private fun plan(
        selected: List<String>,
        onDisk: List<Mod>,
        translations: Map<String, List<String>> = emptyMap(),
    ) = ActivationPlan.of(
        selected = ids(*selected.toTypedArray()),
        onDisk = onDisk.associateBy { it.packageId },
        translations = translations.map { (t, o) -> id(t) to ids(*o.toTypedArray()) }.toMap(),
    )

    private val core = id("ludeon.rimworld")

    @Test
    fun `selected mods plus Core, sorted`() {
        val p = plan(listOf("b.mod", "a.mod"), listOf(mod("a.mod", "Alpha"), mod("b.mod", "Beta", loadBefore = listOf("a.mod"))))
        assertEquals(listOf(core) + ids("b.mod", "a.mod"), p.order)
        assertEquals(ids("a.mod", "b.mod"), p.selected)
        assertFalse(p.blocked)
    }

    @Test
    fun `dependencies are added transitively`() {
        val p = plan(
            listOf("a.mod"),
            listOf(mod("a.mod", deps = listOf(dep("b.lib"))), mod("b.lib", deps = listOf(dep("c.lib"))), mod("c.lib"), mod("unused")),
        )
        assertEquals(ids("b.lib", "c.lib"), p.dependencies)
        assertEquals(setOf(core) + ids("a.mod", "b.lib", "c.lib"), p.order.toSet())
    }

    @Test
    fun `a dependency is satisfied by an alternative already in the set, else the first one on disk is added`() {
        val inSet = plan(listOf("a.mod", "alt.two"), listOf(mod("a.mod", deps = listOf(dep("main", "alt.one", "alt.two"))), mod("main"), mod("alt.two")))
        assertEquals(emptyList(), inSet.dependencies)

        val onDisk = plan(listOf("a.mod"), listOf(mod("a.mod", deps = listOf(dep("main", "alt.one", "alt.two"))), mod("alt.one"), mod("alt.two")))
        assertEquals(ids("alt.one"), onDisk.dependencies)
    }

    @Test
    fun `a DLC is added only when some mod requires it`() {
        val p = plan(
            listOf("a.mod", "b.mod"),
            listOf(
                mod("a.mod", deps = listOf(dep("Ludeon.RimWorld.Biotech"))),
                mod("b.mod", deps = listOf(dep("ludeon.rimworld.royalty"), dep("ludeon.rimworld"))),
            ),
        )
        assertEquals(ids("ludeon.rimworld.royalty", "ludeon.rimworld.biotech"), p.dlcs)
        assertEquals(ids("ludeon.rimworld", "ludeon.rimworld.royalty", "ludeon.rimworld.biotech", "a.mod", "b.mod"), p.order)
        assertEquals(emptyList(), p.dependencies)
    }

    @Test
    fun `translations of every mod in the set come along, with their own dependencies`() {
        val p = plan(
            listOf("a.mod"),
            listOf(
                mod("a.mod", deps = listOf(dep("lib"))),
                mod("lib"),
                mod("a.ru", deps = listOf(dep("a.mod"), dep("ru.lib"))),
                mod("lib.ru"),
                mod("ru.lib"),
                mod("other.ru"),
            ),
            translations = mapOf("a.ru" to listOf("a.mod"), "lib.ru" to listOf("lib"), "other.ru" to listOf("other"), "gone.ru" to listOf("a.mod")),
        )
        assertEquals(ids("a.ru", "lib.ru"), p.translations)
        assertEquals(ids("lib", "ru.lib"), p.dependencies)
    }

    @Test
    fun `a missing selected mod is only a warning`() {
        val p = plan(listOf("a.mod", "gone.mod"), listOf(mod("a.mod")))
        assertEquals(ids("gone.mod"), p.missingSelected)
        assertFalse(p.blocked)
        assertEquals(listOf(core, id("a.mod")), p.order)
    }

    @Test
    fun `a missing dependency blocks, listing who needs it`() {
        val p = plan(
            listOf("a.mod", "b.mod"),
            listOf(mod("a.mod", deps = listOf(dep("lib"))), mod("b.mod", deps = listOf(dep("lib", "lib.alt")))),
        )
        assertTrue(p.blocked)
        val missing = p.missingDependencies.single()
        assertEquals(id("lib"), missing.dependency.packageId)
        assertEquals("lib name", missing.dependency.displayName)
        assertEquals(ids("a.mod", "b.mod"), missing.requiredBy)
        assertEquals(emptyList(), p.order)
    }

    @Test
    fun `a load order cycle blocks`() {
        val p = plan(listOf("a.mod", "b.mod"), listOf(mod("a.mod", loadAfter = listOf("b.mod")), mod("b.mod", loadAfter = listOf("a.mod"))))
        assertTrue(p.blocked)
        assertEquals(setOf(id("a.mod"), id("b.mod")), p.cycle.toSet())
        assertEquals(emptyList(), p.order)
    }

    private fun game(vararg dlcs: String) = listOf(mod("ludeon.rimworld", "Core")) + dlcs.map { mod(it, it.substringAfterLast('.')) }

    @Test
    fun `a selected DLC is written like a mod`() {
        val p = plan(listOf("ludeon.rimworld.ideology", "a.mod"), game("ludeon.rimworld.ideology", "ludeon.rimworld.biotech") + mod("a.mod"))
        assertEquals(ids("ludeon.rimworld", "ludeon.rimworld.ideology", "a.mod"), p.order)
        assertEquals(ids("ludeon.rimworld.ideology"), p.dlcs)
        assertEquals(ids("a.mod"), p.selected)
    }

    @Test
    fun `with the game folder scanned, a required DLC that is not installed blocks, a selected one only warns`() {
        val p = plan(
            listOf("a.mod", "ludeon.rimworld.odyssey"),
            game("ludeon.rimworld.royalty") + mod("a.mod", deps = listOf(dep("ludeon.rimworld.biotech"))),
        )
        assertEquals(id("ludeon.rimworld.biotech"), p.missingDependencies.single().dependency.packageId)
        assertEquals(ids("ludeon.rimworld.odyssey"), p.missingSelected)
    }
}
