package rimmods.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.io.path.Path
import kotlinx.serialization.Serializable
import rimmods.Settings
import rimmods.setup.Host
import rimmods.setup.PathChecks
import rimmods.setup.PathDetector
import rimmods.setup.RealHost
import rimmods.setup.SetupChecks
import rimmods.setup.SetupPaths

@Serializable
data class SetupResponse(
    val configured: Boolean,
    /** The app's own config folder (settings.yaml, backups). */
    val configDir: String,
    val os: String,
    /** The saved settings, or what was detected when there are none. */
    val paths: SetupPaths,
    /** What detection finds right now (to offer "use detected"). */
    val detected: SetupPaths,
    val checks: SetupChecks,
)

/** The welcome page (docs/paths.md): detected paths, checks, saving settings.yaml. */
fun Route.setupRoutes(state: AppState, host: Host = RealHost) {
    fun detected(): SetupPaths {
        val d = PathDetector.detect(host)
        return SetupPaths(
            gameDir = d.gameDir?.toString().orEmpty(),
            gameConfigDir = d.gameConfigDir?.toString().orEmpty(),
            workshopDir = d.workshopDir?.toString().orEmpty(),
            localModsDir = d.localModsDir?.toString().orEmpty(),
            libraryFile = state.configDir.resolve("library.yaml").toString(),
        )
    }

    get("/api/setup") {
        val s = state.settingsOrNull()
        val found = detected()
        val paths = s?.let {
            SetupPaths(
                it.gameDir?.toString().orEmpty(), it.gameConfigDir.toString(), it.workshopDir?.toString().orEmpty(),
                it.localModsDir?.toString().orEmpty(), it.libraryFile.toString(),
            )
        } ?: found
        call.respond(SetupResponse(s != null, state.configDir.toString(), host.os.name, paths, found, PathChecks.check(paths)))
    }

    post("/api/setup/check") {
        call.respond(PathChecks.check(call.receive<SetupPaths>()))
    }

    post("/api/setup") {
        val p = call.receive<SetupPaths>()
        val checks = PathChecks.check(p)
        if (!checks.ok) return@post call.respond(HttpStatusCode.BadRequest, checks)
        fun path(s: String) = s.trim().takeIf { it.isNotEmpty() }?.let(::Path)
        val settings = Settings(
            configDir = state.configDir,
            gameDir = path(p.gameDir),
            gameConfigDir = path(p.gameConfigDir)!!,
            workshopDir = path(p.workshopDir),
            localModsDir = path(p.localModsDir),
            libraryFile = path(p.libraryFile)!!,
            port = state.settingsOrNull()?.port ?: Settings.DEFAULT_PORT,
        )
        Settings.save(settings)
        state.configure(settings)
        call.respond(checks)
    }
}
