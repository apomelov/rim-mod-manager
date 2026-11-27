# Finding translations in Sortout

A helper that **suggests** translation marks for mods still in Sortout (requirements, Translations). Suggestions are reviewed by the user, never applied silently.

Survey: the user's library on 2026-11-27, with 49 hand-made marks as ground truth (some translations cover a pack of up to four mods; one mod has two translations).

## What gives a translation away

| Evidence | How it is computed | Survey result |
|---|---|---|
| **Content match** (main) | The translation's keys: `DefInjected` defNames and `Keyed` keys in its non-English `Languages`. A mod's keys: `defName`s in its `Defs` and keys in its `Languages/English/Keyed`. Overlap = shared keys. | The right original has the most shared keys for 46 of 49 marks; every pack member has some. |
| No own content | No `Assemblies`, `Defs`, `Patches`, `Sounds`, `Textures` (see `about-xml.md`, Languages) | 47 of 49 translations; only 1 unmarked mod is like that, and it is a real (second) translation. |
| Dependency | `modDependencies` / `loadAfter` name the mod | Names the original for 42 of 49, but also libraries and neighbours (wrong for packs). Supporting evidence only. |
| Workshop link | `?id=…` in the description or `url` resolves to a mod on disk | Only 6 of 49. Supporting evidence only. |

Words like "RU" or "Rus" in names are not used.

## Algorithm

1. **Candidates**: mods in Sortout that ship a non-English language, or translate keys in any folder (Languages for other game versions count here), and either
   - have no own content, or
   - have own content but ≥ 80 % of their translation keys are shared with one other mod they depend on or link to (forks like a Russian Prepare Carefully).
2. **Originals** for a candidate: any mod on disk except the candidate. Greedy cover: take the mod sharing the most not-yet-covered keys, repeat while it adds **at least 3 keys** and either **≥ 10 % of the candidate's keys** or **≥ 30 % of that mod's own keys**. This keeps every member of a pack and drops libraries that share a key or two.
3. **Ties** (two mods share the same keys, e.g. a mod and another that bundles it): a mod named by a dependency or a Workshop link wins.
4. A candidate with no original found (no shared keys) is still listed, as "looks like a translation", so it can be linked by hand.

Each suggestion carries its evidence (shared keys, dependency, link) for display.

**UI**: "Match translations" in the header opens the suggestions over the tree (not as a modal, so the details panel stays usable for checking a mod): one row per pair, original left, translation right, evidence in between (how many of the original's own keys the translation translates, e.g. `6/10 keys (60%)`, plus dependency/link); ✓ adds the original to the translation's mark and removes the row, × dismisses it for this session. The same original can come up for several translations, and one translation for several originals.

## Validation

A comparison against the user's marks (precision and recall of pairs, misses and extras) is a manual dev check on real data, not part of the test suite. Result on 2026-11-27: 55 of 58 expected pairs found, 2 extra (`ow.ru.core` → two small mods it also depends on, plausibly right). Expected misses: two translations whose `Languages` share no keys with any mod (`kamikadza13.roolo.almostthere`, `am.rus.smartstockpilecreation`), and one pack member sharing no keys. Run it with `RMM_TRUTH=<library.yaml with marks> ./gradlew :server:test --tests '*RealDataCheck*'`.
