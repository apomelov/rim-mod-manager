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
  /** Core and the DLCs found in the game folder, in release order. */
  game?: ModEntry[];
}

export interface LibraryResponse {
  gameVersion: string;
  library: LibraryView;
  /** translation packageId → packageIds it translates */
  translations: Record<string, string[]>;
  problems: Problem[];
  builds: Build[];
  /** The open tab; absent when there are no builds. */
  currentBuild?: string;
}

export interface Build {
  name: string;
  /** Explicitly selected packageIds. */
  mods: string[];
}

export interface Problem {
  message: string;
  /** Set when the app may delete this mods-folder entry (only .dds textures left): its folder name. */
  removableFolder?: string;
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
  return post(`/api/library/${command}`, body);
}

async function post(url: string, body: object): Promise<LibraryResponse> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw await errorOf(res, url);
  return res.json();
}

/** Deletes a leftover folder (only .dds textures) from the mods folder; the server re-checks it first. */
export const deleteLeftover = (folder: string) => post("/api/leftovers/delete", { folder });

/** Folder paths look like `QoL/base`; the root is "". `to: null` means Sortout. */
export const libraryEdits = {
  moveMods: (mods: string[], to: string | null) => edit("move-mods", { mods, to }),
  moveFolder: (from: string, toParent: string) => edit("move-folder", { from, toParent }),
  createFolder: (parent: string, name: string) => edit("create-folder", { parent, name }),
  renameFolder: (path: string, name: string) => edit("rename-folder", { path, name }),
  deleteFolder: (path: string) => edit("delete-folder", { path }),
  setExpanded: (path: string, expanded: boolean) => edit("expanded", { path, expanded }),
  markTranslation: (translation: string, originals: string[]) => edit("mark-translation", { translation, originals }),
  unmarkTranslation: (translation: string) => edit("unmark-translation", { translation }),
};

export const buildEdits = {
  create: (name: string) => post("/api/builds/create", { name }),
  rename: (name: string, newName: string) => post("/api/builds/rename", { name, newName }),
  duplicate: (name: string) => post("/api/builds/duplicate", { name }),
  remove: (name: string) => post("/api/builds/delete", { name }),
  setCurrent: (name: string) => post("/api/builds/current", { name }),
  select: (build: string, mods: string[], selected: boolean) => post("/api/builds/select", { build, mods, selected }),
};

export interface ActivationPlan {
  /** The sorted list to write; empty when blocked. */
  order: string[];
  selected: string[];
  dependencies: string[];
  translations: string[];
  dlcs: string[];
  missingSelected: string[];
  missingDependencies: { dependency: Dependency; requiredBy: string[] }[];
  cycle: string[];
}

export interface ActivationPreview {
  build: string;
  plan: ActivationPlan;
  blocked: boolean;
}

export interface ActivationResult {
  build: string;
  written: number;
  file: string;
  backup: string;
}

export async function fetchActivation(build: string): Promise<ActivationPreview> {
  const res = await fetch(`/api/builds/activation?build=${encodeURIComponent(build)}`);
  if (!res.ok) throw await errorOf(res, "activation preview");
  return res.json();
}

export async function activate(build: string): Promise<ActivationResult> {
  const res = await fetch("/api/builds/activate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ build }),
  });
  if (!res.ok) throw await errorOf(res, "activate");
  return res.json();
}
