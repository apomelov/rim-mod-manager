# Activation sort

How Activate orders the mod list before writing `ModsConfig.xml`. Goal: the same order the game's own "Auto-sort mods" would produce, so the game starts with the list as is.

Source: the game's code, `Verse.ModsConfig.TrySortMods` and `Verse.DirectedAcyclicGraph` in RimWorld 1.6.4871 (`Assembly-CSharp.dll`, read as IL on 2026-11-27), plus the Core/DLC `About.xml` files. Only `About.xml` data is used; community rule databases (RimSort's) are out of scope.

## Input

1. The set to activate (see requirements, Export pipeline): selected mods, their transitive dependencies, their translations, Core, the DLCs some mod in the set requires.
2. **Initial order** (the only tie-break, see below):
   1. Core, then the DLCs in release order: Royalty, Ideology, Biotech, Anomaly, Odyssey.
   2. All other mods by name, case-insensitively, then by packageId.

## Constraints

- Only the four load-order fields create edges: `loadBefore`, `loadAfter`, `forceLoadBefore`, `forceLoadAfter`. `loadBefore`/`loadAfter` use their `…ByVersion` entry for the game version when there is one (it replaces the plain list). There is no `forceLoad…ByVersion`.
- `modDependencies` do **not** order anything (the game only checks that they are active); `incompatibleWith` neither.
- A rule naming a mod outside the set is ignored.
- packageIds are compared case-insensitively.
- Core and DLCs get no special treatment: their order comes from their own `forceLoad…` rules, and ordinary mods get no implicit "after Core". A mod that declares `loadBefore` Core (e.g. Harmony) ends up above it.

## Algorithm (as the game does it)

- Number the mods 0..n-1 in the initial order. For each mod i in that order, add edges in this order: for each id in `loadBefore`, then `forceLoadBefore`, that names mod t → "i before t"; for each id in `loadAfter`, then `forceLoadAfter`, that names mod t → "t before i". Each mod keeps its predecessors in insertion order.
- If there is a cycle, stop (see Cycles).
- Depth-first, post-order: walk the mods in the initial order; to emit a mod, first emit (recursively, in insertion order) its predecessors not emitted yet, then the mod itself.
- So a list that already satisfies every edge comes out unchanged.

## Cycles

The game aborts the sort and leaves the order as is. Activation instead **blocks**, like a missing dependency, and lists the mods forming the cycle (the game names only where its search started, which may not be in the cycle).

## Writing

- `activeMods`: the sorted packageIds, lowercased (as the game writes them).
- `version` and `knownExpansions` are kept from the existing file: on start the game force-activates any owned DLC missing from `knownExpansions`.
