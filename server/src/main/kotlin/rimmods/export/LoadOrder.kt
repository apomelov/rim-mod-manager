package rimmods.export

import rimmods.scan.AboutInfo
import rimmods.scan.PackageId

/** A mod's load-order rules from About.xml (version-specific variants already resolved). */
data class LoadRules(
    val loadBefore: List<PackageId> = emptyList(),
    val loadAfter: List<PackageId> = emptyList(),
    val forceLoadBefore: List<PackageId> = emptyList(),
    val forceLoadAfter: List<PackageId> = emptyList(),
) {
    companion object {
        val NONE = LoadRules()
        fun of(about: AboutInfo) = LoadRules(about.loadBefore, about.loadAfter, about.forceLoadBefore, about.forceLoadAfter)
    }
}

/** The game's "Auto-sort mods" (`Verse.ModsConfig.TrySortMods`), ported as specified in docs/sort.md. */
object LoadOrder {
    sealed interface Result {
        data class Sorted(val order: List<PackageId>) : Result

        /** Nothing was sorted: these mods' rules form a cycle (in cycle order). */
        data class Cycle(val mods: List<PackageId>) : Result
    }

    /** Core and the DLCs in release order (see [ludeonRules] for when their About.xml is not available). */
    val CORE_AND_DLCS: List<PackageId> get() = PackageId.CORE_AND_DLCS

    /** Fallback for Core/DLCs not scanned from the game folder: what their About.xml declare (each DLC force-loads after Core and every earlier DLC). */
    fun ludeonRules(id: PackageId): LoadRules? {
        val i = CORE_AND_DLCS.indexOf(id)
        return when {
            i < 0 -> null
            i == 0 -> LoadRules(forceLoadBefore = CORE_AND_DLCS.subList(1, 3))
            else -> LoadRules(forceLoadAfter = CORE_AND_DLCS.subList(0, i))
        }
    }

    /** The tie-break order: Core, the DLCs in release order, then every other mod by name (case-insensitive), then id. */
    fun initialOrder(ids: Collection<PackageId>, nameOf: (PackageId) -> String): List<PackageId> {
        val (ludeon, mods) = ids.distinct().partition { it in CORE_AND_DLCS }
        return ludeon.sortedBy { CORE_AND_DLCS.indexOf(it) } +
            mods.sortedWith(compareBy({ nameOf(it).lowercase() }, { it.value }))
    }

    fun sort(initial: List<PackageId>, rulesOf: (PackageId) -> LoadRules): Result {
        val index = initial.withIndex().associate { (i, id) -> id to i }
        // before[v]: the mods that must load before v, in the order the edges were added.
        val before = List(initial.size) { mutableListOf<Int>() }
        for ((i, id) in initial.withIndex()) {
            val r = rulesOf(id)
            for (t in r.loadBefore + r.forceLoadBefore) index[t]?.takeIf { it != i }?.let { before[it] += i }
            for (t in r.loadAfter + r.forceLoadAfter) index[t]?.takeIf { it != i }?.let { before[i] += it }
        }

        findCycle(before)?.let { cycle -> return Result.Cycle(cycle.map { initial[it] }) }

        val emitted = BooleanArray(initial.size)
        val order = mutableListOf<PackageId>()
        fun visit(v: Int) {
            emitted[v] = true
            for (p in before[v]) if (!emitted[p]) visit(p)
            order += initial[v]
        }
        for (v in initial.indices) if (!emitted[v]) visit(v)
        return Result.Sorted(order)
    }

    /** A cycle in the "must load before" graph, as node indices, or null. */
    private fun findCycle(before: List<List<Int>>): List<Int>? {
        val state = IntArray(before.size) // 0 = new, 1 = on the current path, 2 = done
        val path = mutableListOf<Int>()
        fun visit(v: Int): List<Int>? {
            state[v] = 1
            path += v
            for (p in before[v]) {
                if (state[p] == 1) return path.subList(path.indexOf(p), path.size).toList()
                if (state[p] == 0) visit(p)?.let { return it }
            }
            path.removeAt(path.size - 1)
            state[v] = 2
            return null
        }
        for (v in before.indices) if (state[v] == 0) visit(v)?.let { return it }
        return null
    }
}
