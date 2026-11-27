import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { Tree, type NodeApi, type NodeRendererProps, type TreeApi } from "react-arborist";
import { ContextMenu } from "./Menu";
import { buildEdits, iconUrl, libraryEdits, type Build, type LibraryResponse, type LibraryView, type ModEntry } from "./api";
import { allFolders, buildTree, folderId, freshName, isPredefined, SORTOUT_ID, type TreeNode } from "./treeData";

type FolderNode = Extract<TreeNode, { kind: "folder" }>;
type Menu = { x: number; y: number; node: TreeNode | null };

interface Props {
  view: LibraryView;
  showHidden: boolean;
  selected?: string;
  onSelect: (packageId: string) => void;
  /** Runs an edit; on success the app takes the new library. Resolves to whether it succeeded. */
  onEdit: (edit: Promise<LibraryResponse>) => Promise<boolean>;
  /** The open build tab; without one the tree has no checkboxes. */
  build?: Build;
}

interface Ctx {
  /** Mods selected in the open build; null when there is no build (no checkboxes). */
  checked: Set<string> | null;
  check: (mods: string[], selected: boolean) => void;
  selected?: string;
  showHidden: boolean;
  onSelect: (packageId: string) => void;
  openMenu: (e: MouseEvent, node: TreeNode | null) => void;
  /** The folder (or Sortout) a drag would drop into; its whole subtree is highlighted. */
  dropZone: string | null;
  hoverDrag: (node: NodeApi<TreeNode>) => void;
}

/** Mods a folder's checkbox stands for: every mod below it, except marked translations (they follow their originals). */
function checkableMods(node: TreeNode): string[] {
  if (node.kind === "mod") return node.entry.hidden ? [] : [node.entry.packageId];
  if (node.kind === "folder") return node.children.flatMap(checkableMods);
  return [];
}

function Checkbox({ node }: { node: TreeNode }) {
  const { checked, check } = useContext(TreeContext);
  if (!checked) return null;
  if (node.kind === "game" && node.core) {
    return <input type="checkbox" className="check" checked disabled title="Core is always loaded" />;
  }
  const mods = node.kind === "sortout" ? [] : node.kind === "game" ? [node.entry.packageId] : checkableMods(node);
  if (mods.length === 0 || node.kind === "translation") return <span className="check-space" />;
  const on = mods.filter((m) => checked.has(m)).length;
  const all = on === mods.length;
  return (
    <input
      type="checkbox"
      className="check"
      checked={on > 0 && all}
      ref={(el) => {
        if (el) el.indeterminate = on > 0 && !all;
      }}
      onMouseDown={(e) => e.stopPropagation()}
      onClick={(e) => e.stopPropagation()}
      onChange={() => check(mods, !all)}
    />
  );
}

/** The folder or Sortout a row belongs to: the row itself if it is one, else its nearest such ancestor. */
function zoneOf(node: NodeApi<TreeNode> | null): NodeApi<TreeNode> | null {
  for (let n = node; n && !n.isRoot; n = n.parent) {
    if (n.data.kind === "folder" || n.data.kind === "sortout") return n;
  }
  return null;
}

function inZone(node: NodeApi<TreeNode>, zone: string | null): boolean {
  if (!zone) return false;
  for (let n: NodeApi<TreeNode> | null = node; n && !n.isRoot; n = n.parent) if (n.id === zone) return true;
  return false;
}

/**
 * Whether a drag may drop into [zone]: mods into a folder or Sortout, folders into a folder (not their own subtree,
 * not predefined ones). Nodes already in [zone] are skipped, but at least one must actually move: the zone a drag
 * started from is not a target.
 */
function canDropInto(zone: NodeApi<TreeNode> | null, dragged: NodeApi<TreeNode>[]): boolean {
  if (!zone) return false;
  const z = zone.data;
  const moving = dragged.filter((n) => zoneOf(n.parent)?.id !== zone.id);
  return moving.length > 0 && moving.every((n) => {
    const d = n.data;
    if (d.kind === "mod") return true;
    if (d.kind !== "folder" || z.kind !== "folder" || isPredefined(d.path)) return false;
    return z.path !== d.path && !z.path.startsWith(d.path + "/");
  });
}

const TreeContext = createContext<Ctx>(null!);

/** Placeholder for mods without an icon: a jigsaw piece. */
function PuzzleIcon() {
  return (
    <svg className="icon placeholder" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 8h4a2.2 2.2 0 1 1 4.4 0H17v3.8a2.2 2.2 0 1 1 0 4.4V20H5v-3.8a2.2 2.2 0 1 0 0-4.4Z" />
    </svg>
  );
}

function Icon({ mod }: { mod: ModEntry }) {
  return mod.hasIcon ? <img className="icon" src={iconUrl(mod.packageId)} alt="" loading="lazy" /> : <PuzzleIcon />;
}

function EditInput({ node }: { node: NodeApi<TreeNode> }) {
  return (
    <input
      className="rename"
      autoFocus
      defaultValue={node.data.name}
      onFocus={(e) => e.currentTarget.select()}
      onBlur={() => node.reset()}
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => {
        if (e.key === "Enter") node.submit(e.currentTarget.value);
        if (e.key === "Escape") node.reset();
      }}
    />
  );
}

function Row({ node, style, dragHandle }: NodeRendererProps<TreeNode>) {
  const ctx = useContext(TreeContext);
  const d = node.data;
  const entry = d.kind === "mod" || d.kind === "translation" || d.kind === "game" ? d.entry : undefined;
  const classes = [
    "tree-row", // not "row": react-arborist gives its drag preview container that class
    d.kind,
    node.isSelected && "selected",
    entry && entry.packageId === ctx.selected && "current",
    inZone(node, ctx.dropZone) && "drop",
    entry?.missing && "missing",
    entry?.hidden && "hidden",
  ]
    .filter(Boolean)
    .join(" ");

  const onClick = (e: MouseEvent) => {
    if (e.ctrlKey) {
      // react-arborist toggles a row in the selection only with Meta (Cmd on a Mac); on Linux that is Ctrl.
      e.stopPropagation();
      if (node.isSelected) node.deselect();
      else {
        node.selectMulti();
        if (entry) ctx.onSelect(entry.packageId);
      }
      return;
    }
    if (entry) ctx.onSelect(entry.packageId);
    else node.toggle();
  };
  const translationCount = entry?.translations?.length ?? 0;

  return (
    <div
      ref={dragHandle}
      style={style}
      className={classes}
      onClick={onClick}
      onContextMenu={(e) => ctx.openMenu(e, d)}
      onMouseDown={(e) => {
        // Select on press, not on click, so the row being dragged is the selected one (and shown in the details).
        // A row already in a multi-selection keeps it, so the whole selection can be dragged.
        if (e.button !== 0 || e.ctrlKey || e.shiftKey || e.metaKey || node.isSelected || node.isEditing) return;
        node.select();
        if (entry) ctx.onSelect(entry.packageId);
      }}
      onDragOver={() => ctx.hoverDrag(node)}
    >
      <span
        className="arrow"
        onClick={(e) => {
          e.stopPropagation();
          node.toggle();
        }}
      >
        {node.isLeaf ? "" : node.isOpen ? "▾" : "▸"}
      </span>
      <Checkbox node={d} />
      {entry && <Icon mod={entry} />}
      {node.isEditing ? <EditInput node={node} /> : <span className="name" title={entry?.packageId}>{d.name}</span>}
      {entry?.missing && <span className="badge">missing</span>}
      {(d.kind === "folder" || d.kind === "sortout") && <span className="count">{d.count}</span>}
      {d.kind === "mod" && !ctx.showHidden && translationCount > 0 && <span className="badge">+{translationCount} tr</span>}
    </div>
  );
}

function useSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  useEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver(([e]) => setSize({ width: e.contentRect.width, height: e.contentRect.height }));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return { ref, ...size };
}

export function LibraryTree({ view, showHidden, selected, onSelect, onEdit, build }: Props) {
  const checked = useMemo(() => (build ? new Set(build.mods) : null), [build]);
  const data = useMemo(() => buildTree(view, showHidden), [view, showHidden]);
  const tree = useRef<TreeApi<TreeNode> | null>(null);
  const box = useSize<HTMLDivElement>();
  const [menu, setMenu] = useState<Menu | null>(null);
  const closeMenu = useCallback(() => setMenu(null), []);
  const [pendingRename, setPendingRename] = useState<string>();
  const syncing = useRef(false);
  const [dropZone, setDropZone] = useState<string | null>(null);
  const zoneRef = useRef<NodeApi<TreeNode> | null>(null);
  const rowHovered = useRef(false);

  function setZone(zone: NodeApi<TreeNode> | null) {
    zoneRef.current = zone;
    setDropZone(zone?.id ?? null);
  }

  // Cancelled drags (Esc, dropped outside) clear the zone here; a real drop clears it in onMove.
  // Not on "drop": this listener is registered before react-dnd's, so it would clear the zone before the drop is handled.
  useEffect(() => {
    const clear = () => setZone(null);
    window.addEventListener("dragend", clear);
    return () => window.removeEventListener("dragend", clear);
  }, []);

  const [initialOpenState] = useState(() =>
    Object.fromEntries([[SORTOUT_ID, true], ...allFolders(data).map((f) => [f.id, f.expanded])]),
  );

  // The library file is the source of truth for folders' expanded state (ids change on rename/move).
  useEffect(() => {
    const t = tree.current;
    if (!t) return;
    syncing.current = true;
    for (const f of allFolders(data)) {
      const n = t.get(f.id);
      if (n && n.isOpen !== f.expanded) (f.expanded ? t.open : t.close).call(t, f.id);
    }
    syncing.current = false;
    if (pendingRename) {
      t.get(pendingRename)?.edit();
      setPendingRename(undefined);
    }
  }, [data, pendingRename]);

  const parentOf = (node: NodeApi<TreeNode> | null): TreeNode | null => (node && !node.isRoot ? node.data : null);

  const ctx: Ctx = {
    checked,
    check: (mods, on) => {
      if (build) onEdit(buildEdits.select(build.name, mods, on));
    },
    selected,
    showHidden,
    onSelect,
    openMenu: (e, node) => {
      e.preventDefault();
      e.stopPropagation();
      if (node?.kind === "game" || node?.kind === "translation") return setMenu(null); // nothing to do with them
      setMenu({ x: e.clientX, y: e.clientY, node });
    },
    dropZone,
    hoverDrag: (node) => {
      rowHovered.current = true;
      const zone = zoneOf(node);
      const valid = canDropInto(zone, tree.current?.dragNodes ?? []) ? zone : null;
      if (valid?.id !== zoneRef.current?.id) setZone(valid);
    },
  };

  async function createFolder(parent: FolderNode | null) {
    const siblings = (parent ? parent.children : data).filter((n): n is FolderNode => n.kind === "folder").map((n) => n.name);
    const name = freshName(siblings);
    const parentPath = parent?.path ?? "";
    if (!(await onEdit(libraryEdits.createFolder(parentPath, name)))) return;
    if (parent && !parent.expanded) await onEdit(libraryEdits.setExpanded(parent.path, true));
    setPendingRename(folderId(parentPath ? `${parentPath}/${name}` : name));
  }

  async function deleteFolder(f: FolderNode) {
    const what = f.count > 0 ? ` Its ${f.count} mod(s) go back to Sortout.` : "";
    if (window.confirm(`Delete folder "${f.path}"?${what}`)) await onEdit(libraryEdits.deleteFolder(f.path));
  }

  /** The right-clicked mod, or every selected mod if it is part of a multi-selection. */
  function menuMods(node: TreeNode): string[] {
    const sel = tree.current?.selectedNodes.map((n) => n.data) ?? [];
    const group = sel.some((n) => n.id === node.id) ? sel : [node];
    return group.flatMap((n) => (n.kind === "mod" ? [n.entry.packageId] : []));
  }

  /** Where a mod row currently sits: a folder node, Sortout, or null (shown under an original). */
  const containerOf = (node: TreeNode) => parentOf(tree.current?.get(node.id)?.parent ?? null);

  function moveTargets(node: TreeNode) {
    const here = containerOf(node);
    const folders = data.filter((n): n is FolderNode => n.kind === "folder");
    return [
      ...folders.filter((f) => f.id !== here?.id).map((f) => ({ label: f.name, to: f.path as string | null })),
      ...(here?.kind === "sortout" ? [] : [{ label: "Sortout", to: null }]),
    ];
  }

  return (
    <TreeContext.Provider value={ctx}>
      <div
        className="tree-box"
        ref={box.ref}
        onContextMenu={(e) => ctx.openMenu(e, null)}
        onDragOver={() => {
          // Rows report first (the event bubbles up here); outside any row there is no drop zone.
          if (!rowHovered.current && zoneRef.current) setZone(null);
          rowHovered.current = false;
        }}
      >
        {box.height > 0 && (
          <Tree<TreeNode>
            ref={(t) => {
              tree.current = t ?? null;
            }}
            data={data}
            width={box.width}
            height={box.height}
            rowHeight={24}
            indent={18}
            openByDefault={false}
            initialOpenState={initialOpenState}
            disableEdit={(d) => d.kind !== "folder"}
            disableDrag={(d) => d.kind === "sortout" || d.kind === "translation" || d.kind === "game"}
            renderCursor={() => null}
            disableDrop={({ dragNodes }) => !canDropInto(zoneRef.current, dragNodes)}
            onSelect={(nodes) => {
              const d = nodes.length === 1 ? nodes[0].data : undefined;
              if (d && (d.kind === "mod" || d.kind === "translation" || d.kind === "game")) onSelect(d.entry.packageId);
            }}
            onToggle={(id) => {
              if (syncing.current || !id.startsWith("f:")) return;
              const n = tree.current?.get(id);
              if (n && n.data.kind === "folder") onEdit(libraryEdits.setExpanded(n.data.path, n.isOpen));
            }}
            onRename={({ name, node }) => {
              if (node.data.kind === "folder" && name.trim() !== node.data.name) {
                onEdit(libraryEdits.renameFolder(node.data.path, name));
              }
            }}
            onMove={async ({ dragNodes }) => {
              // Where the cursor sits inside the zone does not matter: the view sorts by name.
              const zone = zoneRef.current?.data;
              setZone(null);
              if (!zone || (zone.kind !== "folder" && zone.kind !== "sortout")) return;
              const mods = dragNodes.flatMap((n) => (n.data.kind === "mod" ? [n.data.entry.packageId] : []));
              if (mods.length) await onEdit(libraryEdits.moveMods(mods, zone.kind === "folder" ? zone.path : null));
              if (zone.kind !== "folder") return;
              for (const n of dragNodes) {
                if (n.data.kind === "folder") await onEdit(libraryEdits.moveFolder(n.data.path, zone.path));
              }
            }}
          >
            {Row}
          </Tree>
        )}
      </div>
      {menu && (
        <ContextMenu x={menu.x} y={menu.y} onClose={closeMenu}>
          {menu.node?.kind === "folder" ? (
            <>
              <li onClick={() => createFolder(menu.node as FolderNode)}>New subfolder</li>
              <li onClick={() => tree.current?.get(menu.node!.id)?.edit()}>Rename</li>
              <li onClick={() => deleteFolder(menu.node as FolderNode)}>Delete</li>
              {(menu.node as FolderNode).path.includes("/") && (
                <li onClick={() => onEdit(libraryEdits.moveFolder((menu.node as FolderNode).path, ""))}>Move to top level</li>
              )}
            </>
          ) : menu.node?.kind === "mod" ? (
            moveTargets(menu.node).map((t) => (
              <li key={t.label} onClick={() => onEdit(libraryEdits.moveMods(menuMods(menu.node!), t.to))}>
                Move to {t.label}
              </li>
            ))
          ) : (
            <li onClick={() => createFolder(null)}>New folder</li>
          )}
        </ContextMenu>
      )}
    </TreeContext.Provider>
  );
}
