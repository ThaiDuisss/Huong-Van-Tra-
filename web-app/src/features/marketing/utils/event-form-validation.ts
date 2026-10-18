import type {
    EventCreateRequest,
    EventUpdateRequest
} from "../types/event.types";

export type EventFormInput = EventCreateRequest | EventUpdateRequest;

export type EventFormField = keyof EventFormInput;

export type EventFormErrors = Partial<Record<EventFormField, string>>;

export function validateEventForm(input: EventFormInput) {
    const errors: EventFormErrors = {};
    const name = input.name.trim();
    const description = input.description?.trim() ?? "";
    const startAt = input.startAt;
    const endAt = input.endAt;
    const now = Date.now();
    const startTime = startAt ? new Date(startAt).getTime() : Number.NaN;
    const endTime = endAt ? new Date(endAt).getTime() : Number.NaN;

    if (!name) {
        errors.name = "Tên sự kiện là bắt buộc.";
    } else if (name.length > 255) {
        errors.name = "Tên sự kiện không được vượt quá 255 ký tự.";
    }

    if (description.length > 2000) {
        errors.description = "Mô tả không được vượt quá 2000 ký tự.";
    }

    if (!startAt) {
        errors.startAt = "Vui lòng chọn thời gian bắt đầu.";
    } else if (Number.isNaN(startTime)) {
        errors.startAt = "Thời gian bắt đầu không hợp lệ.";
    } else if (startTime < now) {
        errors.startAt = "Thời gian bắt đầu phải là hiện tại hoặc tương lai.";
    }

    if (!endAt) {
        errors.endAt = "Vui lòng chọn thời gian kết thúc.";
    } else if (Number.isNaN(endTime)) {
        errors.endAt = "Thời gian kết thúc không hợp lệ.";
    } else if (endTime < now) {
        errors.endAt = "Thời gian kết thúc phải là hiện tại hoặc tương lai.";
    }

    if (
        !errors.startAt &&
        !errors.endAt &&
        !Number.isNaN(startTime) &&
        !Number.isNaN(endTime) &&
        startTime >= endTime
    ) {
        errors.endAt = "Thời gian kết thúc phải sau thời gian bắt đầu.";
    }

    if (!input.eventType) {
        errors.eventType = "Vui lòng chọn loại sự kiện.";
    }

    if (!input.audience) {
        errors.audience = "Vui lòng chọn đối tượng áp dụng.";
    }

    return errors;
}

export function hasEventFormErrors(errors: EventFormErrors) {
    return Object.values(errors).some(Boolean);
}

export function getFirstEventFormError(errors: EventFormErrors) {
    return Object.values(errors).find(Boolean) ?? null;
}

export function normalizeEventPayload<T extends EventFormInput>(input: T): T {
    return {
        ...input,
        name: input.name.trim(),
        description: input.description?.trim() || undefined
    };
}

export function getEventApiErrorMessage(
    error: unknown,
    fallback: string
) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    if (typeof error !== "object" || !error) {
        return fallback;
    }

    const maybeError = error as {
        message?: unknown;
        errors?: unknown;
        fieldErrors?: unknown;
        response?: {
            data?: {
                message?: unknown;
                errors?: unknown;
                fieldErrors?: unknown;
            };
        };
    };
    const responseData = maybeError.response?.data;
    const message =
        pickMessage(responseData?.message) ?? pickMessage(maybeError.message);
    const fieldMessage =
        pickFieldErrorMessage(responseData?.fieldErrors) ??
        pickFieldErrorMessage(responseData?.errors) ??
        pickFieldErrorMessage(maybeError.fieldErrors) ??
        pickFieldErrorMessage(maybeError.errors);

    return fieldMessage ?? message ?? fallback;
}

function pickMessage(value: unknown) {
    return typeof value === "string" && value.trim() ? value : null;
}

function pickFieldErrorMessage(value: unknown) {
    if (!value || typeof value !== "object") {
        return null;
    }

    const messages = Object.entries(value)
        .map(([field, message]) => {
            if (typeof message === "string" && message.trim()) {
                return `${field}: ${message}`;
            }

            if (Array.isArray(message)) {
                const text = message.filter(Boolean).join(", ");
                return text ? `${field}: ${text}` : null;
            }

            return null;
        })
        .filter(Boolean);

    return messages.length ? messages.join("; ") : null;
}
