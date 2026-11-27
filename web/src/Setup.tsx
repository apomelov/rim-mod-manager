import { useEffect, useRef, useState } from "react";
import { checkSetup, saveSetup, type SetupChecks, type SetupPaths, type SetupResponse } from "./api";

const FIELDS: { key: keyof SetupPaths; label: string; hint: string }[] = [
  { key: "gameDir", label: "Game folder", hint: "Holds Data/ and Mods/ (on macOS: the .app or the folder next to it)" },
  { key: "gameConfigDir", label: "Game config folder", hint: "Holds ModsConfig.xml, the mod list the game reads" },
  { key: "workshopDir", label: "Steam Workshop mods", hint: "…/steamapps/workshop/content/294100; empty without Steam" },
  { key: "localModsDir", label: "Local mods", hint: "The game's Mods folder; its copy of a mod wins over Workshop's" },
  { key: "libraryFile", label: "Library file", hint: "Your folders, translation marks and builds; can live in git or a synced folder" },
];

interface Props {
  setup: SetupResponse;
  onSaved: () => void;
  /** Present when the app is already set up (the page was opened as Settings). */
  onCancel?: () => void;
}

/** The welcome page (docs/paths.md): detected paths with a check of each, editable, saved to settings.yaml. */
export function Setup({ setup, onSaved, onCancel }: Props) {
  const [paths, setPaths] = useState<SetupPaths>(setup.paths);
  const [checks, setChecks] = useState<SetupChecks>(setup.checks);
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const latest = useRef(0);

  useEffect(() => {
    const n = ++latest.current;
    const t = setTimeout(() => {
      checkSetup(paths).then((c) => n === latest.current && setChecks(c), (e: Error) => setError(e.message));
    }, 300);
    return () => clearTimeout(t);
  }, [paths]);

  const allOk = FIELDS.every((f) => checks[f.key].ok);

  async function save() {
    setSaving(true);
    try {
      await saveSetup(paths);
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="setup">
      <h1>{setup.configured ? "Settings" : "Welcome to RimWorld Mod Manager"}</h1>
      {!setup.configured && (
        <p>
          These are the folders found on this computer ({setup.os.toLowerCase()}). Check them, fix any that are wrong, and save. They
          are stored in <code>{setup.configDir}</code>.
        </p>
      )}
      {FIELDS.map(({ key, label, hint }) => {
        const c = checks[key];
        const detected = setup.detected[key];
        return (
          <div key={key} className="setup-field">
            <label htmlFor={key}>{label}</label>
            <input id={key} value={paths[key]} spellCheck={false} onChange={(e) => setPaths({ ...paths, [key]: e.target.value })} />
            <div className={c.ok ? "setup-check ok" : "setup-check bad"}>
              {c.ok ? "✓" : "✗"} {c.message}
            </div>
            <div className="muted small">
              {hint}
              {detected && detected !== paths[key] && (
                <>
                  {" · "}found: <code>{detected}</code>{" "}
                  <button className="small" onClick={() => setPaths({ ...paths, [key]: detected })}>
                    use
                  </button>
                </>
              )}
            </div>
          </div>
        );
      })}
      {error && <p className="error">{error}</p>}
      <div className="buttons">
        {onCancel && <button onClick={onCancel}>Cancel</button>}
        <button className="primary" disabled={!allOk || saving} onClick={save}>
          Save
        </button>
      </div>
    </div>
  );
}
