# Paths: detection and settings

Where the game, its config and the mods live on Linux, Windows and macOS, and how the app finds them. Modelled on RimSort's autodetection (`app/services/path_autodetect_service.py`, `game_layout_service.py`, commit aac16e2, read on 2026-11-27); deviations are marked.

## Settings (`settings.yaml`)

| Key | What | Required |
|---|---|---|
| `gameDir` | The game's content folder: the one holding `Data/` (Core, DLCs) and `Mods/`. On macOS this is the `.app` bundle root or its parent, see below. | yes |
| `gameConfigDir` | The game's config folder, holding `ModsConfig.xml`. | yes |
| `workshopDir` | Steam Workshop mods (`…/steamapps/workshop/content/294100`). | no (GOG) |
| `localModsDir` | The game's local mods folder. Default `<gameDir>/Mods`. | no |
| `libraryFile` | `library.yaml`. Default: next to `settings.yaml`. | yes |
| `port` | Default 8765. | — |

Old keys are still read: `modsDir` → `workshopDir`, `modsConfigFile` → its folder as `gameConfigDir`.

**App config folder** (`settings.yaml`, default `library.yaml`, `backups/`): Linux `$XDG_CONFIG_HOME/rimmodmanager` (default `~/.config/rimmodmanager`), Windows `%APPDATA%\rimmodmanager`, macOS `~/Library/Application Support/rimmodmanager`. `-Drmm.configDir` overrides it.

## Checks (shown on the welcome page)

- `gameDir`: contains `Data/Core/About/About.xml`.
- `gameConfigDir`: contains `ModsConfig.xml` (otherwise: "start the game once").
- `workshopDir`, `localModsDir`: an existing folder, or empty.
- `libraryFile`: its folder exists or can be created; an existing file must parse.

## Detection

Every candidate is resolved through symlinks and deduplicated (`~/.steam/steam`, `~/.steam/root` and `~/.steam/debian-installation` are often one folder). Only existing paths that pass the checks are offered; the user confirms them.

**1. Steam root**, first that is a folder with `steamapps/` or `config/libraryfolders.vdf`:
- Linux: `~/.steam/debian-installation`, `~/.steam/steam`, `~/.local/share/Steam`, `~/.var/app/com.valvesoftware.Steam/.local/share/Steam` (Flatpak), `~/snap/steam/common/.local/share/Steam` (Snap).
- Windows: registry `HKCU\Software\Valve\Steam` `SteamPath` *(not in RimSort)*, `HKLM\SOFTWARE\Wow6432Node\Valve\Steam` and `HKLM\SOFTWARE\Valve\Steam` `InstallPath` (read with `reg query`), then `C:\Program Files (x86)\Steam`.
- macOS: `~/Library/Application Support/Steam`.

**2. Library with RimWorld**: `<steam>/config/libraryfolders.vdf`, else `<steam>/steamapps/libraryfolders.vdf` (Valve KeyValues text): the first library whose `apps` lists `294100`; else the Steam root itself. Then:
- game: `<library>/steamapps/common/RimWorld`;
- Workshop: `<library>/steamapps/workshop/content/294100`.

**3. macOS bundle**: inside `…/common/RimWorld` (or an Applications folder) the first `*.app` (`RimWorld.app` from 1.7, `RimWorldMac.app` before). The content folder (`Data/`, `Mods/`) is the bundle root or, failing that, the bundle's parent.

**4. Non-Steam (GOG, standalone)**, only when step 2 finds no game:
- Windows: GOG Galaxy registry `HKLM\SOFTWARE\WOW6432Node\GOG.com\Games\*` (`path`), Heroic `%APPDATA%\heroic\gog_store\installed.json`, a shallow search (2 levels) of `C:\GOG Games`, `~\GOG Games`, `~\Games`.
- Linux: Heroic `~/.config/heroic/gog_store/installed.json`, shallow search of `~/GOG Games`, `~/Games`.
- macOS: `RimWorld*.app` in `/Applications`, `~/Applications`.

**5. Game config folder**, first that exists:
- Linux: Proton `<steam>/steamapps/compatdata/294100/pfx/drive_c/users/steamuser/AppData/LocalLow/Ludeon Studios/RimWorld by Ludeon Studios/Config`; Flatpak `~/.var/app/com.valvesoftware.Steam/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config` *(not in RimSort)*; `~/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config`.
- Windows: `%USERPROFILE%\AppData\LocalLow\Ludeon Studios\RimWorld by Ludeon Studios\Config`.
- macOS: `~/Library/Application Support/RimWorld/Config` (matched case-insensitively; RimSort spells it `Rimworld`).

**6. Local mods**: `<gameDir>/Mods`.

## Game icon

Linux: `<gameDir>/*_Data/Resources/UnityPlayer.png` (`RimWorldLinux_Data` today; the 1.7 rename likely makes it `RimWorld_Data`). Windows (icon inside the `.exe`) and macOS (`.icns`, which browsers do not show): no icon; Core gets the placeholder and the browser its default favicon.

## Not verified

Only the Linux layout is checked on a real machine. Windows and macOS follow RimSort's code and are covered by tests on simulated folder layouts.
