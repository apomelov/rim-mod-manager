package rimmods.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.nio.file.Path
import kotlinx.serialization.Serializable
import rimmods.Settings
import rimmods.library.LibraryStore
import rimmods.library.LibrarySync
import rimmods.translations.ModKeySets
import rimmods.translations.ModKeys
import rimmods.translations.TranslationFinder
import rimmods.translations.TranslationSuggestion

@Serializable
data class SuggestionsResponse(val suggestions: List<TranslationSuggestion>)

/** Suggested translation marks for mods in Sortout (docs/translations.md). Confirming one is an ordinary mark. */
fun Route.translationRoutes(state: AppState) {
    get("/api/translations/suggestions") {
        val s = state.current()
        val library = LibraryStore.load(state.settings().libraryFile).library
        val sortout = LibrarySync.view(library, s.scan.mods).sortout.map { it.packageId }.toSet()
        val found = TranslationFinder.find(s.scan.mods, sortout) { m ->
            s.keys.getOrPut(m.packageId) { ModKeySets(ModKeys.provided(Path.of(m.folder)), ModKeys.translated(Path.of(m.folder))) }
        }
        call.respond(SuggestionsResponse(found))
    }
}
