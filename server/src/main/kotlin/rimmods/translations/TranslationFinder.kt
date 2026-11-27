package rimmods.translations

import kotlinx.serialization.Serializable
import rimmods.scan.Mod
import rimmods.scan.PackageId

/** A mod's keys as [ModKeys] reads them. */
data class ModKeySets(val provided: Set<String>, val translated: Set<String>)

@Serializable
data class SuggestedOriginal(
    val packageId: PackageId,
    /** Keys of the translation this mod defines that no earlier original already covered (the ranking). */
    val sharedKeys: Int,
    /** How many of this mod's own keys the translation translates, out of [originalKeys]. */
    val translatedKeys: Int,
    val originalKeys: Int,
    /** Named by the translation's modDependencies or loadAfter. */
    val dependency: Boolean,
    /** Its Workshop page is linked from the translation's description or url. */
    val link: Boolean,
)

@Serializable
data class TranslationSuggestion(
    val translation: PackageId,
    /** How many keys the translation translates. */
    val translationKeys: Int,
    /** Best first; empty when it looks like a translation but no original was found. */
    val originals: List<SuggestedOriginal>,
)

/** Suggests translation marks for mods in Sortout, as specified in docs/translations.md. Pure. */
object TranslationFinder {
    private const val MIN_SHARED = 3
    private const val SHARE_OF_TRANSLATION = 0.10
    private const val SHARE_OF_ORIGINAL = 0.30
    private const val FORK_COVERAGE = 0.80
    private val WORKSHOP_LINK = Regex("""(?:\?id=|CommunityFilePage/)(\d{6,})""")

    fun find(mods: List<Mod>, sortout: Set<PackageId>, keysOf: (Mod) -> ModKeySets): List<TranslationSuggestion> {
        val byWorkshopId = mods.filter { it.workshopId != null }.associateBy { it.workshopId!! }
        val keys = HashMap<PackageId, ModKeySets>()
        fun keys(m: Mod) = keys.getOrPut(m.packageId) { keysOf(m) }

        val suggestions = mods
            .filter { it.packageId in sortout }
            .mapNotNull { t ->
                val translated = keys(t).translated
                // Languages lists only the game version's folders; keys come from all of them.
                if (t.languages.none { it != "English" } && translated.isEmpty()) return@mapNotNull null
                val deps = (t.about.dependencies.flatMap { listOf(it.packageId) + it.alternatives } + t.about.loadAfter).toSet()
                val links = WORKSHOP_LINK.findAll(t.about.description + " " + t.about.url.orEmpty())
                    .mapNotNull { byWorkshopId[it.groupValues[1]]?.packageId }.toSet()
                val others = mods.filter { it.packageId != t.packageId }

                if (t.content.isNotEmpty()) {
                    val named = others.filter { it.packageId in deps || it.packageId in links }
                    val isFork = translated.isNotEmpty() &&
                        named.any { (keys(it).provided intersect translated).size >= FORK_COVERAGE * translated.size }
                    if (!isFork) return@mapNotNull null
                }

                val originals = mutableListOf<SuggestedOriginal>()
                val rejected = mutableSetOf<PackageId>()
                val left = translated.toMutableSet()
                val minShared = minOf(MIN_SHARED, translated.size)
                while (left.isNotEmpty()) {
                    val best = others
                        .filter { o -> o.packageId !in rejected && originals.none { it.packageId == o.packageId } }
                        .map { o -> o to (keys(o).provided intersect left) }
                        .filter { it.second.isNotEmpty() }
                        .maxWithOrNull(
                            compareBy<Pair<Mod, Set<String>>>({ it.second.size })
                                .thenBy { it.first.packageId in deps || it.first.packageId in links }
                                .thenByDescending { it.first.packageId.value },
                        ) ?: break
                    val (o, shared) = best
                    val enough = shared.size >= minShared && (
                        shared.size >= SHARE_OF_TRANSLATION * translated.size ||
                            shared.size >= SHARE_OF_ORIGINAL * keys(o).provided.size
                        )
                    if (!enough) {
                        rejected += o.packageId // a library sharing a few keys; a smaller module may still qualify
                        continue
                    }
                    val own = keys(o).provided
                    originals += SuggestedOriginal(
                        o.packageId, shared.size, (own intersect translated).size, own.size, o.packageId in deps, o.packageId in links,
                    )
                    left -= shared
                }
                TranslationSuggestion(t.packageId, translated.size, originals)
            }

        val (matched, unmatched) = suggestions.partition { it.originals.isNotEmpty() }
        return matched.sortedByDescending { it.originals.first().sharedKeys } + unmatched.sortedBy { it.translation.value }
    }
}
