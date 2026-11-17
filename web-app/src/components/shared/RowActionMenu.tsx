import { MoreHorizontal } from "lucide-react";
import { type ReactNode, useEffect, useId, useLayoutEffect, useRef } from "react";
import { createPortal } from "react-dom";

import styles from "./RowActionMenu.module.css";

export interface RowAction {
  label: string;
  onSelect: () => void;
  disabled?: boolean;
  /** Icon trước nhãn. */
  icon?: ReactNode;
  /** `danger`: chữ đỏ (xóa, hủy, khóa). */
  tone?: "default" | "danger";
}

interface RowActionMenuProps {
  actions: readonly RowAction[];
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  triggerLabel: string;
  menuLabel: string;
}

export function RowActionMenu({
  actions,
  isOpen,
  onOpenChange,
  triggerLabel,
  menuLabel,
}: RowActionMenuProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const focusLastItemRef = useRef(false);
  const menuId = useId();

  useLayoutEffect(() => {
    const menu = menuRef.current;
    const trigger = triggerRef.current;
    if (!isOpen || !menu || !trigger) return;
    const rect = trigger.getBoundingClientRect();
    const margin = 8;
    menu.style.maxHeight = `${Math.max(0, window.innerHeight - margin * 2)}px`;
    const menuRect = menu.getBoundingClientRect();
    const top =
      window.innerHeight - rect.bottom >= menuRect.height + 4
        ? rect.bottom + 4
        : rect.top - menuRect.height - 4;
    menu.style.left = `${Math.max(margin, Math.min(rect.left, window.innerWidth - menuRect.width - margin))}px`;
    menu.style.top = `${Math.max(margin, Math.min(top, window.innerHeight - menuRect.height - margin))}px`;
    if (!menu.contains(document.activeElement)) {
      const items = menu.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
      items[focusLastItemRef.current ? items.length - 1 : 0]?.focus();
    }
  }, [isOpen, actions.length]);

  useEffect(() => {
    if (!isOpen) return;
    const closeOutside = (event: Event) => {
      const target = event.target as Node;
      if (!triggerRef.current?.contains(target) && !menuRef.current?.contains(target))
        onOpenChange(false);
    };
    const closeOnViewportChange = () => {
      if (menuRef.current?.contains(document.activeElement))
        triggerRef.current?.focus({ preventScroll: true });
      onOpenChange(false);
    };
    const closeOnScroll = (event: Event) => {
      if (!menuRef.current?.contains(event.target as Node)) closeOnViewportChange();
    };
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("focusin", closeOutside);
    window.addEventListener("resize", closeOnViewportChange);
    window.addEventListener("scroll", closeOnScroll, true);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("focusin", closeOutside);
      window.removeEventListener("resize", closeOnViewportChange);
      window.removeEventListener("scroll", closeOnScroll, true);
    };
  }, [isOpen, onOpenChange]);

  const closeMenu = () => {
    triggerRef.current?.focus({ preventScroll: true });
    onOpenChange(false);
  };

  return (
    <div className={styles.triggerWrap}>
      <button
        aria-controls={isOpen ? menuId : undefined}
        aria-expanded={isOpen}
        aria-haspopup="menu"
        aria-label={triggerLabel}
        className={styles.trigger}
        onClick={(event) => {
          event.stopPropagation();
          focusLastItemRef.current = false;
          onOpenChange(!isOpen);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            focusLastItemRef.current = event.key === "ArrowUp";
            onOpenChange(true);
          }
        }}
        ref={triggerRef}
        type="button"
      >
        <MoreHorizontal aria-hidden="true" size={18} />
      </button>
      {isOpen
        ? createPortal(
            <div
              aria-label={menuLabel}
              className={styles.menu}
              id={menuId}
              onKeyDown={(event) => {
                const items = Array.from(
                  event.currentTarget.querySelectorAll<HTMLButtonElement>("button:not(:disabled)"),
                );
                const index = items.indexOf(document.activeElement as HTMLButtonElement);
                if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
                  event.preventDefault();
                  const next =
                    event.key === "Home"
                      ? 0
                      : event.key === "End"
                        ? items.length - 1
                        : (index + (event.key === "ArrowDown" ? 1 : -1) + items.length) %
                          items.length;
                  items[next]?.focus();
                } else if (event.key === "Escape" || event.key === "Tab") {
                  if (event.key === "Escape") event.preventDefault();
                  event.stopPropagation();
                  closeMenu();
                }
              }}
              ref={menuRef}
              role="menu"
            >
              {actions.map((action) => (
                <button
                  className={action.tone === "danger" ? styles.danger : undefined}
                  disabled={action.disabled}
                  key={action.label}
                  onClick={() => {
                    closeMenu();
                    action.onSelect();
                  }}
                  role="menuitem"
                  tabIndex={-1}
                  type="button"
                >
                  {action.icon ? (
                    <span aria-hidden="true" className={styles.icon}>
                      {action.icon}
                    </span>
                  ) : null}
                  {action.label}
                </button>
              ))}
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}
