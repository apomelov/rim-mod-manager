# RimWorld Mod Manager

A personal mod manager for RimWorld. It turns putting together a mod list for a playthrough into ticking boxes, and handles translations and dependencies for you.

## Why

A library of a few hundred mods is hard to manage by hand. About a third of them are translations of other mods: noise that should never need a thought. Different playthroughs (a solo campaign, multiplayer co-op, …) need different mod sets, and switching between them by hand is slow and error-prone.

The app keeps two things apart:

- **the library tree** answers *what do I have*: your own folder hierarchy for all installed mods;
- **builds** answer *what am I playing now*: named mod sets you can switch between and activate in one click.

Wherever the game has a defined behaviour (how it finds a mod's icon, treats packageIds, picks versions, sorts the load order), the app copies it.

## Features

- **Library tree.** A file-manager-style tree of folders and subfolders with drag and drop. Each mod lives in exactly one folder. New mods land in a virtual **Sortout** inbox until you place them. Rows show the mod's icon and flags for the languages it ships.
- **Mod details.** Preview image, name, packageId, version and supported game versions, authors, Workshop link, dependencies and description.
- **Translations follow their mod.** Mark a mod as a translation of one or more mods. Marked translations are hidden from the tree (a "show hidden" toggle brings them back) and are added automatically whenever their original is activated. A helper looks at the mods' files and suggests translation marks for mods still in Sortout. You confirm or dismiss each suggestion; nothing is applied without you.
- **Builds as tabs.** Each build is a tab over the same tree. Its checkboxes show which mods are selected, and a folder's checkbox shows whether all, some or none of its mods are selected. Builds can be created, renamed, duplicated and deleted, and every edit is saved straight away.
- **Core and DLCs.** They are read from the game folder and shown at the top of the tree. Core is always on. DLCs are selected like ordinary mods, and a DLC that a selected mod requires is added automatically.
- **One-click activation.** Activating a build:
  1. takes the selected mods and adds their dependencies (including dependencies of dependencies) and their translations;
  2. stops if a required dependency is missing, and warns about selected mods that are missing from disk;
  3. backs up the current `ModsConfig.xml`;
  4. sorts the list the way the game's own "Auto-sort mods" does, so the game starts with it as is;
  5. writes it to `ModsConfig.xml`.
- **Your decisions are safe.** The disk is the source of truth about mods and is rescanned on every start. Only your decisions (tree, translation marks, builds) are stored, in a single readable YAML file that you can keep under git or sync between machines. Mods that disappear from disk keep their place and are shown greyed out, so unsubscribing and resubscribing does not lose anything.

Not planned: downloading or subscribing to mods, tags, and copying community sorting rules such as RimSort's.

## Running

Download the package for your OS from the releases. Java is included, so nothing else needs to be installed. Start the app, and it opens `http://127.0.0.1:8765/` in the browser. On the first start it finds the game and mods folders and asks you to confirm them.

Settings, the library file and `ModsConfig.xml` backups live in:

| OS | Folder |
|---|---|
| Linux | `~/.config/rimmodmanager` |
| Windows | `%APPDATA%\rimmodmanager` |
| macOS | `~/Library/Application Support/rimmodmanager` |

The packages are not signed: Windows SmartScreen and macOS Gatekeeper warn on the first start. The README inside the package explains how to get past the warning.

## Development

Stack: Kotlin + Ktor backend, React + TypeScript (Vite) frontend, one Gradle project. The server serves the built client.

```sh
./run.sh                        # build the jar if needed, start the server, open the browser (run.cmd on Windows)
./gradlew :server:test          # tests
./gradlew :server:dist          # zip with the jar and start scripts (needs Java 21)
./gradlew :server:jpackageZip   # native package with a bundled Java for the current OS
```

Native packages for Linux, Windows and macOS are built by GitHub Actions (`.github/workflows/package.yml`).

## Documentation

- [Requirements](docs/requirements.md): goals, concepts, behaviour, scope
- [User files](docs/user-files.md): `settings.yaml` and `library.yaml`
- [Paths](docs/paths.md): how the game and mods folders are found on each OS
- [About.xml](docs/about-xml.md): what is read from a mod
- [Activation sort](docs/sort.md): how the load order is computed
- [Finding translations](docs/translations.md): how translation suggestions work
