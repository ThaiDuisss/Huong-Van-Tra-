import {
    Copy,
    MoreVertical,
    Pencil,
    Plus,
    RefreshCw,
    Trash2,
    X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { eventApi } from "../services/event.api";
import { promotionApi } from "../services/promotion.api";
import type { EventResponse } from "../types/event.types";
import type {
    PageResponse,
    PromotionCreateRequest,
    PromotionResponse,
    PromotionUpdateRequest
} from "../types/promotion.types";
import { formatDiscountDate } from "../utils/discount-programs";
import {
    getPromotionApplyMethodLabel,
    getPromotionStatusLabel,
    getPromotionTypeLabel,
    promotionApplyMethodOptions,
    promotionTypeOptions
} from "../utils/promotion-labels";
import {
    canDeletePromotion,
    canEditPromotion,
    getAllowedPromotionActions,
    type PromotionActionType
} from "../utils/promotion-status-transition";
import { routePaths } from "../../../routes/path";

type PromotionMode = "ALL" | "EVENT_FILTERED";

type PromotionFormMode = "create" | "edit";

const emptyPromotionForm: PromotionCreateRequest = {
    eventId: "",
    name: "",
    description: "",
    objective: "",
    type: "",
    applyMethod: "",
    startAt: "",
    endAt: "",
    stackable: false,
    priority: 0
};

const statusClassNames: Record<string, string> = {
    DRAFT: styles.statusInactive,
    SCHEDULED: styles.statusScheduled,
    ACTIVE: styles.statusActive,
    INACTIVE: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    CANCELLED: styles.statusCancelled
};

export function PromotionPage() {
    const [searchParams] = useSearchParams();
    const eventId = searchParams.get("eventId")?.trim() || "";
    const eventName =
        eventId && searchParams.get("eventName")?.trim()
            ? searchParams.get("eventName")?.trim() ?? ""
            : "";
    const displayEventName = eventName || "Sự kiện đã chọn";
    const mode: PromotionMode = eventId ? "EVENT_FILTERED" : "ALL";

    const [resource, setResource] =
        useState<PageResponse<PromotionResponse> | null>(null);
    const [events, setEvents] = useState<EventResponse[]>([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [loading, setLoading] = useState(false);
    const [actionLoadingId, setActionLoadingId] = useState<string | null>(
        null
    );
    const [error, setError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [openMenuId, setOpenMenuId] = useState<string | null>(null);
    const [formState, setFormState] = useState<{
        mode: PromotionFormMode;
        promotion?: PromotionResponse;
    } | null>(null);

    const title = "Chương trình giảm giá";
    const subtitle =
        mode === "EVENT_FILTERED"
            ? "Quản lý các chương trình giảm giá thuộc sự kiện đang chọn"
            : "Quản lý tất cả chương trình giảm giá trong hệ thống";

    const loadPromotions = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const params = {
                page: page - 1,
                size: pageSize
            };

            const promotions =
                mode === "EVENT_FILTERED"
                    ? await promotionApi.getPromotionsByEvent(
                          eventId,
                          params
                      )
                    : await promotionApi.getPromotions(params);

            setResource(promotions);
        } catch (err) {
            console.error("Không thể tải danh sách promotion", err);
            setError(getErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [eventId, mode, page, pageSize]);

    useEffect(() => {
        void loadPromotions();
    }, [loadPromotions, refreshKey]);

    useEffect(() => {
        setPage(1);
        setOpenMenuId(null);
        setFormState(null);
    }, [eventId]);

    useEffect(() => {
        if (mode !== "ALL") {
            return;
        }

        async function loadEventsForForm() {
            try {
                const response = await eventApi.list({
                    page: 0,
                    size: 100
                });
                setEvents(response.content ?? []);
            } catch (err) {
                console.error("Không thể tải danh sách event", err);
            }
        }

        void loadEventsForForm();
    }, [mode]);

    const promotions = resource?.content ?? [];
    const pageCount = Math.max(1, resource?.totalPages ?? 1);
    const totalItems = resource?.totalElements ?? promotions.length;
    const safePage = Math.min(page, pageCount);

    const formInitialValue = useMemo<PromotionCreateRequest>(
        () =>
            formState?.promotion
                ? createPromotionForm(formState.promotion)
                : {
                      ...emptyPromotionForm,
                      eventId: mode === "EVENT_FILTERED" ? eventId : "",
                      type: promotionTypeOptions[0] ?? "",
                      applyMethod: promotionApplyMethodOptions[0] ?? ""
                  },
        [eventId, formState?.promotion, mode]
    );

    function reload() {
        setRefreshKey((current) => current + 1);
    }

    function updatePageSize(nextPageSize: number) {
        setPageSize(nextPageSize);
        setPage(1);
    }

    async function submitPromotion(input: PromotionCreateRequest) {
        const validationError = validatePromotion(input, mode);

        if (validationError) {
            setError(validationError);
            return;
        }

        const payload = normalizePromotionPayload(input);

        try {
            setActionLoadingId(formState?.promotion?.id ?? "create");
            setError(null);

            if (formState?.mode === "edit" && formState.promotion) {
                await promotionApi.updatePromotion(
                    formState.promotion.id,
                    omitEventId(payload)
                );
            } else if (mode === "EVENT_FILTERED") {
                await promotionApi.createPromotionByEvent(eventId, {
                    ...payload,
                    eventId
                });
            } else {
                await promotionApi.createPromotion(payload);
            }

            setFormState(null);
            reload();
        } catch (err) {
            console.error("Không thể lưu promotion", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoadingId(null);
        }
    }

    async function handlePromotionAction(
        promotion: PromotionResponse,
        action: PromotionActionType
    ) {
        if (
            (action === "cancel" || action === "expire") &&
            !window.confirm(
                `${getPromotionActionLabel(
                    action
                )} promotion?\n\nPromotion: ${promotion.name}`
            )
        ) {
            return;
        }

        try {
            setActionLoadingId(promotion.id);
            setOpenMenuId(null);
            setError(null);

            switch (action) {
                case "schedule":
                    await promotionApi.schedulePromotion(promotion.id);
                    break;
                case "activate":
                    await promotionApi.activatePromotion(promotion.id);
                    break;
                case "pause":
                    await promotionApi.pausePromotion(promotion.id);
                    break;
                case "resume":
                    await promotionApi.resumePromotion(promotion.id);
                    break;
                case "cancel":
                    await promotionApi.cancelPromotion(promotion.id);
                    break;
                case "expire":
                    await promotionApi.expirePromotion(promotion.id);
                    break;
                case "duplicate":
                    await promotionApi.duplicatePromotion(promotion.id);
                    break;
            }

            reload();
        } catch (err) {
            console.error("Không thể cập nhật promotion", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoadingId(null);
        }
    }

    async function deletePromotion(promotion: PromotionResponse) {
        if (
            !window.confirm(
                `Xóa promotion?\n\nHành động này không thể hoàn tác.\n\nPromotion: ${promotion.name}`
            )
        ) {
            return;
        }

        try {
            setActionLoadingId(promotion.id);
            setOpenMenuId(null);
            setError(null);
            await promotionApi.deletePromotion(promotion.id);
            reload();
        } catch (err) {
            console.error("Không thể xóa promotion", err);
            setError(getErrorMessage(err));
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
                        {mode === "EVENT_FILTERED" ? (
                            <>
                                <Link
                                    to={routePaths.marketingEvents}
                                    className="font-bold text-[#009b4d]"
                                >
                                    Sự kiện
                                </Link>
                                <span aria-hidden="true">›</span>
                                <span className="font-bold text-[#009b4d]">
                                    {displayEventName}
                                </span>
                                <span aria-hidden="true">›</span>
                            </>
                        ) : (
                            <>
                                <span className="font-bold text-[#009b4d]">
                                    Marketing
                                </span>
                                <span aria-hidden="true">›</span>
                            </>
                        )}
                        <span className="font-bold text-[#17213a]">
                            Chương trình giảm giá
                        </span>
                    </nav>
                    <h1 className={styles.title}>{title}</h1>
                    <p className={styles.description}>{subtitle}</p>
                </div>

                <div className={styles.actions}>
                    <button
                        className={styles.primaryButton}
                        type="button"
                        onClick={() =>
                            setFormState({
                                mode: "create"
                            })
                        }
                    >
                        <Plus size={18} aria-hidden="true" />
                        Tạo promotion
                    </button>
                    <button
                        className={styles.secondaryButton}
                        type="button"
                        disabled={loading}
                        onClick={reload}
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Tải lại
                    </button>
                </div>
            </header>

            {mode === "EVENT_FILTERED" ? (
                <div className={styles.contextBanner}>
                    <div>
                        <strong>Event: {displayEventName}</strong>
                        <p>
                            Đang xem promotion thuộc event này. Tạo mới sẽ tự
                            gắn vào event hiện tại.
                        </p>
                    </div>
                    <Link
                        className={styles.secondaryButton}
                        to={routePaths.discountPrograms}
                    >
                        Xem tất cả promotion
                    </Link>
                </div>
            ) : null}

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <section className={styles.tableCard}>
                <div className={styles.tableScroll}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th>Tên promotion</th>
                                <th>Event</th>
                                <th>Type</th>
                                <th>Apply Method</th>
                                <th>Bắt đầu</th>
                                <th>Kết thúc</th>
                                <th>Stackable</th>
                                <th>Priority</th>
                                <th>Status</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {promotions.map((promotion, index) => {
                                const actions =
                                    getAllowedPromotionActions(promotion);
                                const disabled =
                                    actionLoadingId === promotion.id;
                                const editable =
                                    canEditPromotion(promotion);
                                const deletable =
                                    canDeletePromotion(promotion);
                                const zebraClass =
                                    index % 2 === 0
                                        ? styles.rowOdd
                                        : styles.rowEven;

                                return (
                                    <tr
                                        key={promotion.id}
                                        className={zebraClass}
                                    >
                                        <td>
                                            <strong
                                                className={
                                                    styles.programNameMuted
                                                }
                                            >
                                                {promotion.name}
                                            </strong>
                                        </td>
                                        <td>
                                            {promotion.eventName ||
                                                (mode === "EVENT_FILTERED"
                                                    ? displayEventName
                                                    : promotion.eventId)}
                                        </td>
                                        <td>
                                            {getPromotionTypeLabel(
                                                promotion.type
                                            )}
                                        </td>
                                        <td>
                                            {getPromotionApplyMethodLabel(
                                                promotion.applyMethod
                                            )}
                                        </td>
                                        <td>
                                            {formatDiscountDate(
                                                promotion.startAt
                                            )}
                                        </td>
                                        <td>
                                            {formatDiscountDate(
                                                promotion.endAt
                                            )}
                                        </td>
                                        <td>
                                            {promotion.stackable
                                                ? "Có"
                                                : "Không"}
                                        </td>
                                        <td>{promotion.priority ?? 0}</td>
                                        <td>
                                            <PromotionStatusBadge
                                                promotion={promotion}
                                            />
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
                                                    aria-label={`Mở thao tác ${promotion.name}`}
                                                    onClick={() =>
                                                        setOpenMenuId(
                                                            openMenuId ===
                                                                promotion.id
                                                                ? null
                                                                : promotion.id
                                                        )
                                                    }
                                                >
                                                    <MoreVertical
                                                        size={19}
                                                        aria-hidden="true"
                                                    />
                                                </button>

                                                {openMenuId ===
                                                promotion.id ? (
                                                    <div
                                                        className={
                                                            styles.rowActionMenu
                                                        }
                                                    >
                                                        {editable ? (
                                                            <button
                                                                type="button"
                                                                onClick={() => {
                                                                    setOpenMenuId(
                                                                        null
                                                                    );
                                                                    setFormState(
                                                                        {
                                                                            mode: "edit",
                                                                            promotion
                                                                        }
                                                                    );
                                                                }}
                                                            >
                                                                <Pencil
                                                                    size={
                                                                        15
                                                                    }
                                                                    aria-hidden="true"
                                                                />
                                                                Chỉnh sửa
                                                            </button>
                                                        ) : null}

                                                        {actions.map(
                                                            (action) => (
                                                                <button
                                                                    key={
                                                                        action.action
                                                                    }
                                                                    className={
                                                                        action.danger
                                                                            ? styles.menuDangerItem
                                                                            : undefined
                                                                    }
                                                                    type="button"
                                                                    disabled={
                                                                        disabled
                                                                    }
                                                                    onClick={() =>
                                                                        handlePromotionAction(
                                                                            promotion,
                                                                            action.action
                                                                        )
                                                                    }
                                                                >
                                                                    {action.action ===
                                                                    "duplicate" ? (
                                                                        <Copy
                                                                            size={
                                                                                15
                                                                            }
                                                                            aria-hidden="true"
                                                                        />
                                                                    ) : null}
                                                                    {
                                                                        action.label
                                                                    }
                                                                </button>
                                                            )
                                                        )}

                                                        {deletable ? (
                                                            <button
                                                                className={
                                                                    styles.menuDangerItem
                                                                }
                                                                type="button"
                                                                disabled={
                                                                    disabled
                                                                }
                                                                onClick={() =>
                                                                    deletePromotion(
                                                                        promotion
                                                                    )
                                                                }
                                                            >
                                                                <Trash2
                                                                    size={
                                                                        15
                                                                    }
                                                                    aria-hidden="true"
                                                                />
                                                                Xóa
                                                            </button>
                                                        ) : null}
                                                    </div>
                                                ) : null}
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>

                {!loading && promotions.length === 0 ? (
                    <div className={styles.empty}>
                        Chưa có chương trình giảm giá phù hợp.
                    </div>
                ) : null}

                {loading ? (
                    <div className={styles.empty}>Đang tải dữ liệu...</div>
                ) : null}

                <MarketingPagination
                    page={safePage}
                    pageCount={pageCount}
                    totalItems={totalItems}
                    pageSize={pageSize}
                    onPageChange={setPage}
                    onPageSizeChange={updatePageSize}
                />
            </section>

            {formState ? (
                <PromotionFormModal
                    key={`${formState.mode}-${formState.promotion?.id ?? "new"}`}
                    mode={formState.mode}
                    value={formInitialValue}
                    promotionMode={mode}
                    eventName={displayEventName}
                    events={events}
                    submitting={actionLoadingId !== null}
                    onClose={() => setFormState(null)}
                    onSubmit={submitPromotion}
                />
            ) : null}
        </section>
    );
}

function PromotionStatusBadge({
    promotion
}: {
    promotion: PromotionResponse;
}) {
    return (
        <span
            className={`${styles.statusBadge} ${
                statusClassNames[promotion.status] ?? styles.statusInactive
            }`}
        >
            {getPromotionStatusLabel(promotion.status)}
        </span>
    );
}

function PromotionFormModal({
    mode,
    value,
    promotionMode,
    eventName,
    events,
    submitting,
    onClose,
    onSubmit
}: {
    mode: PromotionFormMode;
    value: PromotionCreateRequest;
    promotionMode: PromotionMode;
    eventName: string;
    events: EventResponse[];
    submitting: boolean;
    onClose(): void;
    onSubmit(input: PromotionCreateRequest): void;
}) {
    const [form, setForm] = useState(value);

    function updateField<K extends keyof PromotionCreateRequest>(
        key: K,
        nextValue: PromotionCreateRequest[K]
    ) {
        setForm((current) => ({
            ...current,
            [key]: nextValue
        }));
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
            <section className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
                <header className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-bold text-[#17213a]">
                            {mode === "create"
                                ? "Tạo promotion"
                                : "Chỉnh sửa promotion"}
                        </h2>
                        <p className="mt-1 text-sm font-medium text-[#667085]">
                            Không set status trong form. Trạng thái được đổi
                            bằng action riêng.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg p-2 hover:bg-[#f2f4f7]"
                        aria-label="Đóng form"
                    >
                        <X size={20} aria-hidden="true" />
                    </button>
                </header>

                <form
                    className={styles.editFormSections}
                    onSubmit={(event) => {
                        event.preventDefault();
                        onSubmit(form);
                    }}
                >
                    <section className={styles.editFormSection}>
                        <h3>Thông tin cơ bản</h3>
                        <div className={styles.detailEditGrid}>
                            {promotionMode === "ALL" ? (
                                <label className={styles.detailField}>
                                    <span>Event</span>
                                    <select
                                        value={form.eventId ?? ""}
                                        onChange={(event) =>
                                            updateField(
                                                "eventId",
                                                event.currentTarget.value
                                            )
                                        }
                                    >
                                        <option value="">
                                            Chọn event
                                        </option>
                                        {events.map((event) => (
                                            <option
                                                key={event.id}
                                                value={event.id}
                                            >
                                                {event.name}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                            ) : (
                                <div className={styles.detailField}>
                                    <span>Event</span>
                                    <p className={styles.readonlyValue}>
                                        {eventName}
                                    </p>
                                </div>
                            )}

                            <label className={styles.detailField}>
                                <span>Tên promotion</span>
                                <input
                                    value={form.name}
                                    onChange={(event) =>
                                        updateField(
                                            "name",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </label>

                            <label className={styles.detailField}>
                                <span>Type</span>
                                <select
                                    value={form.type}
                                    onChange={(event) =>
                                        updateField(
                                            "type",
                                            event.currentTarget.value as PromotionCreateRequest["type"]
                                        )
                                    }
                                >
                                    {promotionTypeOptions.map((type) => (
                                        <option key={type} value={type}>
                                            {getPromotionTypeLabel(type)}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label className={styles.detailField}>
                                <span>Apply Method</span>
                                <select
                                    value={form.applyMethod}
                                    onChange={(event) =>
                                        updateField(
                                            "applyMethod",
                                            event.currentTarget.value as PromotionCreateRequest["applyMethod"]
                                        )
                                    }
                                >
                                    {promotionApplyMethodOptions.map(
                                        (method) => (
                                            <option
                                                key={method}
                                                value={method}
                                            >
                                                {getPromotionApplyMethodLabel(
                                                    method
                                                )}
                                            </option>
                                        )
                                    )}
                                </select>
                            </label>

                            <label
                                className={`${styles.detailField} ${styles.detailFieldFull}`}
                            >
                                <span>Mô tả</span>
                                <textarea
                                    rows={3}
                                    value={form.description ?? ""}
                                    onChange={(event) =>
                                        updateField(
                                            "description",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </label>

                            <label
                                className={`${styles.detailField} ${styles.detailFieldFull}`}
                            >
                                <span>Mục tiêu</span>
                                <textarea
                                    rows={3}
                                    value={form.objective ?? ""}
                                    onChange={(event) =>
                                        updateField(
                                            "objective",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </label>
                        </div>
                    </section>

                    <section className={styles.editFormSection}>
                        <h3>Thời gian và ưu tiên</h3>
                        <div className={styles.detailEditGrid}>
                            <label className={styles.detailField}>
                                <span>Bắt đầu</span>
                                <input
                                    type="datetime-local"
                                    value={form.startAt}
                                    onChange={(event) =>
                                        updateField(
                                            "startAt",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </label>
                            <label className={styles.detailField}>
                                <span>Kết thúc</span>
                                <input
                                    type="datetime-local"
                                    value={form.endAt}
                                    onChange={(event) =>
                                        updateField(
                                            "endAt",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </label>
                            <label className={styles.detailField}>
                                <span>Priority</span>
                                <input
                                    type="number"
                                    min={0}
                                    value={form.priority ?? 0}
                                    onChange={(event) =>
                                        updateField(
                                            "priority",
                                            Number(
                                                event.currentTarget.value
                                            )
                                        )
                                    }
                                />
                            </label>
                            <label className={styles.checkboxField}>
                                <input
                                    type="checkbox"
                                    checked={Boolean(form.stackable)}
                                    onChange={(event) =>
                                        updateField(
                                            "stackable",
                                            event.currentTarget.checked
                                        )
                                    }
                                />
                                <span>Cho phép dùng chung promotion khác</span>
                            </label>
                        </div>
                    </section>

                    <footer className={styles.eventPanelFooter}>
                        <button
                            className={styles.secondaryButton}
                            type="button"
                            disabled={submitting}
                            onClick={onClose}
                        >
                            Hủy
                        </button>
                        <button
                            className={styles.primaryButton}
                            type="submit"
                            disabled={submitting}
                        >
                            {submitting ? "Đang lưu..." : "Lưu"}
                        </button>
                    </footer>
                </form>
            </section>
        </div>
    );
}

function createPromotionForm(
    promotion: PromotionResponse
): PromotionCreateRequest {
    return {
        eventId: promotion.eventId,
        name: promotion.name,
        description: promotion.description ?? "",
        objective: promotion.objective ?? "",
        type: promotion.type,
        applyMethod: promotion.applyMethod,
        startAt: toDateTimeLocal(promotion.startAt),
        endAt: toDateTimeLocal(promotion.endAt),
        stackable: Boolean(promotion.stackable),
        priority: promotion.priority ?? 0
    };
}

function normalizePromotionPayload(
    input: PromotionCreateRequest
): PromotionCreateRequest {
    return {
        ...input,
        eventId: input.eventId || undefined,
        name: input.name.trim(),
        description: input.description?.trim() || undefined,
        objective: input.objective?.trim() || undefined,
        stackable: Boolean(input.stackable),
        priority: Number(input.priority ?? 0)
    };
}

function omitEventId(
    input: PromotionCreateRequest
): PromotionUpdateRequest {
    const { eventId: _eventId, ...payload } = input;

    return payload;
}

function validatePromotion(
    input: PromotionCreateRequest,
    mode: PromotionMode
) {
    if (mode === "ALL" && !input.eventId) {
        return "Vui lòng chọn event.";
    }

    if (!input.name.trim()) {
        return "Tên promotion là bắt buộc.";
    }

    if (!input.type) {
        return "Vui lòng chọn type.";
    }

    if (!input.applyMethod) {
        return "Vui lòng chọn apply method.";
    }

    if (!input.startAt || !input.endAt) {
        return "Vui lòng chọn thời gian bắt đầu và kết thúc.";
    }

    if (new Date(input.startAt).getTime() >= new Date(input.endAt).getTime()) {
        return "Thời gian kết thúc phải sau thời gian bắt đầu.";
    }

    if (Number(input.priority ?? 0) < 0) {
        return "Priority phải lớn hơn hoặc bằng 0.";
    }

    return null;
}

function toDateTimeLocal(value: string) {
    return value ? value.slice(0, 16) : "";
}

function getPromotionActionLabel(action: PromotionActionType) {
    const labels: Record<PromotionActionType, string> = {
        schedule: "Lên lịch",
        activate: "Kích hoạt",
        pause: "Tạm khóa",
        resume: "Tiếp tục",
        cancel: "Hủy",
        expire: "Đánh dấu hết hạn",
        duplicate: "Sao chép"
    };

    return labels[action];
}

function getErrorMessage(error: unknown) {
    if (error instanceof Error && error.message) {
        return error.message;
    }

    if (typeof error === "object" && error) {
        const maybeMessage =
            "message" in error ? (error as { message?: unknown }).message : null;

        if (typeof maybeMessage === "string" && maybeMessage.trim()) {
            return maybeMessage;
        }
    }

    return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}
