import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Plus,
    RefreshCw,
    Search,
    X,
    XCircle
} from "lucide-react";
import { ApiError } from "@hvt/api-client";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { ReturnInspectionModal } from "../components/ReturnInspectionModal";
import { ReturnBusinessProgress } from "../components/ReturnBusinessProgress";
import {
    cancelOrderReturn,
    getCustomerReturnQcSummary,
    getOrderRefundDetail,
    getOrderReturnList,
    getOrderReturnStatusCounts,
    getOrderReturnDetail,
    getOrderReturnTimeline,
    receiveOrderReturn
} from "../services/order-return.api";
import type { OrderPageResponse } from "../types/order.types";
import type {
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderRefundDetailResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    OrderReturnTimelineResponse,
    ReturnFilterKey,
    ReturnStatus
} from "../types/order-return.types";
import {
    formatReturnQuantity,
    getReturnCode,
    getReturnConditionLabel,
    getReturnDisplayStatusLabel,
    getReturnErrorMessage,
    getOrderRefundStatusLabel,
    getReturnReasonLabel,
    getReturnStatusLabel,
    toReturnNumber,
    type ReturnDisplayStatus
} from "../utils/order-return.utils";
import { formatCurrency, formatDateTime } from "../utils/order-formatters";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = ReturnFilterKey;

interface ConfirmAction {
    type: "receive" | "cancel";
    returnItem: OrderReturnListRow;
}

const RETURN_STATUS_FILTERS: {
    key: ReturnStatusFilter;
    label: string;
    isError?: boolean;
}[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Đã tạo" },
    { key: "RECEIVED", label: "Chờ kiểm hàng" },
    { key: "PROCESSING", label: "Đang xử lý" },
    { key: "PENDING_REFUND", label: "Chờ hoàn tiền" },
    { key: "COMPLETED", label: "Hoàn thành" },
    { key: "CANCELLED", label: "Đã hủy" },
    { key: "PROCESSING_FAILED", label: "Có lỗi", isError: true }
];

const RETURN_LIST_PAGE_SIZE = 20;

const emptyReturnPage: OrderPageResponse<OrderReturnListRow> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: RETURN_LIST_PAGE_SIZE
};

const initialStatusCounts: Record<ReturnStatusFilter, number> = {
    ALL: 0,
    CREATED: 0,
    RECEIVED: 0,
    PROCESSING: 0,
    PENDING_REFUND: 0,
    COMPLETED: 0,
    CANCELLED: 0,
    PROCESSING_FAILED: 0
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

const QC_DISPOSITION_DISPLAY_LABELS: Record<CustomerReturnQcResult, string> = {
    RESTOCK: "Đạt chất lượng",
    DAMAGED: "Hàng hỏng",
    QUARANTINE: "Cách ly",
    WRITE_OFF: "Tiêu hủy"
};

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
    const [statusCounts, setStatusCounts] =
        useState<Record<ReturnStatusFilter, number>>(initialStatusCounts);

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
                displayStatus:
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

    useEffect(() => {
        let ignore = false;

        getOrderReturnStatusCounts({
            fromDate: timeRange?.fromDate,
            keyword: debouncedSearchTerm,
            toDate: timeRange?.toDate
        })
            .then((counts) => {
                if (!ignore && counts) {
                    setStatusCounts({
                        ALL: counts.ALL ?? 0,
                        CREATED: counts.CREATED ?? 0,
                        RECEIVED: counts.RECEIVED ?? 0,
                        PROCESSING: counts.PROCESSING ?? 0,
                        PENDING_REFUND: counts.PENDING_REFUND ?? 0,
                        COMPLETED: counts.COMPLETED ?? 0,
                        CANCELLED: counts.CANCELLED ?? 0,
                        PROCESSING_FAILED: counts.PROCESSING_FAILED ?? 0
                    });
                }
            })
            .catch((error: unknown) => {
                if (!ignore) {
                    console.warn("Không thể tải số lượng trạng thái phiếu hoàn:", error);
                }
            });

        return () => {
            ignore = true;
        };
    }, [debouncedSearchTerm, reloadKey, timeRange?.fromDate, timeRange?.toDate]);

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

                if (type === "receive") {
                    getOrderReturnDetail(returnItem.id)
                        .then((freshDetail) => {
                            applyTransitionResult(freshDetail, returnItem);
                        })
                        .catch((error: unknown) => {
                            console.warn(
                                "Không thể tải lại chi tiết phiếu hoàn sau khi nhận hàng:",
                                error
                            );
                        });
                }
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
                                {RETURN_STATUS_FILTERS.map((filter) => {
                                    const isActive = statusFilter === filter.key;
                                    const count = statusCounts[filter.key] ?? 0;
                                    const chipClass = filter.isError
                                        ? `${styles.filterChip} ${styles.errorFilterChip} ${
                                              isActive ? styles.activeErrorFilterChip : ""
                                          }`
                                        : `${styles.filterChip} ${
                                              isActive ? styles.activeFilterChip : ""
                                          }`;

                                    return (
                                        <button
                                            className={chipClass}
                                            key={filter.key}
                                            onClick={() =>
                                                handleStatusFilterChange(
                                                    filter.key
                                                )
                                            }
                                            type="button"
                                        >
                                            {filter.label}{" "}
                                            <span className={styles.filterChipCount}>
                                                {count.toLocaleString("vi-VN")}
                                            </span>
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    </div>
                </div>

                <div className={styles.tableCard}>
                    <div className={styles.tableWrap}>
                        <table className={styles.returnTable}>
                            <colgroup>
                                <col style={{ width: "24%" }} />
                                <col style={{ width: "20%" }} />
                                <col style={{ width: "12%" }} />
                                <col style={{ width: "24%" }} />
                                <col style={{ width: "20%" }} />
                            </colgroup>
                            <thead>
                                <tr>
                                    <th>Mã phiếu hoàn</th>
                                    <th>Trạng thái</th>
                                    <th>Sản phẩm</th>
                                    <th>Mã đơn hàng</th>
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
                                              <td>
                                                  <ReturnStatusBadge
                                                      displayStatus={row.displayStatus}
                                                      status={row.status}
                                                  />
                                              </td>
                                              <td>
                                                  {formatReturnQuantity(
                                                      getReturnItemCount(row)
                                                  )}{" "}
                                                  sản phẩm
                                              </td>
                                              <td className={styles.code}>
                                                  {row.orderCode || "--"}
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
                    onRefreshDetail={() => {
                        setReloadKey((k) => k + 1);
                        getOrderReturnDetail(activeReturn.id)
                            .then((freshDetail) => {
                                applyTransitionResult(freshDetail, activeReturn);
                            })
                            .catch((err) =>
                                console.warn(
                                    "Lỗi làm mới chi tiết phiếu:",
                                    err
                                )
                            );
                    }}
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
    onRefreshDetail,
    onRequestCancel,
    onRequestComplete,
    onToast,
    returnItem
}: {
    detailError: string | null;
    isDetailLoading: boolean;
    isTransitioning: boolean;
    onClose: () => void;
    onRefreshDetail?: () => void;
    onRequestCancel: (returnItem: OrderReturnListRow) => void;
    onRequestComplete: (returnItem: OrderReturnListRow) => void;
    onToast: (message: string) => void;
    returnItem: OrderReturnListRow;
}) {
    const canTransition = returnItem.status === "CREATED";
    const [qcSummary, setQcSummary] =
        useState<CustomerReturnQcSummaryResponse | null>(null);
    const [refundDetail, setRefundDetail] =
        useState<OrderRefundDetailResponse | null>(null);
    const [timeline, setTimeline] = useState<OrderReturnTimelineResponse[]>([]);
    const [isQcSummaryLoading, setIsQcSummaryLoading] = useState(false);
    const [qcSummaryError, setQcSummaryError] = useState<string | null>(null);
    const [qcReloadKey, setQcReloadKey] = useState(0);
    const [isInspectionModalOpen, setIsInspectionModalOpen] = useState(false);

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

    const totalReturned = useMemo(
        () =>
            summaryLines.reduce(
                (sum, line) => sum + toReturnNumber(line.returnedQuantity),
                0
            ),
        [summaryLines]
    );
    const totalPosted = useMemo(
        () =>
            summaryLines.reduce(
                (sum, line) => sum + toReturnNumber(line.postedQcQuantity),
                0
            ),
        [summaryLines]
    );
    const totalRemaining = useMemo(
        () =>
            summaryLines.reduce(
                (sum, line) => sum + toReturnNumber(line.remainingQcQuantity),
                0
            ),
        [summaryLines]
    );

    const shouldLoadQcSummary =
        returnItem.status === "RECEIVED" || returnItem.status === "COMPLETED";
    const isQcCompleted =
        summaryLines.length > 0 &&
        totalRemaining === 0 &&
        qcSummary?.inspectionStatus !== "CREATED" &&
        (qcSummary?.qcStatus === "COMPLETED" || qcSummary?.inspectionStatus === "POSTED");

    const displayStatus = returnItem.displayStatus ?? "RECEIVED";

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
            setRefundDetail(null);
            setTimeline([]);
            setQcSummaryError(null);
            setIsInspectionModalOpen(false);
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [returnItem.id]);

    useEffect(() => {
        if (!shouldLoadQcSummary) {
            const timerId = window.setTimeout(() => {
                setQcSummary(null);
                setRefundDetail(null);
                setTimeline([]);
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
                            getReturnErrorMessage(
                                error,
                                "Không thể tải thông tin kiểm tra hàng trả"
                            )
                        );
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsQcSummaryLoading(false);
                    }
                });

            getOrderRefundDetail(returnItem.id)
                .then((refund) => {
                    if (!ignore) {
                        setRefundDetail(refund);
                    }
                })
                .catch(() => {
                    if (!ignore) {
                        setRefundDetail(null);
                    }
                });

            getOrderReturnTimeline(returnItem.id)
                .then((entries) => {
                    if (!ignore) {
                        setTimeline(entries ?? []);
                    }
                })
                .catch(() => {
                    if (!ignore) {
                        setTimeline([]);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [qcReloadKey, returnItem.id, shouldLoadQcSummary]);

    const handleOpenInspection = () => {
        if (returnItem.status !== "RECEIVED") {
            onToast("Phiếu hoàn chưa ở trạng thái đã nhận hàng.");
            return;
        }

        if (isQcCompleted) {
            onToast("Tất cả sản phẩm đã được kiểm tra xong.");
            return;
        }

        setIsInspectionModalOpen(true);
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
                        {/* Section A: Thông tin phiếu hoàn */}
                        <section className={styles.detailSection}>
                            <div className={styles.detailInfoGrid}>
                                <InfoRow
                                    label="Trạng thái"
                                    value={
                                        <ReturnStatusBadge
                                            displayStatus={displayStatus}
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

                        {/* Section B: Sản phẩm hoàn */}
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

                        <ReturnBusinessProgress
                            qcSummary={qcSummary}
                            returnData={returnItem}
                            timeline={timeline}
                        />

                        {/* Section C: Kiểm tra hàng trả (Summary Only) */}
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
                                !isQcCompleted &&
                                remainingLines.length > 0 ? (
                                    <button
                                        className={styles.primaryButton}
                                        disabled={isQcSummaryLoading}
                                        onClick={handleOpenInspection}
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
                            {isQcCompleted ? (
                                <PanelMessage
                                    description="Toàn bộ sản phẩm của phiếu hoàn đã được kiểm tra chất lượng và xử lý kho thành công."
                                    title="Đã hoàn tất kiểm tra và xử lý hàng trả."
                                />
                            ) : returnItem.status === "COMPLETED" ? (
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
                                        <div className={styles.panelMessage}>
                                            <strong>{qcSummaryError}</strong>
                                            <span>
                                                Không thể tải dữ liệu kiểm tra chất lượng từ dịch vụ kho.
                                            </span>
                                            <div>
                                                <button
                                                    className={styles.secondaryButton}
                                                    onClick={() =>
                                                        setQcReloadKey(
                                                            (k) => k + 1
                                                        )
                                                    }
                                                    style={{
                                                        marginTop: "8px",
                                                        display: "inline-flex",
                                                        alignItems: "center",
                                                        gap: "6px"
                                                    }}
                                                    type="button"
                                                >
                                                    <RefreshCw
                                                        size={14}
                                                        aria-hidden="true"
                                                    />
                                                    Thử lại tải dữ liệu kho
                                                </button>
                                            </div>
                                        </div>
                                    ) : null}

                                    {!isQcSummaryLoading &&
                                    !qcSummaryError &&
                                    qcSummary ? (
                                        <>
                                            {/* Thống kê tóm tắt 3 chỉ số */}
                                            <div className={styles.qcStatBar}>
                                                <div className={styles.qcStatCard}>
                                                    <span>Đã trả</span>
                                                    <strong>
                                                        {formatReturnQuantity(totalReturned)}
                                                    </strong>
                                                </div>
                                                <div className={styles.qcStatCard}>
                                                    <span>Đã kiểm tra</span>
                                                    <strong>
                                                        {formatReturnQuantity(totalPosted)}
                                                    </strong>
                                                </div>
                                                <div className={styles.qcStatCard}>
                                                    <span>Còn lại</span>
                                                    <strong>
                                                        {formatReturnQuantity(totalRemaining)}
                                                    </strong>
                                                </div>
                                            </div>

                                            {qcSummary.inspectedAt ? (
                                                <div style={{ fontSize: "12px", color: "var(--color-text-secondary, #64748b)", marginTop: "8px" }}>
                                                    Kiểm hàng gần nhất: {formatDateTime(qcSummary.inspectedAt)}
                                                </div>
                                            ) : null}

                                            {qcSummary.qcStatus === "PENDING" ? (
                                                <PanelMessage
                                                    description="Phiếu hoàn đã được nhận hàng và đang chờ kiểm tra chất lượng."
                                                    title="Chờ kiểm tra chất lượng"
                                                />
                                            ) : null}

                                            {/* Tóm tắt từng sản phẩm */}
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
                                                                (disposition, dIdx) => (
                                                                    <span
                                                                        key={`${disposition.result}-${disposition.destinationLocationId ?? dIdx}`}
                                                                        style={{
                                                                            display: "inline-flex",
                                                                            alignItems: "center",
                                                                            gap: "4px"
                                                                        }}
                                                                    >
                                                                        <DispositionBadge
                                                                            result={
                                                                                disposition.result
                                                                            }
                                                                        />
                                                                        <span>
                                                                            {formatReturnQuantity(
                                                                                disposition.quantity
                                                                            )}
                                                                        </span>
                                                                        {disposition.destinationLocationName ? (
                                                                            <span style={{ fontSize: "12px", color: "var(--color-text-secondary, #64748b)" }}>
                                                                                ({disposition.destinationLocationName})
                                                                            </span>
                                                                        ) : null}
                                                                    </span>
                                                                )
                                                            )}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </>
                                    ) : null}
                                </>
                            ) : null}
                        </section>

                        {/* Section D: Hoàn tiền */}
                        <section className={styles.detailSection}>
                            <h3>Hoàn tiền</h3>
                            <div className={styles.detailInfoGrid}>
                                <InfoRow
                                    label="Chính sách hoàn tiền"
                                    value={
                                        returnItem.refundRequired === false
                                            ? "Không hoàn tiền (tự động hoàn tất khi kiểm kho)"
                                            : "Có hoàn tiền"
                                    }
                                />
                                <InfoRow
                                    label="Trạng thái hoàn tiền"
                                    value={(() => {
                                        if (returnItem.refundRequired === false) {
                                            return "Không phát sinh";
                                        }
                                        if (refundDetail?.refundStatus === "REFUNDED") {
                                            return "Đã hoàn tất";
                                        }
                                        if (refundDetail?.refundStatus === "PENDING") {
                                            return isQcCompleted
                                                ? "Sẵn sàng hoàn tiền (đã kiểm kho)"
                                                : "Chờ xử lý sau kiểm kho";
                                        }
                                        if (refundDetail?.refundStatus) {
                                            return getOrderRefundStatusLabel(refundDetail.refundStatus);
                                        }
                                        return returnItem.status === "COMPLETED"
                                            ? "Đã hoàn tất"
                                            : "Chờ xử lý sau kiểm kho";
                                    })()}
                                />
                                {refundDetail?.refundAmount ? (
                                    <InfoRow
                                        label="Số tiền hoàn"
                                        value={formatCurrency(toReturnNumber(refundDetail.refundAmount))}
                                    />
                                ) : null}
                                {refundDetail?.refundedAt ? (
                                    <InfoRow
                                        label="Thời gian hoàn"
                                        value={formatDateTime(refundDetail.refundedAt)}
                                    />
                                ) : null}
                            </div>
                            {displayStatus === "PENDING_REFUND" ? (
                                <PanelMessage
                                    description="Hàng trả đã được kiểm tra và xử lý kho thành công. Vui lòng thực hiện hoàn tiền cho khách hàng tại mục Quản lý hoàn tiền."
                                    title="Sẵn sàng hoàn tiền"
                                />
                            ) : null}
                        </section>

                        <div className={styles.detailActions}>
                            {canTransition ? (
                                <>
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
                                </>
                            ) : (
                                <button
                                    className={styles.secondaryButton}
                                    onClick={onClose}
                                    type="button"
                                >
                                    Đóng
                                </button>
                            )}
                        </div>
                    </>
                ) : null}
            </aside>

            {isInspectionModalOpen && (
                <ReturnInspectionModal
                    isOpen={isInspectionModalOpen}
                    onClose={() => setIsInspectionModalOpen(false)}
                    onSuccess={() => {
                        setQcReloadKey((k) => k + 1);
                        onRefreshDetail?.();
                    }}
                    onRefresh={() => {
                        setQcReloadKey((k) => k + 1);
                        onRefreshDetail?.();
                    }}
                    onToast={onToast}
                    qcSummary={qcSummary}
                    returnItem={returnItem}
                />
            )}
        </div>
    );
}

function ReturnStatusBadge({
    status,
    displayStatus
}: {
    status?: ReturnStatus | null;
    displayStatus?: ReturnDisplayStatus | null;
}) {
    const effectiveStatus: ReturnDisplayStatus | ReturnStatus | undefined =
        displayStatus ?? status ?? undefined;

    let statusClass = styles.createdBadge;
    if (effectiveStatus === "RECEIVED") {
        statusClass = styles.receivedBadge;
    } else if (effectiveStatus === "PROCESSING") {
        statusClass = styles.processingBadge;
    } else if (effectiveStatus === "PROCESSING_FAILED") {
        statusClass = styles.processingFailedBadge;
    } else if (effectiveStatus === "PENDING_REFUND") {
        statusClass = styles.pendingRefundBadge;
    } else if (effectiveStatus === "COMPLETED") {
        statusClass = styles.completedBadge;
    } else if (effectiveStatus === "CANCELLED") {
        statusClass = styles.cancelledBadge;
    }

    const label = displayStatus
        ? getReturnDisplayStatusLabel(displayStatus)
        : getReturnStatusLabel(status);

    return (
        <span className={`${styles.statusBadge} ${statusClass}`}>
            {label}
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
