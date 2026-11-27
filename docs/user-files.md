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
translations:        # mods marked as translations → the mods they translate
  some.mod.ru: [original.mod]
builds:
  - name: Solo
    mods: [some.author.mod, another.mod]
currentBuild: Solo   # the tab open in the UI; optional
```

- **currentBuild:** the name of the open tab; missing or unknown → the first build.
- **Folders:** a tree; each folder has `name`, optional `expanded` (default `false`), `folders` and `mods`. The order of the lists does not matter: the UI sorts subfolders and mods by name (subfolders first).
- **Translations** are the user's marks: a key is a translation mod, its value the mods it translates (may be empty or name mods not on disk). A marked translation is hidden and shown under each of its originals that is on disk.
- **Sortout** is not stored: it is computed as every mod on disk that is neither in the tree nor shown under an original, and is shown as the first, virtual folder.
- **packageIds** are normalised to lowercase on load.
- **Missing file** → created with the predefined top-level folders `Library`, `UI`, `UX`, `QoL`, `Tools`, all empty, so every mod starts in Sortout.
- Folder names are unique among siblings; a mod appears at most once in the tree.

### Loading a hand-edited file

The file is trusted but checked; problems are reported, never silently fixed on disk:

- A mod listed in several folders → the first occurrence (depth-first, file order) wins; the others are reported.
- Duplicate sibling folder names → reported; both are kept until the user renames one.
- Malformed YAML → the app refuses to start the library view and shows the error; the file is not overwritten.
- Any edit made in the app rewrites the file from the loaded model, so reported problems (e.g. a mod listed twice) are resolved on that save the way they were displayed.
