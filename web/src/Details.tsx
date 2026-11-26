import { useEffect, useState } from "react";
import { fetchMod, previewUrl, type LibraryResponse, type ModDetails, type ModEntry } from "./api";
import { TranslationSection } from "./Translation";

interface Props {
  packageId?: string;
  marks: Record<string, string[]>;
  mods: ModEntry[];
  onSelect: (packageId: string) => void;
  onEdit: (edit: Promise<LibraryResponse>) => Promise<boolean>;
}

export function Details({ packageId, marks, mods, onSelect, onEdit }: Props) {
  const [mod, setMod] = useState<ModDetails | null>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    setMod(undefined);
    setError(undefined);
    if (packageId) fetchMod(packageId).then((m) => setMod(m ?? null), (e: Error) => setError(e.message));
  }, [packageId]);

  if (!packageId) return <p className="muted">Select a mod.</p>;
  if (error) return <p className="error">{error}</p>;
  if (mod === undefined) return <p className="muted">Loading…</p>;
  if (mod === null) return <p className="muted"><code>{packageId}</code> is not on disk.</p>;

  const a = mod.about;
  return (
    <article>
      {mod.preview ? <img className="preview" src={previewUrl(a.packageId)} alt="" /> : <div className="preview none">no preview</div>}
      <h2>{a.name}</h2>
      <dl>
        <dt>packageId</dt>
        <dd><code>{a.packageIdOriginal}</code></dd>
        {a.modVersion && (<><dt>Version</dt><dd>{a.modVersion}</dd></>)}
        <dt>Game</dt>
        <dd>{a.supportedVersions.join(", ") || "—"}</dd>
        <dt>Authors</dt>
        <dd>{a.authors.join(", ") || "—"}</dd>
        {mod.workshopId && (
          <>
            <dt>Workshop</dt>
            <dd>
              <a href={`https://steamcommunity.com/sharedfiles/filedetails/?id=${mod.workshopId}`} target="_blank" rel="noreferrer">
                {mod.workshopId}
              </a>{" "}
              · <a href={`steam://url/CommunityFilePage/${mod.workshopId}`}>open in Steam</a>
            </dd>
          </>
        )}
        {a.dependencies.length > 0 && (
          <>
            <dt>Requires</dt>
            <dd>
              {a.dependencies.map((d, i) => (
                <span key={d.packageId}>
                  {i > 0 && ", "}
                  <a href="#" onClick={(e) => { e.preventDefault(); onSelect(d.packageId); }}>
                    {d.displayName ?? d.packageId}
                  </a>
                </span>
              ))}
            </dd>
          </>
        )}
      </dl>
      <TranslationSection packageId={a.packageId} marks={marks} mods={mods} onSelect={onSelect} onEdit={onEdit} />
      <pre className="description">{a.description}</pre>
    </article>
  );
}
