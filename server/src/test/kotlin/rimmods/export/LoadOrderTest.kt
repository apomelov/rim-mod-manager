package rimmods.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import rimmods.scan.PackageId

class LoadOrderTest {
    private fun ids(vararg s: String) = s.map(PackageId::of)

    private fun rules(
        before: List<String> = emptyList(),
        after: List<String> = emptyList(),
        forceBefore: List<String> = emptyList(),
        forceAfter: List<String> = emptyList(),
    ) = LoadRules(ids(*before.toTypedArray()), ids(*after.toTypedArray()), ids(*forceBefore.toTypedArray()), ids(*forceAfter.toTypedArray()))

    private fun sort(initial: List<String>, rules: Map<String, LoadRules>): List<String> {
        val r = LoadOrder.sort(ids(*initial.toTypedArray())) { rules[it.value] ?: LoadRules.NONE }
        return assertIs<LoadOrder.Result.Sorted>(r).order.map { it.value }
    }

    // --- the game's DFS ---

    @Test
    fun `a list that already satisfies every rule comes out unchanged`() {
        val r = mapOf("c" to rules(after = listOf("a")), "a" to rules(before = listOf("b")))
        assertEquals(listOf("a", "b", "c"), sort(listOf("a", "b", "c"), r))
    }

    @Test
    fun `loadAfter and loadBefore move a mod, nothing else moves`() {
        assertEquals(listOf("b", "a", "c"), sort(listOf("a", "b", "c"), mapOf("a" to rules(after = listOf("b")))))
        assertEquals(listOf("c", "a", "b"), sort(listOf("a", "b", "c"), mapOf("c" to rules(before = listOf("a")))))
    }

    @Test
    fun `forceLoad rules order like the soft ones`() {
        assertEquals(listOf("b", "a"), sort(listOf("a", "b"), mapOf("a" to rules(forceAfter = listOf("b")))))
        assertEquals(listOf("b", "a"), sort(listOf("a", "b"), mapOf("b" to rules(forceBefore = listOf("a")))))
    }

    @Test
    fun `predecessors are emitted depth-first in the order the rules list them`() {
        val r = mapOf("a" to rules(after = listOf("d", "c")), "c" to rules(after = listOf("e")))
        assertEquals(listOf("d", "e", "c", "a", "b"), sort(listOf("a", "b", "c", "d", "e"), r))
    }

    @Test
    fun `edges are added in the initial order of the mod declaring them`() {
        // Edges into "x": "z" (z.loadBefore, z comes before x) then "y" (x.loadAfter). "w" pulls x in first.
        val r = mapOf("w" to rules(after = listOf("x")), "z" to rules(before = listOf("x")), "x" to rules(after = listOf("y")))
        assertEquals(listOf("z", "y", "x", "w"), sort(listOf("w", "z", "x", "y"), r))
    }

    @Test
    fun `rules naming mods outside the list, or the mod itself, are ignored`() {
        val r = mapOf("a" to rules(after = listOf("missing", "a"), before = listOf("other.missing")))
        assertEquals(listOf("a", "b"), sort(listOf("a", "b"), r))
    }

    @Test
    fun `a cycle stops the sort and names exactly the mods in it`() {
        val r = mapOf(
            "a" to rules(after = listOf("b")),
            "b" to rules(after = listOf("c")),
            "c" to rules(forceAfter = listOf("b")),
        )
        val result = LoadOrder.sort(ids("a", "b", "c", "d")) { r[it.value] ?: LoadRules.NONE }
        assertEquals(setOf("b", "c"), assertIs<LoadOrder.Result.Cycle>(result).mods.map { it.value }.toSet())
    }

    // --- initial order and Core/DLCs ---

    @Test
    fun `initial order is Core, DLCs in release order, then mods by name`() {
        val names = mapOf("m.b" to "beta", "m.a" to "Alpha", "m.a2" to "alpha")
        val initial = LoadOrder.initialOrder(
            ids("m.b", "ludeon.rimworld.biotech", "m.a2", "ludeon.rimworld", "m.a", "ludeon.rimworld.royalty"),
        ) { names[it.value] ?: it.value }
        assertEquals(
            ids("ludeon.rimworld", "ludeon.rimworld.royalty", "ludeon.rimworld.biotech", "m.a", "m.a2", "m.b"),
            initial,
        )
    }

    @Test
    fun `Core and DLC rules keep them in release order, and Harmony can go above Core`() {
        val initial = ids("ludeon.rimworld.biotech", "ludeon.rimworld", "ludeon.rimworld.ideology", "brrainz.harmony")
        val harmony = rules(before = listOf("ludeon.rimworld"))
        val r = LoadOrder.sort(initial) { if (it.value == "brrainz.harmony") harmony else LoadOrder.ludeonRules(it) ?: LoadRules.NONE }
        assertEquals(
            ids("brrainz.harmony", "ludeon.rimworld", "ludeon.rimworld.ideology", "ludeon.rimworld.biotech"),
            assertIs<LoadOrder.Result.Sorted>(r).order,
        )
    }
}
