import { useState } from "react";
import { libraryEdits, type LibraryResponse, type ModEntry } from "./api";
import { ModPicker } from "./ModPicker";

interface Props {
  packageId: string;
  marks: Record<string, string[]>;
  mods: ModEntry[];
  onSelect: (packageId: string) => void;
  onEdit: (edit: Promise<LibraryResponse>) => Promise<boolean>;
}

/** Marking a mod as a translation, or listing its translations. */
export function TranslationSection({ packageId, marks, mods, onSelect, onEdit }: Props) {
  const [picking, setPicking] = useState(false);
  const names = new Map(mods.map((m) => [m.packageId, m.name]));
  const name = (id: string) => names.get(id) ?? id;
  const link = (id: string) => (
    <a href="#" onClick={(e) => { e.preventDefault(); onSelect(id); }}>{name(id)}</a>
  );

  const originals = marks[packageId];
  const translatedBy = Object.entries(marks).filter(([, o]) => o.includes(packageId)).map(([t]) => t);
  const translations = new Set(Object.keys(marks));

  const pick = async (original: string) => {
    if (await onEdit(libraryEdits.markTranslation(packageId, [...(originals ?? []), original]))) setPicking(false);
  };
  const picker = picking && (
    <ModPicker
      mods={mods}
      exclude={new Set([packageId, ...(originals ?? []), ...translations])}
      onPick={pick}
      onCancel={() => setPicking(false)}
    />
  );

  if (originals) {
    return (
      <section className="translation">
        <h3>Translation of</h3>
        <ul>
          {originals.length === 0 && <li className="muted">nothing yet</li>}
          {originals.map((o) => (
            <li key={o}>
              {link(o)}{" "}
              <button
                className="small"
                title="Remove"
                onClick={() => onEdit(libraryEdits.markTranslation(packageId, originals.filter((x) => x !== o)))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
        {picker || (
          <p>
            <button onClick={() => setPicking(true)}>Add original…</button>{" "}
            <button onClick={() => onEdit(libraryEdits.unmarkTranslation(packageId))}>Not a translation</button>
          </p>
        )}
      </section>
    );
  }

  return (
    <section className="translation">
      {translatedBy.length > 0 && (
        <>
          <h3>Translations</h3>
          <ul>
            {translatedBy.map((t) => (
              <li key={t}>{link(t)}</li>
            ))}
          </ul>
        </>
      )}
      {picker ||
        (translatedBy.length === 0 && (
          <p>
            <button onClick={() => setPicking(true)}>Mark as translation of…</button>
          </p>
        ))}
    </section>
  );
}
