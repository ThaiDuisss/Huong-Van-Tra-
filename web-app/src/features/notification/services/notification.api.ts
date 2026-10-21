import { apiClient } from "../../../api/api-client";
import type {
    NotificationPageResponse,
    NotificationResponse,
    UnreadCountResponse
} from "../types/notification.types";

interface NotificationQuery {
    page?: number;
    size?: number;
}

export const notificationApi = {
    list(query: NotificationQuery = {}) {
        const params: Record<string, string | number> = {
            page: query.page ?? 0,
            size: query.size ?? 20
        };

        return apiClient.get<NotificationPageResponse>("/notifications", {
            query: params
        });
    },

    unreadCount() {
        return apiClient.get<UnreadCountResponse>(
            "/notifications/unread-count"
        );
    },

    markRead(id: string) {
        return apiClient.patch<NotificationResponse, Record<string, never>>(
            `/notifications/${encodeURIComponent(id)}/read`,
            {}
        );
    },

    markAllRead() {
        return apiClient.patch<{ updated: number }, Record<string, never>>(
            "/notifications/read-all",
            {}
        );
    }
};
