package rimmods.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import rimmods.scan.PackageId

class LibraryEditsTest {
    private fun id(s: String) = PackageId.of(s)
    private fun ids(vararg s: String) = s.map(PackageId::of)
    private fun path(s: String) = FolderPath.parse(s)

    private val lib = Library(
        folders = listOf(
            Folder(
                "QoL", expanded = true,
                folders = listOf(Folder("base", mods = ids("b.one", "b.two")), Folder("other")),
                mods = ids("q.one", "q.two", "q.three"),
            ),
            Folder("UI", mods = ids("u.one")),
        ),
    )

    private fun Library.folder(p: String): Folder {
        var level = folders
        var f: Folder? = null
        for (n in path(p).names) { f = level.first { it.name == n }; level = f.folders }
        return f!!
    }

    // --- moving mods ---

    @Test
    fun `move a mod from Sortout into a folder, at the end`() {
        val r = LibraryEdits.moveMods(lib, ids("new.mod"), path("UI"))
        assertEquals(ids("u.one", "new.mod"), r.folder("UI").mods)
    }

    @Test
    fun `move mods out of other folders into one`() {
        val r = LibraryEdits.moveMods(lib, ids("u.one", "b.one"), path("QoL"))
        assertEquals(ids("q.one", "q.two", "q.three", "u.one", "b.one"), r.folder("QoL").mods)
        assertEquals(emptyList(), r.folder("UI").mods)
        assertEquals(ids("b.two"), r.folder("QoL/base").mods)
    }

    @Test
    fun `move a mod into a nested folder`() {
        val r = LibraryEdits.moveMods(lib, ids("u.one"), path("QoL/other"))
        assertEquals(ids("u.one"), r.folder("QoL/other").mods)
    }

    @Test
    fun `move a mod to Sortout removes it from the tree`() {
        val r = LibraryEdits.moveMods(lib, ids("b.one"), to = null)
        assertEquals(ids("b.two"), r.folder("QoL/base").mods)
    }

    @Test
    fun `moving mods into the root or a missing folder fails`() {
        assertFailsWith<LibraryEditException> { LibraryEdits.moveMods(lib, ids("u.one"), FolderPath.ROOT) }
        assertFailsWith<LibraryEditException> { LibraryEdits.moveMods(lib, ids("u.one"), path("Nope")) }
    }

    // --- moving folders ---

    @Test
    fun `move a folder to the root`() {
        val r = LibraryEdits.moveFolder(lib, path("QoL/other"), FolderPath.ROOT)
        assertEquals(listOf("QoL", "UI", "other"), r.folders.map { it.name })
        assertEquals(listOf("base"), r.folder("QoL").folders.map { it.name })
    }

    @Test
    fun `move a folder with its contents into another folder, at the end`() {
        val r = LibraryEdits.moveFolder(lib, path("QoL/base"), path("UI"))
        assertEquals(Folder("base", mods = ids("b.one", "b.two")), r.folder("UI/base"))
    }

    @Test
    fun `a folder cannot move into itself or a descendant`() {
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(lib, path("QoL"), path("QoL")) }
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(lib, path("QoL"), path("QoL/base")) }
    }

    @Test
    fun `moving a folder next to a same-named folder fails`() {
        val l = LibraryEdits.createFolder(lib, FolderPath.ROOT, "base")
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(l, path("QoL/base"), FolderPath.ROOT) }
    }

    @Test
    fun `a predefined top-level folder cannot leave the root`() {
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(lib, path("UI"), path("QoL")) }
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(lib, path("QoL"), path("UI")) }
        val l = LibraryEdits.createFolder(lib, FolderPath.ROOT, "Content")
        assertFailsWith<LibraryEditException> { LibraryEdits.moveFolder(l, path("UI"), path("Content")) }
    }

    @Test
    fun `a nested folder with a predefined name is not protected`() {
        val l = LibraryEdits.createFolder(lib, path("QoL"), "Tools")
        assertEquals(Folder("Tools"), LibraryEdits.moveFolder(l, path("QoL/Tools"), path("UI")).folder("UI/Tools"))
    }

    // --- create / rename / delete / expand ---

    @Test
    fun `create a folder at the root and nested, trimmed`() {
        val r = LibraryEdits.createFolder(LibraryEdits.createFolder(lib, FolderPath.ROOT, "  Tools "), path("QoL/base"), "deep")
        assertEquals(listOf("QoL", "UI", "Tools"), r.folders.map { it.name })
        assertEquals(Folder("deep"), r.folder("QoL/base/deep"))
    }

    @Test
    fun `folder names must be non-empty, without slash, unique among siblings`() {
        assertFailsWith<LibraryEditException> { LibraryEdits.createFolder(lib, FolderPath.ROOT, "  ") }
        assertFailsWith<LibraryEditException> { LibraryEdits.createFolder(lib, FolderPath.ROOT, "a/b") }
        assertFailsWith<LibraryEditException> { LibraryEdits.createFolder(lib, FolderPath.ROOT, "UI") }
        assertFailsWith<LibraryEditException> { LibraryEdits.createFolder(lib, path("Nope"), "x") }
        assertFailsWith<LibraryEditException> { LibraryEdits.renameFolder(lib, path("UI"), "QoL") }
    }

    @Test
    fun `rename keeps contents and position`() {
        val r = LibraryEdits.renameFolder(lib, path("QoL/base"), "core")
        assertEquals(listOf("core", "other"), r.folder("QoL").folders.map { it.name })
        assertEquals(ids("b.one", "b.two"), r.folder("QoL/core").mods)
        assertEquals(lib, LibraryEdits.renameFolder(lib, path("UI"), "UI"))
    }

    @Test
    fun `delete removes the subtree and its mods return to Sortout`() {
        val r = LibraryEdits.deleteFolder(lib, path("QoL"))
        assertEquals(listOf("UI"), r.folders.map { it.name })
        assertFailsWith<LibraryEditException> { LibraryEdits.deleteFolder(lib, path("Nope")) }
    }

    @Test
    fun `set expanded`() {
        assertEquals(true, LibraryEdits.setExpanded(lib, path("QoL/base"), true).folder("QoL/base").expanded)
        assertEquals(false, LibraryEdits.setExpanded(lib, path("QoL"), false).folder("QoL").expanded)
    }

    // --- translations ---

    @Test
    fun `mark, re-mark and unmark a translation`() {
        val marked = LibraryEdits.markTranslation(lib, id("t.ru"), ids("a.mod", "a.mod"))
        assertEquals(mapOf(id("t.ru") to ids("a.mod")), marked.translations)
        val remarked = LibraryEdits.markTranslation(marked, id("t.ru"), ids("a.mod", "b.mod"))
        assertEquals(mapOf(id("t.ru") to ids("a.mod", "b.mod")), remarked.translations)
        assertEquals(emptyMap(), LibraryEdits.unmarkTranslation(remarked, id("t.ru")).translations)
    }

    @Test
    fun `a translation may have no originals yet`() {
        assertEquals(mapOf(id("t.ru") to emptyList()), LibraryEdits.markTranslation(lib, id("t.ru"), emptyList()).translations)
    }

    @Test
    fun `translations do not chain and do not translate themselves`() {
        val marked = LibraryEdits.markTranslation(lib, id("t.ru"), ids("a.mod"))
        assertFailsWith<LibraryEditException> { LibraryEdits.markTranslation(lib, id("t.ru"), ids("t.ru")) }
        assertFailsWith<LibraryEditException> { LibraryEdits.markTranslation(marked, id("t2.ru"), ids("t.ru")) }
        assertFailsWith<LibraryEditException> { LibraryEdits.markTranslation(marked, id("a.mod"), ids("x.mod")) }
    }

    @Test
    fun `folder paths parse and print`() {
        assertEquals(listOf("QoL", "base"), FolderPath.parse(" QoL / base/").names)
        assertEquals(FolderPath.ROOT, FolderPath.parse(""))
        assertEquals(FolderPath.ROOT, FolderPath.parse(null))
        assertEquals("QoL/base", FolderPath.parse("QoL/base").toString())
    }
}
