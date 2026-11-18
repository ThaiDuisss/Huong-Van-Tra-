import { useEffect, useRef, useState, type CSSProperties } from "react";
import { createPortal } from "react-dom";
import { Bell } from "lucide-react";

import { IconButton } from "../../../components/ui/Button";
import { useNotifications } from "../context/useNotifications";
import { NotificationPanel } from "./NotificationPanel";

/** Khoảng cách giữa nút / mép sidebar / mép màn hình và khung thông báo. */
const PANEL_GAP_PX = 12;

interface NotificationButtonProps {
    /**
     * `pos`: chuông ở đáy sidebar POS, khung mở bên phải sidebar từ mép trên xuống.
     * `topbar`: chuông ở topbar trang quản lý, khung thả xuống ngay dưới nút, canh phải.
     */
    placement: "pos" | "topbar";
}

function badgeText(count: number) {
    return count > 9 ? "9+" : String(count);
}

export function NotificationButton({ placement }: NotificationButtonProps) {
    const [open, setOpen] = useState(false);
    const [panelStyle, setPanelStyle] = useState<CSSProperties>({});
    const buttonRef = useRef<HTMLButtonElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);
    const {
        notifications,
        unreadCount,
        connected,
        markRead,
        markAllRead
    } = useNotifications();

    useEffect(() => {
        if (!open) {
            return undefined;
        }

        // Khung render qua portal: không bị overflow-hidden của sidebar/topbar cắt mất.
        const placePanel = () => {
            const button = buttonRef.current;

            if (!button) {
                return;
            }

            if (placement === "pos") {
                const sidebar = button.closest("nav") ?? button;
                setPanelStyle({
                    left: sidebar.getBoundingClientRect().right + PANEL_GAP_PX,
                    top: PANEL_GAP_PX,
                    maxHeight: `calc(100dvh - ${PANEL_GAP_PX * 2}px)`
                });
                return;
            }

            const rect = button.getBoundingClientRect();
            setPanelStyle({
                right: Math.max(PANEL_GAP_PX, window.innerWidth - rect.right),
                top: rect.bottom + 8,
                maxHeight: `calc(100dvh - ${rect.bottom + 8 + PANEL_GAP_PX}px)`
            });
        };
        const closeOnOutsideClick = (event: PointerEvent) => {
            const target = event.target as Node;
            if (!panelRef.current?.contains(target) && !buttonRef.current?.contains(target)) {
                setOpen(false);
            }
        };
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setOpen(false);
            }
        };

        placePanel();
        window.addEventListener("resize", placePanel);
        document.addEventListener("pointerdown", closeOnOutsideClick);
        document.addEventListener("keydown", closeOnEscape);

        return () => {
            window.removeEventListener("resize", placePanel);
            document.removeEventListener("pointerdown", closeOnOutsideClick);
            document.removeEventListener("keydown", closeOnEscape);
        };
    }, [open, placement]);

    const toggle = () => setOpen((current) => !current);

    return (
        <div className="relative">
            {placement === "pos" ? (
                <button
                    ref={buttonRef}
                    type="button"
                    className="relative grid size-9 place-items-center rounded-lg text-[#159734] transition hover:bg-[#f4f5f3] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600"
                    aria-label="Thông báo"
                    aria-expanded={open}
                    title="Thông báo"
                    onClick={toggle}
                >
                    <Bell size={21} strokeWidth={1.9} aria-hidden="true" />
                    {unreadCount > 0 ? (
                        <span className="absolute -right-1 -top-1 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[9px] font-black leading-4 text-white">
                            {badgeText(unreadCount)}
                        </span>
                    ) : null}
                </button>
            ) : (
                <IconButton
                    ref={buttonRef}
                    aria-label="Thông báo"
                    aria-expanded={open}
                    className="relative"
                    size="md"
                    title="Thông báo"
                    variant="secondary"
                    onClick={toggle}
                >
                    <Bell size={17} aria-hidden="true" />
                    {unreadCount > 0 ? (
                        <span className="absolute -right-1.5 -top-1.5 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[9px] font-black leading-4 text-white">
                            {badgeText(unreadCount)}
                        </span>
                    ) : null}
                </IconButton>
            )}

            {open
                ? createPortal(
                    <div
                        ref={panelRef}
                        className="fixed z-50 flex max-w-[calc(100vw-24px)] flex-col"
                        style={panelStyle}
                    >
                        <NotificationPanel
                            notifications={notifications}
                            unreadCount={unreadCount}
                            connected={connected}
                            onMarkRead={(id) => {
                                void markRead(id);
                            }}
                            onMarkAllRead={() => {
                                void markAllRead();
                            }}
                        />
                    </div>,
                    document.body
                )
                : null}
        </div>
    );
}
