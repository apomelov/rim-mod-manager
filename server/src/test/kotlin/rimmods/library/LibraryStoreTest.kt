package rimmods.library

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import rimmods.scan.PackageId

class LibraryStoreTest {
    @TempDir
    lateinit var dir: Path

    private fun ids(vararg s: String) = s.map(PackageId::of)

    @Test
    fun `default library has the predefined empty top-level folders`() {
        val lib = LibraryStore.default()
        assertEquals(listOf("Library", "UI", "UX", "QoL", "Tools"), lib.folders.map { it.name })
        assertTrue(lib.folders.all { it.mods.isEmpty() && it.folders.isEmpty() && !it.expanded })
        assertEquals(emptyList(), lib.builds)
    }

    @Test
    fun `parses the documented format`() {
        val loaded = LibraryStore.parse(
            """
            folders:
              - name: QoL
                expanded: true
                folders:
                  - name: base
                    mods: [brrainz.harmony, some.author.mod]
                mods: [another.mod]
              - name: Library
            translations:
              some.mod.ru: [original.mod]
            builds:
              - name: Solo
                mods: [some.author.mod, another.mod]
            currentBuild: Solo
            """.trimIndent(),
        )
        assertEquals(emptyList(), loaded.problems)
        val lib = loaded.library
        val qol = lib.folders[0]
        assertEquals("QoL", qol.name)
        assertTrue(qol.expanded)
        assertEquals(ids("another.mod"), qol.mods)
        assertEquals(Folder("base", false, emptyList(), ids("brrainz.harmony", "some.author.mod")), qol.folders.single())
        assertEquals(Folder("Library"), lib.folders[1])
        assertEquals(mapOf(PackageId.of("some.mod.ru") to ids("original.mod")), lib.translations)
        assertEquals(listOf(Build("Solo", ids("some.author.mod", "another.mod"))), lib.builds)
        assertEquals("Solo", lib.currentBuild)
    }

    @Test
    fun `an empty file is an empty library`() {
        assertEquals(LoadedLibrary(Library(), emptyList()), LibraryStore.parse(""))
    }

    @Test
    fun `packageIds are lowercased everywhere`() {
        val lib = LibraryStore.parse(
            """
            folders: [{name: A, mods: [Brrainz.Harmony]}]
            translations: {Some.Mod.RU: [Original.Mod]}
            builds: [{name: B, mods: [Brrainz.Harmony]}]
            """.trimIndent(),
        ).library
        assertEquals(ids("brrainz.harmony"), lib.folders.single().mods)
        assertEquals(mapOf(PackageId.of("some.mod.ru") to ids("original.mod")), lib.translations)
        assertEquals(ids("brrainz.harmony"), lib.builds.single().mods)
    }

    @Test
    fun `a mod in several folders stays in the first one, depth-first, and is reported`() {
        val loaded = LibraryStore.parse(
            """
            folders:
              - name: A
                folders:
                  - name: A1
                    mods: [x.mod]
                mods: [x.mod]
              - name: B
                mods: [X.Mod, y.mod]
            """.trimIndent(),
        )
        val a = loaded.library.folders[0]
        assertEquals(ids("x.mod"), a.folders.single().mods)
        assertEquals(emptyList(), a.mods)
        assertEquals(ids("y.mod"), loaded.library.folders[1].mods)
        assertEquals(1, loaded.problems.size)
        assertTrue("x.mod" in loaded.problems.single())
    }

    @Test
    fun `duplicate sibling folder names are reported and kept`() {
        val loaded = LibraryStore.parse("folders: [{name: A, mods: [a.one]}, {name: A, mods: [a.two]}]")
        assertEquals(listOf("A", "A"), loaded.library.folders.map { it.name })
        assertEquals(1, loaded.problems.size)
    }

    @Test
    fun `malformed YAML is an error`() {
        assertFailsWith<LibraryFileException> { LibraryStore.parse("folders: [{name: A, mods: [") }
        assertFailsWith<LibraryFileException> { LibraryStore.parse("folders: 42") }
    }

    @Test
    fun `write then parse gives the same library`() {
        val lib = Library(
            folders = listOf(
                Folder("QoL", true, listOf(Folder("base", false, emptyList(), ids("a.one"))), ids("a.two")),
                Folder("Library"),
            ),
            translations = mapOf(PackageId.of("t.ru") to ids("a.one", "a.two"), PackageId.of("t2.ru") to emptyList()),
            builds = listOf(Build("Solo", ids("a.one"))),
            currentBuild = "Solo",
        )
        assertEquals(LoadedLibrary(lib, emptyList()), LibraryStore.parse(LibraryStore.write(lib)))
    }

    @Test
    fun `written YAML is plain, and ambiguous names stay strings`() {
        val lib = Library(folders = listOf(Folder("QoL", mods = ids("a.one")), Folder("yes"), Folder("123")))
        val text = LibraryStore.write(lib)
        assertTrue("name: QoL" in text, text)
        assertTrue(text.endsWith("\n"))
        assertEquals(lib, LibraryStore.parse(text).library)
    }

    @Test
    fun `load creates the default file when it is missing`() {
        val file = dir.resolve("rimmodmanager").resolve("library.yaml")
        val loaded = LibraryStore.load(file)
        assertEquals(LibraryStore.default(), loaded.library)
        assertTrue(file.exists())
        assertEquals(LibraryStore.default(), LibraryStore.parse(file.readText()).library)
    }

    @Test
    fun `load never overwrites a malformed file`() {
        val file = dir.resolve("library.yaml")
        file.writeText("folders: [")
        assertFailsWith<LibraryFileException> { LibraryStore.load(file) }
        assertEquals("folders: [", file.readText())
    }

    @Test
    fun `save then load round-trips`() {
        val file = dir.resolve("library.yaml")
        val lib = Library(folders = listOf(Folder("A", mods = ids("a.one"))))
        LibraryStore.save(file, lib)
        assertEquals(lib, LibraryStore.load(file).library)
    }
}
