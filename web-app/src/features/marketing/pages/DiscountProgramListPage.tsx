import {
    AlertCircle,
    CheckCircle2,
    Plus,
    RefreshCw,
    Trash2,
    X
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button } from "../../../components/ui/Button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
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

type PendingStatusAction = {
    action: EventAction;
    event: EventResponse;
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
    const [pendingStatusAction, setPendingStatusAction] =
        useState<PendingStatusAction | null>(null);
    const [pendingRemoveEvent, setPendingRemoveEvent] =
        useState<EventResponse | null>(null);

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

            setResource(withoutDeletedEvents(eventsPage));
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
        if (needsStatusActionConfirmation(action)) {
            setPendingStatusAction({ action, event });
            return;
        }

        await runStatusAction(event, action);
    }

    async function runStatusAction(
        event: EventResponse,
        action: EventAction
    ) {
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

    async function confirmPendingStatusAction() {
        if (!pendingStatusAction) {
            return;
        }

        const { action, event } = pendingStatusAction;

        await runStatusAction(event, action);
        setPendingStatusAction(null);
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

    function handleRemove(event: EventResponse) {
        setPendingRemoveEvent(event);
    }

    async function confirmPendingRemoveEvent() {
        if (!pendingRemoveEvent) {
            return;
        }

        const event = pendingRemoveEvent;

        try {
            setActionLoadingId(event.id);
            await eventApi.remove(event.id);
            showSuccess("Xóa sự kiện thành công.");
            setPendingRemoveEvent(null);
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
            <ListPageHeader
                actions={
                    <>
                        <Button
                            variant="primary"
                            onClick={() => setCreateOpen(true)}
                        >
                            <Plus size={18} aria-hidden="true" />
                            Tạo sự kiện
                        </Button>

                        <Button onClick={reload} disabled={loading}>
                            <RefreshCw size={17} aria-hidden="true" />
                            Tải lại
                        </Button>
                    </>
                }
                breadcrumb={
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
                }
                description="Quản lý các sự kiện, chiến dịch khuyến mãi và trạng thái vận hành trong hệ thống."
                title="Sự kiện khuyến mãi"
            />

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

            <Dialog
                open={pendingStatusAction !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setPendingStatusAction(null);
                    }
                }}
            >
                {pendingStatusAction ? (
                    <EventStatusConfirmDialog
                        pending={pendingStatusAction}
                        loading={
                            actionLoadingId === pendingStatusAction.event.id
                        }
                        onCancel={() => setPendingStatusAction(null)}
                        onConfirm={() => void confirmPendingStatusAction()}
                    />
                ) : null}
            </Dialog>

            <Dialog
                open={pendingRemoveEvent !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setPendingRemoveEvent(null);
                    }
                }}
            >
                {pendingRemoveEvent ? (
                    <EventRemoveConfirmDialog
                        event={pendingRemoveEvent}
                        loading={actionLoadingId === pendingRemoveEvent.id}
                        onCancel={() => setPendingRemoveEvent(null)}
                        onConfirm={() => void confirmPendingRemoveEvent()}
                    />
                ) : null}
            </Dialog>

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

function withoutDeletedEvents(
    page: PageResponse<EventResponse>
): PageResponse<EventResponse> {
    const content = (page.content ?? []).filter((event) => !event.isDeleted);

    if (content.length === page.content.length) {
        return page;
    }

    const totalElements = Math.max(
        0,
        page.totalElements - (page.content.length - content.length)
    );

    return {
        ...page,
        content,
        totalElements,
        totalPages: Math.max(1, Math.ceil(totalElements / page.size)),
        empty: content.length === 0 && totalElements === 0
    };
}

function EventStatusConfirmDialog({
    pending,
    loading,
    onCancel,
    onConfirm
}: {
    pending: PendingStatusAction;
    loading: boolean;
    onCancel(): void;
    onConfirm(): void;
}) {
    const isCancel = pending.action === "cancel";
    const title = isCancel ? "Hủy sự kiện?" : "Kết thúc sự kiện?";
    const description = isCancel
        ? "Sự kiện sau khi hủy sẽ không thể đổi trạng thái lại."
        : "Sự kiện sau khi kết thúc sẽ không thể đổi trạng thái.";
    const confirmLabel = isCancel ? "Hủy sự kiện" : "Kết thúc sự kiện";

    return (
        <DialogContent className="w-[min(520px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                    <AlertCircle
                        className={isCancel ? "text-[#c4422e]" : "text-[#a86500]"}
                        size={18}
                    />
                    {title}
                </DialogTitle>
                <DialogDescription>{description}</DialogDescription>
            </DialogHeader>

            <div className="px-6 py-5 text-sm font-semibold text-[#425047]">
                <div className="rounded-xl border border-[#eef2f0] bg-[#f8faf9] p-4">
                    Sự kiện:{" "}
                    <strong className="font-black text-[#172317]">
                        {pending.event.name}
                    </strong>
                </div>
            </div>

            <DialogFooter>
                <Button disabled={loading} onClick={onCancel}>
                    Đóng
                </Button>
                <Button
                    variant={isCancel ? "danger" : "primary"}
                    disabled={loading}
                    onClick={onConfirm}
                >
                    {loading ? "Đang xử lý..." : confirmLabel}
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}

function needsStatusActionConfirmation(action: EventAction) {
    return action === "cancel" || action === "end";
}

function EventRemoveConfirmDialog({
    event,
    loading,
    onCancel,
    onConfirm
}: {
    event: EventResponse;
    loading: boolean;
    onCancel(): void;
    onConfirm(): void;
}) {
    return (
        <DialogContent className="w-[min(520px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                    <Trash2 className="text-[#c4422e]" size={18} />
                    Xóa sự kiện?
                </DialogTitle>
                <DialogDescription>
                    Hành động này không thể hoàn tác.
                </DialogDescription>
            </DialogHeader>

            <div className="px-6 py-5 text-sm font-semibold text-[#425047]">
                <div className="rounded-xl border border-[#f1c8c1] bg-[#fff5f3] p-4">
                    Sự kiện:{" "}
                    <strong className="font-black text-[#172317]">
                        {event.name}
                    </strong>
                </div>
            </div>

            <DialogFooter>
                <Button disabled={loading} onClick={onCancel}>
                    Đóng
                </Button>
                <Button
                    variant="danger"
                    disabled={loading}
                    onClick={onConfirm}
                >
                    {loading ? "Đang xóa..." : "Xóa sự kiện"}
                </Button>
            </DialogFooter>
        </DialogContent>
    );
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
