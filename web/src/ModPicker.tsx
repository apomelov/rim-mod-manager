import { useState } from "react";
import type { ModEntry } from "./api";

interface Props {
  mods: ModEntry[];
  exclude: Set<string>;
  onPick: (packageId: string) => void;
  onCancel: () => void;
}

/** Type-to-search list of mods. */
export function ModPicker({ mods, exclude, onPick, onCancel }: Props) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const matches = q
    ? mods
        .filter((m) => !exclude.has(m.packageId))
        .filter((m) => m.name.toLowerCase().includes(q) || m.packageId.includes(q))
        .sort((a, b) => a.name.localeCompare(b.name))
        .slice(0, 12)
    : [];
  return (
    <div className="picker">
      <input
        autoFocus
        placeholder="Search mods…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Escape") onCancel();
          if (e.key === "Enter" && matches.length > 0) onPick(matches[0].packageId);
        }}
      />
      <ul>
        {matches.map((m) => (
          <li key={m.packageId} onClick={() => onPick(m.packageId)} title={m.packageId}>
            {m.name}
          </li>
        ))}
      </ul>
      <button onClick={onCancel}>Cancel</button>
    </div>
  );
}
