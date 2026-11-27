# About.xml parsing spec

Primary reference: [RimWorld Wiki — Modding Tutorials/About.xml](https://rimworldwiki.com/wiki/Modding_Tutorials/About.xml) (revision oldid=184741, Nov 2026).
Where the wiki is silent, the rules below come from a survey of the user's library (189 Workshop mods, 2026-11-26) and are marked *(survey)*.

## Location

- Mods folder: one folder per mod, folder name = Workshop id.
- `<mod>/About/About.xml` — required; a folder without it is not a mod (skip with a warning). See Errors for leftover folders.
- `<mod>/About/PublishedFileId.txt` — Workshop id. Optional *(survey: 1 of 189 lacks it)*; fall back to the folder name.

## Tolerant reading *(survey)*

- Element names are matched case-insensitively (one mod has root `<modMetaData>`). The wiki does not specify case rules for elements.
- A UTF-8 BOM may be present (66 of 189).
- Text values are trimmed.
- Unknown elements are ignored.

## Fields we read

| Element | Shape | Notes |
|---|---|---|
| `packageId` | text | **The identity.** Case-insensitive: RimWorld lowercases all ids. We normalise to lowercase for keys and comparison, keep the original for display. Globally unique per the wiki. |
| `name` | text | Display name. Missing → fall back to packageId. |
| `author` | text | Comma-separated names. |
| `authors` | list of `<li>` | Alternative to `author`. If both are present, merge. |
| `description` | text | Plain text; input for translation matching. |
| `supportedVersions` | list of `<li>` (`1.6`) | Shown in UI; not used for blocking. |
| `modDependencies` | list of `<li>` items | See below. |
| `modDependenciesByVersion` | `<vX.Y>` → list of items | Overrides `modDependencies` for that version. |
| `loadAfter`, `loadBefore` (+ `…ByVersion`), `forceLoadAfter`, `forceLoadBefore` (no `ByVersion` in the game) | list of `<li>` packageIds | Input for the activation sort (see requirements, Export pipeline). |
| `incompatibleWith` (+ `ByVersion`) | list of `<li>` packageIds | Metadata; may be shown as a warning later. |
| `url` | text | Optional link. |
| `modVersion` | text | The mod's own version (1.4+); shown in the details panel. |
| `modIconPath` | text | Icon texture path, see Images. |

Not read: `descriptionsByVersion`, `steamWorkshopUrl` (top-level), `downloadUrl`.

### Dependency item

```xml
<li>
  <packageId>brrainz.harmony</packageId>
  <displayName>Harmony</displayName>
  <steamWorkshopUrl>steam://url/CommunityFilePage/2009463077</steamWorkshopUrl>
  <alternativePackageIds><li>…</li></alternativePackageIds>  <!-- 1.6+ -->
</li>
```

- A dependency is satisfied if `packageId` **or any of `alternativePackageIds`** is present in the set. *(survey: no local mod uses alternatives yet, but 1.6 supports them.)*
- `displayName` and the Workshop id from `steamWorkshopUrl` are kept so a missing dependency can be shown and linked even though the mod is not on disk.

## Version-specific variants

- Keys are written `v<major>.<minor>` (`v1.6`). *(survey: v1.1–v1.7 occur.)*
- The game version comes from `<version>` in `ModsConfig.xml` (currently `1.6.4871 rev598` → `1.6`).
- Resolution (wiki does not specify fallback; this follows the wiki's "overrides" wording): if a `…ByVersion` element has a key for the game version, use it; otherwise use the plain element. Other version keys are ignored.

## Images

Neither image is required; the UI shows a placeholder when one is missing. File names are matched case-insensitively *(survey: `Preview.png` 139, `preview.png` 48)*.

- **Preview** (details panel): `About/Preview.png`. About.xml has no field for it.
- **Icon** (tree rows), first match wins:
  1. `modIconPath` (1.5+): a texture path without extension, relative to a `Textures` folder. Searched as `**/Textures/<path>.png` anywhere inside the mod folder (mods keep textures in `Textures/`, `1.6/Textures/`, `Common/Textures/`, …).
  2. If not found in the mod itself, the same path in its dependencies on disk, transitively, nearest first *(survey: 17 Vanilla Expanded mods point at `UI/VELogo`, which lives in the VE framework; for some it is a dependency of a dependency)*.
  3. `About/ModIcon.png` (loaded automatically by the game since 1.5).
- Images are resolved during the scan; the API serves only those resolved files, never a path supplied by the client.

## Languages

A mod's languages are the folder names inside any `Languages` folder in it: at the root or in a load folder (`Common/`, `1.6/`, conditional ones like `Biotech/`, nested up to two levels). Version folders (`1.5`, `v1.5`) count only for the game version; LoadFolders.xml is not read, so this approximates the game's load folders. Names are normalised: the native-name suffix is dropped (`Russian (Русский)` → `Russian`) and known aliases are mapped (`TraditionalChinese` → `ChineseTraditional`, `PortugueseBrazil` → `PortugueseBrazilian`, `KoreanFastChannel` → `Korean`). *(survey, 2026-11-27: 150 of 187 mods have Languages; root 122, version folders, `Common/`, conditional subfolders.)*

## Core and DLCs

- Ids starting with `ludeon.rimworld` are Core/DLCs (see requirements, "Core and DLCs"). They are never looked up in the mods folder.
- The list of DLCs can be read from `<knownExpansions>` in `ModsConfig.xml`.

## Errors

- Malformed XML or missing `packageId` → the mod is reported in a scan-problems list and skipped; the scan never fails as a whole.
- Duplicate `packageId` across folders → all copies reported as problems; the scanner keeps the first by folder-name order (the game itself loads only one), so this is a real problem for the user to fix.
- A folder without About.xml is reported as probably leftovers of a removed mod. Steam deletes only the files it downloaded, so textures generated later stay behind after unsubscribing: RimSort's texture optimisation (todds) writes a `.dds` next to each `.png` *(survey, 2026-11-27: 65 of 189 mods have them)*. If the folder holds at least one file and only `.dds` files (no symlinks), the problem says so and the UI offers to delete the folder, after a confirmation; the server re-checks the folder right before deleting. This is the only write the app makes to the mods folder, and only on an explicit click.
