package rimmods.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import rimmods.scan.PackageId

class BuildEditsTest {
    private fun ids(vararg s: String) = s.map(PackageId::of)

    private val lib = Library(
        builds = listOf(Build("Solo", ids("a.one", "a.two")), Build("Coop", ids("a.one"))),
        currentBuild = "Coop",
    )

    private fun Library.build(name: String) = builds.first { it.name == name }

    // --- current build ---

    @Test
    fun `current build falls back to the first one, or none`() {
        assertEquals("Coop", BuildEdits.current(lib)?.name)
        assertEquals("Solo", BuildEdits.current(lib.copy(currentBuild = null))?.name)
        assertEquals("Solo", BuildEdits.current(lib.copy(currentBuild = "Gone"))?.name)
        assertEquals(null, BuildEdits.current(Library()))
    }

    @Test
    fun `switch the current build`() {
        assertEquals("Solo", BuildEdits.setCurrent(lib, "Solo").currentBuild)
        assertFailsWith<LibraryEditException> { BuildEdits.setCurrent(lib, "Nope") }
    }

    // --- create / rename / duplicate / delete ---

    @Test
    fun `create a build at the end, trimmed, and make it current`() {
        val r = BuildEdits.create(lib, "  Odyssey ")
        assertEquals(listOf("Solo", "Coop", "Odyssey"), r.builds.map { it.name })
        assertEquals(Build("Odyssey"), r.build("Odyssey"))
        assertEquals("Odyssey", r.currentBuild)
    }

    @Test
    fun `build names are non-empty and unique`() {
        assertFailsWith<LibraryEditException> { BuildEdits.create(lib, "  ") }
        assertFailsWith<LibraryEditException> { BuildEdits.create(lib, "Solo") }
        assertFailsWith<LibraryEditException> { BuildEdits.rename(lib, "Solo", "Coop") }
    }

    @Test
    fun `rename keeps mods and position, and the current build follows`() {
        val r = BuildEdits.rename(lib, "Coop", " Multiplayer ")
        assertEquals(listOf("Solo", "Multiplayer"), r.builds.map { it.name })
        assertEquals(ids("a.one"), r.build("Multiplayer").mods)
        assertEquals("Multiplayer", r.currentBuild)
        assertEquals("Coop", BuildEdits.rename(lib, "Solo", "Main").currentBuild)
        assertEquals(lib, BuildEdits.rename(lib, "Solo", "Solo"))
        assertFailsWith<LibraryEditException> { BuildEdits.rename(lib, "Nope", "X") }
    }

    @Test
    fun `duplicate goes right after the original with a fresh name and becomes current`() {
        val r = BuildEdits.duplicate(lib, "Solo")
        assertEquals(listOf("Solo", "Solo copy", "Coop"), r.builds.map { it.name })
        assertEquals(ids("a.one", "a.two"), r.build("Solo copy").mods)
        assertEquals("Solo copy", r.currentBuild)
        assertEquals(listOf("Solo", "Solo copy 2", "Solo copy", "Coop"), BuildEdits.duplicate(r, "Solo").builds.map { it.name })
    }

    @Test
    fun `deleting the current build makes its right neighbour current, else the left one`() {
        val three = BuildEdits.create(lib, "Third").let { BuildEdits.setCurrent(it, "Coop") }
        assertEquals("Third", BuildEdits.delete(three, "Coop").currentBuild)
        assertEquals("Coop", BuildEdits.delete(BuildEdits.setCurrent(three, "Third"), "Third").currentBuild)
        assertEquals("Coop", BuildEdits.delete(three, "Solo").currentBuild)
        assertEquals(null, BuildEdits.delete(Library(builds = listOf(Build("Only")), currentBuild = "Only"), "Only").currentBuild)
        assertFailsWith<LibraryEditException> { BuildEdits.delete(lib, "Nope") }
    }

    // --- selecting mods ---

    @Test
    fun `select adds mods once, deselect removes them`() {
        val r = BuildEdits.select(lib, "Coop", ids("a.two", "a.one", "a.two", "b.new"), selected = true)
        assertEquals(ids("a.one", "a.two", "b.new"), r.build("Coop").mods)
        val d = BuildEdits.select(r, "Coop", ids("a.one", "not.there"), selected = false)
        assertEquals(ids("a.two", "b.new"), d.build("Coop").mods)
        assertEquals(ids("a.one", "a.two"), d.build("Solo").mods)
        assertFailsWith<LibraryEditException> { BuildEdits.select(lib, "Nope", ids("a.one"), selected = true) }
    }
}
