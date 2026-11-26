# About.xml parsing spec

Primary reference: [RimWorld Wiki — Modding Tutorials/About.xml](https://rimworldwiki.com/wiki/Modding_Tutorials/About.xml) (revision oldid=184741, Nov 2026).
Where the wiki is silent, the rules below come from a survey of the user's library (189 Workshop mods, 2026-11-26) and are marked *(survey)*.

## Location

- Mods folder: one folder per mod, folder name = Workshop id.
- `<mod>/About/About.xml` — required; a folder without it is not a mod (skip with a warning).
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
| `loadAfter`, `loadBefore`, `forceLoadAfter`, `forceLoadBefore` (+ `…ByVersion`) | list of `<li>` packageIds | Metadata only; ordering is RimSort's job. |
| `incompatibleWith` (+ `ByVersion`) | list of `<li>` packageIds | Metadata; may be shown as a warning later. |
| `url` | text | Optional link. |

Not read: `modVersion`, `modIconPath`, `descriptionsByVersion`, `steamWorkshopUrl` (top-level), `downloadUrl`.

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

## Core and DLCs

- Ids starting with `ludeon.rimworld` are Core/DLCs (see requirements, "Core and DLCs"). They are never looked up in the mods folder.
- The list of DLCs can be read from `<knownExpansions>` in `ModsConfig.xml`.

## Errors

- Malformed XML or missing `packageId` → the mod is reported in a scan-problems list and skipped; the scan never fails as a whole.
- Duplicate `packageId` across folders → all copies reported as problems; the scanner keeps the first by folder-name order (the game itself loads only one), so this is a real problem for the user to fix.
