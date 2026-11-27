package rimmods.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import rimmods.Settings
import rimmods.export.ActivationPlan
import rimmods.export.ModsConfigWriter
import rimmods.library.LibraryEditException
import rimmods.library.LibraryStore

@Serializable
data class ActivationPreview(val build: String, val plan: ActivationPlan, val blocked: Boolean)

@Serializable
data class ActivationResult(val build: String, val written: Int, val file: String, val backup: String, val plan: ActivationPlan)

@Serializable
data class ActivateRequest(val build: String)

/** Activation (requirements, Export pipeline): preview the plan, then write it to the game's ModsConfig.xml. */
fun Route.activationRoutes(state: AppState) {
    /** Rescans first: the plan must match the disk at the moment of writing. */
    fun plan(build: String): ActivationPlan {
        val settings = state.settings()
        val snapshot = state.rescan()
        val library = LibraryStore.load(settings.libraryFile).library
        val b = library.builds.firstOrNull { it.name == build } ?: throw LibraryEditException("No build '$build'")
        return ActivationPlan.of(b.mods, snapshot.byId, library.translations)
    }

    get("/api/builds/activation") {
        val build = call.request.queryParameters["build"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val p = plan(build)
        call.respond(ActivationPreview(build, p, p.blocked))
    }

    post("/api/builds/activate") {
        val r = call.receive<ActivateRequest>()
        val p = plan(r.build)
        val settings = state.settings()
        if (p.blocked) throw LibraryEditException("Build '${r.build}' cannot be activated: missing dependencies or a load order cycle")
        val backup = ModsConfigWriter.write(settings.modsConfigFile, p.order, settings.backupDir)
        call.respond(ActivationResult(r.build, p.order.size, settings.modsConfigFile.toString(), backup.toString(), p))
    }
}
