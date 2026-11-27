package rimmods.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.RoutingCall
import io.ktor.server.request.receive
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import rimmods.Settings
import rimmods.library.LibraryEditException
import rimmods.library.LibraryFileException
import rimmods.library.LoadedLibrary
import rimmods.library.LibraryStore
import rimmods.library.LibrarySync
import rimmods.library.LibraryView
import rimmods.scan.GameVersion
import rimmods.scan.Mod
import rimmods.scan.PackageId
import rimmods.export.ModsConfigWriteException
import rimmods.library.BuildEdits
import rimmods.scan.Leftovers
import rimmods.scan.LeftoverException
import rimmods.scan.ScanProblem
import java.io.File

@Serializable
data class ModsResponse(val gameVersion: GameVersion, val mods: List<Mod>, val problems: List<ScanProblem>)

@Serializable
data class LibraryResponse(
    val gameVersion: GameVersion,
    val library: LibraryView,
    /** Translation marks: translation packageId → the packageIds it translates. */
    val translations: Map<String, List<String>>,
    /** Scan problems and library-file problems. */
    val problems: List<Problem>,
    val builds: List<BuildView> = emptyList(),
    /** The open tab (resolved: an unknown stored name falls back to the first build); null when there are no builds. */
    val currentBuild: String? = null,
)

@Serializable
data class BuildView(val name: String, val mods: List<String>)

@Serializable
data class Problem(
    val message: String,
    /** Set for a mods-folder entry the app may delete (see [Leftovers]): its folder name. */
    val removableFolder: String? = null,
)

@Serializable
data class DeleteLeftoverRequest(val folder: String)

@Serializable
data class ErrorResponse(val error: String)

fun Application.module(settings: Settings, state: AppState = AppState(settings)) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<LibraryFileException> { call, e ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("${settings.libraryFile}: ${e.message}"))
        }
        exception<ModsConfigWriteException> { call, e ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Cannot write ModsConfig.xml"))
        }
        exception<LeftoverException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Cannot delete"))
        }
        exception<LibraryEditException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Edit rejected"))
        }
    }
    routing {
        get("/api/mods") {
            val s = state.current()
            call.respond(ModsResponse(s.gameVersion, s.scan.mods, s.scan.problems))
        }
        get("/api/mods/{packageId}") {
            val mod = call.mod(state) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(mod)
        }
        // Only files resolved by the scan are served, never a client-supplied path.
        get("/api/mods/{packageId}/preview") { call.respondImage(call.mod(state)?.preview) }
        get("/api/mods/{packageId}/icon") { call.respondImage(call.mod(state)?.icon) }
        get("/api/library") {
            state.rescan()
            call.respond(libraryResponse(state, LibraryStore.load(settings.libraryFile)))
        }
        libraryEditRoutes(settings, state)
        activationRoutes(settings, state)
        translationRoutes(settings, state)
        post("/api/leftovers/delete") {
            val r = call.receive<DeleteLeftoverRequest>()
            Leftovers.delete(settings.modsDir, r.folder)
            state.rescan()
            call.respond(libraryResponse(state, LibraryStore.load(settings.libraryFile)))
        }
        // Client build, present only in the fat jar; in development Vite serves the client.
        staticResources("/", "static")
    }
}

fun libraryResponse(state: AppState, loaded: LoadedLibrary): LibraryResponse {
    val s = state.current()
    return LibraryResponse(
        gameVersion = s.gameVersion,
        library = LibrarySync.view(loaded.library, s.scan.mods, s.game),
        translations = loaded.library.translations.map { (k, v) -> k.value to v.map { it.value } }.toMap(),
        problems = s.scan.problems.map {
            Problem("${it.folder}: ${it.message}", removableFolder = if (it.texturesOnly) File(it.folder).name else null)
        } + loaded.problems.map { Problem(it) },
        builds = loaded.library.builds.map { b -> BuildView(b.name, b.mods.map { it.value }) },
        currentBuild = BuildEdits.current(loaded.library)?.name,
    )
}

private fun RoutingCall.mod(state: AppState): Mod? =
    parameters["packageId"]?.let { state.current().byId[PackageId.of(it)] }

private suspend fun RoutingCall.respondImage(path: String?) {
    val file = path?.let(::File)?.takeIf { it.isFile }
    if (file == null) respond(HttpStatusCode.NotFound) else respondFile(file)
}
