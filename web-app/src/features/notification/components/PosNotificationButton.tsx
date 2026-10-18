import { useState } from "react";
import { Bell } from "lucide-react";

import { useNotifications } from "../context/useNotifications";
import { NotificationPanel } from "./NotificationPanel";

export function PosNotificationButton() {
    const [open, setOpen] = useState(false);
    const {
        notifications,
        unreadCount,
        connected,
        markRead,
        markAllRead
    } = useNotifications();

    return (
        <div className="relative">
            <button
                type="button"
                className="relative grid size-9 place-items-center rounded-lg text-[#159734] transition hover:bg-[#f4f5f3] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-600"
                aria-label="Thông báo"
                title="Thông báo"
                onClick={() => setOpen((current) => !current)}
            >
                <Bell size={21} strokeWidth={1.9} aria-hidden="true" />
                {unreadCount > 0 ? (
                    <span className="absolute -right-1 -top-1 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[9px] font-black leading-4 text-white">
                        {unreadCount > 9 ? "9+" : unreadCount}
                    </span>
                ) : null}
            </button>

            {open ? (
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
            ) : null}
        </div>
    );
}
