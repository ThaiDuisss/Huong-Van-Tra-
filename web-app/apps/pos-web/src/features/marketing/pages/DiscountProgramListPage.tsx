import { AlertCircle, CheckCircle2, Plus, RefreshCw, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { EventCreateModal } from "../components/discount-programs/EventCreateModel";
import {
    defaultEventListFilters,
    DiscountProgramFilters,
    getInitialEventFilters,
    type EventFilterFormState
} from "../components/discount-programs/DiscountProgramFilters";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import {
    DiscountProgramTable,
    type EventAction
} from "../components/discount-programs/DiscountProgramTable";
import { eventApi } from "../services/event.api";
import type {
    EventQuery,
    EventResponse,
    EventUpdateRequest,
    PageResponse
} from "../types/event.types";
import {
    getFirstEventFormError,
    hasEventFormErrors,
    normalizeEventPayload,
    validateEventForm
} from "../utils/event-form-validation";

type PageNotice = {
    type: "success" | "error";
    message: string;
};

export function DiscountProgramListPage() {
    const [createOpen, setCreateOpen] = useState(false);
    const [draftFilters, setDraftFilters] =
        useState<EventFilterFormState>(defaultEventListFilters);
    const [appliedFilters, setAppliedFilters] =
        useState<EventFilterFormState>(defaultEventListFilters);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [resource, setResource] =
        useState<PageResponse<EventResponse> | null>(null);
    const [eventTypes, setEventTypes] = useState<string[]>([]);
    const [eventAudiences, setEventAudiences] = useState<string[]>([]);
    const [eventStatuses, setEventStatuses] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);
    const [notice, setNotice] = useState<PageNotice | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [actionLoadingId, setActionLoadingId] = useState<string | null>(
        null
    );

    const showSuccess = useCallback((message: string) => {
        setNotice({
            type: "success",
            message
        });
    }, []);

    const showError = useCallback((message: string) => {
        setNotice({
            type: "error",
            message
        });
    }, []);

    const loadEvents = useCallback(async () => {
        try {
            setLoading(true);

            const [eventsPage, types, audiences, statuses] =
                await Promise.all([
                    eventApi.list(
                        createEventQuery(appliedFilters, page, pageSize)
                    ),
                    eventApi.eventTypes(),
                    eventApi.eventAudiences(),
                    eventApi.eventStatuses()
                ]);

            setResource(eventsPage);
            setEventTypes(types);
            setEventAudiences(audiences);
            setEventStatuses(statuses);
        } catch (err) {
            console.error("Không thể tải danh sách sự kiện", err);
            showError(
                "Không thể tải danh sách sự kiện. Vui lòng thử lại."
            );
        } finally {
            setLoading(false);
        }
    }, [appliedFilters, page, pageSize, showError]);

    useEffect(() => {
        void loadEvents();
    }, [loadEvents, refreshKey]);

    useEffect(() => {
        if (!notice) {
            return;
        }

        const timeoutId = window.setTimeout(() => {
            setNotice(null);
        }, 3500);

        return () => window.clearTimeout(timeoutId);
    }, [notice]);

    const events = resource?.content ?? [];
    const pageCount = Math.max(1, resource?.totalPages ?? 1);
    const totalItems = resource?.totalElements ?? events.length;
    const safePage = Math.min(page, pageCount);

    function updatePageSize(nextPageSize: number) {
        setPageSize(nextPageSize);
        setPage(1);
    }

    function reload() {
        setRefreshKey((current) => current + 1);
    }

    function searchEvents() {
        const validationError = validateEventFilter(draftFilters);

        if (validationError) {
            showError(validationError);
            return;
        }

        setNotice(null);
        setAppliedFilters({
            ...draftFilters,
            keyword: draftFilters.keyword.trim()
        });
        setPage(1);
        reload();
    }

    function clearFilters() {
        const initialFilters = getInitialEventFilters();

        setNotice(null);
        setDraftFilters(initialFilters);
        setAppliedFilters(initialFilters);
        setPage(1);
        reload();
    }

    function handleCreated() {
        setCreateOpen(false);
        showSuccess("Tạo sự kiện thành công.");
        reload();
    }

    async function handleAction(
        event: EventResponse,
        action: EventAction
    ) {
        if (!confirmStatusAction(event, action)) {
            return;
        }

        try {
            setActionLoadingId(event.id);

            switch (action) {
                case "schedule":
                    await eventApi.schedule(event.id);
                    break;

                case "activate":
                    await eventApi.activate(event.id);
                    break;

                case "pause":
                    await eventApi.pause(event.id);
                    break;

                case "resume":
                    await eventApi.resume(event.id);
                    break;

                case "cancel":
                    await eventApi.cancel(event.id);
                    break;

                case "end":
                    await eventApi.end(event.id);
                    break;
            }

            showSuccess(getEventActionSuccessMessage(action));
            reload();
        } catch (err) {
            console.error("Không thể cập nhật trạng thái sự kiện", err);
            showError(
                "Không thể cập nhật trạng thái sự kiện. Vui lòng thử lại."
            );
        } finally {
            setActionLoadingId(null);
        }
    }

    async function handleUpdate(
        event: EventResponse,
        input: EventUpdateRequest
    ): Promise<boolean | void> {
        const validationErrors = validateEventForm(input);

        if (hasEventFormErrors(validationErrors)) {
            showError(
                getFirstEventFormError(validationErrors) ??
                    "Vui lòng kiểm tra lại thông tin sự kiện."
            );
            return false;
        }

        if (!input.name.trim()) {
            showError("Tên sự kiện là bắt buộc.");
            return;
        }

        if (!input.startAt || !input.endAt) {
            showError("Vui lòng chọn thời gian bắt đầu và kết thúc.");
            return;
        }

        if (
            new Date(input.startAt).getTime() >=
            new Date(input.endAt).getTime()
        ) {
            showError("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        try {
            setActionLoadingId(event.id);
            await eventApi.update(event.id, normalizeEventPayload(input));
            showSuccess("Cập nhật sự kiện thành công.");
            reload();
            return true;
        } catch (err) {
            console.error("Không thể cập nhật sự kiện", err);
            showError("Không thể cập nhật sự kiện. Vui lòng thử lại.");
        } finally {
            setActionLoadingId(null);
        }
    }

    async function handleRemove(event: EventResponse) {
        const confirmed = window.confirm(
            `Xóa sự kiện?\n\nHành động này không thể hoàn tác.\n\nSự kiện: ${event.name}`
        );

        if (!confirmed) {
            return;
        }

        try {
            setActionLoadingId(event.id);
            await eventApi.remove(event.id);
            showSuccess("Xóa sự kiện thành công.");
            reload();
        } catch (err) {
            console.error("Không thể xóa sự kiện", err);
            showError("Không thể xóa sự kiện. Vui lòng thử lại.");
        } finally {
            setActionLoadingId(null);
        }
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.titleBlock}>
                    <nav
                        className="flex items-center gap-2 text-sm"
                        aria-label="Breadcrumb"
                    >
                        <span className="font-bold text-[#009b4d]">
                            Marketing
                        </span>
                        <span aria-hidden="true">›</span>
                        <span className="font-bold text-[#17213a]">
                            Sự kiện khuyến mãi
                        </span>
                    </nav>

                    <h1 className={styles.title}>
                        Sự kiện khuyến mãi
                    </h1>

                    <p className={styles.description}>
                        Quản lý các sự kiện, chiến dịch khuyến mãi và
                        trạng thái vận hành trong hệ thống.
                    </p>
                </div>

                <div className={styles.actions}>
                    <button
                        className={styles.primaryButton}
                        type="button"
                        onClick={() => setCreateOpen(true)}
                    >
                        <Plus size={18} aria-hidden="true" />
                        Tạo sự kiện
                    </button>

                    <button
                        className={styles.secondaryButton}
                        type="button"
                        onClick={reload}
                        disabled={loading}
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Tải lại
                    </button>
                </div>
            </header>

            <DiscountProgramFilters
                filters={draftFilters}
                eventTypes={eventTypes}
                eventAudiences={eventAudiences}
                eventStatuses={eventStatuses}
                loading={loading}
                showClear={
                    hasAnyEventFilter(draftFilters) ||
                    hasAnyEventFilter(appliedFilters)
                }
                onFiltersChange={setDraftFilters}
                onSearch={searchEvents}
                onClear={clearFilters}
            />

            <DiscountProgramTable
                events={events}
                eventTypes={eventTypes}
                eventAudiences={eventAudiences}
                page={safePage}
                pageCount={pageCount}
                totalItems={totalItems}
                pageSize={pageSize}
                loading={loading}
                actionLoadingId={actionLoadingId}
                onPageChange={setPage}
                onPageSizeChange={updatePageSize}
                onAction={handleAction}
                onUpdate={handleUpdate}
                onRemove={handleRemove}
            />

            <EventCreateModal
                open={createOpen}
                onClose={() => setCreateOpen(false)}
                onCreated={handleCreated}
            />

            {notice ? (
                <EventToast notice={notice} onClose={() => setNotice(null)} />
            ) : null}
        </section>
    );
}

function EventToast({
    notice,
    onClose
}: {
    notice: PageNotice;
    onClose(): void;
}) {
    const isSuccess = notice.type === "success";
    const Icon = isSuccess ? CheckCircle2 : AlertCircle;

    return (
        <div
            className={`${styles.toast} ${
                isSuccess ? styles.toastSuccess : styles.toastError
            }`}
            role="status"
            aria-live="polite"
        >
            <Icon size={20} aria-hidden="true" />
            <span>{notice.message}</span>
            <button
                type="button"
                className={styles.toastClose}
                onClick={onClose}
                aria-label="Đóng thông báo"
            >
                <X size={16} aria-hidden="true" />
            </button>
        </div>
    );
}

function confirmStatusAction(
    event: EventResponse,
    action: EventAction
) {
    if (action === "cancel") {
        return window.confirm(
            `Hủy sự kiện?\n\nSự kiện sau khi hủy sẽ không thể đổi trạng thái lại.\n\nSự kiện: ${event.name}`
        );
    }

    if (action === "end") {
        return window.confirm(
            `Kết thúc sự kiện?\n\nSự kiện sau khi kết thúc sẽ không thể đổi trạng thái.\n\nSự kiện: ${event.name}`
        );
    }

    return true;
}

function getEventActionSuccessMessage(action: EventAction) {
    const messages: Record<EventAction, string> = {
        schedule: "Lên lịch sự kiện thành công.",
        activate: "Kích hoạt sự kiện thành công.",
        pause: "Tạm dừng sự kiện thành công.",
        resume: "Tiếp tục sự kiện thành công.",
        cancel: "Hủy sự kiện thành công.",
        end: "Kết thúc sự kiện thành công."
    };

    return messages[action];
}

function createEventQuery(
    filters: EventFilterFormState,
    page: number,
    pageSize: number
): EventQuery {
    return {
        page: page - 1,
        size: pageSize,
        ...buildEventFilterParams(filters)
    };
}

function buildEventFilterParams(
    filters: EventFilterFormState
): EventQuery {
    return {
        keyword: filters.keyword.trim() || undefined,
        status:
            filters.status === "ALL" ? undefined : filters.status,
        eventType:
            filters.eventType === "ALL"
                ? undefined
                : filters.eventType,
        audience:
            filters.audience === "ALL"
                ? undefined
                : filters.audience,
        endFrom: toStartOfDay(filters.from),
        startTo: toEndOfDay(filters.to)
    };
}

function validateEventFilter(filters: EventFilterFormState) {
    if (filters.from && filters.to && filters.from > filters.to) {
        return "Từ ngày không được sau Đến ngày.";
    }

    return null;
}

function hasAnyEventFilter(filters: EventFilterFormState) {
    return (
        filters.keyword.trim().length > 0 ||
        filters.eventType !== "ALL" ||
        filters.audience !== "ALL" ||
        filters.status !== "ALL" ||
        filters.from.length > 0 ||
        filters.to.length > 0
    );
}

function toStartOfDay(value: string) {
    return value ? `${value}T00:00:00` : undefined;
}

function toEndOfDay(value: string) {
    return value ? `${value}T23:59:59` : undefined;
}
