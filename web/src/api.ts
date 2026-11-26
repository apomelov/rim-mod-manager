export interface ModEntry {
  packageId: string;
  name: string;
  missing?: boolean;
  hidden?: boolean;
  translations?: ModEntry[];
  hasIcon?: boolean;
}

export interface FolderView {
  name: string;
  expanded?: boolean;
  folders?: FolderView[];
  mods?: ModEntry[];
}

export interface LibraryView {
  sortout: ModEntry[];
  folders: FolderView[];
}

export interface LibraryResponse {
  gameVersion: string;
  library: LibraryView;
  /** translation packageId → packageIds it translates */
  translations: Record<string, string[]>;
  problems: string[];
}

export interface Dependency {
  packageId: string;
  displayName?: string;
  workshopId?: string;
  alternatives?: string[];
}

export interface ModDetails {
  about: {
    packageId: string;
    packageIdOriginal: string;
    name: string;
    authors: string[];
    description: string;
    supportedVersions: string[];
    dependencies: Dependency[];
    url?: string;
    modVersion?: string;
  };
  workshopId?: string;
  folder: string;
  preview?: string;
  icon?: string;
}

export const iconUrl = (packageId: string) => `/api/mods/${encodeURIComponent(packageId)}/icon`;
export const previewUrl = (packageId: string) => `/api/mods/${encodeURIComponent(packageId)}/preview`;

async function errorOf(res: Response, what: string): Promise<Error> {
  const body = await res.json().catch(() => undefined);
  return new Error(body?.error ?? `${what}: ${res.status}`);
}

export async function fetchLibrary(): Promise<LibraryResponse> {
  const res = await fetch("/api/library");
  if (!res.ok) throw await errorOf(res, "GET /api/library");
  return res.json();
}

/** Resolves to undefined when the mod is not on disk. */
export async function fetchMod(packageId: string): Promise<ModDetails | undefined> {
  const res = await fetch(`/api/mods/${encodeURIComponent(packageId)}`);
  if (res.status === 404) return undefined;
  if (!res.ok) throw await errorOf(res, `GET /api/mods/${packageId}`);
  return res.json();
}

async function edit(command: string, body: object): Promise<LibraryResponse> {
  const res = await fetch(`/api/library/${command}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw await errorOf(res, command);
  return res.json();
}

/** Folder paths look like `QoL/base`; the root is "". `to: null` means Sortout. */
export const libraryEdits = {
  moveMods: (mods: string[], to: string | null, before: string | null) => edit("move-mods", { mods, to, before }),
  moveFolder: (from: string, toParent: string, before: string | null) => edit("move-folder", { from, toParent, before }),
  createFolder: (parent: string, name: string) => edit("create-folder", { parent, name }),
  renameFolder: (path: string, name: string) => edit("rename-folder", { path, name }),
  deleteFolder: (path: string) => edit("delete-folder", { path }),
  setExpanded: (path: string, expanded: boolean) => edit("expanded", { path, expanded }),
  markTranslation: (translation: string, originals: string[]) => edit("mark-translation", { translation, originals }),
  unmarkTranslation: (translation: string) => edit("unmark-translation", { translation }),
};
