import {
    CalendarDays,
    Copy,
    MoreVertical,
    Pencil,
    Plus,
    RefreshCw,
    Search,
    SlidersHorizontal,
    Trash2,
    X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import { MarketingPagination } from "../components/MarketingPagination";
import { MarketingSelectField } from "../components/MarketingSelectField";
import styles from "../components/discount-programs/DiscountProgramPage.module.css";
import { eventApi } from "../services/event.api";
import { promotionApi } from "../services/promotion.api";
import type { EventResponse } from "../types/event.types";
import type {
    PageResponse,
    PromotionCreateRequest,
    PromotionQuery,
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

interface PromotionFilterFormState {
    keyword: string;
    eventId: string;
    type: string;
    applyMethod: string;
    status: string;
    startFrom: string;
    startTo: string;
    endFrom: string;
    endTo: string;
}

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
    PAUSED: styles.statusPaused,
    EXPIRED: styles.statusExpired,
    ENDED: styles.statusUsed,
    CANCELLED: styles.statusCancelled
};

const promotionStatusOptions = Object.keys(statusClassNames);

const emptyPromotionFilters: PromotionFilterFormState = {
    keyword: "",
    eventId: "ALL",
    type: "ALL",
    applyMethod: "ALL",
    status: "ALL",
    startFrom: "",
    startTo: "",
    endFrom: "",
    endTo: ""
};

export function PromotionPage() {
    const navigate = useNavigate();
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
    const [filters, setFilters] = useState<PromotionFilterFormState>(
        emptyPromotionFilters
    );
    const [appliedFilters, setAppliedFilters] =
        useState<PromotionFilterFormState>(emptyPromotionFilters);
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

            const params: PromotionQuery = {
                page: page - 1,
                size: pageSize,
                ...buildPromotionQuery(appliedFilters)
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
            console.error("Không thể tải danh sách khuyến mãi", err);
            setError(getErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }, [appliedFilters, eventId, mode, page, pageSize]);

    useEffect(() => {
        void loadPromotions();
    }, [loadPromotions, refreshKey]);

    useEffect(() => {
        setPage(1);
        setOpenMenuId(null);
        setFormState(null);
        setFilters(emptyPromotionFilters);
        setAppliedFilters(emptyPromotionFilters);
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

    function searchPromotions() {
        setAppliedFilters(filters);
        setPage(1);
        setOpenMenuId(null);
    }

    function clearPromotionFilters() {
        setFilters(emptyPromotionFilters);
        setAppliedFilters(emptyPromotionFilters);
        setPage(1);
        setOpenMenuId(null);
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
            console.error("Không thể lưu khuyến mãi", err);
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
            (action === "cancel" || action === "expire" || action === "end") &&
            !window.confirm(
                `${getPromotionActionLabel(
                    action
                )} khuyến mãi?\n\nKhuyến mãi: ${promotion.name}`
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
                case "end":
                    await promotionApi.endPromotion(promotion.id);
                    break;
                case "duplicate":
                    await promotionApi.duplicatePromotion(promotion.id);
                    break;
            }

            reload();
        } catch (err) {
            console.error("Không thể cập nhật khuyến mãi", err);
            setError(getErrorMessage(err));
        } finally {
            setActionLoadingId(null);
        }
    }

    async function deletePromotion(promotion: PromotionResponse) {
        if (
            !window.confirm(
                `Xóa khuyến mãi?\n\nHành động này không thể hoàn tác.\n\nKhuyến mãi: ${promotion.name}`
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
            console.error("Không thể xóa khuyến mãi", err);
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
                        Tạo khuyến mãi
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
                        <strong>Sự kiện: {displayEventName}</strong>
                        <p>
                            Đang xem khuyến mãi thuộc sự kiện này. Tạo mới sẽ tự
                            gắn vào sự kiện hiện tại.
                        </p>
                    </div>
                    <Link
                        className={styles.secondaryButton}
                        to={routePaths.discountPrograms}
                    >
                        Xem tất cả khuyến mãi
                    </Link>
                </div>
            ) : null}

            {error ? (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <PromotionFilters
                filters={filters}
                events={events}
                mode={mode}
                loading={loading}
                showClear={hasPromotionFilters(appliedFilters)}
                onFiltersChange={setFilters}
                onSearch={searchPromotions}
                onClear={clearPromotionFilters}
            />

            <section className={styles.tableCard}>
                <div className={styles.tableScroll}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th>Tên khuyến mãi</th>
                                <th>Trạng thái</th>
                                <th>Sự kiện</th>
                                <th>Cách áp dụng</th>
                                <th>Bắt đầu</th>
                                <th>Kết thúc</th>
                                <th>Thao tác</th>
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
                                        className={`${zebraClass} ${styles.summaryRow}`}
                                        onClick={() =>
                                            navigate(
                                                getPromotionDetailPath(
                                                    promotion.id
                                                )
                                            )
                                        }
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
                                            <PromotionStatusBadge
                                                promotion={promotion}
                                            />
                                        </td>
                                        <td>
                                            {formatPromotionEventName(
                                                promotion,
                                                mode,
                                                displayEventName
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
                                        <td
                                            className={styles.actionCell}
                                            onClick={(event) =>
                                                event.stopPropagation()
                                            }
                                        >
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

function PromotionFilters({
    filters,
    events,
    mode,
    loading,
    showClear,
    onFiltersChange,
    onSearch,
    onClear
}: {
    filters: PromotionFilterFormState;
    events: EventResponse[];
    mode: PromotionMode;
    loading: boolean;
    showClear: boolean;
    onFiltersChange(value: PromotionFilterFormState): void;
    onSearch(): void;
    onClear(): void;
}) {
    const [detailOpen, setDetailOpen] = useState(false);
    const hasDetailFilters =
        (mode === "ALL" && filters.eventId !== "ALL") ||
        filters.type !== "ALL" ||
        filters.applyMethod !== "ALL" ||
        filters.status !== "ALL" ||
        filters.startFrom.length > 0 ||
        filters.startTo.length > 0 ||
        filters.endFrom.length > 0 ||
        filters.endTo.length > 0;

    return (
        <section className={styles.filtersCard}>
            <form
                className={styles.filters}
                onSubmit={(event) => {
                    event.preventDefault();
                    onSearch();
                }}
            >
                <div className={styles.filterMainRow}>
                    <label className={styles.search}>
                        <Search size={18} aria-hidden="true" />
                        <span className="sr-only">Tìm kiếm khuyến mãi</span>
                        <input
                            className={styles.searchInput}
                            value={filters.keyword}
                            placeholder="Tìm kiếm khuyến mãi..."
                            onChange={(event) =>
                                onFiltersChange({
                                    ...filters,
                                    keyword: event.currentTarget.value
                                })
                            }
                        />
                    </label>

                    <button
                        className={`${styles.filterToggleButton} ${
                            detailOpen || hasDetailFilters
                                ? styles.filterToggleButtonActive
                                : ""
                        }`}
                        type="button"
                        aria-label="Lọc chi tiết"
                        aria-expanded={detailOpen}
                        onClick={() =>
                            setDetailOpen((current) => !current)
                        }
                    >
                        <SlidersHorizontal
                            size={18}
                            aria-hidden="true"
                        />
                    </button>

                    <button
                        className={styles.primaryButton}
                        type="submit"
                        disabled={loading}
                    >
                        <Search size={17} aria-hidden="true" />
                        Tìm kiếm
                    </button>

                    {showClear ? (
                        <button
                            className={styles.secondaryButton}
                            type="button"
                            disabled={loading}
                            onClick={onClear}
                        >
                            <X size={17} aria-hidden="true" />
                            Xóa lọc
                        </button>
                    ) : null}
                </div>

                {detailOpen ? (
                    <div className={styles.filterDetailPanel}>
                        {mode === "ALL" ? (
                            <MarketingSelectField
                                label="Sự kiện"
                                value={filters.eventId}
                                onChange={(eventId) =>
                                    onFiltersChange({
                                        ...filters,
                                        eventId
                                    })
                                }
                                options={[
                                    {
                                        label: "Tất cả sự kiện",
                                        value: "ALL"
                                    },
                                    ...events.map((event) => ({
                                        label: event.name,
                                        value: event.id
                                    }))
                                ]}
                            />
                        ) : null}

                        <MarketingSelectField
                            label="Loại khuyến mãi"
                            value={filters.type}
                            onChange={(type) =>
                                onFiltersChange({ ...filters, type })
                            }
                            options={[
                                {
                                    label: "Tất cả loại khuyến mãi",
                                    value: "ALL"
                                },
                                ...promotionTypeOptions.map((type) => ({
                                    label: getPromotionTypeLabel(type),
                                    value: type
                                }))
                            ]}
                        />

                        <MarketingSelectField
                            label="Cách áp dụng"
                            value={filters.applyMethod}
                            onChange={(applyMethod) =>
                                onFiltersChange({
                                    ...filters,
                                    applyMethod
                                })
                            }
                            options={[
                                {
                                    label: "Tất cả cách áp dụng",
                                    value: "ALL"
                                },
                                ...promotionApplyMethodOptions.map(
                                    (method) => ({
                                        label: getPromotionApplyMethodLabel(
                                            method
                                        ),
                                        value: method
                                    })
                                )
                            ]}
                        />

                        <MarketingSelectField
                            label="Trạng thái"
                            value={filters.status}
                            onChange={(status) =>
                                onFiltersChange({ ...filters, status })
                            }
                            options={[
                                {
                                    label: "Tất cả trạng thái",
                                    value: "ALL"
                                },
                                ...promotionStatusOptions.map((status) => ({
                                    label: getPromotionStatusLabel(status),
                                    value: status
                                }))
                            ]}
                        />

                        <div className={styles.filterDateGroup}>
                            <DateFilterField
                                label="Bắt đầu từ"
                                value={filters.startFrom}
                                onChange={(startFrom) =>
                                    onFiltersChange({
                                        ...filters,
                                        startFrom
                                    })
                                }
                            />
                            <DateFilterField
                                label="Bắt đầu đến"
                                value={filters.startTo}
                                onChange={(startTo) =>
                                    onFiltersChange({
                                        ...filters,
                                        startTo
                                    })
                                }
                            />
                        </div>

                        <div className={styles.filterDateGroup}>
                            <DateFilterField
                                label="Kết thúc từ"
                                value={filters.endFrom}
                                onChange={(endFrom) =>
                                    onFiltersChange({
                                        ...filters,
                                        endFrom
                                    })
                                }
                            />
                            <DateFilterField
                                label="Kết thúc đến"
                                value={filters.endTo}
                                onChange={(endTo) =>
                                    onFiltersChange({
                                        ...filters,
                                        endTo
                                    })
                                }
                            />
                        </div>
                    </div>
                ) : null}
            </form>
        </section>
    );
}

function DateFilterField({
    label,
    value,
    onChange
}: {
    label: string;
    value: string;
    onChange(value: string): void;
}) {
    return (
        <label className={styles.filterField}>
            <span className={styles.filterFieldLabel}>{label}</span>
            <span className={styles.dateWrap}>
                <CalendarDays size={17} aria-hidden="true" />
                <input
                    type="date"
                    className={styles.dateInput}
                    value={value}
                    aria-label={label}
                    onChange={(event) =>
                        onChange(event.currentTarget.value)
                    }
                />
            </span>
        </label>
    );
}

function buildPromotionQuery(
    filters: PromotionFilterFormState
): Omit<PromotionQuery, "page" | "size"> {
    return {
        keyword: filters.keyword.trim() || undefined,
        eventId:
            filters.eventId !== "ALL" ? filters.eventId : undefined,
        type: filters.type !== "ALL" ? filters.type : undefined,
        applyMethod:
            filters.applyMethod !== "ALL"
                ? filters.applyMethod
                : undefined,
        status: filters.status !== "ALL" ? filters.status : undefined,
        startFrom: toStartOfDay(filters.startFrom),
        startTo: toEndOfDay(filters.startTo),
        endFrom: toStartOfDay(filters.endFrom),
        endTo: toEndOfDay(filters.endTo)
    };
}

function hasPromotionFilters(filters: PromotionFilterFormState) {
    return Object.values(buildPromotionQuery(filters)).some(Boolean);
}

function toStartOfDay(value: string) {
    return value ? `${value}T00:00:00` : undefined;
}

function toEndOfDay(value: string) {
    return value ? `${value}T23:59:59` : undefined;
}

function getPromotionDetailPath(promotionId: string) {
    return routePaths.discountProgramDetail.replace(
        ":programId",
        encodeURIComponent(promotionId)
    );
}

function formatPromotionEventName(
    promotion: PromotionResponse,
    mode: PromotionMode,
    displayEventName: string
) {
    return (
        promotion.eventName?.trim() ||
        (mode === "EVENT_FILTERED"
            ? displayEventName
            : promotion.eventId?.trim()) ||
        "Không có"
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
                                ? "Tạo khuyến mãi"
                                : "Chỉnh sửa khuyến mãi"}
                        </h2>
                        <p className="mt-1 text-sm font-medium text-[#667085]">
                            Không chọn trạng thái trong form. Trạng thái được
                            đổi bằng thao tác riêng.
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
                                    <span>Sự kiện</span>
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
                                            Chọn sự kiện
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
                                    <span>Sự kiện</span>
                                    <p className={styles.readonlyValue}>
                                        {eventName}
                                    </p>
                                </div>
                            )}

                            <label className={styles.detailField}>
                                <span>Tên khuyến mãi</span>
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
                                <span>Loại</span>
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
                                <span>Cách áp dụng</span>
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
                                <span>Độ ưu tiên</span>
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
                                <span>Cho phép dùng chung khuyến mãi khác</span>
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
        return "Tên khuyến mãi là bắt buộc.";
    }

    if (!input.type) {
        return "Vui lòng chọn loại khuyến mãi.";
    }

    if (!input.applyMethod) {
        return "Vui lòng chọn cách áp dụng.";
    }

    if (!input.startAt || !input.endAt) {
        return "Vui lòng chọn thời gian bắt đầu và kết thúc.";
    }

    if (new Date(input.startAt).getTime() >= new Date(input.endAt).getTime()) {
        return "Thời gian kết thúc phải sau thời gian bắt đầu.";
    }

    if (Number(input.priority ?? 0) < 0) {
        return "Độ ưu tiên phải lớn hơn hoặc bằng 0.";
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
        end: "Kết thúc",
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
