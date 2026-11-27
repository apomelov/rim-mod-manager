import { useEffect, useState } from "react";
import { Dialog } from "./Dialog";
import { activate, fetchActivation, type ActivationPreview, type ActivationResult, type Dependency } from "./api";

interface Props {
  build: string;
  /** Display name for a packageId (falls back to the id). */
  nameOf: (packageId: string) => string;
  onClose: () => void;
}

function workshopLink(d: Dependency) {
  if (!d.workshopId) return null;
  return (
    <>
      {" "}
      <a href={`steam://url/CommunityFilePage/${d.workshopId}`}>Steam</a>{" "}
      <a href={`https://steamcommunity.com/sharedfiles/filedetails/?id=${d.workshopId}`} target="_blank" rel="noreferrer">
        web
      </a>
    </>
  );
}

/** Confirms activation: what will be written, warnings, and blockers that prevent it. */
export function ActivateDialog({ build, nameOf, onClose }: Props) {
  const [preview, setPreview] = useState<ActivationPreview>();
  const [result, setResult] = useState<ActivationResult>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchActivation(build).then(setPreview, (e: Error) => setError(e.message));
  }, [build]);

  async function run() {
    setBusy(true);
    try {
      setResult(await activate(build));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const p = preview?.plan;
  const names = (ids: string[]) => ids.map(nameOf).join(", ");

  return (
    <Dialog title={`Activate “${build}”`} onClose={onClose} locked={busy}>
        {error && <p className="error">{error}</p>}
        {!p && !error && <p className="muted">Checking…</p>}

        {p && !result && (
          <>
            <p>
              The selected mods, their required dependencies and the available translations will be written directly to the
              game's mod list (<code>ModsConfig.xml</code>), sorted the way the game sorts. The current list is backed up first.
            </p>
            <dl>
              <dt>Selected</dt>
              <dd>{p.selected.length} mod(s)</dd>
              <dt>Dependencies</dt>
              <dd>{p.dependencies.length ? `+${p.dependencies.length}: ${names(p.dependencies)}` : "none to add"}</dd>
              <dt>Translations</dt>
              <dd>{p.translations.length ? `+${p.translations.length}: ${names(p.translations)}` : "none"}</dd>
              <dt>DLCs</dt>
              <dd>{p.dlcs.length ? names(p.dlcs) : "none"} (Core always)</dd>
              {!preview.blocked && (
                <>
                  <dt>Total</dt>
                  <dd>{p.order.length} entries</dd>
                </>
              )}
            </dl>

            {p.missingSelected.length > 0 && (
              <div className="warning">
                <h3>Not on disk, skipped</h3>
                <ul>
                  {p.missingSelected.map((id) => (
                    <li key={id}>{nameOf(id)}</li>
                  ))}
                </ul>
              </div>
            )}
            {p.missingDependencies.length > 0 && (
              <div className="error">
                <h3>Missing dependencies — activation blocked</h3>
                <ul>
                  {p.missingDependencies.map((m) => (
                    <li key={m.dependency.packageId}>
                      {m.dependency.displayName ?? m.dependency.packageId} <span className="muted">({m.dependency.packageId})</span>,
                      required by {names(m.requiredBy)}
                      {workshopLink(m.dependency)}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {p.cycle.length > 0 && (
              <div className="error">
                <h3>Load order cycle — activation blocked</h3>
                <p>{names(p.cycle)}</p>
              </div>
            )}
          </>
        )}

        {result && (
          <p>
            Wrote {result.written} entries to <code>{result.file}</code>.<br />
            Backup: <code>{result.backup}</code>
          </p>
        )}

        <div className="buttons">
          {result ? (
            <button onClick={onClose}>Close</button>
          ) : (
            <>
              <button onClick={onClose} disabled={busy}>
                Cancel
              </button>
              <button className="primary" onClick={run} disabled={!preview || preview.blocked || busy}>
                Activate
              </button>
            </>
          )}
        </div>
    </Dialog>
  );
}
