/** Game language folder names → ISO country codes for flag emoji. */
const COUNTRY: Record<string, string> = {
  Russian: "RU",
  Ukrainian: "UA",
  English: "GB",
  German: "DE",
  French: "FR",
  Spanish: "ES",
  SpanishLatin: "MX",
  Portuguese: "PT",
  PortugueseBrazilian: "BR",
  Italian: "IT",
  Polish: "PL",
  Czech: "CZ",
  Slovak: "SK",
  Slovenian: "SI",
  Hungarian: "HU",
  Romanian: "RO",
  Dutch: "NL",
  Swedish: "SE",
  Norwegian: "NO",
  Danish: "DK",
  Finnish: "FI",
  Estonian: "EE",
  Greek: "GR",
  Turkish: "TR",
  ChineseSimplified: "CN",
  ChineseTraditional: "TW",
  Japanese: "JP",
  Korean: "KR",
  Vietnamese: "VN",
  Thai: "TH",
};

const flag = (country: string) => String.fromCodePoint(...[...country].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));

/** English first, Russian second, the rest alphabetically. */
const FIRST = ["English", "Russian"];

/** Without "show all languages" only these are shown: many mods ship a dozen languages out of the box. */
export const visibleLanguages = (languages: string[], all: boolean) => (all ? languages : languages.filter((l) => FIRST.includes(l)));
export function sortLanguages(languages: string[]): string[] {
  const rank = (l: string) => (FIRST.includes(l) ? FIRST.indexOf(l) : FIRST.length);
  return [...new Set(languages)].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

/** Flag emoji for the languages, each with its own tooltip; an unknown language shows as its first two letters. */
export function Flags({ languages, prefix }: { languages: string[]; prefix?: string }) {
  if (languages.length === 0) return null;
  return (
    <span className="flags">
      {prefix}
      {sortLanguages(languages).map((l) =>
        COUNTRY[l] ? (
          <span key={l} title={l}>
            {flag(COUNTRY[l])}
          </span>
        ) : (
          <span key={l} title={l} className="lang-code">
            {l.slice(0, 2).toUpperCase()}
          </span>
        ),
      )}
    </span>
  );
}
