import { X } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import {
    getEventAudienceLabel,
    getEventTypeLabel
} from "../../utils/event-labels";
import { Button, IconButton } from "../../../../components/ui/Button";
import { Field, TextArea, TextInput } from "../../../../components/ui/Field";
import { ModalShell } from "../../../../components/ui/Overlay";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../../components/ui/select";
import {
    getFirstEventFormError,
    hasEventFormErrors,
    normalizeEventPayload,
    validateEventForm,
    type EventFormErrors
} from "../../utils/event-form-validation";
import { eventApi } from "../../services/event.api";
import type {
    EventCreateRequest,
    EventResponse
} from "../../types/event.types";

interface EventCreateModalProps {
    open: boolean;
    onClose(): void;
    onCreated(event: EventResponse): void;
}

const initialForm: EventCreateRequest = {
    name: "",
    description: "",
    startAt: "",
    endAt: "",
    eventType: "",
    audience: ""
};

export function EventCreateModal({
    open,
    onClose,
    onCreated
}: EventCreateModalProps) {
    const [form, setForm] = useState<EventCreateRequest>(initialForm);
    const [eventTypes, setEventTypes] = useState<string[]>([]);
    const [eventAudiences, setEventAudiences] = useState<string[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [fieldErrors, setFieldErrors] = useState<EventFormErrors>({});

    useEffect(() => {
        if (!open) {
            return;
        }

        async function loadOptions() {
            try {
                const [types, audiences] = await Promise.all([
                    eventApi.eventTypes(),
                    eventApi.eventAudiences()
                ]);

                setEventTypes(types);
                setEventAudiences(audiences);
                setError(null);
                setFieldErrors({});

                setForm((current) => ({
                    ...current,
                    eventType: current.eventType || types[0] || "",
                    audience: current.audience || audiences[0] || ""
                }));
            } catch (err) {
                console.error("Không thể tải enum event", err);
                setError("Không thể tải dữ liệu loại sự kiện.");
            }
        }

        void loadOptions();
    }, [open]);

    if (!open) {
        return null;
    }

    function updateField<K extends keyof EventCreateRequest>(
        key: K,
        value: EventCreateRequest[K]
    ) {
        setForm((current) => ({
            ...current,
            [key]: value
        }));
        setFieldErrors((current) => ({
            ...current,
            [key]: undefined
        }));
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const validationErrors = validateEventForm(form);

        if (hasEventFormErrors(validationErrors)) {
            setFieldErrors(validationErrors);
            setError(getFirstEventFormError(validationErrors));
            return;
        }

        if (!form.name.trim()) {
            setError("Tên sự kiện là bắt buộc.");
            return;
        }

        if (!form.startAt || !form.endAt) {
            setError("Vui lòng chọn thời gian bắt đầu và kết thúc.");
            return;
        }

        if (new Date(form.startAt).getTime() >= new Date(form.endAt).getTime()) {
            setError("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        if (!form.eventType) {
            setError("Vui lòng chọn loại sự kiện.");
            return;
        }

        if (!form.audience) {
            setError("Vui lòng chọn đối tượng áp dụng.");
            return;
        }

        try {
            setSubmitting(true);
            setError(null);
            setFieldErrors({});

            const createdEvent = await eventApi.create(
                normalizeEventPayload(form)
            );

            setForm(initialForm);
            setFieldErrors({});
            onCreated(createdEvent);
            onClose();
        } catch (err) {
            console.error("Không thể tạo sự kiện", err);
            setError("Không thể tạo sự kiện. Vui lòng kiểm tra dữ liệu.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <ModalShell
            className="w-full max-w-2xl p-6"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="event-create-title"
            onClose={onClose}
        >
            <header className="mb-5 flex items-start justify-between gap-4">
                <div>
                    <h2
                        className="text-xl font-bold text-[#17213a]"
                        id="event-create-title"
                    >
                        Tạo sự kiện khuyến mãi
                    </h2>

                    <p className="mt-1 text-sm font-medium text-[#667085]">
                        Sự kiện dùng để nhóm các chương trình giảm giá, coupon
                        và campaign marketing.
                    </p>
                </div>

                <IconButton aria-label="Đóng form" onClick={onClose}>
                    <X size={20} aria-hidden="true" />
                </IconButton>
            </header>

            {error ? (
                <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <form onSubmit={handleSubmit} className="space-y-4">
                <Field label="Tên sự kiện" error={fieldErrors.name}>
                    <TextInput
                        value={form.name}
                        onChange={(event) =>
                            updateField("name", event.currentTarget.value)
                        }
                        placeholder="Tet Holiday Campaign 2027"
                    />
                </Field>

                <Field label="Mô tả" error={fieldErrors.description}>
                    <TextArea
                        value={form.description}
                        onChange={(event) =>
                            updateField(
                                "description",
                                event.currentTarget.value
                            )
                        }
                        placeholder="Chiến dịch khuyến mãi Tết cho toàn bộ khách hàng"
                        rows={3}
                        className="resize-none"
                    />
                </Field>

                <div className="grid gap-4 md:grid-cols-2">
                    <Field
                        label="Thời gian bắt đầu"
                        error={fieldErrors.startAt}
                    >
                        <TextInput
                            type="datetime-local"
                            value={form.startAt}
                            onChange={(event) =>
                                updateField(
                                    "startAt",
                                    event.currentTarget.value
                                )
                            }
                        />
                    </Field>

                    <Field label="Thời gian kết thúc" error={fieldErrors.endAt}>
                        <TextInput
                            type="datetime-local"
                            value={form.endAt}
                            onChange={(event) =>
                                updateField("endAt", event.currentTarget.value)
                            }
                        />
                    </Field>
                </div>

                <div className="grid gap-4 md:grid-cols-2">
                    <Field label="Loại sự kiện" error={fieldErrors.eventType}>
                        <Select
                            className="w-full"
                            value={form.eventType}
                            onValueChange={(value) =>
                                updateField("eventType", value)
                            }
                        >
                            <SelectTrigger
                                aria-label="Loại sự kiện"
                                className="h-10 w-full rounded-lg border-[#dfe6ee] px-3 text-sm text-[#172317] shadow-none hover:bg-white"
                            >
                                <SelectValue placeholder="Chọn loại sự kiện" />
                            </SelectTrigger>
                            <SelectContent className="z-[90] rounded-xl border-[#d0d5dd] p-1.5 shadow-[0_18px_40px_rgba(15,23,42,0.16)]">
                                {eventTypes.map((type) => (
                                    <SelectItem key={type} value={type}>
                                        {getEventTypeLabel(type)}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>

                    <Field label="Đối tượng áp dụng" error={fieldErrors.audience}>
                        <Select
                            className="w-full"
                            value={form.audience}
                            onValueChange={(value) =>
                                updateField("audience", value)
                            }
                        >
                            <SelectTrigger
                                aria-label="Đối tượng áp dụng"
                                className="h-10 w-full rounded-lg border-[#dfe6ee] px-3 text-sm text-[#172317] shadow-none hover:bg-white"
                            >
                                <SelectValue placeholder="Chọn đối tượng" />
                            </SelectTrigger>
                            <SelectContent className="z-[90] rounded-xl border-[#d0d5dd] p-1.5 shadow-[0_18px_40px_rgba(15,23,42,0.16)]">
                                {eventAudiences.map((audience) => (
                                    <SelectItem key={audience} value={audience}>
                                        {getEventAudienceLabel(audience)}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>
                </div>

                <footer className="mt-6 flex justify-end gap-3">
                    <Button onClick={onClose} disabled={submitting}>
                        Hủy
                    </Button>

                    <Button
                        type="submit"
                        variant="primary"
                        disabled={submitting}
                    >
                        {submitting ? "Đang tạo..." : "Tạo sự kiện"}
                    </Button>
                </footer>
            </form>
        </ModalShell>
    );
}
