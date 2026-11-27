import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";

/**
 * A context menu at the cursor, flipped up and/or left when it would not fit in the viewport.
 * Closes on any click (items run first), Esc, or scrolling anywhere.
 */
export function ContextMenu({ x, y, onClose, children }: { x: number; y: number; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLUListElement>(null);
  const [pos, setPos] = useState({ left: x, top: y });

  useLayoutEffect(() => {
    const r = ref.current!.getBoundingClientRect();
    setPos({
      left: x + r.width > window.innerWidth ? Math.max(0, x - r.width) : x,
      top: y + r.height > window.innerHeight ? Math.max(0, y - r.height) : y,
    });
  }, [x, y]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    // Scroll does not bubble; capture catches inner scroll containers too.
    window.addEventListener("click", onClose);
    window.addEventListener("scroll", onClose, true);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("click", onClose);
      window.removeEventListener("scroll", onClose, true);
      window.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  return (
    <ul className="menu" ref={ref} style={pos} onContextMenu={(e) => e.preventDefault()}>
      {children}
    </ul>
  );
}
