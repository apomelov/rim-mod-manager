import type { FolderView, LibraryView, ModEntry } from "./api";

/** Rows of the library tree as react-arborist sees them. */
export type TreeNode =
  | { id: "sortout"; kind: "sortout"; name: string; count: number; children: TreeNode[] }
  | { id: string; kind: "folder"; name: string; path: string; expanded: boolean; count: number; children: TreeNode[] }
  | { id: string; kind: "mod"; name: string; entry: ModEntry; children: TreeNode[] | null }
  | { id: string; kind: "translation"; name: string; entry: ModEntry; children: null }
  /** Core or a DLC: shown first, not movable; Core is always on. */
  | { id: string; kind: "game"; name: string; entry: ModEntry; core: boolean; children: null };

export const CORE_ID = "ludeon.rimworld";

export const SORTOUT_ID = "sortout";

/** Mirrors the server's LibraryStore.PREDEFINED_FOLDERS: these stay at the top level. */
export const PREDEFINED_FOLDERS = ["Library", "UI", "UX", "QoL", "Tools"];
export const isPredefined = (path: string) => PREDEFINED_FOLDERS.includes(path);
export const folderId = (path: string) => `f:${path}`;
export const modId = (packageId: string) => `m:${packageId}`;

function modNode(m: ModEntry, showHidden: boolean): TreeNode {
  const translations = showHidden ? (m.translations ?? []) : [];
  return {
    id: modId(m.packageId),
    kind: "mod",
    name: m.name,
    entry: m,
    children: translations.length
      ? translations.map((t) => ({ id: `t:${m.packageId}:${t.packageId}`, kind: "translation", name: t.name, entry: t, children: null }))
      : null,
  };
}

function mods(list: ModEntry[] | undefined, showHidden: boolean): TreeNode[] {
  return (list ?? []).filter((m) => showHidden || !m.hidden).map((m) => modNode(m, showHidden));
}

function count(nodes: TreeNode[]): number {
  return nodes.reduce((n, c) => n + (c.kind === "folder" ? c.count : c.kind === "mod" ? 1 : 0), 0);
}

function folderNode(f: FolderView, parent: string, showHidden: boolean): TreeNode {
  const path = parent ? `${parent}/${f.name}` : f.name;
  const children = [...(f.folders ?? []).map((c) => folderNode(c, path, showHidden)), ...mods(f.mods, showHidden)];
  return { id: folderId(path), kind: "folder", name: f.name, path, expanded: f.expanded ?? false, count: count(children), children };
}

export function buildTree(view: LibraryView, showHidden: boolean): TreeNode[] {
  const sortout = mods(view.sortout, showHidden);
  return [
    ...(view.game ?? []).map(
      (g): TreeNode => ({ id: `g:${g.packageId}`, kind: "game", name: g.name, entry: g, core: g.packageId === CORE_ID, children: null }),
    ),
    ...view.folders.map((f) => folderNode(f, "", showHidden)),
    { id: SORTOUT_ID, kind: "sortout", name: "Sortout", count: sortout.length, children: sortout },
  ];
}

/** Every folder node, depth-first. */
export function allFolders(nodes: TreeNode[]): Extract<TreeNode, { kind: "folder" }>[] {
  return nodes.flatMap((n) => (n.kind === "folder" ? [n, ...allFolders(n.children)] : []));
}

/** Every mod on disk that appears anywhere in the view (for pickers and name lookup). */
export function allMods(view: LibraryView): ModEntry[] {
  const seen = new Map<string, ModEntry>();
  const add = (m: ModEntry) => {
    if (!seen.has(m.packageId)) seen.set(m.packageId, m);
    (m.translations ?? []).forEach(add);
  };
  const walk = (f: FolderView) => {
    (f.folders ?? []).forEach(walk);
    (f.mods ?? []).forEach(add);
  };
  view.sortout.forEach(add);
  view.folders.forEach(walk);
  return [...seen.values()];
}

/** A name for a new folder that does not clash with its siblings. */
export function freshName(siblings: string[], base = "New folder"): string {
  if (!siblings.includes(base)) return base;
  for (let i = 2; ; i++) if (!siblings.includes(`${base} ${i}`)) return `${base} ${i}`;
}
