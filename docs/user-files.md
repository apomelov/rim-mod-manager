# User files

Both files live in `$XDG_CONFIG_HOME/rimmodmanager/` (default `~/.config/rimmodmanager/`). They are YAML so they can be edited by hand; the app rewrites them whole on save, so hand-written comments do not survive.

## settings.yaml

Machine-specific paths; not meant for git.

```yaml
modsDir: ~/.steam/steam/steamapps/workshop/content/294100
modsConfigFile: ~/.config/unity3d/Ludeon Studios/RimWorld by Ludeon Studios/Config/ModsConfig.xml
port: 8765
```

- A leading `~` is expanded to the home directory.
- Missing file or missing keys → the defaults above.

## library.yaml

All user decisions; meant to be kept under git.

```yaml
folders:
  - name: QoL
    expanded: true
    folders:
      - name: base
        mods: [brrainz.harmony, some.author.mod]
    mods: [another.mod]
  - name: Library
    expanded: false
    mods: []
translations:        # manual overrides on top of auto-matching: translation → originals
  some.mod.ru: [original.mod]
hidden:              # visibility overrides
  some.mod: false
builds:
  - name: Solo
    mods: [some.author.mod, another.mod]
```

- **Folders:** a tree; each folder has `name`, optional `expanded` (default `false`), `folders` and `mods`. The order of each list is the display order; in the UI subfolders always come before mods.
- **Sortout** is not stored: it is computed as every mod on disk that is not in the tree and not a hidden translation, and is shown as the first, virtual folder.
- **packageIds** are normalised to lowercase on load.
- **Missing file** → created with the predefined top-level folders `Library`, `UI`, `UX`, `QoL`, `Tools`, all empty, so every mod starts in Sortout.
- Folder names are unique among siblings; a mod appears at most once in the tree.

### Loading a hand-edited file

The file is trusted but checked; problems are reported, never silently fixed on disk:

- A mod listed in several folders → the first occurrence (depth-first, file order) wins; the others are reported.
- Duplicate sibling folder names → reported; both are kept until the user renames one.
- Malformed YAML → the app refuses to start the library view and shows the error; the file is not overwritten.
