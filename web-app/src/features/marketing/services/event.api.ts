import { apiClient } from "../../../api/api-client";
import type {
    EventCreateRequest,
    EventExtendRequest,
    EventQuery,
    EventResponse,
    EventUpdateRequest,
    PageResponse
} from "../types/event.types";

const EVENT_API_BASE = "/promotions/events";

export const eventApi = {
    list(query: EventQuery = {}) {
        return apiClient.get<PageResponse<EventResponse>>(
            EVENT_API_BASE,
            {
                query
            }
        );
    },

    active(query: EventQuery = {}) {
        return apiClient.get<PageResponse<EventResponse>>(
            `${EVENT_API_BASE}/active`,
            {
                query
            }
        );
    },

    detail(id: string) {
        return apiClient.get<EventResponse>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}`
        );
    },

    create(input: EventCreateRequest) {
        return apiClient.post<EventResponse, EventCreateRequest>(
            EVENT_API_BASE,
            input
        );
    },

    update(id: string, input: EventUpdateRequest) {
        return apiClient.put<EventResponse, EventUpdateRequest>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}`,
            input
        );
    },

    remove(id: string) {
        return apiClient.delete<void>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}`
        );
    },

    eventTypes() {
        return apiClient.get<string[]>(
            `${EVENT_API_BASE}/event-types`
        );
    },

    eventAudiences() {
        return apiClient.get<string[]>(
            `${EVENT_API_BASE}/event-audiences`
        );
    },

    eventStatuses() {
        return apiClient.get<string[]>(
            `${EVENT_API_BASE}/event-statuses`
        );
    },

    schedule(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/schedule`,
            {}
        );
    },

    activate(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/activate`,
            {}
        );
    },

    pause(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/pause`,
            {}
        );
    },

    resume(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/resume`,
            {}
        );
    },

    cancel(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/cancel`,
            {}
        );
    },

    end(id: string) {
        return apiClient.post<EventResponse, Record<string, never>>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/end`,
            {}
        );
    },

    extend(id: string, input: EventExtendRequest) {
        return apiClient.patch<EventResponse, EventExtendRequest>(
            `${EVENT_API_BASE}/${encodeURIComponent(id)}/extend`,
            input
        );
    }
};