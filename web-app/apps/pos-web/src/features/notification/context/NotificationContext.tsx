import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type ReactNode
} from "react";
import {
    EventStreamContentType,
    fetchEventSource
} from "@microsoft/fetch-event-source";

import { keycloak } from "../../../auth/keycloak";
import { getQueuedAccessToken } from "../../../auth/token-provider";
import { notificationApi } from "../services/notification.api";
import type { NotificationResponse } from "../types/notification.types";
import {
    NotificationContext,
    type NotificationContextValue
} from "./notification-context";

const apiBaseUrl =
    import.meta.env.VITE_API_BASE_URL ??
    "http://localhost:8080/api";

function mergeNewest(
    current: NotificationResponse[],
    incoming: NotificationResponse
) {
    const withoutDuplicate = current.filter(
        (item) => item.id !== incoming.id
    );

    return [incoming, ...withoutDuplicate]
        .sort((left, right) =>
            Date.parse(right.createdAt) - Date.parse(left.createdAt)
        )
        .slice(0, 30);
}

async function fetchNotificationStream(
    input: RequestInfo | URL,
    init?: RequestInit
) {
    const headers = new Headers(init?.headers);

    headers.set("Accept", EventStreamContentType);
    headers.set("X-Client-Type", "POS_WEB");

    if (keycloak.authenticated) {
        const accessToken = await getQueuedAccessToken(30);

        if (accessToken) {
            headers.set("Authorization", `Bearer ${accessToken}`);
        }
    }

    return fetch(input, {
        ...init,
        headers
    });
}

export function NotificationProvider({
    children
}: {
    children: ReactNode;
}) {
    const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [connected, setConnected] = useState(false);
    const streamControllerRef = useRef<AbortController | null>(null);

    const refresh = useCallback(async () => {
        const [page, count] = await Promise.all([
            notificationApi.list({
                page: 0,
                size: 20
            }),
            notificationApi.unreadCount()
        ]);

        setNotifications(page.content ?? []);
        setUnreadCount(count.count ?? 0);
    }, []);

    const markRead = useCallback(async (id: string) => {
        const updated = await notificationApi.markRead(id);
        setNotifications((current) =>
            current.map((item) => item.id === id ? updated : item)
        );
        setUnreadCount((current) => Math.max(0, current - 1));
    }, []);

    const markAllRead = useCallback(async () => {
        await notificationApi.markAllRead();
        setNotifications((current) =>
            current.map((item) => ({
                ...item,
                status: "READ",
                readAt: item.readAt ?? new Date().toISOString()
            }))
        );
        setUnreadCount(0);
    }, []);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            void refresh().catch((error) => {
                console.error("Không tải được thông báo", error);
            });
        }, 0);

        return () => window.clearTimeout(timeoutId);
    }, [refresh]);

    useEffect(() => {
        const streamController = new AbortController();
        const url = `${apiBaseUrl}/notifications/stream`;

        streamControllerRef.current = streamController;

        void fetchEventSource(url, {
            signal: streamController.signal,
            fetch: fetchNotificationStream,
            headers: {
                Accept: EventStreamContentType,
                "X-Client-Type": "POS_WEB"
            },
            onmessage(event) {
                if (event.event === "connected") {
                    setConnected(true);
                    return;
                }

                if (event.event !== "notification") {
                    return;
                }

                try {
                    const notification = JSON.parse(event.data) as NotificationResponse;
                    setNotifications((current) => mergeNewest(current, notification));
                    if (notification.status !== "READ") {
                        setUnreadCount((current) => current + 1);
                    }
                    window.dispatchEvent(new CustomEvent("hvt:notification", {
                        detail: notification
                    }));
                } catch (error) {
                    console.error("Không parse được notification SSE", error);
                }
            },
            onclose() {
                setConnected(false);
            },
            onerror(error) {
                setConnected(false);
                console.error("Kết nối thông báo bị gián đoạn", error);
            }
        }).catch((error) => {
            if (!streamController.signal.aborted) {
                console.error("Không kết nối được luồng thông báo", error);
            }
        });

        return () => {
            streamController.abort();
            streamControllerRef.current = null;
            setConnected(false);
        };
    }, []);

    const value = useMemo<NotificationContextValue>(() => ({
        notifications,
        unreadCount,
        connected,
        refresh,
        markRead,
        markAllRead
    }), [
        connected,
        markAllRead,
        markRead,
        notifications,
        refresh,
        unreadCount
    ]);

    return (
        <NotificationContext.Provider value={value}>
            {children}
        </NotificationContext.Provider>
    );
}
