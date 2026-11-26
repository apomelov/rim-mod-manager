package rimmods.library

import kotlin.test.Test
import kotlin.test.assertEquals
import rimmods.scan.AboutInfo
import rimmods.scan.Mod
import rimmods.scan.PackageId

class LibrarySyncTest {
    private fun id(s: String) = PackageId.of(s)
    private fun ids(vararg s: String) = s.map(PackageId::of)

    private fun mod(packageId: String, name: String = packageId) = Mod(
        about = AboutInfo(
            packageId = id(packageId), packageIdOriginal = packageId, name = name, authors = emptyList(),
            description = "", supportedVersions = emptyList(), dependencies = emptyList(),
            loadAfter = emptyList(), loadBefore = emptyList(), forceLoadAfter = emptyList(),
            forceLoadBefore = emptyList(), incompatibleWith = emptyList(),
        ),
        workshopId = null,
        folder = "/mods/$packageId",
    )

    private fun entry(
        packageId: String,
        name: String = packageId,
        missing: Boolean = false,
        hidden: Boolean = false,
        translations: List<ModEntry> = emptyList(),
    ) = ModEntry(id(packageId), name, missing, hidden, translations)

    private fun translation(packageId: String, name: String = packageId) = entry(packageId, name, hidden = true)

    @Test
    fun `first run - every mod is in Sortout, sorted by name case-insensitively`() {
        val view = LibrarySync.view(
            LibraryStore.default(),
            listOf(mod("c.mod", "charlie"), mod("a.mod", "Alpha"), mod("b.mod", "bravo")),
        )
        assertEquals(listOf(entry("a.mod", "Alpha"), entry("b.mod", "bravo"), entry("c.mod", "charlie")), view.sortout)
        assertEquals(LibraryStore.PREDEFINED_FOLDERS, view.folders.map { it.name })
        assertEquals(true, view.folders.all { it.mods.isEmpty() })
    }

    @Test
    fun `placed mods leave Sortout and keep their stored order and folder structure`() {
        val library = Library(
            folders = listOf(
                Folder(
                    "QoL", expanded = true,
                    folders = listOf(Folder("base", mods = ids("b.mod"))),
                    mods = ids("z.mod", "a.mod"),
                ),
            ),
        )
        val view = LibrarySync.view(library, listOf(mod("a.mod"), mod("b.mod"), mod("z.mod"), mod("new.mod")))

        assertEquals(listOf(entry("new.mod")), view.sortout)
        val qol = view.folders.single()
        assertEquals(true, qol.expanded)
        assertEquals(listOf(entry("z.mod"), entry("a.mod")), qol.mods)
        assertEquals(FolderView("base", false, emptyList(), listOf(entry("b.mod"))), qol.folders.single())
    }

    @Test
    fun `a mod in the tree but not on disk keeps its place, flagged missing, named by packageId`() {
        val library = Library(folders = listOf(Folder("A", mods = ids("gone.mod", "here.mod"))))
        val view = LibrarySync.view(library, listOf(mod("here.mod", "Here")))
        assertEquals(listOf(entry("gone.mod", missing = true), entry("here.mod", "Here")), view.folders.single().mods)
        assertEquals(emptyList(), view.sortout)
    }

    @Test
    fun `an unmarked translation is an ordinary mod in Sortout`() {
        val view = LibrarySync.view(Library(), listOf(mod("a.mod"), mod("a.mod.ru")))
        assertEquals(listOf(entry("a.mod"), entry("a.mod.ru")), view.sortout)
    }

    @Test
    fun `a marked translation is hidden under its original in a folder`() {
        val library = Library(
            folders = listOf(Folder("A", mods = ids("a.mod"))),
            translations = mapOf(id("a.mod.ru") to ids("a.mod")),
        )
        val view = LibrarySync.view(library, listOf(mod("a.mod"), mod("a.mod.ru", "A RU")))
        assertEquals(listOf(entry("a.mod", translations = listOf(translation("a.mod.ru", "A RU")))), view.folders.single().mods)
        assertEquals(emptyList(), view.sortout)
    }

    @Test
    fun `a marked translation follows its original into Sortout`() {
        val library = Library(translations = mapOf(id("a.mod.ru") to ids("a.mod")))
        val view = LibrarySync.view(library, listOf(mod("a.mod"), mod("a.mod.ru")))
        assertEquals(listOf(entry("a.mod", translations = listOf(translation("a.mod.ru")))), view.sortout)
    }

    @Test
    fun `a translation of several mods appears under each, sorted by name`() {
        val library = Library(
            folders = listOf(Folder("A", mods = ids("one.mod")), Folder("B", mods = ids("two.mod"))),
            translations = mapOf(id("z.pack.ru") to ids("one.mod", "two.mod"), id("a.one.ru") to ids("one.mod")),
        )
        val view = LibrarySync.view(library, listOf(mod("one.mod"), mod("two.mod"), mod("z.pack.ru"), mod("a.one.ru")))
        assertEquals(
            listOf(entry("one.mod", translations = listOf(translation("a.one.ru"), translation("z.pack.ru")))),
            view.folders[0].mods,
        )
        assertEquals(listOf(entry("two.mod", translations = listOf(translation("z.pack.ru")))), view.folders[1].mods)
    }

    @Test
    fun `a translation whose originals are not on disk stays in Sortout, hidden`() {
        val library = Library(
            folders = listOf(Folder("A", mods = ids("gone.mod"))),
            translations = mapOf(id("gone.mod.ru") to ids("gone.mod", "never.seen"), id("empty.ru") to emptyList()),
        )
        val view = LibrarySync.view(library, listOf(mod("gone.mod.ru"), mod("empty.ru")))
        assertEquals(listOf(translation("empty.ru"), translation("gone.mod.ru")), view.sortout)
        assertEquals(listOf(entry("gone.mod", missing = true)), view.folders.single().mods)
    }

    @Test
    fun `a translation placed in a folder is still shown under its original, not in the folder`() {
        val library = Library(
            folders = listOf(Folder("A", mods = ids("a.mod")), Folder("T", mods = ids("a.mod.ru"))),
            translations = mapOf(id("a.mod.ru") to ids("a.mod")),
        )
        val view = LibrarySync.view(library, listOf(mod("a.mod"), mod("a.mod.ru")))
        assertEquals(listOf(entry("a.mod", translations = listOf(translation("a.mod.ru")))), view.folders[0].mods)
        assertEquals(emptyList(), view.folders[1].mods)
    }

    @Test
    fun `a translation placed in a folder with no original on disk stays in the folder, hidden`() {
        val library = Library(
            folders = listOf(Folder("T", mods = ids("a.mod.ru"))),
            translations = mapOf(id("a.mod.ru") to ids("a.mod")),
        )
        val view = LibrarySync.view(library, listOf(mod("a.mod.ru")))
        assertEquals(listOf(translation("a.mod.ru")), view.folders.single().mods)
        assertEquals(emptyList(), view.sortout)
    }

    @Test
    fun `a missing translation is not listed under its original`() {
        val library = Library(
            folders = listOf(Folder("A", mods = ids("a.mod"))),
            translations = mapOf(id("gone.ru") to ids("a.mod")),
        )
        val view = LibrarySync.view(library, listOf(mod("a.mod")))
        assertEquals(listOf(entry("a.mod")), view.folders.single().mods)
    }
}
