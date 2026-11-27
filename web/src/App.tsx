import { useCallback, useEffect, useMemo, useState } from "react";
import { deleteLeftover, fetchLibrary, fetchSetup, type LibraryResponse, type Problem, type SetupResponse } from "./api";
import { BuildTabs } from "./BuildTabs";
import { useConfirm } from "./Dialog";
import { Details } from "./Details";
import { Setup } from "./Setup";
import { Suggestions } from "./Suggestions";
import { LibraryTree } from "./Tree";
import { allMods } from "./treeData";

/** Setup first: without settings (or when asked) the welcome page, otherwise the library. */
export function App() {
  const [setup, setSetup] = useState<SetupResponse>();
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState(false);
  const [generation, setGeneration] = useState(0);

  const load = useCallback(() => fetchSetup().then(setSetup, (e: Error) => setError(e.message)), []);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <p className="error">{error}</p>;
  if (!setup) return <p>Loading…</p>;
  if (!setup.configured || editing) {
    return (
      <Setup
        setup={setup}
        onSaved={() => {
          setEditing(false);
          setGeneration((g) => g + 1); // reload the library with the new paths
          load();
        }}
        onCancel={setup.configured ? () => setEditing(false) : undefined}
      />
    );
  }
  return (
    <Library
      key={generation}
      onSettings={() => {
        load();
        setEditing(true);
      }}
    />
  );
}

function Library({ onSettings }: { onSettings: () => void }) {
  const [data, setData] = useState<LibraryResponse>();
  const [error, setError] = useState<string>();
  const [editError, setEditError] = useState<string>();
  const [showHidden, setShowHidden] = useState(false);
  const [showAllLanguages, setShowAllLanguages] = useState(false);
  const [findingTranslations, setFindingTranslations] = useState(false);
  const [selected, setSelected] = useState<string>();
  const confirm = useConfirm();

  useEffect(() => {
    fetchLibrary().then(setData, (e: Error) => setError(e.message));
  }, []);

  const mods = useMemo(() => (data ? allMods(data.library) : []), [data]);
  const entries = useMemo(() => new Map([...mods, ...(data?.library.game ?? [])].map((m) => [m.packageId, m])), [mods, data]);
  const nameOf = useCallback((id: string) => entries.get(id)?.name ?? id, [entries]);
  const entryOf = useCallback((id: string) => entries.get(id), [entries]);
  const closeSuggestions = useCallback(() => setFindingTranslations(false), []);

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
        <label>
          <input type="checkbox" checked={showAllLanguages} onChange={(e) => setShowAllLanguages(e.target.checked)} /> show all
          languages
        </label>
        <button onClick={onSettings}>Settings</button>
        <button onClick={() => setFindingTranslations(true)} disabled={findingTranslations}>
          Match translations
        </button>
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
          showAllLanguages={showAllLanguages}
          selected={selected}
          onSelect={setSelected}
          onEdit={onEdit}
          build={data.builds.find((b) => b.name === data.currentBuild)}
        />
        {findingTranslations && (
          <Suggestions marks={data.translations} entryOf={entryOf} onSelect={setSelected} onEdit={onEdit} onClose={closeSuggestions} />
        )}
      </main>
    </div>
  );
}
