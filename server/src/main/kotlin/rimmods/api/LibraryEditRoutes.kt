package rimmods.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import rimmods.Settings
import rimmods.library.BuildEdits
import rimmods.library.FolderPath
import rimmods.library.Library
import rimmods.library.LibraryEdits
import rimmods.library.LibraryStore
import rimmods.scan.PackageId

/** Folder paths are `QoL/base`; the root is "". Mod ids are packageIds. */
@Serializable
data class MoveModsRequest(val mods: List<String>, val to: String? = null)

@Serializable
data class MoveFolderRequest(val from: String, val toParent: String = "")

@Serializable
data class CreateFolderRequest(val parent: String = "", val name: String)

@Serializable
data class RenameFolderRequest(val path: String, val name: String)

@Serializable
data class FolderRequest(val path: String)

@Serializable
data class ExpandedRequest(val path: String, val expanded: Boolean)

@Serializable
data class MarkTranslationRequest(val translation: String, val originals: List<String> = emptyList())

@Serializable
data class UnmarkTranslationRequest(val translation: String)

@Serializable
data class BuildRequest(val name: String)

@Serializable
data class RenameBuildRequest(val name: String, val newName: String)

@Serializable
data class SelectModsRequest(val build: String, val mods: List<String>, val selected: Boolean)

private val editLock = Mutex()

/** Every edit: load the file, apply, save, answer with the fresh library (no rescan). */
fun Route.libraryEditRoutes(state: AppState) {
    suspend fun RoutingCall.edit(change: (Library) -> Library) {
        val response = editLock.withLock {
            val file = state.settings().libraryFile
            val loaded = LibraryStore.load(file)
            val changed = change(loaded.library)
            if (changed != loaded.library) LibraryStore.save(file, changed)
            libraryResponse(state, loaded.copy(library = changed))
        }
        respond(response)
    }

    fun ids(list: List<String>) = list.map(PackageId::of)

    post("/api/library/move-mods") {
        val r = call.receive<MoveModsRequest>()
        call.edit { LibraryEdits.moveMods(it, ids(r.mods), r.to?.let(FolderPath::parse)) }
    }
    post("/api/library/move-folder") {
        val r = call.receive<MoveFolderRequest>()
        call.edit { LibraryEdits.moveFolder(it, FolderPath.parse(r.from), FolderPath.parse(r.toParent)) }
    }
    post("/api/library/create-folder") {
        val r = call.receive<CreateFolderRequest>()
        call.edit { LibraryEdits.createFolder(it, FolderPath.parse(r.parent), r.name) }
    }
    post("/api/library/rename-folder") {
        val r = call.receive<RenameFolderRequest>()
        call.edit { LibraryEdits.renameFolder(it, FolderPath.parse(r.path), r.name) }
    }
    post("/api/library/delete-folder") {
        val r = call.receive<FolderRequest>()
        call.edit { LibraryEdits.deleteFolder(it, FolderPath.parse(r.path)) }
    }
    post("/api/library/expanded") {
        val r = call.receive<ExpandedRequest>()
        call.edit { LibraryEdits.setExpanded(it, FolderPath.parse(r.path), r.expanded) }
    }
    post("/api/library/mark-translation") {
        val r = call.receive<MarkTranslationRequest>()
        call.edit { LibraryEdits.markTranslation(it, PackageId.of(r.translation), ids(r.originals)) }
    }
    post("/api/library/unmark-translation") {
        val r = call.receive<UnmarkTranslationRequest>()
        call.edit { LibraryEdits.unmarkTranslation(it, PackageId.of(r.translation)) }
    }

    post("/api/builds/create") {
        val r = call.receive<BuildRequest>()
        call.edit { BuildEdits.create(it, r.name) }
    }
    post("/api/builds/rename") {
        val r = call.receive<RenameBuildRequest>()
        call.edit { BuildEdits.rename(it, r.name, r.newName) }
    }
    post("/api/builds/duplicate") {
        val r = call.receive<BuildRequest>()
        call.edit { BuildEdits.duplicate(it, r.name) }
    }
    post("/api/builds/delete") {
        val r = call.receive<BuildRequest>()
        call.edit { BuildEdits.delete(it, r.name) }
    }
    post("/api/builds/current") {
        val r = call.receive<BuildRequest>()
        call.edit { BuildEdits.setCurrent(it, r.name) }
    }
    post("/api/builds/select") {
        val r = call.receive<SelectModsRequest>()
        call.edit { BuildEdits.select(it, r.build, ids(r.mods), r.selected) }
    }
}
