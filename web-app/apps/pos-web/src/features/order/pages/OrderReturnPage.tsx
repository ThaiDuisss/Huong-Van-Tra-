import {
    AlertTriangle,
    ArrowRight,
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Loader2,
    Plus,
    RefreshCw,
    Search,
    X,
    XCircle
} from "lucide-react";
import { ApiError } from "@hvt/api-client";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import {
    cancelOrderReturn,
    getCustomerReturnQcSummary,
    getOrderReturnList,
    getOrderReturnDetail,
    getReturnInventoryDisposition,
    receiveOrderReturn,
    submitReturnInventoryDisposition
} from "../services/order-return.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import type { InventoryLocationResponse } from "../../inventory/types/inventory.types";
import type { OrderPageResponse } from "../types/order.types";
import type {
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderReturnInventoryDispositionResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason,
    ReturnStatus
} from "../types/order-return.types";
import {
    CUSTOMER_RETURN_QC_RESULT_EFFECTS,
    CUSTOMER_RETURN_QC_RESULTS,
    formatReturnQuantity,
    getAllowedCustomerReturnQcResults,
    getCustomerReturnQcResultLabel,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    getReturnStatusLabel,
    toReturnNumber
} from "../utils/order-return.utils";
import { formatDateTime } from "../utils/order-formatters";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = "ALL" | ReturnStatus;

interface ConfirmAction {
    type: "receive" | "cancel";
    returnItem: OrderReturnListRow;
}

interface QcDraftLine {
    selected: boolean;
    quantity: string;
    result: CustomerReturnQcResult;
    destinationLocationId: string;
    note: string;
}

const RETURN_STATUS_FILTERS: {
    key: ReturnStatusFilter;
    label: string;
}[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Đã tạo" },
    { key: "RECEIVED", label: "Đã nhận hàng" },
    { key: "COMPLETED", label: "Hoàn tất" },
    { key: "CANCELLED", label: "Đã hủy" }
];

const RETURN_LIST_PAGE_SIZE = 20;

const emptyReturnPage: OrderPageResponse<OrderReturnListRow> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: RETURN_LIST_PAGE_SIZE
};

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setDebouncedValue(value);
        }, delayMs);

        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

function getReturnItemCount(returnItem: OrderReturnListRow) {
    if (returnItem.itemCount !== null && returnItem.itemCount !== undefined) {
        return toReturnNumber(returnItem.itemCount);
    }

    return (returnItem.items ?? []).reduce(
        (total, item) => total + toReturnNumber(item.quantity),
        0
    );
}

function getQcQuantity(draft?: QcDraftLine) {
    if (!draft?.quantity.trim()) {
        return 0;
    }

    const quantity = Number(draft.quantity);

    return Number.isFinite(quantity) ? quantity : 0;
}

function getDefaultQcResult(condition?: ReturnCondition | null) {
    return condition === "SEALED" ? "RESTOCK" : "DAMAGED";
}

function createDefaultQcDraftLine(
    line?: CustomerReturnQcSummaryLine | null
): QcDraftLine {
    return {
        selected: false,
        quantity: "1",
        result: getDefaultQcResult(line?.condition),
        destinationLocationId: "",
        note: ""
    };
}

function findReturnItem(
    returnItem: OrderReturnListRow,
    returnItemId: string
) {
    return (returnItem.items ?? []).find((item) => item.id === returnItemId);
}

function getReturnLineName(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);

    return item?.productNameSnapshot?.trim() || "Sản phẩm";
}

function getReturnLineMeta(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);
    const sku = item?.skuSnapshot?.trim() || "--";
    const variant = item?.variantNameSnapshot?.trim() || "Không có phân loại";

    return `${sku} · ${variant}`;
}

function getReturnLineReasonLabel(reason?: string | null) {
    return getReturnReasonLabel(reason as ReturnReason | null);
}

function isSaleableReturnLocation(location: InventoryLocationResponse) {
    return (
        location.status === "ACTIVE" &&
        location.type !== "DAMAGED" &&
        location.type !== "QUARANTINE"
    );
}

function getLocationOptionLabel(location: InventoryLocationResponse) {
    const code = String(location.code ?? "").trim();
    const name = String(location.name ?? "").trim();

    if (code && name) {
        return `${name} (${code})`;
    }

    return name || code || "Vị trí kho";
}

const QC_DISPOSITION_DISPLAY_LABELS: Record<CustomerReturnQcResult, string> = {
    RESTOCK: "Nhập lại kho",
    DAMAGED: "Hàng hỏng",
    QUARANTINE: "Cách ly",
    WRITE_OFF: "Tiêu hủy"
};

function getQcDispositionDisplayLabel(result: CustomerReturnQcResult) {
    return QC_DISPOSITION_DISPLAY_LABELS[result] ?? result;
}

function logQcSummaryError(returnId: string, error: unknown) {
    if (!import.meta.env.DEV) {
        return;
    }

    if (error instanceof ApiError) {
        console.error("Không thể tải QC summary của phiếu hoàn", {
            returnId,
            status: error.status,
            code: error.code,
            requestId: error.requestId
        });
        return;
    }

    console.error("Không thể tải QC summary của phiếu hoàn", {
        returnId,
        error
    });
}

function DispositionBadge({ result }: { result?: CustomerReturnQcResult | null }) {
    if (!result) {
        return (
            <span className={`${styles.dispositionBadge} ${styles.dispositionDefault}`}>
                --
            </span>
        );
    }

    const config: Record<
        CustomerReturnQcResult,
        { label: string; className: string }
    > = {
        RESTOCK: {
            label: QC_DISPOSITION_DISPLAY_LABELS.RESTOCK,
            className: styles.dispositionRestock
        },
        DAMAGED: {
            label: QC_DISPOSITION_DISPLAY_LABELS.DAMAGED,
            className: styles.dispositionDamaged
        },
        QUARANTINE: {
            label: QC_DISPOSITION_DISPLAY_LABELS.QUARANTINE,
            className: styles.dispositionQuarantine
        },
        WRITE_OFF: {
            label: QC_DISPOSITION_DISPLAY_LABELS.WRITE_OFF,
            className: styles.dispositionWriteOff
        }
    };

    const current = config[result] ?? {
        label: result,
        className: styles.dispositionDefault
    };

    return (
        <span className={`${styles.dispositionBadge} ${current.className}`}>
            {current.label}
        </span>
    );
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

export function OrderReturnPage() {
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const [statusFilter, setStatusFilter] =
        useState<ReturnStatusFilter>("ALL");
    const [returnPage, setReturnPage] = useState(emptyReturnPage);
    const [currentPage, setCurrentPage] = useState(0);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [isRowsLoading, setIsRowsLoading] = useState(false);
    const [rowsError, setRowsError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const [activeReturn, setActiveReturn] =
        useState<OrderReturnListRow | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(
        null
    );
    const [isTransitioning, setIsTransitioning] = useState(false);
    const [toast, setToast] = useState<string | null>(null);
    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setToast(null);
        }, 3200);

        return () => window.clearTimeout(timerId);
    }, [toast]);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsRowsLoading(true);
            setRowsError(null);

            getOrderReturnList({
                fromDate: timeRange?.fromDate,
                keyword: debouncedSearchTerm,
                status:
                    statusFilter === "ALL" ? undefined : statusFilter,
                toDate: timeRange?.toDate,
                page: currentPage,
                size: RETURN_LIST_PAGE_SIZE,
                sort: "createdAt,desc"
            })
                .then((page) => {
                    if (!ignore) {
                        setReturnPage({
                            ...emptyReturnPage,
                            ...page,
                            content: page.content ?? []
                        });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setReturnPage(emptyReturnPage);
                        setRowsError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsRowsLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        currentPage,
        debouncedSearchTerm,
        reloadKey,
        statusFilter,
        timeRange?.fromDate,
        timeRange?.toDate
    ]);

    const visibleRows = returnPage.content ?? [];
    const totalPages = Math.max(returnPage.totalPages ?? 0, 1);
    const totalElements = returnPage.totalElements ?? 0;
    const fromRecord =
        totalElements > 0 ? returnPage.number * returnPage.size + 1 : 0;
    const toRecord = Math.min(
        (returnPage.number + 1) * returnPage.size,
        totalElements
    );
    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setCurrentPage(0);
    };

    const handleStatusFilterChange = (status: ReturnStatusFilter) => {
        setStatusFilter(status);
        setCurrentPage(0);
    };

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        setCurrentPage(0);
    };

    const clearTimeRange = () => {
        setSelectedTimeNode(null);
        setCurrentPage(0);
    };
    const openReturnDetail = (row: OrderReturnListRow) => {
        setActiveReturn(row);
        setDetailError(null);
        setIsDetailLoading(true);

        getOrderReturnDetail(row.id)
            .then((detail) => {
                setActiveReturn({
                    ...row,
                    ...detail,
                    orderCode: row.orderCode,
                    customerName: row.customerName
                });
            })
            .catch((error: unknown) => {
                setDetailError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsDetailLoading(false);
            });
    };

    const closeReturnDetail = () => {
        setActiveReturn(null);
        setDetailError(null);
        setIsDetailLoading(false);
        setConfirmAction(null);
    };

    const applyTransitionResult = (
        updatedReturn: OrderReturnResponse,
        fallback: OrderReturnListRow
    ) => {
        const row: OrderReturnListRow = {
            ...fallback,
            ...updatedReturn,
            orderCode: fallback.orderCode,
            customerName: fallback.customerName
        };

        setActiveReturn(row);
        setReturnPage((current) => ({
            ...current,
            content: current.content.map((item) =>
                item.id === row.id ? row : item
            )
        }));
    };

    const executeTransition = () => {
        if (!confirmAction) {
            return;
        }

        const { type, returnItem } = confirmAction;
        const action =
            type === "receive" ? receiveOrderReturn : cancelOrderReturn;

        setIsTransitioning(true);
        action(returnItem.id)
            .then((updatedReturn) => {
                applyTransitionResult(updatedReturn, returnItem);
                setToast(
                    type === "receive"
                        ? "Đã xác nhận nhận hàng."
                        : "Đã hủy phiếu hoàn."
                );
                setConfirmAction(null);
                setReloadKey((key) => key + 1);
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsTransitioning(false);
            });
    };

    return (
        <section
            className={`${styles.page} ${
                isTimeCollapsed ? styles.pageTimeCollapsed : ""
            }`}
        >
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={clearTimeRange}
                onSelect={selectTimeNode}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
                selectedNode={selectedTimeNode}
            />

            <div className={styles.content}>
                <div className={styles.header}>
                    <div className={styles.title}>
                        <h1>Hoàn hàng</h1>
                        <p>Quản lý và theo dõi các phiếu hoàn hàng</p>
                    </div>

                    <div className={styles.headerActions}>
                        <button
                            className={styles.secondaryButton}
                            onClick={() => setReloadKey((key) => key + 1)}
                            type="button"
                        >
                            <RefreshCw size={18} aria-hidden="true" />
                            Làm mới
                        </button>
                        <button
                            className={styles.primaryButton}
                            onClick={() => navigate("/system/orders/returns/create")}
                            type="button"
                        >
                            <Plus size={18} aria-hidden="true" />
                            Tạo phiếu hoàn hàng
                        </button>
                    </div>
                </div>

                <div className={styles.workspace}>
                <div className={styles.filterRows}>
                    <div className={styles.filterToolbar}>
                        <label className={styles.searchBox}>
                            <Search size={20} aria-hidden="true" />
                            <input
                                aria-label="Tìm phiếu hoàn"
                                onChange={(event) =>
                                    handleSearchChange(event.target.value)
                                }
                                placeholder="Tìm mã đơn, khách hàng..."
                                value={searchTerm}
                            />
                        </label>

                        <div className={styles.filterGroup}>
                            <span className={styles.filterLabel}>
                                Trạng thái:
                            </span>
                            <div className={styles.quickFilters}>
                                {RETURN_STATUS_FILTERS.map((filter) => (
                                    <button
                                        className={`${styles.filterChip} ${
                                            statusFilter === filter.key
                                                ? styles.activeFilterChip
                                                : ""
                                        }`}
                                        key={filter.key}
                                        onClick={() =>
                                            handleStatusFilterChange(
                                                filter.key
                                            )
                                        }
                                        type="button"
                                    >
                                        {filter.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>

                <div className={styles.tableCard}>
                    <div className={styles.tableWrap}>
                        <table className={styles.returnTable}>
                            <colgroup>
                                <col style={{ width: "24%" }} />
                                <col style={{ width: "24%" }} />
                                <col style={{ width: "16%" }} />
                                <col style={{ width: "17%" }} />
                                <col style={{ width: "19%" }} />
                            </colgroup>
                            <thead>
                                <tr>
                                    <th>Mã phiếu hoàn</th>
                                    <th>Mã đơn hàng</th>
                                    <th>Sản phẩm</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày tạo</th>
                                </tr>
                            </thead>
                            <tbody>
                                {isRowsLoading ? (
                                    <ReturnTableMessage
                                        description="Dữ liệu đang được tải từ Order-Service"
                                        title="Đang tải phiếu hoàn"
                                    />
                                ) : null}

                                {!isRowsLoading && rowsError ? (
                                    <ReturnTableMessage
                                        description={rowsError}
                                        title="Không tải được danh sách"
                                    />
                                ) : null}

                                {!isRowsLoading && !rowsError
                                    ? visibleRows.map((row, index) => (
                                          <tr
                                              className={
                                                  index % 2 === 0
                                                      ? styles.zebraFirstRow
                                                      : styles.zebraSecondRow
                                              }
                                              key={row.id}
                                              onClick={() =>
                                                  openReturnDetail(row)
                                              }
                                          >
                                              <td className={styles.code}>
                                                  {getReturnCode(row)}
                                              </td>
                                              <td className={styles.code}>
                                                  {row.orderCode || "--"}
                                              </td>
                                              <td>
                                                  {formatReturnQuantity(
                                                      getReturnItemCount(row)
                                                  )}{" "}
                                                  sản phẩm
                                              </td>
                                              <td>
                                                  <ReturnStatusBadge
                                                      status={row.status}
                                                  />
                                              </td>
                                              <td>
                                                  {formatDateTime(
                                                      row.createdAt
                                                  )}
                                              </td>
                                          </tr>
                                      ))
                                    : null}

                                {!isRowsLoading &&
                                !rowsError &&
                                visibleRows.length === 0 ? (
                                    <ReturnTableMessage
                                        description="Thử đổi từ khóa hoặc tạo phiếu hoàn mới"
                                        title="Chưa có phiếu hoàn phù hợp"
                                    />
                                ) : null}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.pagination}>
                        <span>
                            Hiển thị {fromRecord} - {toRecord} trong{" "}
                            {totalElements} phiếu hoàn
                        </span>
                        <div className={styles.pages}>
                            <button
                                className={styles.pageButton}
                                disabled={currentPage === 0}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.max(0, page - 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronLeft size={18} aria-hidden="true" />
                            </button>
                            {getPageNumbers(totalPages, currentPage).map(
                                (page) => (
                                    <button
                                        className={`${styles.pageButton} ${
                                            page === currentPage
                                                ? styles.activePage
                                                : ""
                                        }`}
                                        key={page}
                                        onClick={() => setCurrentPage(page)}
                                        type="button"
                                    >
                                        {page + 1}
                                    </button>
                                )
                            )}
                            {totalPages > 5 ? <span>...</span> : null}
                            <button
                                className={styles.pageButton}
                                disabled={currentPage >= totalPages - 1}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.min(totalPages - 1, page + 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronRight size={18} aria-hidden="true" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {activeReturn ? (
                <ReturnDetailModal
                    detailError={detailError}
                    isDetailLoading={isDetailLoading}
                    isTransitioning={isTransitioning}
                    onClose={closeReturnDetail}
                    onRequestCancel={(returnItem) =>
                        setConfirmAction({
                            type: "cancel",
                            returnItem
                        })
                    }
                    onRequestComplete={(returnItem) =>
                        setConfirmAction({
                            type: "receive",
                            returnItem
                        })
                    }
                    onToast={setToast}
                    returnItem={activeReturn}
                />
            ) : null}

            {confirmAction ? (
                <div className={styles.confirmBackdrop} role="presentation">
                    <div
                        aria-modal="true"
                        className={styles.confirmDialog}
                        role="dialog"
                    >
                        <h2>
                            {confirmAction.type === "receive"
                                ? "Xác nhận cửa hàng đã nhận hàng trả lại từ khách?"
                                : "Hủy phiếu hoàn?"}
                        </h2>
                        <p>
                            {confirmAction.type === "receive"
                                ? "Sau khi xác nhận nhận hàng, phiếu hoàn sẽ không thể hủy."
                                : "Bạn có chắc muốn hủy phiếu hoàn hàng này?"}
                        </p>
                        <div className={styles.confirmActions}>
                            <button
                                className={styles.secondaryButton}
                                disabled={isTransitioning}
                                onClick={() => setConfirmAction(null)}
                                type="button"
                            >
                                Giữ lại
                            </button>
                            <button
                                className={
                                    confirmAction.type === "receive"
                                        ? styles.primaryButton
                                        : styles.dangerButton
                                }
                                disabled={isTransitioning}
                                onClick={executeTransition}
                                type="button"
                            >
                                {isTransitioning
                                    ? "Đang xử lý..."
                                    : "Xác nhận"}
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}

            {toast ? <div className={styles.toast}>{toast}</div> : null}
            </div>
        </section>
    );
}

function ReturnDetailModal({
    detailError,
    isDetailLoading,
    isTransitioning,
    onClose,
    onRequestCancel,
    onRequestComplete,
    onToast,
    returnItem
}: {
    detailError: string | null;
    isDetailLoading: boolean;
    isTransitioning: boolean;
    onClose: () => void;
    onRequestCancel: (returnItem: OrderReturnListRow) => void;
    onRequestComplete: (returnItem: OrderReturnListRow) => void;
    onToast: (message: string) => void;
    returnItem: OrderReturnListRow;
}) {
    const canTransition = returnItem.status === "CREATED";
    const [qcSummary, setQcSummary] =
        useState<CustomerReturnQcSummaryResponse | null>(null);
    const [isQcSummaryLoading, setIsQcSummaryLoading] = useState(false);
    const [qcSummaryError, setQcSummaryError] = useState<string | null>(null);
    const [qcReloadKey, setQcReloadKey] = useState(0);
    const [isQcOpen, setIsQcOpen] = useState(false);
    const [isConfirmingDisposition, setIsConfirmingDisposition] =
        useState(false);
    const [qcDraftLines, setQcDraftLines] = useState<Record<string, QcDraftLine>>(
        {}
    );
    const [qcNote, setQcNote] = useState("");
    const [qcFormError, setQcFormError] = useState<string | null>(null);
    const [activeLocations, setActiveLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [damagedLocations, setDamagedLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [isLocationLoading, setIsLocationLoading] = useState(false);
    const [locationError, setLocationError] = useState<string | null>(null);
    const [isSubmittingDisposition, setIsSubmittingDisposition] =
        useState(false);
    const [submissionCommandId, setSubmissionCommandId] = useState<
        string | null
    >(null);
    const [activeDispositionCommand, setActiveDispositionCommand] =
        useState<OrderReturnInventoryDispositionResponse | null>(null);

    const summaryLines = useMemo(
        () => qcSummary?.lines ?? [],
        [qcSummary?.lines]
    );
    const remainingLines = useMemo(
        () =>
            summaryLines.filter(
                (line) => toReturnNumber(line.remainingQcQuantity) > 0
            ),
        [summaryLines]
    );
    const allLocationsMap = useMemo(() => {
        const map = new Map<string, InventoryLocationResponse>();
        activeLocations.forEach((loc) => map.set(String(loc.id), loc));
        damagedLocations.forEach((loc) => map.set(String(loc.id), loc));
        return map;
    }, [activeLocations, damagedLocations]);
    const restockLocations = useMemo(
        () => activeLocations.filter(isSaleableReturnLocation),
        [activeLocations]
    );
    const normalizedDamagedLocations = useMemo(() => {
        const map = new Map<string, InventoryLocationResponse>();
        activeLocations
            .filter((l) => l.status === "ACTIVE" && l.type === "DAMAGED")
            .forEach((l) => map.set(String(l.id), l));
        damagedLocations
            .filter((l) => l.status === "ACTIVE" && l.type === "DAMAGED")
            .forEach((l) => map.set(String(l.id), l));
        return Array.from(map.values());
    }, [activeLocations, damagedLocations]);
    const shouldLoadQcSummary =
        returnItem.status === "RECEIVED" || returnItem.status === "COMPLETED";
    const isQcCompleted = qcSummary?.qcStatus === "COMPLETED";
    const qcStatusLabel = (() => {
        if (isQcSummaryLoading) {
            return "Đang tải...";
        }

        if (qcSummaryError) {
            return "Không thể tải";
        }

        if (
            !qcSummary ||
            qcSummary.qcStatus === "PENDING" ||
            qcSummary.qcStatus === "UNAVAILABLE" ||
            qcSummary.qcStatus === "UNKNOWN"
        ) {
            return "Chờ kiểm tra chất lượng";
        }

        if (qcSummary.qcStatus === "PARTIAL") {
            return "Đang kiểm tra chất lượng";
        }

        return "Đã kiểm tra chất lượng";
    })();

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setQcSummary(null);
            setQcSummaryError(null);
            setIsQcOpen(false);
            setIsConfirmingDisposition(false);
            setActiveDispositionCommand(null);
            setSubmissionCommandId(null);
            setIsSubmittingDisposition(false);
            setQcDraftLines({});
            setQcNote("");
            setQcFormError(null);
            setActiveLocations([]);
            setDamagedLocations([]);
            setLocationError(null);
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [returnItem.id]);

    useEffect(() => {
        if (!shouldLoadQcSummary) {
            const timerId = window.setTimeout(() => {
                setQcSummary(null);
                setQcSummaryError(null);
                setIsQcSummaryLoading(false);
            }, 0);

            return () => window.clearTimeout(timerId);
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsQcSummaryLoading(true);
            setQcSummaryError(null);

            getCustomerReturnQcSummary(returnItem.id)
                .then((summary) => {
                    if (!ignore) {
                        setQcSummary(summary);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        logQcSummaryError(returnItem.id, error);
                        setQcSummary(null);
                        setQcSummaryError(
                            "Không thể tải thông tin kiểm tra hàng trả"
                        );
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsQcSummaryLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [qcReloadKey, returnItem.id, shouldLoadQcSummary]);

    useEffect(() => {
        if (!isQcOpen) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsLocationLoading(true);
            setLocationError(null);

            Promise.all([
                inventoryLocationApi.list({
                    page: 0,
                    size: 100,
                    status: "ACTIVE"
                }),
                inventoryLocationApi.list({
                    page: 0,
                    size: 100,
                    status: "ACTIVE",
                    type: "DAMAGED"
                })
            ])
                .then(([activePage, damagedPage]) => {
                    if (!ignore) {
                        setActiveLocations(activePage.content ?? []);
                        setDamagedLocations(damagedPage.content ?? []);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setActiveLocations([]);
                        setDamagedLocations([]);
                        setLocationError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsLocationLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [isQcOpen]);

    useEffect(() => {
        if (!isQcOpen || activeDispositionCommand) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setQcDraftLines((current) => {
                const nextEntries = remainingLines.map((line) => [
                    line.returnItemId,
                    current[line.returnItemId] ?? createDefaultQcDraftLine(line)
                ]);

                return Object.fromEntries(nextEntries);
            });
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [activeDispositionCommand, isQcOpen, remainingLines]);

    const openQcForm = () => {
        if (returnItem.status !== "RECEIVED") {
            onToast("Phiếu hoàn chưa ở trạng thái đã nhận hàng.");
            return;
        }

        if (isQcCompleted) {
            onToast("Đã kiểm tra xong hàng trả.");
            return;
        }

        setIsQcOpen(true);
        setIsConfirmingDisposition(false);
        setActiveDispositionCommand(null);
        setSubmissionCommandId(null);
        setQcFormError(null);
    };

    const updateQcDraftLine = (
        returnItemId: string,
        update: Partial<QcDraftLine>
    ) => {
        setSubmissionCommandId(null);
        setIsConfirmingDisposition(false);
        if (activeDispositionCommand && activeDispositionCommand.status !== "PENDING") {
            setActiveDispositionCommand(null);
        }
        setQcDraftLines((current) => {
            const line = summaryLines.find(
                (summaryLine) => summaryLine.returnItemId === returnItemId
            );
            const currentLine =
                current[returnItemId] ?? createDefaultQcDraftLine(line);
            const nextLine = {
                ...currentLine,
                ...update
            };

            if (update.result) {
                nextLine.destinationLocationId = "";
            }

            return {
                ...current,
                [returnItemId]: nextLine
            };
        });
    };

    const getLocationOptions = useCallback(
        (result: CustomerReturnQcResult) => {
            if (result === "RESTOCK") {
                return restockLocations;
            }

            if (result === "DAMAGED") {
                return normalizedDamagedLocations;
            }

            return [];
        },
        [normalizedDamagedLocations, restockLocations]
    );

    const selectedLines = useMemo(
        () =>
            remainingLines.filter(
                (line) => qcDraftLines[line.returnItemId]?.selected
            ),
        [qcDraftLines, remainingLines]
    );

    const selectedLinesRequireLocation = useMemo(
        () =>
            selectedLines.some((line) => {
                const draft =
                    qcDraftLines[line.returnItemId] ??
                    createDefaultQcDraftLine(line);

                return draft.result !== "WRITE_OFF";
            }),
        [qcDraftLines, selectedLines]
    );

    const hasEmptyLocationConflict = useMemo(
        () =>
            selectedLines.some((line) => {
                const draft =
                    qcDraftLines[line.returnItemId] ??
                    createDefaultQcDraftLine(line);
                if (draft.result === "WRITE_OFF") {
                    return false;
                }
                return getLocationOptions(draft.result).length === 0;
            }),
        [getLocationOptions, qcDraftLines, selectedLines]
    );

    const validateQcDraft = () => {
        if (selectedLines.length === 0) {
            return "Vui lòng chọn ít nhất một sản phẩm cần kiểm tra.";
        }

        for (const line of selectedLines) {
            const draft =
                qcDraftLines[line.returnItemId] ?? createDefaultQcDraftLine(line);
            const quantity = getQcQuantity(draft);
            const remainingQuantity = toReturnNumber(line.remainingQcQuantity);
            const allowedResults = getAllowedCustomerReturnQcResults(
                line.condition
            );
            const productName = getReturnLineName(returnItem, line);

            if (quantity <= 0) {
                return `Số lượng kiểm tra của "${productName}" phải lớn hơn 0.`;
            }

            if (quantity > remainingQuantity) {
                return `Số lượng kiểm tra của "${productName}" vượt quá số lượng còn lại.`;
            }

            if (!allowedResults.includes(draft.result)) {
                return `Sản phẩm "${productName}" với tình trạng này không được nhập lại kho.`;
            }

            if (draft.result === "RESTOCK") {
                if (restockLocations.length === 0) {
                    return `Chưa cấu hình vị trí kho bán được.`;
                }

                const isValidLocation = restockLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return `Vui lòng chọn vị trí kho bán được để nhập lại.`;
                }
            }

            if (draft.result === "DAMAGED") {
                if (normalizedDamagedLocations.length === 0) {
                    return `Chưa cấu hình vị trí hàng hỏng.`;
                }

                const isValidLocation = normalizedDamagedLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return `Vui lòng chọn vị trí kho hàng hỏng.`;
                }
            }

            if (
                line.condition === "SEALED" &&
                draft.result === "DAMAGED" &&
                draft.note.trim().length < 5 &&
                qcNote.trim().length < 5
            ) {
                return "Vui lòng nhập lý do khi đánh dấu hàng nguyên niêm phong là hàng hỏng.";
            }

            if (
                line.condition === "SEALED" &&
                draft.result === "WRITE_OFF" &&
                draft.note.trim().length < 5 &&
                qcNote.trim().length < 5
            ) {
                return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
            }
        }

        return null;
    };

    const handleProceedToConfirmation = () => {
        const validationError = validateQcDraft();
        if (validationError) {
            setQcFormError(validationError);
            return;
        }
        setQcFormError(null);
        setIsConfirmingDisposition(true);
    };

    useEffect(() => {
        if (
            !activeDispositionCommand ||
            activeDispositionCommand.status !== "PENDING"
        ) {
            return;
        }

        let ignore = false;
        const intervalId = window.setInterval(() => {
            getReturnInventoryDisposition(
                returnItem.id,
                activeDispositionCommand.commandId
            )
                .then((updatedCommand) => {
                    if (ignore) {
                        return;
                    }
                    setActiveDispositionCommand(updatedCommand);
                    if (updatedCommand.status === "PROCESSED") {
                        onToast("Đã cập nhật kho thành công.");
                        setQcReloadKey((key) => key + 1);
                        setQcDraftLines({});
                        setQcNote("");
                        setQcFormError(null);
                        setSubmissionCommandId(null);
                        setIsConfirmingDisposition(false);
                    } else if (updatedCommand.status === "FAILED") {
                        const failureMsg = updatedCommand.failureReason
                            ? getReturnErrorMessage(
                                  new Error(updatedCommand.failureReason)
                              )
                            : "Không thể cập nhật kho. Vui lòng kiểm tra lại.";
                        setQcFormError(failureMsg);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        console.warn(
                            "Lỗi khi tra cứu trạng thái lệnh xử lý kho:",
                            error
                        );
                    }
                });
        }, 1500);

        return () => {
            ignore = true;
            window.clearInterval(intervalId);
        };
    }, [activeDispositionCommand, onToast, returnItem.id]);

    const submitDisposition = () => {
        const validationError = validateQcDraft();

        if (validationError) {
            setQcFormError(validationError);
            return;
        }

        const selectedLines = remainingLines
            .filter((line) => qcDraftLines[line.returnItemId]?.selected)
            .map((line) => {
                const draft =
                    qcDraftLines[line.returnItemId] ??
                    createDefaultQcDraftLine(line);

                return {
                    returnItemId: line.returnItemId,
                    quantity: getQcQuantity(draft),
                    result: draft.result,
                    destinationLocationId:
                        draft.result === "WRITE_OFF"
                            ? null
                            : draft.destinationLocationId || null,
                    note: draft.note.trim() || null
                };
            });

        if (selectedLines.length === 0) {
            setQcFormError("Vui lòng chọn ít nhất một sản phẩm để xử lý kho.");
            return;
        }

        const commandId = submissionCommandId ?? crypto.randomUUID();
        setSubmissionCommandId(commandId);
        setQcFormError(null);
        setIsSubmittingDisposition(true);

        submitReturnInventoryDisposition(returnItem.id, {
            commandId,
            lines: selectedLines
        })
            .then((response) => {
                setActiveDispositionCommand(response);
                if (response.status === "PROCESSED") {
                    onToast("Đã cập nhật kho thành công.");
                    setQcReloadKey((key) => key + 1);
                    setQcDraftLines({});
                    setQcNote("");
                    setSubmissionCommandId(null);
                    setIsConfirmingDisposition(false);
                } else if (response.status === "FAILED") {
                    const failureMsg = response.failureReason
                        ? getReturnErrorMessage(new Error(response.failureReason))
                        : "Không thể cập nhật kho.";
                    setQcFormError(failureMsg);
                } else {
                    onToast("Đã gửi lệnh xử lý kho. Đang cập nhật tồn kho...");
                }
            })
            .catch((error: unknown) => {
                setQcFormError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsSubmittingDisposition(false);
            });
    };

    const handleResetDispositionAfterFailure = () => {
        setActiveDispositionCommand(null);
        setSubmissionCommandId(null);
        setQcFormError(null);
        setIsConfirmingDisposition(false);
    };

    const handleContinueQcAfterProcessed = () => {
        setActiveDispositionCommand(null);
        setSubmissionCommandId(null);
        setQcFormError(null);
        setQcDraftLines({});
        setQcNote("");
        setIsConfirmingDisposition(false);
    };

    const getDestinationName = (locationId?: string | null) => {
        if (!locationId) {
            return "--";
        }

        const location = allLocationsMap.get(String(locationId));

        return location ? getLocationOptionLabel(location) : "--";
    };

    return (
        <div
            className={styles.modalBackdrop}
            onClick={onClose}
            role="presentation"
        >
            <aside
                aria-label="Chi tiết phiếu hoàn"
                aria-modal="true"
                className={styles.detailModal}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.modalHeader}>
                    <div>
                        <h2>{getReturnCode(returnItem)}</h2>
                        <p>{returnItem.orderCode || "Đơn hàng"}</p>
                    </div>
                    <button
                        aria-label="Đóng chi tiết phiếu hoàn"
                        className={styles.closeButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {isDetailLoading ? (
                    <PanelMessage
                        description="Đang tải thông tin phiếu hoàn"
                        title="Đang tải chi tiết"
                    />
                ) : null}
                {!isDetailLoading && detailError ? (
                    <PanelMessage
                        description={detailError}
                        title="Không tải được chi tiết"
                    />
                ) : null}
                {!isDetailLoading && !detailError ? (
                    <>
                        <section className={styles.detailSection}>
                            <div className={styles.detailInfoGrid}>
                                <InfoRow
                                    label="Trạng thái"
                                    value={
                                        <ReturnStatusBadge
                                            status={returnItem.status}
                                        />
                                    }
                                />
                                <InfoRow
                                    label="Khách hàng"
                                    value={returnItem.customerName || "Khách lẻ"}
                                />
                                <InfoRow
                                    label="Chính sách hoàn tiền"
                                    value={
                                        returnItem.refundRequired === false
                                            ? "Không hoàn tiền"
                                            : "Có hoàn tiền"
                                    }
                                />
                                <InfoRow
                                    label="Ngày tạo"
                                    value={formatDateTime(returnItem.createdAt)}
                                />
                                <InfoRow
                                    label="Cập nhật"
                                    value={formatDateTime(returnItem.updatedAt)}
                                />
                            </div>
                            {returnItem.note ? (
                                <div className={styles.detailNote}>
                                    <strong>Ghi chú</strong>
                                    <p>{returnItem.note}</p>
                                </div>
                            ) : null}
                        </section>

                        <section className={styles.detailSection}>
                            <h3>Sản phẩm hoàn</h3>
                            <div className={styles.detailItems}>
                                {(returnItem.items ?? []).map((item, index) => (
                                    <div
                                        className={styles.detailItem}
                                        key={item.id ?? index}
                                    >
                                        <div className={styles.detailItemIdentity}>
                                            <strong>
                                                {item.productNameSnapshot ||
                                                    "Sản phẩm"}
                                            </strong>
                                            <span>
                                                SKU: {item.skuSnapshot || "--"}
                                                {item.variantNameSnapshot
                                                    ? ` · ${item.variantNameSnapshot}`
                                                    : ""}
                                            </span>
                                        </div>
                                        <div className={styles.detailItemQuantity}>
                                            <span>Số lượng hoàn</span>
                                            <strong>
                                                {formatReturnQuantity(
                                                    item.quantity
                                                )}
                                            </strong>
                                        </div>
                                        <dl className={styles.detailItemMeta}>
                                            <div>
                                                <dt>Lý do</dt>
                                                <dd>
                                                    {getReturnReasonLabel(
                                                        item.reason
                                                    )}
                                                </dd>
                                            </div>
                                            <div>
                                                <dt>Tình trạng</dt>
                                                <dd>
                                                    {getReturnConditionLabel(
                                                        item.condition
                                                    )}
                                                </dd>
                                            </div>
                                        </dl>
                                        {item.note ? (
                                            <p className={styles.detailItemNote}>
                                                <span>Ghi chú</span>
                                                {item.note}
                                            </p>
                                        ) : null}
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className={styles.detailSection}>
                            <div className={styles.qcHeader}>
                                <div>
                                    <h3>Kiểm tra hàng trả</h3>
                                    {shouldLoadQcSummary && !qcSummaryError ? (
                                        <p>
                                            Trạng thái xử lý kho:{" "}
                                            <strong>
                                                {qcStatusLabel}
                                            </strong>
                                        </p>
                                    ) : null}
                                </div>
                                {returnItem.status === "RECEIVED" &&
                                qcSummary &&
                                !isQcCompleted ? (
                                    <button
                                        className={styles.primaryButton}
                                        disabled={
                                            isQcSummaryLoading ||
                                            isSubmittingDisposition ||
                                            activeDispositionCommand?.status ===
                                                "PENDING"
                                        }
                                        onClick={openQcForm}
                                        type="button"
                                    >
                                        <Plus size={18} aria-hidden="true" />
                                        Kiểm tra hàng trả
                                    </button>
                                ) : null}
                            </div>

                            {returnItem.status === "CREATED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn cần được xác nhận nhận hàng trước khi kiểm tra chất lượng."
                                    title="Chưa thể kiểm tra hàng trả"
                                />
                            ) : null}
                            {returnItem.status === "CANCELLED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn đã hủy nên không phát sinh kiểm tra chất lượng."
                                    title="Không thể kiểm tra hàng trả"
                                />
                            ) : null}
                            {returnItem.status === "COMPLETED" ? (
                                <PanelMessage
                                    description={
                                        returnItem.refundRequired === false
                                            ? "Phiếu hoàn đã hoàn tất xử lý hàng. Không phát sinh hoàn tiền."
                                            : "Phiếu hoàn đã hoàn tất."
                                    }
                                    title="Đã hoàn tất"
                                />
                            ) : null}

                            {shouldLoadQcSummary ? (
                                <>
                                    {isQcSummaryLoading ? (
                                        <PanelMessage
                                            description="Đang tải kết quả và tiến độ xử lý kho."
                                            title="Đang tải thông tin kiểm tra"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading && qcSummaryError ? (
                                        <PanelMessage
                                            title={qcSummaryError}
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading &&
                                    !qcSummaryError &&
                                    qcSummary?.qcStatus === "PENDING" ? (
                                        <PanelMessage
                                            description={
                                                qcSummary.inspectionStatus === "CREATED"
                                                    ? "Phiếu kiểm tra đã được tạo và đang chờ hoàn tất xử lý kho."
                                                    : "Phiếu hoàn đã được nhận hàng và đang chờ kiểm tra chất lượng."
                                            }
                                            title="Chờ kiểm tra chất lượng"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading &&
                                    !qcSummaryError &&
                                    qcSummary ? (
                                        <div className={styles.qcSummaryList}>
                                            {summaryLines.map((line) => (
                                                <div
                                                    className={styles.qcSummaryItem}
                                                    key={line.returnItemId}
                                                >
                                                    <div>
                                                        <strong>
                                                            {getReturnLineName(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </strong>
                                                        <span>
                                                            {getReturnLineMeta(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </span>
                                                    </div>
                                                    <div>
                                                        <span>
                                                            Đã trả:{" "}
                                                            {formatReturnQuantity(
                                                                line.returnedQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Đã kiểm tra:{" "}
                                                            {formatReturnQuantity(
                                                                line.postedQcQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Còn lại:{" "}
                                                            {formatReturnQuantity(
                                                                line.remainingQcQuantity
                                                            )}
                                                        </span>
                                                        {(line.dispositions ?? []).map(
                                                            (disposition) => (
                                                                <span
                                                                    key={disposition.result}
                                                                >
                                                                    {getQcDispositionDisplayLabel(
                                                                        disposition.result
                                                                    )}
                                                                    :{" "}
                                                                    {formatReturnQuantity(
                                                                        disposition.quantity
                                                                    )}
                                                                </span>
                                                            )
                                                        )}
                                                    </div>
                                                </div>
                                            ))}
                                            {isQcCompleted ? (
                                                <p className={styles.qcDoneNote}>
                                                    Đã kiểm tra xong hàng trả
                                                </p>
                                            ) : null}
                                        </div>
                                    ) : null}

                                    {isQcOpen ? (
                                        <div className={styles.qcPanel}>
                                            <div className={styles.qcPanelHeader}>
                                                <div>
                                                    <strong>
                                                        Xử lý kho hàng trả
                                                    </strong>
                                                    <span>
                                                        Chỉ hiển thị sản phẩm còn
                                                        số lượng chưa xử lý kho.
                                                    </span>
                                                </div>
                                                <button
                                                    className={styles.secondaryButton}
                                                    disabled={
                                                        isSubmittingDisposition ||
                                                        activeDispositionCommand?.status ===
                                                            "PENDING"
                                                    }
                                                    onClick={() => {
                                                        setIsQcOpen(false);
                                                        setActiveDispositionCommand(null);
                                                        setSubmissionCommandId(null);
                                                        setQcFormError(null);
                                                    }}
                                                    type="button"
                                                >
                                                    Đóng
                                                </button>
                                            </div>

                                            {!activeDispositionCommand ? (
                                                <>
                                                    <div className={styles.qcStepBar}>
                                                        <span
                                                            className={
                                                                !isConfirmingDisposition
                                                                    ? styles.qcStepActive
                                                                    : styles.qcStepInactive
                                                            }
                                                        >
                                                            1. Thiết lập xử lý
                                                        </span>
                                                        <ChevronRight size={14} aria-hidden="true" />
                                                        <span
                                                            className={
                                                                isConfirmingDisposition
                                                                    ? styles.qcStepActive
                                                                    : styles.qcStepInactive
                                                            }
                                                        >
                                                            2. Xác nhận chi tiết
                                                        </span>
                                                    </div>

                                                    {!isConfirmingDisposition ? (
                                                        <>
                                                            <div
                                                                className={
                                                                    styles.qcFormList
                                                                }
                                                            >
                                                                {remainingLines.map(
                                                                    (line) => {
                                                                        const draft =
                                                                            qcDraftLines[
                                                                                line
                                                                                    .returnItemId
                                                                            ] ??
                                                                            createDefaultQcDraftLine(
                                                                                line
                                                                            );
                                                                        const allowedResults =
                                                                            getAllowedCustomerReturnQcResults(
                                                                                line.condition
                                                                            );
                                                                        const locationOptions =
                                                                            getLocationOptions(
                                                                                draft.result
                                                                            );
                                                                        const isLocationRequired =
                                                                            draft.result !==
                                                                            "WRITE_OFF";
                                                                        const isLocationEmpty =
                                                                            isLocationRequired &&
                                                                            locationOptions.length ===
                                                                                0;

                                                                        return (
                                                                            <div
                                                                                className={
                                                                                    styles.qcFormItem
                                                                                }
                                                                                key={
                                                                                    line.returnItemId
                                                                                }
                                                                            >
                                                                                <label
                                                                                    className={
                                                                                        styles.qcItemSelector
                                                                                    }
                                                                                >
                                                                                    <input
                                                                                        checked={
                                                                                            draft.selected
                                                                                        }
                                                                                        onChange={(
                                                                                            event
                                                                                        ) =>
                                                                                            updateQcDraftLine(
                                                                                                line.returnItemId,
                                                                                                {
                                                                                                    selected:
                                                                                                        event
                                                                                                            .target
                                                                                                            .checked
                                                                                                }
                                                                                            )
                                                                                        }
                                                                                        type="checkbox"
                                                                                    />
                                                                                    <span>
                                                                                        <strong>
                                                                                            {getReturnLineName(
                                                                                                returnItem,
                                                                                                line
                                                                                            )}
                                                                                        </strong>
                                                                                        <small>
                                                                                            {getReturnLineMeta(
                                                                                                returnItem,
                                                                                                line
                                                                                            )}
                                                                                        </small>
                                                                                    </span>
                                                                                </label>
                                                                                <div
                                                                                    className={
                                                                                        styles.qcMetaGrid
                                                                                    }
                                                                                >
                                                                                    <InfoRow
                                                                                        label="Tình trạng"
                                                                                        value={getReturnConditionLabel(
                                                                                            line.condition
                                                                                        )}
                                                                                    />
                                                                                    <InfoRow
                                                                                        label="Lý do"
                                                                                        value={getReturnLineReasonLabel(
                                                                                            line.reason
                                                                                        )}
                                                                                    />
                                                                                    <InfoRow
                                                                                        label="Đã trả"
                                                                                        value={formatReturnQuantity(
                                                                                            line.returnedQuantity
                                                                                        )}
                                                                                    />
                                                                                    <InfoRow
                                                                                        label="Đã kiểm tra"
                                                                                        value={formatReturnQuantity(
                                                                                            line.postedQcQuantity
                                                                                        )}
                                                                                    />
                                                                                    <InfoRow
                                                                                        label="Còn lại"
                                                                                        value={formatReturnQuantity(
                                                                                            line.remainingQcQuantity
                                                                                        )}
                                                                                    />
                                                                                </div>
                                                                                <div
                                                                                    className={
                                                                                        styles.qcInputGrid
                                                                                    }
                                                                                >
                                                                                    <label>
                                                                                        <span>
                                                                                            Số lượng QC
                                                                                            lần
                                                                                            này
                                                                                        </span>
                                                                                        <input
                                                                                            className={
                                                                                                styles.quantityInput
                                                                                            }
                                                                                            disabled={
                                                                                                !draft.selected
                                                                                            }
                                                                                            max={toReturnNumber(
                                                                                                line.remainingQcQuantity
                                                                                            )}
                                                                                            min="0"
                                                                                            onChange={(
                                                                                                event
                                                                                            ) =>
                                                                                                updateQcDraftLine(
                                                                                                    line.returnItemId,
                                                                                                    {
                                                                                                        quantity:
                                                                                                            event
                                                                                                                .target
                                                                                                                .value
                                                                                                    }
                                                                                                )
                                                                                            }
                                                                                            type="number"
                                                                                            value={
                                                                                                draft.quantity
                                                                                            }
                                                                                        />
                                                                                    </label>
                                                                                    <label>
                                                                                        <span>
                                                                                            Kết quả QC
                                                                                        </span>
                                                                                        <select
                                                                                            disabled={
                                                                                                !draft.selected
                                                                                            }
                                                                                            onChange={(
                                                                                                event
                                                                                            ) =>
                                                                                                updateQcDraftLine(
                                                                                                    line.returnItemId,
                                                                                                    {
                                                                                                        result: event
                                                                                                            .target
                                                                                                            .value as CustomerReturnQcResult
                                                                                                    }
                                                                                                )
                                                                                            }
                                                                                            value={
                                                                                                draft.result
                                                                                            }
                                                                                        >
                                                                                            {CUSTOMER_RETURN_QC_RESULTS.map(
                                                                                                (
                                                                                                    result
                                                                                                ) => (
                                                                                                    <option
                                                                                                        disabled={
                                                                                                            !allowedResults.includes(
                                                                                                                result
                                                                                                            )
                                                                                                        }
                                                                                                        key={
                                                                                                            result
                                                                                                        }
                                                                                                        value={
                                                                                                            result
                                                                                                        }
                                                                                                    >
                                                                                                        {getCustomerReturnQcResultLabel(
                                                                                                            result
                                                                                                        )}
                                                                                                    </option>
                                                                                                )
                                                                                            )}
                                                                                        </select>
                                                                                    </label>
                                                                                    <label>
                                                                                        <span>
                                                                                            Vị trí đích
                                                                                        </span>
                                                                                        {isLocationRequired ? (
                                                                                            <select
                                                                                                disabled={
                                                                                                    !draft.selected ||
                                                                                                    isLocationLoading ||
                                                                                                    isLocationEmpty
                                                                                                }
                                                                                                onChange={(
                                                                                                    event
                                                                                            ) =>
                                                                                                updateQcDraftLine(
                                                                                                    line.returnItemId,
                                                                                                    {
                                                                                                        destinationLocationId:
                                                                                                            event
                                                                                                                .target
                                                                                                                .value
                                                                                                    }
                                                                                                )
                                                                                            }
                                                                                            value={
                                                                                                draft.destinationLocationId
                                                                                            }
                                                                                        >
                                                                                            <option value="">
                                                                                                {isLocationEmpty
                                                                                                    ? draft.result === "DAMAGED"
                                                                                                        ? "-- Chưa có kho hàng hỏng --"
                                                                                                        : "-- Chưa có kho bán được --"
                                                                                                    : "Chọn vị trí"}
                                                                                            </option>
                                                                                            {locationOptions.map(
                                                                                                (
                                                                                                    location
                                                                                                ) => (
                                                                                                    <option
                                                                                                        key={String(
                                                                                                            location.id
                                                                                                        )}
                                                                                                        value={String(
                                                                                                            location.id
                                                                                                        )}
                                                                                                    >
                                                                                                        {getLocationOptionLabel(
                                                                                                            location
                                                                                                        )}
                                                                                                    </option>
                                                                                                )
                                                                                            )}
                                                                                        </select>
                                                                                        ) : (
                                                                                            <div
                                                                                                aria-disabled={
                                                                                                    !draft.selected
                                                                                                }
                                                                                                className={
                                                                                                    styles.qcReadonlyField
                                                                                                }
                                                                                            >
                                                                                                Không áp dụng
                                                                                            </div>
                                                                                        )}
                                                                                    </label>
                                                                                <label
                                                                                    className={
                                                                                        styles.fullField
                                                                                    }
                                                                                >
                                                                                    <span>
                                                                                        Ghi chú dòng
                                                                                    </span>
                                                                                    <textarea
                                                                                        disabled={
                                                                                            !draft.selected
                                                                                        }
                                                                                        onChange={(
                                                                                            event
                                                                                        ) =>
                                                                                            updateQcDraftLine(
                                                                                                line.returnItemId,
                                                                                                {
                                                                                                    note: event
                                                                                                        .target
                                                                                                        .value
                                                                                                }
                                                                                            )
                                                                                        }
                                                                                        placeholder="Ghi chú tình trạng kiểm tra"
                                                                                        value={
                                                                                            draft.note
                                                                                        }
                                                                                    />
                                                                                </label>
                                                                            </div>
                                                                            <p
                                                                                className={
                                                                                    styles.qcEffect
                                                                                }
                                                                            >
                                                                                {
                                                                                    CUSTOMER_RETURN_QC_RESULT_EFFECTS[
                                                                                        draft
                                                                                            .result
                                                                                    ]
                                                                                }
                                                                            </p>

                                                                            {draft.selected ? (
                                                                                <div className={styles.qcItemDispositionSummary}>
                                                                                    <span>Xử lý:</span>
                                                                                    <DispositionBadge result={draft.result} />
                                                                                    <span>Điểm đến:</span>
                                                                                    <strong>
                                                                                        {draft.result === "WRITE_OFF"
                                                                                            ? "Không áp dụng"
                                                                                            : getDestinationName(draft.destinationLocationId)}
                                                                                    </strong>
                                                                                </div>
                                                                            ) : null}

                                                                            {draft.selected && draft.result === "DAMAGED" && normalizedDamagedLocations.length === 0 ? (
                                                                                <div className={styles.emptyLocationWarning}>
                                                                                    <AlertTriangle size={15} aria-hidden="true" />
                                                                                    Chưa cấu hình vị trí hàng hỏng.
                                                                                </div>
                                                                            ) : null}
                                                                            {draft.selected && draft.result === "RESTOCK" && restockLocations.length === 0 ? (
                                                                                <div className={styles.emptyLocationWarning}>
                                                                                    <AlertTriangle size={15} aria-hidden="true" />
                                                                                    Chưa cấu hình vị trí kho bán được.
                                                                                </div>
                                                                            ) : null}
                                                                        </div>
                                                                    );
                                                                }
                                                            )}
                                                        </div>

                                                        {remainingLines.length ===
                                                        0 ? (
                                                            <PanelMessage
                                                                description="Không còn sản phẩm cần xử lý kho trong phiếu hoàn này."
                                                                title="Đã xử lý kho xong"
                                                            />
                                                        ) : null}

                                                        <label
                                                            className={
                                                                styles.fullField
                                                            }
                                                        >
                                                            <span>
                                                                Ghi chú lệnh xử lý kho
                                                            </span>
                                                            <textarea
                                                                onChange={(
                                                                    event
                                                                ) => {
                                                                    setSubmissionCommandId(
                                                                        null
                                                                    );
                                                                    setQcNote(
                                                                        event
                                                                            .target
                                                                            .value
                                                                    );
                                                                }}
                                                                placeholder="Ghi chú chung nếu có"
                                                                value={qcNote}
                                                            />
                                                        </label>

                                                        {selectedLinesRequireLocation && locationError ? (
                                                            <p className={styles.qcError}>
                                                                {locationError}
                                                            </p>
                                                        ) : null}

                                                        {qcFormError ? (
                                                            <p className={styles.qcError}>
                                                                {qcFormError}
                                                            </p>
                                                        ) : null}

                                                        <div className={styles.qcActions}>
                                                            <button
                                                                className={
                                                                    styles.secondaryButton
                                                                }
                                                                onClick={() => {
                                                                    setIsQcOpen(false);
                                                                    setActiveDispositionCommand(null);
                                                                    setSubmissionCommandId(null);
                                                                    setQcFormError(null);
                                                                }}
                                                                type="button"
                                                            >
                                                                Đóng
                                                            </button>
                                                            <button
                                                                className={
                                                                    styles.primaryButton
                                                                }
                                                                disabled={
                                                                    (selectedLinesRequireLocation &&
                                                                        isLocationLoading) ||
                                                                    remainingLines.length === 0 ||
                                                                    selectedLines.length === 0 ||
                                                                    hasEmptyLocationConflict
                                                                }
                                                                onClick={
                                                                    handleProceedToConfirmation
                                                                }
                                                                type="button"
                                                            >
                                                                Tiếp tục xác nhận ({selectedLines.length})
                                                                <ArrowRight size={18} aria-hidden="true" />
                                                            </button>
                                                        </div>
                                                    </>
                                                ) : (
                                                    <div className={styles.qcConfirmationPanel}>
                                                        <div className={styles.qcConfirmationHeader}>
                                                            <div>
                                                                <h4>Xác nhận xử lý kho hàng trả</h4>
                                                                <p>
                                                                    Vui lòng kiểm tra kỹ danh sách sản phẩm, phương án xử lý và vị trí kho trước khi cập nhật tồn kho.
                                                                </p>
                                                            </div>
                                                        </div>

                                                        <div className={styles.qcConfirmationList}>
                                                            {selectedLines.map((line) => {
                                                                const draft =
                                                                    qcDraftLines[line.returnItemId] ??
                                                                    createDefaultQcDraftLine(line);
                                                                const quantity = getQcQuantity(draft);

                                                                return (
                                                                    <div
                                                                        className={styles.qcConfirmationCard}
                                                                        key={line.returnItemId}
                                                                    >
                                                                        <div className={styles.qcConfirmationTitleRow}>
                                                                            <strong>
                                                                                {getReturnLineName(returnItem, line)}
                                                                            </strong>
                                                                            <span>
                                                                                {getReturnLineMeta(returnItem, line)}
                                                                            </span>
                                                                        </div>
                                                                        <div className={styles.qcConfirmationGrid}>
                                                                            <div>
                                                                                <span className={styles.confirmLabel}>
                                                                                    Số lượng hoàn:
                                                                                </span>
                                                                                <strong>
                                                                                    SL {formatReturnQuantity(quantity)}
                                                                                </strong>
                                                                            </div>
                                                                            <div>
                                                                                <span className={styles.confirmLabel}>
                                                                                    Tình trạng:
                                                                                </span>
                                                                                <span>
                                                                                    {getReturnConditionLabel(
                                                                                        line.condition
                                                                                    )}
                                                                                </span>
                                                                            </div>
                                                                            <div>
                                                                                <span className={styles.confirmLabel}>
                                                                                    Xử lý:
                                                                                </span>
                                                                                <div>
                                                                                    <DispositionBadge
                                                                                        result={draft.result}
                                                                                    />
                                                                                </div>
                                                                            </div>
                                                                            <div>
                                                                                <span className={styles.confirmLabel}>
                                                                                    Điểm đến:
                                                                                </span>
                                                                                <strong
                                                                                    className={
                                                                                        styles.confirmLocationText
                                                                                    }
                                                                                >
                                                                                    {draft.result === "WRITE_OFF"
                                                                                        ? "Không áp dụng"
                                                                                        : getDestinationName(
                                                                                              draft.destinationLocationId
                                                                                          )}
                                                                                </strong>
                                                                            </div>
                                                                        </div>
                                                                        {draft.note.trim() ? (
                                                                            <div className={styles.confirmNoteRow}>
                                                                                <span className={styles.confirmLabel}>
                                                                                    Ghi chú:
                                                                                </span>
                                                                                <span>{draft.note.trim()}</span>
                                                                            </div>
                                                                        ) : null}
                                                                    </div>
                                                                );
                                                            })}
                                                        </div>

                                                        {qcNote.trim() ? (
                                                            <div className={styles.confirmGeneralNote}>
                                                                <strong>Ghi chú lệnh xử lý:</strong>
                                                                <span>{qcNote.trim()}</span>
                                                            </div>
                                                        ) : null}

                                                        {qcFormError ? (
                                                            <p className={styles.qcError}>{qcFormError}</p>
                                                        ) : null}

                                                        <div className={styles.qcActions}>
                                                            <button
                                                                className={styles.secondaryButton}
                                                                disabled={isSubmittingDisposition}
                                                                onClick={() => setIsConfirmingDisposition(false)}
                                                                type="button"
                                                            >
                                                                <ChevronLeft size={18} aria-hidden="true" />
                                                                Quay lại chỉnh sửa
                                                            </button>
                                                            <button
                                                                className={styles.primaryButton}
                                                                disabled={isSubmittingDisposition}
                                                                onClick={submitDisposition}
                                                                type="button"
                                                            >
                                                                {isSubmittingDisposition ? (
                                                                    <>
                                                                        <Loader2
                                                                            className="animate-spin"
                                                                            size={18}
                                                                            aria-hidden="true"
                                                                        />
                                                                        Đang cập nhật kho...
                                                                    </>
                                                                ) : (
                                                                    <>
                                                                        <CheckCircle2 size={18} aria-hidden="true" />
                                                                        Xác nhận cập nhật kho
                                                                    </>
                                                                )}
                                                            </button>
                                                        </div>
                                                    </div>
                                                )}
                                            </>
                                        ) : (
                                            <>
                                                <div
                                                    className={
                                                        styles.qcReviewPanel
                                                    }
                                                >
                                                {activeDispositionCommand.status ===
                                                "PENDING" ? (
                                                    <div>
                                                        <strong
                                                            style={{
                                                                display:
                                                                    "flex",
                                                                alignItems:
                                                                    "center",
                                                                gap: 8
                                                            }}
                                                        >
                                                            <Loader2
                                                                className="animate-spin"
                                                                size={16}
                                                                aria-hidden="true"
                                                            />
                                                            Đang xử lý cập
                                                            nhật kho...
                                                        </strong>
                                                        <span>
                                                            Lệnh đang được
                                                            xử lý bất đồng
                                                            bộ qua hệ thống
                                                            tồn kho. Vui
                                                            lòng đợi trong
                                                            giây lát...
                                                        </span>
                                                    </div>
                                                ) : null}
                                                {activeDispositionCommand.status ===
                                                "PROCESSED" ? (
                                                    <div>
                                                        <strong
                                                            style={{
                                                                display:
                                                                    "flex",
                                                                alignItems:
                                                                    "center",
                                                                gap: 8,
                                                                color: "#07883a"
                                                            }}
                                                        >
                                                            <CheckCircle2
                                                                size={16}
                                                                aria-hidden="true"
                                                            />
                                                            Đã cập nhật kho
                                                            thành công
                                                        </strong>
                                                        <span>
                                                            Tồn kho đã được
                                                            cập nhật thành
                                                            công theo kết
                                                            quả kiểm tra.
                                                            {activeDispositionCommand.inspectionId
                                                                ? ` (Phiếu kiểm tra: ${activeDispositionCommand.inspectionId})`
                                                                : ""}
                                                        </span>
                                                    </div>
                                                ) : null}
                                                {activeDispositionCommand.status ===
                                                "FAILED" ? (
                                                    <div>
                                                        <strong
                                                            style={{
                                                                display:
                                                                    "flex",
                                                                alignItems:
                                                                    "center",
                                                                gap: 8,
                                                                color: "#b91c1c"
                                                            }}
                                                        >
                                                            <XCircle
                                                                size={16}
                                                                aria-hidden="true"
                                                            />
                                                            Không thể cập
                                                            nhật kho
                                                        </strong>
                                                        <span>
                                                            {activeDispositionCommand.failureReason
                                                                ? getReturnErrorMessage(
                                                                      new Error(
                                                                          activeDispositionCommand.failureReason
                                                                      )
                                                                  )
                                                                : "Lệnh xử lý kho bị từ chối từ hệ thống tồn kho."}
                                                        </span>
                                                    </div>
                                                ) : null}

                                                <div
                                                    className={
                                                        styles.qcReviewList
                                                    }
                                                >
                                                    {(
                                                        activeDispositionCommand.lines ??
                                                        []
                                                    ).map((line) => {
                                                        const summaryLine =
                                                            summaryLines.find(
                                                                (item) =>
                                                                    item.returnItemId ===
                                                                    line.returnItemId
                                                            );

                                                        return (
                                                            <div
                                                                className={
                                                                    styles.qcReviewItem
                                                                }
                                                                key={
                                                                    line.returnItemId ||
                                                                    line.orderItemId ||
                                                                    "disposition-line"
                                                                }
                                                            >
                                                                <strong>
                                                                    {summaryLine
                                                                        ? getReturnLineName(
                                                                              returnItem,
                                                                              summaryLine
                                                                          )
                                                                        : "Sản phẩm"}
                                                                </strong>
                                                                <span>
                                                                    SL{" "}
                                                                    {formatReturnQuantity(
                                                                        line.quantity
                                                                    )}{" "}
                                                                    ·{" "}
                                                                    <DispositionBadge
                                                                        result={
                                                                            line.result
                                                                        }
                                                                    />
                                                                </span>
                                                                <small>
                                                                    Vị trí:{" "}
                                                                    {line.result === "WRITE_OFF"
                                                                        ? "Không áp dụng"
                                                                        : getDestinationName(
                                                                              line.destinationLocationId
                                                                          )}
                                                                </small>
                                                                {line.note ? (
                                                                    <p>
                                                                        {
                                                                            line.note
                                                                        }
                                                                    </p>
                                                                ) : null}
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>

                                            <div className={styles.qcActions}>
                                                {activeDispositionCommand?.status ===
                                                "PENDING" ? (
                                                    <button
                                                        className={
                                                            styles.primaryButton
                                                        }
                                                        disabled
                                                        type="button"
                                                    >
                                                        <Loader2
                                                            className="animate-spin"
                                                            size={18}
                                                            aria-hidden="true"
                                                        />
                                                        Đang xử lý kho...
                                                    </button>
                                                ) : null}

                                                {activeDispositionCommand?.status ===
                                                "PROCESSED" ? (
                                                    <>
                                                        {remainingLines.length >
                                                        0 ? (
                                                            <button
                                                                className={
                                                                    styles.primaryButton
                                                                }
                                                                onClick={
                                                                    handleContinueQcAfterProcessed
                                                                }
                                                                type="button"
                                                            >
                                                                <Plus
                                                                    size={18}
                                                                    aria-hidden="true"
                                                                />
                                                                Xử lý sản phẩm
                                                                còn lại (
                                                                {
                                                                    remainingLines.length
                                                                }
                                                                )
                                                            </button>
                                                        ) : null}
                                                        <button
                                                            className={
                                                                styles.secondaryButton
                                                            }
                                                            onClick={() => {
                                                                setIsQcOpen(
                                                                    false
                                                                );
                                                                setActiveDispositionCommand(
                                                                    null
                                                                );
                                                                setSubmissionCommandId(
                                                                    null
                                                                );
                                                            }}
                                                            type="button"
                                                        >
                                                            Đóng
                                                        </button>
                                                    </>
                                                ) : null}

                                                {activeDispositionCommand?.status ===
                                                "FAILED" ? (
                                                    <>
                                                        <button
                                                            className={
                                                                styles.primaryButton
                                                            }
                                                            onClick={
                                                                handleResetDispositionAfterFailure
                                                            }
                                                            type="button"
                                                        >
                                                            Chỉnh sửa và gửi lệnh
                                                            mới
                                                        </button>
                                                        <button
                                                            className={
                                                                styles.secondaryButton
                                                            }
                                                            onClick={() => {
                                                                setIsQcOpen(
                                                                    false
                                                                );
                                                                setActiveDispositionCommand(
                                                                    null
                                                                );
                                                                setSubmissionCommandId(
                                                                    null
                                                                );
                                                            }}
                                                            type="button"
                                                        >
                                                            Đóng
                                                        </button>
                                                    </>
                                                ) : null}
                                            </div>
                                        </>
                                    )}
                                </div>
                            ) : null}
                                </>
                            ) : null}
                        </section>

                        {canTransition ? (
                            <div className={styles.detailActions}>
                                <button
                                    className={styles.secondaryButton}
                                    disabled={isTransitioning}
                                    onClick={() => onRequestCancel(returnItem)}
                                    type="button"
                                >
                                    <XCircle size={18} aria-hidden="true" />
                                    Hủy phiếu
                                </button>
                                <button
                                    className={styles.primaryButton}
                                    disabled={isTransitioning}
                                    onClick={() =>
                                        onRequestComplete(returnItem)
                                    }
                                    type="button"
                                >
                                    <CheckCircle2 size={18} aria-hidden="true" />
                                    Xác nhận nhận hàng
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </aside>
        </div>
    );
}

function ReturnStatusBadge({ status }: { status?: ReturnStatus | null }) {
    const statusClass =
        status === "RECEIVED"
            ? styles.completedBadge
            : status === "COMPLETED"
                ? styles.completedBadge
                : status === "CANCELLED"
                    ? styles.cancelledBadge
                    : styles.createdBadge;

    return (
        <span className={`${styles.statusBadge} ${statusClass}`}>
            {getReturnStatusLabel(status)}
        </span>
    );
}

function ReturnTableMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <tr>
            <td className={styles.emptyCell} colSpan={5}>
                <strong>{title}</strong>
                <span>{description}</span>
            </td>
        </tr>
    );
}

function PanelMessage({
    description,
    title
}: {
    description?: string;
    title: string;
}) {
    return (
        <div className={styles.panelMessage}>
            <strong>{title}</strong>
            {description ? <span>{description}</span> : null}
        </div>
    );
}

function InfoRow({
    label,
    value
}: {
    label: string;
    value: ReactNode;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            <strong>{value || "--"}</strong>
        </div>
    );
}
