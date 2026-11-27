import { useEffect, useMemo, useState } from "react";
import { deleteLeftover, fetchLibrary, type LibraryResponse, type Problem } from "./api";
import { BuildTabs } from "./BuildTabs";
import { useConfirm } from "./Dialog";
import { Details } from "./Details";
import { LibraryTree } from "./Tree";
import { allMods } from "./treeData";

export function App() {
  const [data, setData] = useState<LibraryResponse>();
  const [error, setError] = useState<string>();
  const [editError, setEditError] = useState<string>();
  const [showHidden, setShowHidden] = useState(false);
  const [selected, setSelected] = useState<string>();
  const confirm = useConfirm();

  useEffect(() => {
    fetchLibrary().then(setData, (e: Error) => setError(e.message));
  }, []);

  const mods = useMemo(() => (data ? allMods(data.library) : []), [data]);
  const nameOf = useMemo(() => {
    const names = new Map([...mods, ...(data?.library.game ?? [])].map((m) => [m.packageId, m.name]));
    return (id: string) => names.get(id) ?? id;
  }, [mods, data]);

  const onEdit = async (edit: Promise<LibraryResponse>) => {
    try {
      setData(await edit);
      setEditError(undefined);
      return true;
    } catch (e) {
      setEditError((e as Error).message);
      return false;
    }
  };

  const removeLeftover = async (p: Problem) => {
    const ok = await confirm({
      title: "Delete leftover folder",
      message: <>Delete <code>{p.removableFolder}</code> from the mods folder? It holds only .dds textures.</>,
      confirm: "Delete",
      danger: true,
    });
    if (ok) onEdit(deleteLeftover(p.removableFolder!));
  };

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p>Scanning…</p>;

  return (
    <div className="layout">
      <header>
        <h1>RimWorld Mod Manager</h1>
        <span className="muted">Game {data.gameVersion}</span>
        <label>
          <input type="checkbox" checked={showHidden} onChange={(e) => setShowHidden(e.target.checked)} /> show hidden
        </label>
        {editError && (
          <span className="error" onClick={() => setEditError(undefined)} title="Dismiss">
            {editError} ×
          </span>
        )}
      </header>
      <aside>
        <Details packageId={selected} marks={data.translations} mods={mods} onSelect={setSelected} onEdit={onEdit} />
      </aside>
      <main>
        {data.problems.length > 0 && (
          <ul className="error problems">
            {data.problems.map((p) => (
              <li key={p.message}>
                {p.message}
                {p.removableFolder && (
                  <>
                    {" "}
                    <button className="small" onClick={() => removeLeftover(p)}>
                      Delete folder
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        <BuildTabs builds={data.builds} current={data.currentBuild} onEdit={onEdit} nameOf={nameOf} />
        <LibraryTree
          view={data.library}
          showHidden={showHidden}
          selected={selected}
          onSelect={setSelected}
          onEdit={onEdit}
          build={data.builds.find((b) => b.name === data.currentBuild)}
        />
      </main>
    </div>
  );
}
