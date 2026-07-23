import { CheckCheck, Wifi, WifiOff } from "lucide-react";

import type { NotificationResponse } from "../types/notification.types";

interface NotificationPanelProps {
    notifications: NotificationResponse[];
    unreadCount: number;
    connected: boolean;
    onMarkRead(id: string): void;
    onMarkAllRead(): void;
}

function formatTime(value: string) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    return new Intl.DateTimeFormat("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        day: "2-digit",
        month: "2-digit"
    }).format(date);
}

export function NotificationPanel({
    notifications,
    unreadCount,
    connected,
    onMarkRead,
    onMarkAllRead
}: NotificationPanelProps) {
    return (
        <section className="absolute bottom-12 left-0 z-50 w-[340px] rounded-xl border border-[#dfe7e2] bg-white p-3 text-[#172317] shadow-[0_22px_64px_rgba(15,35,20,0.20)]">
            <header className="flex items-center justify-between gap-3 border-b border-[#edf0f4] pb-3">
                <div>
                    <h2 className="text-[14px] font-extrabold">Thông báo</h2>
                    <p className="mt-0.5 flex items-center gap-1 text-[11px] font-semibold text-[#5f6b7a]">
                        {connected ? (
                            <Wifi size={13} className="text-[#159734]" />
                        ) : (
                            <WifiOff size={13} className="text-amber-600" />
                        )}
                        {connected ? "Realtime đang bật" : "Đang kết nối lại"}
                    </p>
                </div>

                <button
                    type="button"
                    onClick={onMarkAllRead}
                    disabled={unreadCount === 0}
                    className="inline-flex h-8 items-center gap-1 rounded-lg border border-[#d8eadc] px-2 text-[11px] font-bold text-[#159734] disabled:cursor-not-allowed disabled:opacity-40"
                >
                    <CheckCheck size={14} aria-hidden="true" />
                    Đọc hết
                </button>
            </header>

            <div className="mt-3 grid max-h-[420px] gap-2 overflow-y-auto pr-1">
                {notifications.length === 0 ? (
                    <p className="rounded-lg bg-[#f7fff9] px-3 py-5 text-center text-[12px] font-semibold text-[#5f6b7a]">
                        Chưa có thông báo mới.
                    </p>
                ) : notifications.map((notification) => {
                    const unread = notification.status !== "READ";

                    return (
                        <button
                            key={notification.id}
                            type="button"
                            onClick={() => {
                                if (unread) {
                                    onMarkRead(notification.id);
                                }
                            }}
                            className={`grid gap-1 rounded-lg border px-3 py-2 text-left transition ${
                                unread
                                    ? "border-[#bde9c6] bg-[#f3fff6]"
                                    : "border-[#edf0f4] bg-white hover:bg-[#f7f8fb]"
                            }`}
                        >
                            <div className="flex items-start justify-between gap-2">
                                <strong className="line-clamp-2 text-[12px] font-extrabold text-[#172317]">
                                    {notification.title}
                                </strong>
                                <span className="shrink-0 text-[10px] font-semibold text-[#7a8492]">
                                    {formatTime(notification.createdAt)}
                                </span>
                            </div>
                            <p className="line-clamp-2 text-[11px] font-medium leading-snug text-[#5f6b7a]">
                                {notification.message}
                            </p>
                            {notification.orderCode ? (
                                <span className="text-[10px] font-bold text-[#159734]">
                                    Đơn {notification.orderCode}
                                </span>
                            ) : null}
                        </button>
                    );
                })}
            </div>
        </section>
    );
}
