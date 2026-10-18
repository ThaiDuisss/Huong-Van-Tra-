export interface NotificationResponse {
    id: string;
    type: string;
    title: string;
    message: string;
    orderId?: string | null;
    orderCode?: string | null;
    returnId?: string | null;
    returnCode?: string | null;
    createdAt: string;
    readAt?: string | null;
    status: string;
}

export interface NotificationPageResponse {
    content: NotificationResponse[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface UnreadCountResponse {
    count: number;
}
