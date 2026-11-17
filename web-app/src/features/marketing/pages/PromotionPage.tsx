import {
    AlertCircle,
    CalendarDays,
    Copy,
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

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { type RowAction, RowActionMenu } from "../../../components/shared/RowActionMenu";
import { Button, IconButton } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import {
    Field,
    NativeSelect,
    TextArea,
    TextInput
} from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { SearchInput } from "../../../components/ui/SearchInput";
import {
    StatusBadge,
    type BadgeTone
} from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import { MarketingPagination } from "../components/MarketingPagination";
import { MarketingSelectField } from "../components/MarketingSelectField";
import {
    MarketingTableSkeleton,
    MarketingToast,
    type MarketingNotice
} from "../components/MarketingFeedback";
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
import {
    formatDiscountDateParts
} from "../utils/discount-programs";
import { getMarketingStatusTone } from "../utils/event-labels";
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

const promotionStatusOptions = [
    "DRAFT",
    "SCHEDULED",
    "ACTIVE",
    "PAUSED",
    "EXPIRED",
    "ENDED",
    "CANCELLED"
];

const applyMethodTones: Record<string, BadgeTone> = {
    AUTO: "info",
    COUPON_CODE: "success",
    DIRECT_DISCOUNT: "warning"
};

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
    const [notice, setNotice] = useState<MarketingNotice | null>(null);
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
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
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
                setEvents(
                    (response.content ?? []).filter((event) => !event.isDeleted)
                );
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

            setNotice({
                type: "success",
                message:
                    formState?.mode === "edit"
                        ? "Cập nhật chương trình giảm giá thành công."
                        : "Tạo chương trình giảm giá thành công."
            });
            setFormState(null);
            reload();
        } catch (err) {
            console.error("Không thể lưu khuyến mãi", err);
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
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

            setNotice({
                type: "success",
                message: `${getPromotionActionLabel(action)} chương trình thành công.`
            });
            reload();
        } catch (err) {
            console.error("Không thể cập nhật khuyến mãi", err);
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
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
            setNotice({
                type: "success",
                message: "Xóa chương trình giảm giá thành công."
            });
            reload();
        } catch (err) {
            console.error("Không thể xóa khuyến mãi", err);
            const message = getErrorMessage(err);
            setError(message);
            setNotice({ type: "error", message });
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
                            onClick={() =>
                                setFormState({
                                    mode: "create"
                                })
                            }
                        >
                            <Plus size={18} aria-hidden="true" />
                            Tạo khuyến mãi
                        </Button>
                        <Button disabled={loading} onClick={reload}>
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
                }
                description={subtitle}
                title={title}
            />

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
                        className={buttonClassName({ variant: "secondary" })}
                        to={routePaths.discountPrograms}
                    >
                        Xem tất cả khuyến mãi
                    </Link>
                </div>
            ) : null}

            {error ? (
                <div className={styles.pageAlert} role="alert">
                    <AlertCircle size={18} aria-hidden="true" />
                    <div>
                        <strong>Không thể tải dữ liệu</strong>
                        <p>{error}</p>
                    </div>
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
                    <Table className={`${styles.table} ${styles.promotionTable}`}>
                        <colgroup>
                            <col className={styles.promotionNameColumn} />
                            <col className={styles.promotionStatusColumn} />
                            <col className={styles.promotionApplyColumn} />
                            <col className={styles.promotionTimeColumn} />
                            <col className={styles.promotionActionColumn} />
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableHeaderCell>Tên khuyến mãi</TableHeaderCell>
                                <TableHeaderCell>Trạng thái</TableHeaderCell>
                                <TableHeaderCell>Cách áp dụng</TableHeaderCell>
                                <TableHeaderCell>Thời gian</TableHeaderCell>
                                <TableHeaderCell>Thao tác</TableHeaderCell>
                            </TableRow>
                        </TableHead>
                        <tbody>
                            {loading ? (
                                <MarketingTableSkeleton columns={5} />
                            ) : promotions.map((promotion) => {
                                const actions =
                                    getAllowedPromotionActions(promotion);
                                const disabled =
                                    actionLoadingId === promotion.id;
                                const editable =
                                    canEditPromotion(promotion);
                                const deletable =
                                    canDeletePromotion(promotion);
                                return (
                                    <TableRow
                                        key={promotion.id}
                                        className={styles.summaryRow}
                                        striped
                                        onClick={() =>
                                            navigate(
                                                getPromotionDetailPath(
                                                    promotion.id
                                                )
                                            )
                                        }
                                    >
                                        <TableCell>
                                            <strong
                                                className={
                                                    styles.programNameMuted
                                                }
                                            >
                                                {promotion.name}
                                            </strong>
                                        </TableCell>
                                        <TableCell>
                                            <StatusBadge
                                                tone={getMarketingStatusTone(
                                                    promotion.status
                                                )}
                                            >
                                                {getPromotionStatusLabel(
                                                    promotion.status
                                                )}
                                            </StatusBadge>
                                        </TableCell>
                                        <TableCell>
                                            <StatusBadge
                                                shape="tag"
                                                tone={
                                                    applyMethodTones[
                                                        promotion.applyMethod
                                                    ] ?? "neutral"
                                                }
                                            >
                                                {getPromotionApplyMethodLabel(
                                                    promotion.applyMethod
                                                )}
                                            </StatusBadge>
                                        </TableCell>
                                        <TableCell className={styles.promotionTimeCell}>
                                            <PromotionTimeRange
                                                startAt={promotion.startAt}
                                                endAt={promotion.endAt}
                                            />
                                        </TableCell>
                                        <TableCell
                                            className={styles.actionCell}
                                            onClick={(event) =>
                                                event.stopPropagation()
                                            }
                                        >
                                            <RowActionMenu
                                                actions={[
                                                    ...(editable
                                                        ? [
                                                              {
                                                                  label: "Chỉnh sửa",
                                                                  icon: <Pencil size={15} />,
                                                                  onSelect: () =>
                                                                      setFormState({
                                                                          mode: "edit",
                                                                          promotion
                                                                      })
                                                              }
                                                          ]
                                                        : []),
                                                    ...actions.map(
                                                        (action): RowAction => ({
                                                            label: action.label,
                                                            icon:
                                                                action.action === "duplicate" ? (
                                                                    <Copy size={15} />
                                                                ) : undefined,
                                                            tone: action.danger ? "danger" : "default",
                                                            disabled,
                                                            onSelect: () =>
                                                                void handlePromotionAction(
                                                                    promotion,
                                                                    action.action
                                                                )
                                                        })
                                                    ),
                                                    ...(deletable
                                                        ? [
                                                              {
                                                                  label: "Xóa",
                                                                  icon: <Trash2 size={15} />,
                                                                  tone: "danger" as const,
                                                                  disabled,
                                                                  onSelect: () =>
                                                                      void deletePromotion(promotion)
                                                              }
                                                          ]
                                                        : [])
                                                ]}
                                                isOpen={openMenuId === promotion.id}
                                                menuLabel={`Thao tác ${promotion.name}`}
                                                onOpenChange={(open) =>
                                                    setOpenMenuId(open ? promotion.id : null)
                                                }
                                                triggerLabel={`Mở thao tác ${promotion.name}`}
                                            />
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                        </tbody>
                    </Table>
                </div>

                {!loading && !error && promotions.length === 0 ? (
                    <PromotionEmptyState
                        mode={mode}
                        hasFilters={hasPromotionFilters(appliedFilters)}
                        onClear={clearPromotionFilters}
                        onCreate={() =>
                            setFormState({
                                mode: "create"
                            })
                        }
                    />
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

            <MarketingToast
                notice={notice}
                onClose={() => setNotice(null)}
            />
        </section>
    );
}

function PromotionEmptyState({
    mode,
    hasFilters,
    onClear,
    onCreate
}: {
    mode: PromotionMode;
    hasFilters: boolean;
    onClear(): void;
    onCreate(): void;
}) {
    return (
        <div className={styles.emptyState}>
            <div className={styles.emptyStateIcon}>
                <Search size={24} aria-hidden="true" />
            </div>
            <h3>Chưa có chương trình giảm giá phù hợp</h3>
            <p>
                {hasFilters
                    ? "Thử bỏ bớt điều kiện lọc hoặc tìm bằng từ khóa khác."
                    : mode === "EVENT_FILTERED"
                      ? "Sự kiện này chưa có chương trình giảm giá nào. Bạn có thể tạo mới và hệ thống sẽ tự gắn vào sự kiện hiện tại."
                      : "Bạn có thể tạo chương trình giảm giá đầu tiên để bắt đầu quản lý ưu đãi."}
            </p>
            <div className={styles.emptyStateActions}>
                {hasFilters ? (
                    <Button onClick={onClear}>
                        <X size={17} aria-hidden="true" />
                        Xóa bộ lọc
                    </Button>
                ) : null}
                <Button variant="primary" onClick={onCreate}>
                    <Plus size={17} aria-hidden="true" />
                    Tạo khuyến mãi
                </Button>
            </div>
        </div>
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
                    <SearchInput
                        value={filters.keyword}
                        placeholder="Tìm kiếm khuyến mãi..."
                        onChange={(keyword) =>
                            onFiltersChange({ ...filters, keyword })
                        }
                    />

                    <IconButton
                        size="search"
                        className={`${
                            detailOpen || hasDetailFilters
                                ? styles.filterToggleButtonActive
                                : ""
                        }`}
                        variant="secondary"
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
                    </IconButton>

                    <Button
                        className="max-[900px]:col-span-2 max-[900px]:w-full"
                        size="search"
                        variant="primary"
                        type="submit"
                        disabled={loading}
                    >
                        <Search size={17} aria-hidden="true" />
                        Tìm kiếm
                    </Button>

                    {showClear ? (
                        <Button
                            className="max-[900px]:col-span-2 max-[900px]:w-full"
                        size="search"
                            disabled={loading}
                            onClick={onClear}
                        >
                            <X size={17} aria-hidden="true" />
                            Xóa lọc
                        </Button>
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

function PromotionTimeRange({
    startAt,
    endAt
}: {
    startAt: string;
    endAt?: string | null;
}) {
    const start = formatDiscountDateParts(startAt);
    const end = endAt ? formatDiscountDateParts(endAt) : null;

    return (
        <div className={styles.timeRangeCell}>
            <span className={styles.timePoint}>
                <span className={styles.timeDate}>{start.date}</span>
                <span className={styles.timeHour}>{start.time}</span>
            </span>
            <span className={styles.timeSeparator} aria-hidden="true" />
            <span className={styles.timePoint}>
                <span className={styles.timeDate}>{end?.date ?? "Không hạn"}</span>
                <span className={styles.timeHour}>{end?.time ?? "—"}</span>
            </span>
        </div>
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
        <ModalShell
            className="w-full max-w-4xl p-6"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="promotion-form-title"
            onClose={onClose}
        >
            <>
                <header className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2
                            className="text-xl font-bold text-[#17213a]"
                            id="promotion-form-title"
                        >
                            {mode === "create"
                                ? "Tạo khuyến mãi"
                                : "Chỉnh sửa khuyến mãi"}
                        </h2>
                        <p className="mt-1 text-sm font-medium text-[#667085]">
                            Không chọn trạng thái trong form. Trạng thái được
                            đổi bằng thao tác riêng.
                        </p>
                    </div>
                    <IconButton aria-label="Đóng form" onClick={onClose}>
                        <X size={20} aria-hidden="true" />
                    </IconButton>
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
                                <Field label="Sự kiện">
                                    <NativeSelect
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
                                    </NativeSelect>
                                </Field>
                            ) : (
                                <Field label="Sự kiện">
                                    <p className={styles.readonlyValue}>
                                        {eventName}
                                    </p>
                                </Field>
                            )}

                            <Field label="Tên khuyến mãi">
                                <TextInput
                                    value={form.name}
                                    onChange={(event) =>
                                        updateField(
                                            "name",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </Field>

                            <Field label="Loại">
                                <NativeSelect
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
                                </NativeSelect>
                            </Field>

                            <Field label="Cách áp dụng">
                                <NativeSelect
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
                                </NativeSelect>
                            </Field>

                            <Field
                                className={styles.detailFieldFull}
                                label="Mô tả"
                            >
                                <TextArea
                                    rows={3}
                                    value={form.description ?? ""}
                                    onChange={(event) =>
                                        updateField(
                                            "description",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </Field>

                            <Field
                                className={styles.detailFieldFull}
                                label="Mục tiêu"
                            >
                                <TextArea
                                    rows={3}
                                    value={form.objective ?? ""}
                                    onChange={(event) =>
                                        updateField(
                                            "objective",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </Field>
                        </div>
                    </section>

                    <section className={styles.editFormSection}>
                        <h3>Thời gian và ưu tiên</h3>
                        <div className={styles.detailEditGrid}>
                            <Field label="Bắt đầu">
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
                            <Field label="Kết thúc">
                                <TextInput
                                    type="datetime-local"
                                    value={form.endAt}
                                    onChange={(event) =>
                                        updateField(
                                            "endAt",
                                            event.currentTarget.value
                                        )
                                    }
                                />
                            </Field>
                            <Field label="Độ ưu tiên">
                                <TextInput
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
                            </Field>
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
                        <Button disabled={submitting} onClick={onClose}>
                            Hủy
                        </Button>
                        <Button
                            variant="primary"
                            type="submit"
                            disabled={submitting}
                        >
                            {submitting ? "Đang lưu..." : "Lưu"}
                        </Button>
                    </footer>
                </form>
            </>
        </ModalShell>
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
        return normalizePromotionErrorMessage(error.message);
    }

    if (typeof error === "object" && error) {
        const maybeMessage =
            "message" in error ? (error as { message?: unknown }).message : null;

        if (typeof maybeMessage === "string" && maybeMessage.trim()) {
            return normalizePromotionErrorMessage(maybeMessage);
        }
    }

    return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}

function normalizePromotionErrorMessage(message: string) {
    const normalized = message.trim();

    if (/event not found/i.test(normalized)) {
        return "Không tìm thấy sự kiện này. Sự kiện có thể đã bị xóa hoặc đường dẫn không còn hợp lệ.";
    }

    return normalized;
}
