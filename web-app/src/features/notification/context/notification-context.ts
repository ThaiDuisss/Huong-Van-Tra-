import { createContext } from "react";

import type { NotificationResponse } from "../types/notification.types";

export interface NotificationContextValue {
    notifications: NotificationResponse[];
    unreadCount: number;
    connected: boolean;
    refresh(): Promise<void>;
    markRead(id: string): Promise<void>;
    markAllRead(): Promise<void>;
}

export const NotificationContext =
    createContext<NotificationContextValue | null>(null);
