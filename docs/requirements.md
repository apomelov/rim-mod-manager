# RimWorld Mod Manager — Requirements

2026-11-26 · Aleksei Pomelov

> Source: Claude Docs "RimWorld Mod Manager — Requirements" (https://claude.ai/artifact/VZ991vciSe2xpPT6ikjoN2), exported 2026-11-26.

## Problem and goals

A personal mod manager that makes assembling a RimWorld mod list for a given playthrough a matter of ticking boxes, with translations and dependencies handled invisibly.

The library holds a few hundred mods. About a third are Russian translations of other mods: noise that should never need a thought. Different playthroughs (solo campaign, multiplayer co-op, etc.) need different mod sets, and switching between them is currently manual and error-prone. Previous attempts failed mostly because the requirements were never pinned down.

Goals:

- **Translations follow their mod.** Picking a mod silently brings its translation(s) from the collection.
- **A readable library.** One user-controlled hierarchy (folders) instead of tags or graphs; derived relations stay out of sight.
- **Builds as first-class objects.** Named mod sets that can be switched, duplicated and activated in one click.
- **Clean hand-off to the game.** Export writes the mod list; sorting is delegated to RimSort, not reimplemented.

## Core concepts

Two orthogonal axes: the **tree** answers "what do I have", **builds** answer "what am I playing now".

| Concept | What it is | Who maintains it |
|---|---|---|
| Mod | A mod found on disk (Workshop or local Mods folder) | Disk scan |
| Library tree | Folders and subfolders holding mods; the single user-defined ontology | User (drag and drop) |
| Hidden mod | Translations and libraries (Harmony, HugsLib, etc.); not shown unless "show hidden" is on | Derived, with user overrides |
| Translation link | Translation mod → the mod(s) it translates | Derived, with user overrides |
| Dependency | Mod → mods it requires, from About.xml | Disk scan only |
| Build | Named set of explicitly selected mods (a tab) | User |
| Unsorted | Inbox folder for mods not yet placed in the tree | Sync |

Rule of thumb: anything derivable from disk is recomputed and never stored; only user decisions are persisted.

## Data model

Disk is the source of truth about mods; a single user file holds only decisions. The two are joined by packageId.

**From disk (rescanned on every start, never persisted):**

- Workshop folder and local `Mods` folder.
- Per mod: `About/About.xml` (packageId, name, description, author, modDependencies, loadAfter/loadBefore, supported versions) and `About/PublishedFileId.txt` (Workshop id).
- Scanning a few hundred mods is fast enough that no cache is needed.

**User file (persisted):**

- Library tree: folders, order, which mod sits where.
- Translation link overrides (manual fixes on top of auto-matching).
- Hidden/visible overrides.
- Builds: name, explicitly selected mods.
- Optional per-mod notes.

**Storage:** one human-readable JSON (or YAML) file, loaded whole into memory and saved whole. Keep it under git for history and undo. A DBMS is overkill at this size (hundreds of records, tens of KB); SQLite is the fallback if complex queries ever appear.

**Identity:** packageId is the primary key; it is what the game uses in `ModsConfig.xml` and survives resubscribing. Workshop id is stored alongside. The same packageId may exist both in Workshop and in local Mods; a precedence rule is needed (see Open questions).

## Library tree UI

The library is a file-manager-style tree with drag and drop; checkboxes next to it show the active build.

- **Folders and subfolders**, arbitrary depth. Typical layout: `QoL/base` (always on), `QoL/other`, content packs such as vehicles, storage, fortifications.
- **One mod, one folder.** No shortcuts or multi-placement; a mod has exactly one location in the tree.
- **Drag and drop** moves mods and folders.
- **Checkboxes** reflect the build in the current tab. A mod's checkbox is the stored state.
- **Folder checkbox is purely derived**: all children checked, some, or none (tri-state). Clicking it checks or unchecks all descendants. It stores nothing itself.
- **Hidden mods** (translations, libraries) are not shown; a "show hidden" toggle reveals them.
- **Missing mods** (in the tree but not on disk) are shown greyed out.
- **Unsorted** folder collects new mods until they are placed.

## Translations

Translation links are derived once, corrected by hand where needed, and then applied silently at export.

**Initial classification and matching (one-off):**

1. Load all mods (name, description, Workshop id) and classify translation vs original by reading the descriptions, not by keyword heuristics like "RU" or "Rus".
2. For each translation, find its original(s). Workshop links in the description (`?id=…`) are the strongest evidence and serve as a cross-check; links to known libraries are ignored.
3. Output only a mapping table: translation packageId → original packageId(s). A small script merges it into the user file and builds the reverse links. Nothing is retyped by hand.

**Rules:**

- `translates` and `translatedBy` are both lists: one translation can cover a pack of mods, one mod can have several translations.
- `translatedBy` is always computed from `translates`, never stored separately.
- A translation of a mod not in the collection keeps the target id (e.g. `translatesMissing`) rather than losing it.
- Auto-derived links are recomputed; manual overrides in the user file take precedence and survive recomputation.
- New translations added later are matched the same way and flagged for review.

## Builds

Builds are tabs over the same tree; switching a tab changes the checkboxes, nothing else.

- **Content:** a build stores only explicitly selected mods. Dependencies, translations and libraries are added at activation, never stored.
- **Folder state is not stored.** A new mod placed into a folder does not auto-join any build.
- **New mod into a fully checked folder:** when a mod is dragged from Unsorted into a folder that is fully checked in some builds, ask once: "Add to builds X, Y?"
- **Tab actions:** create, duplicate, rename, delete, activate.
- **Edits save immediately**; duplicate and the activation backup make experiments safe.
- **Activate** writes the build to the game's mod list (see Export pipeline).
- **Reverse detection** (reading the current game mod list) never creates lists by default:
  - Strip dependencies, translations and libraries from `ModsConfig.xml`, then compare with existing builds.
  - Exact match → highlight that tab as active.
  - Close match → show the diff ("+2, −1") and offer to update that build or save as new.
  - No match → offer a new build.
- On startup, show which build is currently in the game and whether it was changed from inside RimWorld.
- **Sharing for multiplayer:** export/import a build as a file; on import, show which mods are missing locally.

## Sync rules on startup

On every start the disk scan is reconciled with the user file; nothing the user decided is ever deleted automatically.

| Situation | Action |
|---|---|
| Mod on disk, not in tree | Place in Unsorted (or hidden, if it is a translation or library) |
| Mod in tree, not on disk | Keep its place, show greyed out as missing |
| Missing mod selected in a build | Keep it; warn at activation |
| Mod updated on disk | Nothing; metadata is reread anyway |
| New translation appears | Auto-match, flag for review |

A separate "Remove missing" action cleans up missing mods deliberately; mods are often unsubscribed and resubscribed, so their tree position should survive.

## Export pipeline

Activate turns a build into the game's mod list in a straight run, then hands sorting to RimSort.

1. Collect the explicitly selected mods of the build.
2. Add transitive dependencies (from About.xml).
3. Add translations of every mod in the set.
4. Check for problems: missing mods, missing dependencies. Show them and let the user decide (see Open questions).
5. Back up the current `ModsConfig.xml`.
6. Write the new mod list to `ModsConfig.xml` (or a list RimSort can import).
7. Launch RimSort to sort by community rules.

Sorting logic is not reimplemented: community rules change and are maintained by others.

## Platform and stack

A local web application on Linux (the game runs on Kubuntu).

- **Backend:** Kotlin + Ktor. Spring is overkill for a single-user local tool.
- **Frontend:** React + TypeScript, built with Vite. A ready-made tree component (e.g. react-arborist) covers drag and drop and checkboxes.
- **Build:** one Gradle project. The full build bundles the client's artifacts into the server, which serves them as static files alongside the API: one jar plus a run script that starts the server on localhost and opens the browser.
- **Development:** Vite dev server with hot reload, proxying API calls to Ktor.
- **Logic placement:** the domain logic (scan, sync, translations, export) lives in Kotlin with JUnit tests; the frontend stays thin.

Why: the user reads Kotlin and TypeScript, not Python; the core UI (a drag-and-drop tree with tri-state checkboxes) is cheapest in React. Compose Desktop was considered (one language, no API layer) but would need the tree written from scratch; a Lanterna TUI loses drag and drop, which is the main interaction when sorting a few hundred mods.

## Scope

The MVP is tree + checkboxes + builds + export; everything else waits until it has been used for a couple of playthroughs.

**MVP:**

- Disk scan and sync rules.
- One-off translation matching and auto-inclusion.
- Library tree with drag and drop, derived tri-state folder checkboxes, show hidden.
- Build tabs: create, duplicate, rename, activate.
- Export pipeline with backup and RimSort hand-off.

**Later:**

- Reverse detection with diffs.
- Build export/import for multiplayer.
- Per-mod notes.

**Non-goals:**

- Reimplementing load-order sorting.
- Tags, graphs or multi-placement of mods.
- Downloading or subscribing to mods.

## Open questions

- [ ] Missing dependency at export: warn and continue, or block activation?
  *(Doc comment: default proposal is warn and continue, so activation never gets stuck.)*
- [ ] Same packageId in Workshop and local Mods: which one wins?
- [ ] Write `ModsConfig.xml` directly, or produce a list for RimSort to import?
- [ ] Which mods count as libraries to hide: a fixed list, or anything that is only ever a dependency?
