import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

/** A modal panel over a dimmed page. Esc and a click outside call [onClose] unless [locked]. */
export function Dialog({ title, onClose, locked = false, children }: { title: ReactNode; onClose: () => void; locked?: boolean; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !locked) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [locked, onClose]);

  return (
    <div className="overlay" onClick={() => !locked && onClose()}>
      <div className="dialog" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export interface ConfirmOptions {
  title: string;
  message: ReactNode;
  /** Label of the confirming button, e.g. "Delete". */
  confirm: string;
  /** Styles the confirming button as destructive. */
  danger?: boolean;
}

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm>(() => Promise.resolve(false));

/** `const confirm = useConfirm(); if (await confirm({...})) …` — the app's replacement for window.confirm. */
export const useConfirm = () => useContext(ConfirmContext);

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<(ok: boolean) => void>();

  const confirm = useCallback<Confirm>(
    (options) =>
      new Promise((resolve) => {
        resolver.current?.(false);
        resolver.current = resolve;
        setOpen(options);
      }),
    [],
  );

  const answer = useCallback((ok: boolean) => {
    resolver.current?.(ok);
    resolver.current = undefined;
    setOpen(null);
  }, []);
  const cancel = useCallback(() => answer(false), [answer]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {open && (
        <Dialog title={open.title} onClose={cancel}>
          <div>{open.message}</div>
          <div className="buttons">
            <button onClick={cancel}>Cancel</button>
            <button className={open.danger ? "danger" : "primary"} autoFocus onClick={() => answer(true)}>
              {open.confirm}
            </button>
          </div>
        </Dialog>
      )}
    </ConfirmContext.Provider>
  );
}
