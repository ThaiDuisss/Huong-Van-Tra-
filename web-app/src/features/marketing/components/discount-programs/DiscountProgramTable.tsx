import {
    CalendarDays,
    Clock,
    FileText,
    Gift,
    MoreVertical,
    Pencil,
    Trash2,
    Users,
    X
} from "lucide-react";
import { Fragment, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../../components/ui/select";
import { MarketingPagination } from "../MarketingPagination";
import styles from "./DiscountProgramPage.module.css";
import { routePaths } from "../../../../routes/path";
import type {
    EventResponse,
    EventUpdateRequest
} from "../../types/event.types";
import {
    formatDiscountDate,
    formatDiscountDateParts
} from "../../utils/discount-programs";
import {
    getEventAudienceLabel,
    getEventStatusLabel,
    getEventTypeLabel
} from "../../utils/event-labels";
import {
    hasEventFormErrors,
    validateEventForm,
    type EventFormErrors
} from "../../utils/event-form-validation";
import {
    canDeleteEvent,
    canEditEvent,
    getAllowedEventStatusActions,
    type EventStatusActionType
} from "../../utils/event-status-transition";

interface DiscountProgramTableProps {
    events: EventResponse[];
    eventTypes: string[];
    eventAudiences: string[];
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    loading?: boolean;
    actionLoadingId?: string | null;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
    onAction(event: EventResponse, action: EventAction): void;
    onUpdate(
        event: EventResponse,
        input: EventUpdateRequest
    ): boolean | void | Promise<boolean | void>;
    onRemove(event: EventResponse): void;
}

export type EventAction = EventStatusActionType;

type EventPanelMode = "detail" | "edit";

const statusClassNames: Record<string, string> = {
    ACTIVE: styles.statusActive,
    SCHEDULED: styles.statusScheduled,
    DRAFT: styles.statusInactive,
    PAUSED: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    ENDED: styles.statusUsed,
    CANCELLED: styles.statusCancelled
};

const eventTypeClassNames: Record<string, string> = {
    TIME_BASED: styles.typeSeasonal,
    ORDER_AMOUNT_BASED: styles.typeOrderAmount,
    ORDER_COUNT_BASED: styles.typeOrderCount,
    ITEM_QUANTITY_BASED: styles.typeItemQuantity,
    LOYALTY_POINT_BASED: styles.typeLoyalty,
    MANUAL_CAMPAIGN: styles.typeManual,
    SEASONAL_CAMPAIGN: styles.typeSeasonal,
    FLASH_SALE: styles.typeFlash,
    CLEARANCE_CAMPAIGN: styles.typeClearance,
    LOYALTY_CAMPAIGN: styles.typeLoyalty,
    ORDER_INCENTIVE: styles.typeOrder
};

function toDateTimeLocal(value: string) {
    if (!value) return "";

    return value.slice(0, 16);
}

function createEditForm(event: EventResponse): EventUpdateRequest {
    return {
        name: event.name,
        description: event.description ?? "",
        startAt: toDateTimeLocal(event.startAt),
        endAt: toDateTimeLocal(event.endAt),
        eventType: event.eventType,
        audience: event.audience
    };
}

export function DiscountProgramTable({
    events,
    eventTypes,
    eventAudiences,
    page,
    pageCount,
    totalItems,
    pageSize,
    loading = false,
    actionLoadingId,
    onPageChange,
    onPageSizeChange,
    onAction,
    onUpdate,
    onRemove
}: DiscountProgramTableProps) {
    const [expandedEventId, setExpandedEventId] = useState<string | null>(
        null
    );
    const [panelMode, setPanelMode] =
        useState<EventPanelMode>("detail");
    const [openMenuEventId, setOpenMenuEventId] = useState<string | null>(
        null
    );
    const [editForm, setEditForm] = useState<EventUpdateRequest | null>(
        null
    );
    const [editErrors, setEditErrors] = useState<EventFormErrors>({});

    function openDetail(event: EventResponse) {
        if (expandedEventId === event.id && panelMode === "detail") {
            closePanel();
            return;
        }

        setExpandedEventId(event.id);
        setPanelMode("detail");
        setEditForm(null);
        setEditErrors({});
        setOpenMenuEventId(null);
    }

    function openEdit(event: EventResponse) {
        setExpandedEventId(event.id);
        setPanelMode("edit");
        setEditForm(createEditForm(event));
        setEditErrors({});
        setOpenMenuEventId(null);
    }

    function closePanel() {
        setExpandedEventId(null);
        setPanelMode("detail");
        setEditForm(null);
        setOpenMenuEventId(null);
    }

    function updateField<K extends keyof EventUpdateRequest>(
        key: K,
        value: EventUpdateRequest[K]
    ) {
        setEditForm((current) =>
            current
                ? {
                      ...current,
                      [key]: value
                  }
                : current
        );
        setEditErrors((current) => ({
            ...current,
            [key]: undefined
        }));
    }

    return (
        <section className={styles.tableCard}>
            <div className={styles.tableScroll}>
                <table className={styles.table}>
                    <colgroup>
                        <col className={styles.eventNameColumn} />
                        <col className={styles.statusColumn} />
                        <col className={styles.timeColumn} />
                        <col className={styles.typeColumn} />
                        <col className={styles.actionColumn} />
                    </colgroup>
                    <thead>
                        <tr>
                            <th>Sự kiện</th>
                            <th>Trạng thái</th>
                            <th>Thời gian</th>
                            <th>Loại</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading ? (
                            <EventTableSkeleton />
                        ) : (
                            events.map((event, index) => {
                            const expanded = expandedEventId === event.id;
                            const statusActions =
                                getAllowedEventStatusActions(event);
                            const disabled =
                                actionLoadingId === event.id;
                            const editable = canEditEvent(event);
                            const deletable = canDeleteEvent(event);
                            const zebraClass =
                                index % 2 === 0
                                    ? styles.rowOdd
                                    : styles.rowEven;

                            return (
                                <Fragment key={event.id}>
                                    <tr
                                        className={`${zebraClass} ${
                                            expanded
                                                ? styles.expandedSummaryRow
                                                : styles.summaryRow
                                        }`}
                                        onClick={() => openDetail(event)}
                                    >
                                        <td>
                                            <span
                                                className={
                                                    expanded
                                                        ? styles.programName
                                                        : styles.programNameMuted
                                                }
                                            >
                                                {event.name}
                                            </span>
                                        </td>
                                        <td>
                                            <StatusBadge event={event} />
                                        </td>
                                        <td>
                                            <EventTimeRange event={event} />
                                        </td>
                                        <td>
                                            <span
                                                className={`${styles.typePill} ${
                                                    eventTypeClassNames[
                                                        event.eventType
                                                    ] ?? styles.typeDefault
                                                }`}
                                            >
                                                {getEventTypeLabel(
                                                    event.eventType
                                                )}
                                            </span>
                                        </td>
                                        <td className={styles.actionCell}>
                                            <div
                                                className={
                                                    styles.rowMenuAnchor
                                                }
                                            >
                                                <button
                                                    className={
                                                        styles.tableIconButton
                                                    }
                                                    type="button"
                                                    aria-label={`Mở thao tác ${event.name}`}
                                                    onClick={(clickEvent) => {
                                                        clickEvent.stopPropagation();
                                                        setOpenMenuEventId(
                                                            openMenuEventId ===
                                                                event.id
                                                                ? null
                                                                : event.id
                                                        );
                                                    }}
                                                >
                                                    <MoreVertical
                                                        size={19}
                                                        aria-hidden="true"
                                                    />
                                                </button>

                                                {openMenuEventId ===
                                                event.id ? (
                                                    <div
                                                        className={
                                                            styles.rowActionMenu
                                                        }
                                                        onClick={(clickEvent) =>
                                                            clickEvent.stopPropagation()
                                                        }
                                                    >
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                openDetail(
                                                                    event
                                                                )
                                                            }
                                                        >
                                                            Xem chi tiết
                                                        </button>

                                                        <Link
                                                            to={`${routePaths.discountPrograms}?eventId=${encodeURIComponent(
                                                                event.id
                                                            )}&eventName=${encodeURIComponent(event.name)}`}
                                                            onClick={() =>
                                                                setOpenMenuEventId(
                                                                    null
                                                                )
                                                            }
                                                        >
                                                            Chương trình giảm giá
                                                        </Link>

                                                        {editable ? (
                                                            <button
                                                                type="button"
                                                                onClick={() =>
                                                                    openEdit(
                                                                        event
                                                                    )
                                                                }
                                                            >
                                                                Chỉnh sửa
                                                            </button>
                                                        ) : null}

                                                        {deletable ? (
                                                            <button
                                                                className={
                                                                    styles.menuDangerItem
                                                                }
                                                                type="button"
                                                                disabled={
                                                                    disabled
                                                                }
                                                                onClick={() => {
                                                                    setOpenMenuEventId(
                                                                        null
                                                                    );
                                                                    onRemove(
                                                                        event
                                                                    );
                                                                }}
                                                            >
                                                                Xóa
                                                            </button>
                                                        ) : null}
                                                    </div>
                                                ) : null}
                                            </div>
                                        </td>
                                    </tr>

                                    {expanded ? (
                                        <tr
                                            className={styles.expandedDetailRow}
                                        >
                                            <td colSpan={5}>
                                                {panelMode === "detail" ? (
                                                    <EventDetailView
                                                        event={event}
                                                        statusActions={
                                                            statusActions
                                                        }
                                                        disabled={disabled}
                                                        editable={editable}
                                                        deletable={deletable}
                                                        onClose={closePanel}
                                                        onEdit={() =>
                                                            openEdit(event)
                                                        }
                                                        onAction={(action) =>
                                                            onAction(
                                                                event,
                                                                action
                                                            )
                                                        }
                                                        onRemove={() =>
                                                            onRemove(event)
                                                        }
                                                    />
                                                ) : editForm ? (
                                                    <EventEditForm
                                                        event={event}
                                                        form={editForm}
                                                        errors={editErrors}
                                                        eventTypes={eventTypes}
                                                        eventAudiences={
                                                            eventAudiences
                                                        }
                                                        disabled={disabled}
                                                        onCancel={() => {
                                                            setPanelMode(
                                                                "detail"
                                                            );
                                                            setEditForm(null);
                                                        }}
                                                        onChange={updateField}
                                                        onSave={async () => {
                                                            const validationErrors =
                                                                validateEventForm(
                                                                    editForm
                                                                );

                                                            if (
                                                                hasEventFormErrors(
                                                                    validationErrors
                                                                )
                                                            ) {
                                                                setEditErrors(
                                                                    validationErrors
                                                                );
                                                                return;
                                                            }

                                                            setEditErrors({});

                                                            const saved =
                                                                await onUpdate(
                                                                    event,
                                                                    editForm
                                                                );

                                                            if (saved) {
                                                                setPanelMode(
                                                                    "detail"
                                                                );
                                                                setEditForm(
                                                                    null
                                                                );
                                                            }
                                                        }}
                                                    />
                                                ) : null}
                                            </td>
                                        </tr>
                                    ) : null}
                                </Fragment>
                            );
                            })
                        )}
                    </tbody>
                </table>
            </div>

            {!loading && events.length === 0 ? (
                <div className={styles.empty}>
                    Không tìm thấy sự kiện khuyến mãi phù hợp.
                </div>
            ) : null}

            <MarketingPagination
                page={page}
                pageCount={pageCount}
                totalItems={totalItems}
                pageSize={pageSize}
                onPageChange={onPageChange}
                onPageSizeChange={onPageSizeChange}
            />
        </section>
    );
}

function EventTableSkeleton() {
    return (
        <>
            {Array.from({ length: 8 }, (_, index) => {
                const zebraClass =
                    index % 2 === 0 ? styles.rowOdd : styles.rowEven;

                return (
                    <tr
                        key={`event-loading-${index}`}
                        className={`${zebraClass} ${styles.loadingRow}`}
                    >
                        <td>
                            <span
                                className={`${styles.skeletonLine} ${styles.skeletonTitle}`}
                            />
                        </td>
                        <td>
                            <span
                                className={`${styles.skeletonBlock} ${styles.skeletonStatus}`}
                            />
                        </td>
                        <td>
                            <div className={styles.skeletonTimeRange}>
                                <div className={styles.skeletonTimePoint}>
                                    <span
                                        className={`${styles.skeletonLine} ${styles.skeletonDate}`}
                                    />
                                    <span
                                        className={`${styles.skeletonLine} ${styles.skeletonHour}`}
                                    />
                                </div>
                                <span className={styles.skeletonSeparator} />
                                <div className={styles.skeletonTimePoint}>
                                    <span
                                        className={`${styles.skeletonLine} ${styles.skeletonDate}`}
                                    />
                                    <span
                                        className={`${styles.skeletonLine} ${styles.skeletonHour}`}
                                    />
                                </div>
                            </div>
                        </td>
                        <td>
                            <span
                                className={`${styles.skeletonBlock} ${styles.skeletonType}`}
                            />
                        </td>
                        <td className={styles.actionCell}>
                            <span className={styles.skeletonIcon} />
                        </td>
                    </tr>
                );
            })}
        </>
    );
}

function StatusBadge({ event }: { event: EventResponse }) {
    return (
        <span
            className={`${styles.statusBadge} ${
                statusClassNames[event.status] ?? styles.statusInactive
            }`}
        >
            <CalendarDays size={15} aria-hidden="true" />
            {getEventStatusLabel(event.status)}
        </span>
    );
}

function EventTimeRange({ event }: { event: EventResponse }) {
    const start = formatDiscountDateParts(event.startAt);
    const end = formatDiscountDateParts(event.endAt);

    return (
        <div className={styles.timeRangeCell}>
            <span className={styles.timePoint}>
                <span className={styles.timeDate}>{start.date}</span>
                <span className={styles.timeHour}>{start.time}</span>
            </span>
            <span className={styles.timeSeparator} aria-hidden="true" />
            <span className={styles.timePoint}>
                <span className={styles.timeDate}>{end.date}</span>
                <span className={styles.timeHour}>{end.time}</span>
            </span>
        </div>
    );
}

function EventDetailView({
    event,
    statusActions,
    disabled,
    editable,
    deletable,
    onClose,
    onEdit,
    onAction,
    onRemove
}: {
    event: EventResponse;
    statusActions: ReturnType<typeof getAllowedEventStatusActions>;
    disabled: boolean;
    editable: boolean;
    deletable: boolean;
    onClose(): void;
    onEdit(): void;
    onAction(action: EventAction): void;
    onRemove(): void;
}) {
    return (
        <div className={styles.eventPanel}>
            <div className={styles.eventDetailGrid}>
                <section className={styles.eventDetailSection}>
                    <h3>Thông tin cơ bản</h3>
                    <InfoRow
                        icon={<FileText size={18} aria-hidden="true" />}
                        label="Loại sự kiện"
                        value={getEventTypeLabel(event.eventType)}
                    />
                    <InfoRow
                        icon={<Users size={18} aria-hidden="true" />}
                        label="Đối tượng"
                        value={getEventAudienceLabel(event.audience)}
                    />
                    <InfoRow
                        icon={<FileText size={18} aria-hidden="true" />}
                        label="Mô tả"
                        value={event.description || "-"}
                    />
                </section>

                <section className={styles.eventDetailSection}>
                    <h3>Thời gian</h3>
                    <InfoRow
                        icon={<Clock size={18} aria-hidden="true" />}
                        label="Bắt đầu"
                        value={formatDiscountDate(event.startAt)}
                    />
                    <InfoRow
                        icon={<Clock size={18} aria-hidden="true" />}
                        label="Kết thúc"
                        value={formatDiscountDate(event.endAt)}
                    />
                </section>

                <section className={styles.eventDetailSection}>
                    <h3>Trạng thái</h3>
                    <InfoRow
                        icon={<CalendarDays size={18} aria-hidden="true" />}
                        label="Trạng thái hiện tại"
                        value={getEventStatusLabel(event.status)}
                    />
                    <div className={styles.detailInfo}>
                        <Gift size={18} aria-hidden="true" />
                        <div>
                            <strong>Thao tác khả dụng</strong>
                            {statusActions.length > 0 ? (
                                <div className={styles.statusActions}>
                                    {statusActions.map((statusAction) => (
                                        <button
                                            key={statusAction.targetStatus}
                                            className={`${styles.actionButton} ${
                                                statusAction.danger
                                                    ? styles.dangerButton
                                                    : ""
                                            }`}
                                            type="button"
                                            disabled={disabled}
                                            onClick={() =>
                                                onAction(statusAction.action)
                                            }
                                        >
                                            {statusAction.label}
                                        </button>
                                    ))}
                                </div>
                            ) : (
                                <p>
                                    Không còn thao tác trạng thái khả dụng.
                                </p>
                            )}
                        </div>
                    </div>
                </section>
            </div>

            <footer className={styles.eventPanelFooter}>
                <Link
                    className={styles.secondaryButton}
                    to={`${routePaths.discountPrograms}?eventId=${encodeURIComponent(
                        event.id
                    )}&eventName=${encodeURIComponent(event.name)}`}
                >
                    Chuyển sang chương trình giảm giá
                </Link>

                <button
                    className={styles.secondaryButton}
                    type="button"
                    onClick={onClose}
                >
                    Đóng
                </button>
                {deletable ? (
                    <button
                        className={`${styles.secondaryButton} ${styles.dangerOutlineButton}`}
                        type="button"
                        disabled={disabled}
                        onClick={onRemove}
                    >
                        <Trash2 size={16} aria-hidden="true" />
                        Xóa
                    </button>
                ) : null}
                {editable ? (
                    <button
                        className={styles.primaryButton}
                        type="button"
                        onClick={onEdit}
                    >
                        <Pencil size={16} aria-hidden="true" />
                        Chỉnh sửa
                    </button>
                ) : null}
            </footer>
        </div>
    );
}

function InfoRow({
    icon,
    label,
    value
}: {
    icon: ReactNode;
    label: string;
    value: string;
}) {
    return (
        <div className={styles.detailInfo}>
            {icon}
            <div>
                <strong>{label}</strong>
                <p>{value}</p>
            </div>
        </div>
    );
}

function EventEditForm({
    event,
    form,
    errors,
    eventTypes,
    eventAudiences,
    disabled,
    onCancel,
    onChange,
    onSave
}: {
    event: EventResponse;
    form: EventUpdateRequest;
    errors: EventFormErrors;
    eventTypes: string[];
    eventAudiences: string[];
    disabled: boolean;
    onCancel(): void;
    onChange<K extends keyof EventUpdateRequest>(
        key: K,
        value: EventUpdateRequest[K]
    ): void;
    onSave(): void | Promise<void>;
}) {
    return (
        <div className={styles.eventPanel}>
            <header className={styles.eventPanelHeader}>
                <div>
                    <span className={styles.eventPanelEyebrow}>
                        Chỉnh sửa sự kiện
                    </span>
                    <h2>{event.name}</h2>
                </div>
                <button
                    className={styles.tableIconButton}
                    type="button"
                    onClick={onCancel}
                    aria-label="Hủy chỉnh sửa"
                >
                    <X size={18} aria-hidden="true" />
                </button>
            </header>

            <div className={styles.editFormSections}>
                <section className={styles.editFormSection}>
                    <h3>Thông tin cơ bản</h3>
                    <div className={styles.detailEditGrid}>
                        <label className={styles.detailField}>
                            <span>Tên sự kiện</span>
                            <input
                                value={form.name}
                                onChange={(inputEvent) =>
                                    onChange(
                                        "name",
                                        inputEvent.currentTarget.value
                                    )
                                }
                            />
                            {errors.name ? (
                                <span className={styles.fieldError}>
                                    {errors.name}
                                </span>
                            ) : null}
                        </label>

                        <label className={styles.detailField}>
                            <span>Loại sự kiện</span>
                            <Select
                                className="w-full"
                                value={form.eventType}
                                onValueChange={(value) =>
                                    onChange(
                                        "eventType",
                                        value
                                    )
                                }
                            >
                                <SelectTrigger
                                    aria-label="Loại sự kiện"
                                    className="h-[42px] w-full rounded-lg border-[#cbd5e1] px-3 text-sm text-[#334155] shadow-none hover:bg-white"
                                >
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent className="z-[90] rounded-lg border-[#d0d5dd] p-1 shadow-[0_18px_38px_rgba(15,23,42,0.16)]">
                                    {eventTypes.map((type) => (
                                        <SelectItem key={type} value={type}>
                                            {getEventTypeLabel(type)}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            {errors.eventType ? (
                                <span className={styles.fieldError}>
                                    {errors.eventType}
                                </span>
                            ) : null}
                        </label>

                        <label
                            className={`${styles.detailField} ${styles.detailFieldFull}`}
                        >
                            <span>Mô tả</span>
                            <textarea
                                rows={3}
                                value={form.description ?? ""}
                                onChange={(inputEvent) =>
                                    onChange(
                                        "description",
                                        inputEvent.currentTarget.value
                                    )
                                }
                            />
                            {errors.description ? (
                                <span className={styles.fieldError}>
                                    {errors.description}
                                </span>
                            ) : null}
                        </label>
                    </div>
                </section>

                <section className={styles.editFormSection}>
                    <h3>Thời gian</h3>
                    <div className={styles.detailEditGrid}>
                        <label className={styles.detailField}>
                            <span>Bắt đầu</span>
                            <input
                                type="datetime-local"
                                value={form.startAt}
                                onChange={(inputEvent) =>
                                    onChange(
                                        "startAt",
                                        inputEvent.currentTarget.value
                                    )
                                }
                            />
                            {errors.startAt ? (
                                <span className={styles.fieldError}>
                                    {errors.startAt}
                                </span>
                            ) : null}
                        </label>

                        <label className={styles.detailField}>
                            <span>Kết thúc</span>
                            <input
                                type="datetime-local"
                                value={form.endAt}
                                onChange={(inputEvent) =>
                                    onChange(
                                        "endAt",
                                        inputEvent.currentTarget.value
                                    )
                                }
                            />
                            {errors.endAt ? (
                                <span className={styles.fieldError}>
                                    {errors.endAt}
                                </span>
                            ) : null}
                        </label>
                    </div>
                </section>

                <section className={styles.editFormSection}>
                    <h3>Đối tượng</h3>
                    <label className={styles.detailField}>
                        <span>Loại đối tượng</span>
                        <Select
                            className="w-full"
                            value={form.audience}
                            onValueChange={(value) =>
                                onChange(
                                    "audience",
                                    value
                                )
                            }
                        >
                            <SelectTrigger
                                aria-label="Loại đối tượng"
                                className="h-[42px] w-full rounded-lg border-[#cbd5e1] px-3 text-sm text-[#334155] shadow-none hover:bg-white"
                            >
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent className="z-[90] rounded-lg border-[#d0d5dd] p-1 shadow-[0_18px_38px_rgba(15,23,42,0.16)]">
                                {eventAudiences.map((audience) => (
                                    <SelectItem
                                        key={audience}
                                        value={audience}
                                    >
                                        {getEventAudienceLabel(audience)}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        {errors.audience ? (
                            <span className={styles.fieldError}>
                                {errors.audience}
                            </span>
                        ) : null}
                    </label>
                </section>
            </div>

            <footer className={styles.eventPanelFooter}>
                <button
                    className={styles.secondaryButton}
                    type="button"
                    onClick={onCancel}
                    disabled={disabled}
                >
                    Hủy
                </button>
                <button
                    className={styles.primaryButton}
                    type="button"
                    disabled={disabled}
                    onClick={onSave}
                >
                    Lưu thay đổi
                </button>
            </footer>
        </div>
    );
}
