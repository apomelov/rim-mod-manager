import { useCallback, useState } from "react";
import { buildEdits, type Build, type LibraryResponse } from "./api";
import { ContextMenu } from "./Menu";
import { freshName } from "./treeData";

interface Props {
  builds: Build[];
  current?: string;
  onEdit: (edit: Promise<LibraryResponse>) => Promise<boolean>;
}

/** Browser-like tabs, one per build; the last tab, "+", creates a new build. */
export function BuildTabs({ builds, current, onEdit }: Props) {
  const [menu, setMenu] = useState<{ x: number; y: number; build: string } | null>(null);
  const [renaming, setRenaming] = useState<string>();
  const closeMenu = useCallback(() => setMenu(null), []);

  async function create() {
    const name = freshName(builds.map((b) => b.name), "New build");
    if (await onEdit(buildEdits.create(name))) setRenaming(name);
  }

  async function rename(name: string, newName: string) {
    setRenaming(undefined);
    if (newName.trim() && newName.trim() !== name) await onEdit(buildEdits.rename(name, newName));
  }

  function remove(name: string) {
    if (window.confirm(`Delete build "${name}"? Its mod selection is lost; the mods themselves stay.`)) {
      onEdit(buildEdits.remove(name));
    }
  }

  return (
    <div className="tabs" role="tablist">
      {builds.map((b) => (
        <div
          key={b.name}
          role="tab"
          aria-selected={b.name === current}
          className={b.name === current ? "tab current" : "tab"}
          onClick={() => b.name !== current && onEdit(buildEdits.setCurrent(b.name))}
          onDoubleClick={() => setRenaming(b.name)}
          onContextMenu={(e) => {
            e.preventDefault();
            setMenu({ x: e.clientX, y: e.clientY, build: b.name });
          }}
          title={`${b.mods.length} mod(s) selected`}
        >
          {renaming === b.name ? (
            <input
              className="rename"
              autoFocus
              defaultValue={b.name}
              onFocus={(e) => e.currentTarget.select()}
              onClick={(e) => e.stopPropagation()}
              onBlur={(e) => rename(b.name, e.currentTarget.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") e.currentTarget.blur();
                if (e.key === "Escape") {
                  e.currentTarget.value = b.name; // blur then sees no change
                  e.currentTarget.blur();
                }
              }}
            />
          ) : (
            b.name
          )}
        </div>
      ))}
      <div className="tab add" role="tab" title="New build" onClick={create}>
        +
      </div>
      {menu && (
        <ContextMenu x={menu.x} y={menu.y} onClose={closeMenu}>
          <li onClick={() => setRenaming(menu.build)}>Rename</li>
          <li onClick={() => onEdit(buildEdits.duplicate(menu.build))}>Duplicate</li>
          <li onClick={() => remove(menu.build)}>Delete</li>
          <li className="disabled" title="Comes with the export step" onClick={(e) => e.stopPropagation()}>
            Activate
          </li>
        </ContextMenu>
      )}
    </div>
  );
}
