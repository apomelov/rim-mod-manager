import { createContext, useContext, useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { Tree, type NodeApi, type NodeRendererProps, type TreeApi } from "react-arborist";
import { iconUrl, libraryEdits, type LibraryResponse, type LibraryView, type ModEntry } from "./api";
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
}

interface Ctx {
  selected?: string;
  showHidden: boolean;
  onSelect: (packageId: string) => void;
  openMenu: (e: MouseEvent, node: TreeNode | null) => void;
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
  const entry = d.kind === "mod" || d.kind === "translation" ? d.entry : undefined;
  const classes = [
    "row",
    d.kind,
    node.isSelected && "selected",
    entry && entry.packageId === ctx.selected && "current",
    node.willReceiveDrop && "drop",
    entry?.missing && "missing",
    entry?.hidden && "hidden",
  ]
    .filter(Boolean)
    .join(" ");

  const onClick = () => {
    if (entry) ctx.onSelect(entry.packageId);
    else node.toggle();
  };
  const translationCount = entry?.translations?.length ?? 0;

  return (
    <div ref={dragHandle} style={style} className={classes} onClick={onClick} onContextMenu={(e) => ctx.openMenu(e, d)}>
      <span
        className="arrow"
        onClick={(e) => {
          e.stopPropagation();
          node.toggle();
        }}
      >
        {node.isLeaf ? "" : node.isOpen ? "▾" : "▸"}
      </span>
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

export function LibraryTree({ view, showHidden, selected, onSelect, onEdit }: Props) {
  const data = useMemo(() => buildTree(view, showHidden), [view, showHidden]);
  const tree = useRef<TreeApi<TreeNode> | null>(null);
  const box = useSize<HTMLDivElement>();
  const [menu, setMenu] = useState<Menu | null>(null);
  const [pendingRename, setPendingRename] = useState<string>();
  const syncing = useRef(false);

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

  useEffect(() => {
    if (!menu) return;
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [menu]);

  const parentOf = (node: NodeApi<TreeNode> | null): TreeNode | null => (node && !node.isRoot ? node.data : null);

  const ctx: Ctx = {
    selected,
    showHidden,
    onSelect,
    openMenu: (e, node) => {
      e.preventDefault();
      e.stopPropagation();
      setMenu({ x: e.clientX, y: e.clientY, node });
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

  return (
    <TreeContext.Provider value={ctx}>
      <div className="tree-box" ref={box.ref} onContextMenu={(e) => ctx.openMenu(e, null)}>
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
            disableDrag={(d) => d.kind === "sortout" || d.kind === "translation"}
            disableDrop={({ parentNode, dragNodes }) => {
              const p = parentOf(parentNode);
              if (p && (p.kind === "mod" || p.kind === "translation")) return true;
              if (dragNodes.some((n) => n.data.kind === "folder") && p?.kind === "sortout") return true;
              if (dragNodes.some((n) => n.data.kind === "mod") && p === null) return true;
              if (p !== null && dragNodes.some((n) => n.data.kind === "folder" && isPredefined(n.data.path))) return true;
              return false;
            }}
            onSelect={(nodes) => {
              const d = nodes.length === 1 ? nodes[0].data : undefined;
              if (d && (d.kind === "mod" || d.kind === "translation")) onSelect(d.entry.packageId);
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
            onMove={async ({ dragIds, dragNodes, parentNode, index }) => {
              const parent = parentOf(parentNode);
              const siblings = parent ? (parent.children ?? []) : data;
              const dragged = new Set(dragIds);
              const after = siblings.slice(index).filter((n) => !dragged.has(n.id));

              const mods = dragNodes.flatMap((n) => (n.data.kind === "mod" ? [n.data.entry.packageId] : []));
              if (mods.length && (parent?.kind === "sortout" || parent?.kind === "folder")) {
                const before = after.find((n) => n.kind === "mod");
                const to = parent.kind === "folder" ? parent.path : null;
                await onEdit(libraryEdits.moveMods(mods, to, before && before.kind === "mod" ? before.entry.packageId : null));
              }
              const folders = dragNodes.flatMap((n) => (n.data.kind === "folder" ? [n.data] : []));
              if (folders.length && (parent === null || parent.kind === "folder")) {
                const before = after.find((n): n is FolderNode => n.kind === "folder");
                for (const f of folders) await onEdit(libraryEdits.moveFolder(f.path, parent?.path ?? "", before?.name ?? null));
              }
            }}
          >
            {Row}
          </Tree>
        )}
      </div>
      {menu && (
        <ul className="menu" style={{ left: menu.x, top: menu.y }}>
          {menu.node?.kind === "folder" ? (
            <>
              <li onClick={() => createFolder(menu.node as FolderNode)}>New subfolder</li>
              <li onClick={() => tree.current?.get(menu.node!.id)?.edit()}>Rename</li>
              <li onClick={() => deleteFolder(menu.node as FolderNode)}>Delete</li>
            </>
          ) : (
            <li onClick={() => createFolder(null)}>New folder</li>
          )}
        </ul>
      )}
    </TreeContext.Provider>
  );
}
