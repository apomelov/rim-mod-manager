import { useEffect, useMemo, useState } from "react";
import { fetchSuggestions, iconUrl, libraryEdits, type LibraryResponse, type ModEntry, type TranslationSuggestion } from "./api";
import { Flags } from "./flags";

interface Props {
  /** Current marks: translation → originals (a confirmed pair is added to these). */
  marks: Record<string, string[]>;
  entryOf: (packageId: string) => ModEntry | undefined;
  onSelect: (packageId: string) => void;
  onEdit: (edit: Promise<LibraryResponse>) => Promise<boolean>;
  onClose: () => void;
}

interface Row {
  key: string;
  translation: string;
  original?: string;
  translatedKeys: number;
  originalKeys: number;
  dependency: boolean;
  link: boolean;
}

function rows(suggestions: TranslationSuggestion[]): Row[] {
  return suggestions.flatMap((s): Row[] =>
    s.originals.length
      ? s.originals.map((o) => ({
          key: `${s.translation}>${o.packageId}`,
          translation: s.translation,
          original: o.packageId,
          translatedKeys: o.translatedKeys,
          originalKeys: o.originalKeys,
          dependency: o.dependency,
          link: o.link,
        }))
      : [{ key: s.translation, translation: s.translation, translatedKeys: 0, originalKeys: 0, dependency: false, link: false }],
  );
}

/** Suggested translation marks for Sortout (docs/translations.md): originals left, translations right; confirm or dismiss each. */
export function Suggestions({ marks, entryOf, onSelect, onEdit, onClose }: Props) {
  const [all, setAll] = useState<Row[]>();
  const [gone, setGone] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string>();

  useEffect(() => {
    fetchSuggestions().then((r) => setAll(rows(r.suggestions)), (e: Error) => setError(e.message));
  }, []);

  const shown = useMemo(() => (all ?? []).filter((r) => !gone.has(r.key)), [all, gone]);
  const hide = (r: Row) => setGone((g) => new Set(g).add(r.key));

  async function confirm(r: Row) {
    const originals = [...new Set([...(marks[r.translation] ?? []), r.original!])];
    if (await onEdit(libraryEdits.markTranslation(r.translation, originals))) hide(r);
  }

  const Mod = ({ id }: { id: string }) => {
    const e = entryOf(id);
    return (
      <span className="sugg-mod" onClick={() => onSelect(id)} title={id}>
        {e?.hasIcon ? <img className="icon" src={iconUrl(id)} alt="" loading="lazy" /> : <span className="icon" />}
        <span className="name">{e?.name ?? id}</span>
        <Flags languages={e?.languages ?? []} />
      </span>
    );
  };

  const matched = shown.filter((r) => r.original).length;
  return (
    <div className="suggestions">
      <div className="sugg-head">
        <h2>Translations found in Sortout</h2>
        <span className="muted">
          {all ? `${matched} pair(s), ${shown.length - matched} without an original` : error ? "" : "Searching… (reads every mod's files)"}
        </span>
        <button onClick={onClose}>Back to tree</button>
      </div>
      {error && <p className="error">{error}</p>}
      {all && shown.length === 0 && <p className="muted">Nothing left to review.</p>}
      <div className="sugg-table">
        {shown.map((r) => (
          <div key={r.key} className="sugg-row">
            <div>{r.original ? <Mod id={r.original} /> : <span className="muted">no original found — link it in the details panel</span>}</div>
            <div className="sugg-evidence muted">
              {r.original && (
                <>
                  <span title="Keys of the original this translation translates">
                    {r.translatedKeys}/{r.originalKeys} keys ({Math.round((100 * r.translatedKeys) / Math.max(r.originalKeys, 1))}%)
                  </span>
                  {r.dependency && " · dependency"}
                  {r.link && " · link"}
                </>
              )}
            </div>
            <div>
              <Mod id={r.translation} />
            </div>
            <div className="sugg-actions">
              <button title="Mark as translation" disabled={!r.original} onClick={() => confirm(r)}>
                ✓
              </button>
              <button title="Dismiss this suggestion" onClick={() => hide(r)}>
                ×
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
