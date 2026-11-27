package rimmods.export

import kotlinx.serialization.Serializable
import rimmods.scan.Dependency
import rimmods.scan.Mod
import rimmods.scan.PackageId

@Serializable
data class MissingDependency(val dependency: Dependency, val requiredBy: List<PackageId>)

/**
 * What activating a build writes to the game's mod list (requirements, Export pipeline, steps 1–4 and 6).
 * Pure: the caller supplies the scan and the library's translation marks.
 */
@Serializable
data class ActivationPlan(
    /** The mod list to write, sorted (docs/sort.md); empty when [blocked]. */
    val order: List<PackageId>,
    /** Selected mods that are on disk (DLCs are listed in [dlcs]). */
    val selected: List<PackageId>,
    /** Mods added because something in the set requires them (not Core/DLCs). */
    val dependencies: List<PackageId>,
    /** Marked translations of mods in the set. */
    val translations: List<PackageId>,
    /** DLCs in the list, selected or required by a mod (Core is always written and not listed). */
    val dlcs: List<PackageId>,
    /** Selected but not on disk: a warning, the rest still runs. */
    val missingSelected: List<PackageId>,
    /** Required but not on disk: blocks activation. */
    val missingDependencies: List<MissingDependency>,
    /** Mods whose load-order rules form a cycle: blocks activation. */
    val cycle: List<PackageId>,
) {
    val blocked: Boolean get() = missingDependencies.isNotEmpty() || cycle.isNotEmpty()

    companion object {
        private val CORE = LoadOrder.CORE_AND_DLCS.first()

        /**
         * [onDisk]: every mod found, Workshop and game content (Core/DLCs from the game folder). If the game folder was not
         * scanned (no Core among them), every Core/DLC id is taken as installed.
         */
        fun of(selected: List<PackageId>, onDisk: Map<PackageId, Mod>, translations: Map<PackageId, List<PackageId>>): ActivationPlan {
            val gameScanned = CORE in onDisk
            fun installed(id: PackageId) = id in onDisk || (id.isLudeon && !gameScanned)

            val translationsOf: Map<PackageId, List<PackageId>> = translations
                .filterKeys { it in onDisk }
                .flatMap { (t, originals) -> originals.map { it to t } }
                .groupBy({ it.first }, { it.second })

            val set = LinkedHashSet<PackageId>()
            val queue = ArrayDeque<PackageId>()
            val chosen = mutableListOf<PackageId>()
            val deps = mutableListOf<PackageId>()
            val trans = mutableListOf<PackageId>()
            val dlcs = LinkedHashSet<PackageId>()
            val missingSelected = mutableListOf<PackageId>()
            val missing = LinkedHashMap<PackageId, Pair<Dependency, MutableList<PackageId>>>()

            fun add(id: PackageId, into: MutableList<PackageId>) {
                if (set.add(id)) {
                    into += id
                    queue += id
                }
            }

            for (id in selected.distinct()) {
                when {
                    id == CORE -> {}
                    id.isLudeon && installed(id) -> dlcs += id
                    id in onDisk -> add(id, chosen)
                    else -> missingSelected += id
                }
            }
            while (queue.isNotEmpty()) {
                val mod = onDisk.getValue(queue.removeFirst())
                for (d in mod.about.dependencies) {
                    val candidates = listOf(d.packageId) + d.alternatives
                    if (candidates.any { it in set || it == CORE || it in dlcs }) continue
                    val available = candidates.firstOrNull { installed(it) }
                    when {
                        available != null && available.isLudeon -> dlcs += available
                        available != null -> add(available, deps)
                        else -> missing.getOrPut(d.packageId) { d to mutableListOf() }.second += mod.packageId
                    }
                }
                translationsOf[mod.packageId].orEmpty().forEach { add(it, trans) }
            }

            val byName = compareBy<PackageId>({ onDisk[it]?.about?.name?.lowercase() ?: it.value }, { it.value })
            val missingDeps = missing.values.map { (d, by) -> MissingDependency(d, by) }
            val dlcList = LoadOrder.CORE_AND_DLCS.filter { it in dlcs } + dlcs.filter { it !in LoadOrder.CORE_AND_DLCS }

            var order = emptyList<PackageId>()
            var cycle = emptyList<PackageId>()
            if (missingDeps.isEmpty()) {
                val all = listOf(CORE) + dlcList + set
                val initial = LoadOrder.initialOrder(all) { onDisk[it]?.about?.name ?: it.value }
                val rules = { id: PackageId -> onDisk[id]?.let { LoadRules.of(it.about) } ?: LoadOrder.ludeonRules(id) ?: LoadRules.NONE }
                when (val r = LoadOrder.sort(initial, rules)) {
                    is LoadOrder.Result.Sorted -> order = r.order
                    is LoadOrder.Result.Cycle -> cycle = r.mods
                }
            }

            return ActivationPlan(
                order = order,
                selected = chosen.sortedWith(byName),
                dependencies = deps.sortedWith(byName),
                translations = trans.sortedWith(byName),
                dlcs = dlcList,
                missingSelected = missingSelected,
                missingDependencies = missingDeps,
                cycle = cycle,
            )
        }
    }
}
