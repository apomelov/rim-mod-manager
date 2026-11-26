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

Principle: where the game has a defined behaviour (how it finds a mod's icon, treats packageIds, resolves versions), the app mirrors it.

## Core concepts

Two orthogonal axes: the **tree** answers "what do I have", **builds** answer "what am I playing now".

| Concept | What it is | Who maintains it |
|---|---|---|
| Mod | A mod found in the mods folder on disk | Disk scan |
| Library tree | Folders and subfolders holding mods; the single user-defined ontology | User (drag and drop) |
| Hidden mod | A mod marked as a translation; not shown unless "show hidden" is on. Libraries are not hidden: they live in an ordinary `Library` folder | Follows the translation mark |
| Translation link | Translation mod → the mod(s) it translates | User (marking); auto-suggestions later |
| Dependency | Mod → mods it requires, from About.xml | Disk scan only |
| Build | Named set of explicitly selected mods (a tab) | User |
| Sortout | Virtual inbox folder: mods on disk that are not placed in the tree and are not shown under an original | Computed, never stored |

Rule of thumb: anything derivable from disk is recomputed and never stored; only user decisions are persisted.

## Data model

Disk is the source of truth about mods; a single user file holds only decisions. The two are joined by packageId.

**From disk (rescanned on every start, never persisted):**

- One mods folder: the Steam Workshop content folder (path set in the app's configuration). The local `Mods` folder is not used.
- Per mod: `About/About.xml` (packageId, name, description, author, modDependencies, loadAfter/loadBefore, supported versions) and `About/PublishedFileId.txt` (Workshop id).
- Scanning a few hundred mods is fast enough that no cache is needed.

**User file (persisted):**

- Library tree: folders, order, which mod sits where.
- Translation links: which mods are translations and what they translate.
- Builds: name, explicitly selected mods.
- Optional per-mod notes.

**Storage:** see `docs/user-files.md` for the exact format. One human-readable YAML file (YAML so it is easy to edit by hand), loaded whole into memory and saved whole. Location: `~/.config/rimmodmanager/` (respecting `$XDG_CONFIG_HOME`), next to the app settings. Strictly it is data rather than configuration, but keeping everything in one folder makes it easy to find, edit and put under git. Keep it under git for history and undo. A DBMS is overkill at this size (hundreds of records, tens of KB); SQLite is the fallback if complex queries ever appear.

**Identity:** packageId is the only key; it is what the game uses in `ModsConfig.xml` and survives resubscribing. The Workshop id from `PublishedFileId.txt` is read as metadata only (e.g. to resolve `?id=…` links when matching translations).

**Configuration:** paths are app settings, not hard-coded: the mods folder (Workshop content) and the game's mod list file `ModsConfig.xml` (on Linux usually under `~/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config/`).

## Library tree UI

The library is a file-manager-style tree with drag and drop; checkboxes next to it show the active build.

- **Folders and subfolders**, arbitrary depth. Typical layout: `QoL/base` (always on), `QoL/other`, content packs such as vehicles, storage, fortifications.
- **One mod, one folder.** No shortcuts or multi-placement; a mod has exactly one location in the tree.
- **Drag and drop** moves mods and folders.
- **Checkboxes** reflect the build in the current tab. A mod's checkbox is the stored state.
- **Folder checkbox is purely derived**: all children checked, some, or none (tri-state). Clicking it checks or unchecks all descendants. It stores nothing itself.
- **Hidden mods** (marked translations) are not shown; a "show hidden" toggle reveals them.
- **Translations sit under their original**: a marked translation is shown as a child row of each original mod it translates that is on disk, wherever that original is (a folder or Sortout). If none of its originals is on disk, it stays in Sortout. So a translation is always findable (with "show hidden") and can be re-marked.
- **Missing mods** (in the tree but not on disk) are shown greyed out.
- **Sortout** is a virtual folder, always first: it is computed as every mod on disk that is neither placed in the tree nor shown under an original, and is never stored. On the very first run every mod lands there.
- **Folder contents** are ordered like a file manager: subfolders first, then mods. Folders remember their expanded/collapsed state.
- **Predefined folders** are created when the library file does not exist yet: `Library`, `UI`, `UX`, `QoL`, `Tools` (taken from the user's earlier tagging drafts; the rest are created by hand). Libraries are identified by the user placing them there on the first run, not by a heuristic: libraries appear rarely, and there are no builds yet to derive anything from.

## Mod details panel

A panel to the left of the tree shows the selected mod: preview image, name, packageId, mod version and supported game versions, authors, Workshop link, dependencies and the description. The description is shown as preformatted text for now (it may contain HTML-like tags, which are not interpreted yet). Tree rows show the mod's icon. Where both images come from: `docs/about-xml.md`, "Images".

## Translations

A mod is a translation only once the user marks it as one; until then it sits in Sortout like any other mod. Marked translations are hidden and applied silently at export.

**Marking (MVP):** mark a mod as a translation and pick the mod(s) it translates; unmark or re-link at any time. The mark lives in the user file and is the source of truth.

**Rules:**

- `translates` and `translatedBy` are both lists: one translation can cover a pack of mods, one mod can have several translations.
- `translatedBy` is always computed from `translates`, never stored separately.
- A translation of a mod not in the collection keeps the target id rather than losing it.
- A marked translation is hidden: it is shown only with "show hidden", under its original(s) (see Library tree UI).

**Finding translations in Sortout (later):** a helper that reads names, descriptions and Workshop links (`?id=…`, the strongest evidence; links to known libraries ignored) and *suggests* marks for mods still in Sortout. It classifies by reading descriptions, not by keyword heuristics like "RU" or "Rus". Suggestions are reviewed by the user, never applied silently.

Why manual first: the mark must be correct and always reconfigurable; automation only saves typing and can come once the basics are in use.

## Builds

Builds are tabs over the same tree; switching a tab changes the checkboxes, nothing else.

- **Content:** a build stores only explicitly selected mods. Dependencies, translations and libraries are added at activation, never stored.
- **Folder state is not stored.** A new mod placed into a folder does not auto-join any build.
- **New mod into a fully checked folder:** when a mod is dragged from Sortout into a folder that is fully checked in some builds, ask once: "Add to builds X, Y?"
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
| Mod on disk, not in tree | Shows up in Sortout (or under its original, if marked as a translation); nothing is written |
| Mod in tree, not on disk | Keep its place, show greyed out as missing |
| Missing mod selected in a build | Keep it; warn at activation |
| Mod updated on disk | Nothing; metadata is reread anyway |
| New translation appears | Lands in Sortout like any new mod until marked |

A separate "Remove missing" action cleans up missing mods deliberately; mods are often unsubscribed and resubscribed, so their tree position should survive.

## Export pipeline

Activate turns a build into the game's mod list in a straight run, then hands sorting to RimSort.

1. Collect the explicitly selected mods of the build.
2. Add transitive dependencies (from About.xml).
3. Add translations of every mod in the set.
4. Check for problems: missing mods, missing dependencies. A missing dependency **blocks activation** and is listed: the game would not run with that list anyway, and the fix (subscribing and adding the mod) happens through this app. A missing *selected* mod only produces a warning: the rest of the list still runs.
5. Back up the current `ModsConfig.xml`.
6. Write the new mod list directly to `ModsConfig.xml` (the file the game reads; RimSort works on the same file).
7. Launch RimSort to sort by community rules.

Sorting logic is not reimplemented: community rules change and are maintained by others.

## Core and DLCs

Core and the DLCs (`ludeon.rimworld`, `ludeon.rimworld.royalty`, `ludeon.rimworld.biotech`, …) live in the game's install folder, not in Workshop. For the first version they are not scanned: the known Ludeon packageIds are treated as always installed (all DLCs are owned).

- Core is always written to the mod list.
- A DLC is added to the mod list only when some mod in the set requires it.
- The UI shows which DLCs a build requires.

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
- Marking translations by hand; auto-inclusion at export.
- Library tree with drag and drop, derived tri-state folder checkboxes, show hidden.
- Build tabs: create, duplicate, rename, activate.
- Export pipeline with backup and RimSort hand-off.

**Later:**

- Finding translations in Sortout (suggested marks).
- Reverse detection with diffs.
- Build export/import for multiplayer.
- Per-mod notes.

**Non-goals:**

- Reimplementing load-order sorting.
- Tags, graphs or multi-placement of mods.
- Downloading or subscribing to mods.

## Open questions


None at the moment.
