import type { EventResponse } from "../types/event.types";

export type EventStatus =
    | "DRAFT"
    | "SCHEDULED"
    | "ACTIVE"
    | "PAUSED"
    | "EXPIRED"
    | "ENDED"
    | "CANCELLED";

export type EventStatusActionType =
    | "schedule"
    | "activate"
    | "pause"
    | "resume"
    | "cancel"
    | "end";

export interface EventStatusAction {
    targetStatus: Exclude<EventStatus, "DRAFT">;
    action: EventStatusActionType;
    label: string;
    danger?: boolean;
    requiresConfirm?: boolean;
}

type EventLike = Pick<
    EventResponse,
    "status" | "startAt" | "endAt"
> | null | undefined;

export function toDate(
    value?: string | Date | null
): Date | null {
    if (!value) return null;

    const date =
        value instanceof Date ? value : new Date(value);

    return Number.isNaN(date.getTime()) ? null : date;
}

export function getAllowedEventTargetStatuses(
    event: EventLike,
    now = new Date()
): EventStatus[] {
    if (!event?.status) return [];

    const currentStatus = event.status as EventStatus;

    if (
        currentStatus === "ENDED" ||
        currentStatus === "CANCELLED"
    ) {
        return [];
    }

    const startAt = toDate(event.startAt);
    const endAt = toDate(event.endAt);
    const nowTime = now.getTime();

    const hasValidTimeRange =
        !startAt ||
        !endAt ||
        startAt.getTime() < endAt.getTime();

    const hasStarted =
        !startAt || startAt.getTime() <= nowTime;

    const startsInFuture =
        !!startAt && startAt.getTime() > nowTime;

    const hasNotEnded =
        !endAt || endAt.getTime() > nowTime;

    const hasReachedEndTime =
        !!endAt && endAt.getTime() <= nowTime;

    const canActivate =
        hasStarted && hasNotEnded && hasValidTimeRange;

    const targets: EventStatus[] = [];

    switch (currentStatus) {
        case "DRAFT":
            if (startsInFuture && hasValidTimeRange) {
                targets.push("SCHEDULED");
            }

            if (canActivate) {
                targets.push("ACTIVE");
            }

            targets.push("CANCELLED");
            break;

        case "SCHEDULED":
            if (canActivate) {
                targets.push("ACTIVE");
            }

            if (hasReachedEndTime && hasValidTimeRange) {
                targets.push("EXPIRED");
            }

            targets.push("CANCELLED");
            break;

        case "ACTIVE":
            targets.push("PAUSED", "ENDED", "CANCELLED");

            if (hasReachedEndTime && hasValidTimeRange) {
                targets.push("EXPIRED");
            }
            break;

        case "PAUSED":
            if (startsInFuture && hasNotEnded && hasValidTimeRange) {
                targets.push("SCHEDULED");
            }

            if (canActivate) {
                targets.push("ACTIVE");
            }

            if (hasReachedEndTime && hasValidTimeRange) {
                targets.push("EXPIRED");
            }

            targets.push("ENDED", "CANCELLED");
            break;

        case "EXPIRED":
            if (
                !!endAt &&
                endAt.getTime() > nowTime &&
                canActivate
            ) {
                targets.push("ACTIVE");
            }

            targets.push("ENDED");
            break;

        default:
            break;
    }

    return targets.filter(
        (targetStatus) =>
            targetStatus !== "DRAFT" &&
            targetStatus !== currentStatus
    );
}

export function canEditEvent(event: EventLike): boolean {
    if (!event?.status) return false;

    return (
        event.status === "DRAFT" ||
        event.status === "SCHEDULED" ||
        event.status === "PAUSED"
    );
}

export function canDeleteEvent(event: EventLike): boolean {
    if (!event?.status) return false;

    return (
        event.status === "DRAFT" ||
        event.status === "SCHEDULED"
    );
}

export function getAllowedEventStatusActions(
    event: EventLike,
    now = new Date()
): EventStatusAction[] {
    const currentStatus = event?.status as EventStatus | undefined;

    return getAllowedEventTargetStatuses(event, now)
        .map((targetStatus) =>
            toEventStatusAction(targetStatus, currentStatus)
        )
        .filter(
            (action): action is EventStatusAction =>
                action !== null
        );
}

function toEventStatusAction(
    targetStatus: EventStatus,
    currentStatus?: EventStatus
): EventStatusAction | null {
    switch (targetStatus) {
        case "SCHEDULED":
            return {
                targetStatus,
                action: "schedule",
                label: "Lên lịch"
            };

        case "ACTIVE":
            return {
                targetStatus,
                action:
                    currentStatus === "PAUSED"
                        ? "resume"
                        : "activate",
                label:
                    currentStatus === "PAUSED"
                        ? "Tiếp tục"
                        : "Kích hoạt"
            };

        case "PAUSED":
            return {
                targetStatus,
                action: "pause",
                label: "Tạm dừng"
            };

        case "ENDED":
            return {
                targetStatus,
                action: "end",
                label: "Kết thúc",
                requiresConfirm: true
            };

        case "CANCELLED":
            return {
                targetStatus,
                action: "cancel",
                label: "Hủy sự kiện",
                danger: true,
                requiresConfirm: true
            };

        case "EXPIRED":
            return null;

        case "DRAFT":
        default:
            return null;
    }
}
