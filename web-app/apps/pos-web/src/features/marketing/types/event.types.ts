export type EventTypeValue = string;

export type EventAudienceValue = string;

export type EventStatusValue = string;

export interface EventResponse {
    id: string;
    name: string;
    description?: string;
    startAt: string;
    endAt: string;
    eventType: EventTypeValue;
    audience: EventAudienceValue;
    status: EventStatusValue;
    isDeleted?: boolean;
    createdBy?: string;
    updatedBy?: string;
    createdAt?: string;
    updatedAt?: string;
    version?: number;
}

export interface EventCreateRequest {
    name: string;
    description?: string;
    startAt: string;
    endAt: string;
    eventType: EventTypeValue;
    audience: EventAudienceValue;
}

export interface EventUpdateRequest {
    name: string;
    description?: string;
    startAt: string;
    endAt: string;
    eventType: EventTypeValue;
    audience: EventAudienceValue;
}

export interface EventExtendRequest {
    endAt: string;
}

export interface EventQuery {
    [key: string]: string | number | boolean | undefined;
    page?: number;
    size?: number;
    keyword?: string;
    status?: EventStatusValue;
    eventType?: EventTypeValue;
    audience?: EventAudienceValue;
    startFrom?: string;
    startTo?: string;
    endFrom?: string;
    endTo?: string;
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}
