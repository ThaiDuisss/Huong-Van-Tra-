import {
    ChevronLeft,
    ChevronRight,
    RefreshCw,
    Search,
    WalletCards,
    X
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import {
    getOrderRefundDetail,
    getOrderRefundList,
    getOrderRefundPreview,
    getOrderRefundStats,
    refundOrderReturn
} from "../services/order-return.api";
import type { OrderPageResponse } from "../types/order.types";
import type {
    OrderRefundDetailResponse,
    OrderRefundStatsResponse,
    OrderRefundStatus,
    OrderRefundSummaryResponse,
    OrderReturnRefundPreviewResponse,
    OrderReturnRefundResponse
} from "../types/order-return.types";
import {
    formatCurrency,
    formatDateTime,
    formatQuantity,
    getPaymentStatusLabel
} from "../utils/order-formatters";
import styles from "./OrderRefundPage.module.css";

type RefundFilterKey = "ALL" | OrderRefundStatus;
type ToastState = { message: string; type: "success" | "error" };

const PAGE_SIZE = 20;

const emptyPage: OrderPageResponse<OrderRefundSummaryResponse> = {
    content: [],
    number: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0
};

const FILTERS: {
    key: RefundFilterKey;
    label: string;
    status?: OrderRefundStatus;
    countKey: keyof OrderRefundStatsResponse;
    isError?: boolean;
}[] = [
    { key: "ALL", label: "Tất cả", countKey: "total" },
    { key: "PENDING", label: "Chờ hoàn tiền", status: "PENDING", countKey: "pendingRefund" },
    { key: "NOT_READY", label: "Chờ xử lý", status: "NOT_READY", countKey: "notReady" },
    { key: "REFUNDED", label: "Đã hoàn tiền", status: "REFUNDED", countKey: "refunded" },
    { key: "FAILED", label: "Có lỗi", status: "FAILED", countKey: "failed", isError: true }
];

const REFUND_STATUS_LABELS: Record<OrderRefundStatus, string> = {
    FAILED: "Hoàn tiền thất bại",
    NOT_READY: "Chờ xử lý",
    PENDING: "Chờ hoàn tiền",
    REFUNDED: "Đã hoàn tiền",
    NOT_REQUIRED: "Không hoàn tiền"
};

const QC_STATUS_LABELS: Record<string, string> = {
    COMPLETED: "Hoàn tất",
    PARTIAL: "Đang kiểm",
    PENDING: "Chưa kiểm",
    UNAVAILABLE: "Không xác định",
    UNKNOWN: "Không xác định"
};

const METHOD_LABELS: Record<string, string> = {
    BANK_TRANSFER: "Chuyển khoản",
    CASH: "Tiền mặt",
    UNAVAILABLE: "Không xác định",
    UNKNOWN: "Không xác định"
};

const ERROR_MESSAGES: Record<string, string> = {
    "Payment not found": "Không tìm thấy giao dịch thanh toán.",
    "Refund amount exceeds remaining refundable amount": "Số tiền hoàn vượt quá số tiền còn có thể hoàn.",
    "Refund method is not supported yet": "Phương thức thanh toán hiện tại chưa hỗ trợ hoàn tiền.",
    "Return already refunded": "Phiếu này đã được hoàn tiền.",
    "Return inventory inspection is not completed": "Cần hoàn tất kiểm tra hàng trả trước khi hoàn tiền.",
    "Return is not eligible for refund": "Phiếu hoàn chưa đủ điều kiện hoàn tiền.",
    "Refund is not required for this return": "Phiếu hoàn này không phát sinh hoàn tiền."
};

function getErrorMessage(error: unknown) {
    if (
        typeof error === "object" &&
        error !== null &&
        "status" in error &&
        typeof error.status === "number"
    ) {
        if (error.status === 401) {
            return "Phiên đăng nhập đã hết hạn hoặc không hợp lệ.";
        }

        if (error.status === 403) {
            return "Bạn không có quyền truy cập.";
        }

        if (error.status === 500) {
            return "Máy chủ gặp lỗi khi tải danh sách hoàn tiền.";
        }

        if (error.status === 502) {
            return "Dịch vụ phụ thuộc gặp lỗi khi tải danh sách hoàn tiền.";
        }

        if (error.status === 503) {
            return "Dịch vụ tạm thời không khả dụng.";
        }

        if (error.status === 0) {
            return "Không thể kết nối đến máy chủ.";
        }
    }

    if (
        typeof error === "object" &&
        error !== null &&
        "code" in error &&
        error.code === "NETWORK_ERROR"
    ) {
        return "Không thể kết nối đến máy chủ.";
    }

    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string"
    ) {
        return ERROR_MESSAGES[error.message] ?? error.message;
    }

    return "Không thể tải dữ liệu. Vui lòng thử lại.";
}

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

function displayText(value?: string | null, fallback = "--") {
    return value?.trim() || fallback;
}

function getRefundStatusLabel(status?: OrderRefundStatus | null) {
    return status ? REFUND_STATUS_LABELS[status] ?? status : "--";
}

function getQcStatusLabel(status?: string | null) {
    return status ? QC_STATUS_LABELS[status] ?? status : "--";
}

function getMethodLabel(method?: string | null) {
    return method ? METHOD_LABELS[method] ?? method : "--";
}

function getRefundProgress(row: OrderRefundSummaryResponse): { label: string; className: string } {
    if (row.refundStatus === "FAILED") {
        return { label: "Hoàn tiền thất bại", className: styles.failedBadge };
    }
    if (row.refundStatus === "REFUNDED") {
        return { label: "Đã hoàn tiền", className: styles.refundedBadge };
    }
    if (row.refundStatus === "NOT_REQUIRED" || row.refundRequired === false) {
        return { label: "Không hoàn tiền", className: styles.notRequiredBadge };
    }
    if (row.refundStatus === "PENDING") {
        return { label: "Chờ hoàn tiền", className: styles.pendingRefundBadge };
    }
    if (row.returnStatus === "CREATED") {
        return { label: "Chờ nhận hàng", className: styles.waitingReceiptBadge };
    }
    if (row.returnStatus === "RECEIVED" || row.qcStatus === "PENDING" || row.qcStatus === "PARTIAL") {
        return { label: "Chờ kiểm hàng", className: styles.waitingQcBadge };
    }
    return { label: "Chờ xử lý", className: styles.waitingReceiptBadge };
}

function canRefund(row: OrderRefundSummaryResponse | OrderRefundDetailResponse) {
    return (
        row.refundStatus === "PENDING" &&
        typeof row.refundAmount === "number" &&
        row.refundAmount > 0 &&
        row.refundRequired !== false
    );
}

export function OrderRefundPage() {
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedKeyword = useDebouncedValue(searchTerm.trim(), 400);
    const [activeFilter, setActiveFilter] = useState<RefundFilterKey>("ALL");
    const [currentPage, setCurrentPage] = useState(0);
    const [refundPage, setRefundPage] = useState(emptyPage);
    const [stats, setStats] = useState<OrderRefundStatsResponse>({});
    const [isListLoading, setIsListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [selectedReturnId, setSelectedReturnId] = useState<string | null>(null);
    const [selectedDetail, setSelectedDetail] =
        useState<OrderRefundDetailResponse | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [confirmRow, setConfirmRow] = useState<OrderRefundSummaryResponse | null>(null);
    const [preview, setPreview] = useState<OrderReturnRefundPreviewResponse | null>(null);
    const [isPreviewLoading, setIsPreviewLoading] = useState(false);
    const [previewError, setPreviewError] = useState<string | null>(null);
    const [isRefunding, setIsRefunding] = useState(false);
    const [toast, setToast] = useState<ToastState | null>(null);

    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);
    const activeStatus = activeFilter === "ALL" ? undefined : activeFilter;
    const visibleRefunds = refundPage.content ?? [];
    const totalElements = refundPage.totalElements ?? 0;
    const totalPages = Math.max(refundPage.totalPages ?? 0, 1);
    const fromRecord = totalElements > 0 ? refundPage.number * refundPage.size + 1 : 0;
    const toRecord = Math.min((refundPage.number + 1) * refundPage.size, totalElements);

    const refreshKey = useMemo(
        () => `${timeRange?.fromDate ?? ""}:${timeRange?.toDate ?? ""}:${debouncedKeyword}:${activeStatus ?? ""}:${currentPage}`,
        [activeStatus, currentPage, debouncedKeyword, timeRange?.fromDate, timeRange?.toDate]
    );

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsListLoading(true);
            setListError(null);

            getOrderRefundList({
                fromDate: timeRange?.fromDate,
                keyword: debouncedKeyword,
                page: currentPage,
                size: PAGE_SIZE,
                sort: "createdAt,desc",
                status: activeStatus,
                toDate: timeRange?.toDate
            })
                .then((page) => {
                    if (!ignore) {
                        setRefundPage({ ...emptyPage, ...page, content: page.content ?? [] });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setRefundPage(emptyPage);
                        setListError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsListLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [activeStatus, currentPage, debouncedKeyword, refreshKey, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        let ignore = false;

        getOrderRefundStats({
            fromDate: timeRange?.fromDate,
            keyword: debouncedKeyword,
            toDate: timeRange?.toDate
        })
            .then((response) => {
                if (!ignore) {
                    setStats(response ?? {});
                }
            })
            .catch(() => {
                if (!ignore) {
                    setStats({});
                }
            });

        return () => {
            ignore = true;
        };
    }, [debouncedKeyword, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        if (!selectedReturnId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsDetailLoading(true);
            setDetailError(null);

            getOrderRefundDetail(selectedReturnId)
                .then((detail) => {
                    if (!ignore) {
                        setSelectedDetail(detail);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setSelectedDetail(null);
                        setDetailError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsDetailLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [selectedReturnId]);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => setToast(null), 3000);
        return () => window.clearTimeout(timerId);
    }, [toast]);

    const reloadList = () => {
        setCurrentPage((page) => page);
        getOrderRefundList({
            fromDate: timeRange?.fromDate,
            keyword: debouncedKeyword,
            page: currentPage,
            size: PAGE_SIZE,
            sort: "createdAt,desc",
            status: activeStatus,
            toDate: timeRange?.toDate
        }).then((page) => setRefundPage({ ...emptyPage, ...page, content: page.content ?? [] }));
        getOrderRefundStats({
            fromDate: timeRange?.fromDate,
            keyword: debouncedKeyword,
            toDate: timeRange?.toDate
        }).then((response) => setStats(response ?? {}));
    };

    const openDetail = (row: OrderRefundSummaryResponse) => {
        setSelectedReturnId(row.returnId);
    };

    const closeDetail = () => {
        setSelectedReturnId(null);
        setSelectedDetail(null);
        setDetailError(null);
        setIsDetailLoading(false);
    };

    const openConfirm = (row: OrderRefundSummaryResponse) => {
        setConfirmRow(row);
        setPreview(null);
        setPreviewError(null);
        setIsPreviewLoading(true);

        getOrderRefundPreview(row.returnId)
            .then((response) => {
                setPreview(response);
                if (!response.eligible) {
                    setPreviewError(response.reason ? getErrorMessage({ message: response.reason }) : "Phiếu hoàn chưa đủ điều kiện hoàn tiền.");
                }
            })
            .catch((error: unknown) => {
                setPreviewError(getErrorMessage(error));
            })
            .finally(() => setIsPreviewLoading(false));
    };

    const closeConfirm = () => {
        if (isRefunding) {
            return;
        }
        setConfirmRow(null);
        setPreview(null);
        setPreviewError(null);
        setIsPreviewLoading(false);
    };

    const submitRefund = () => {
        if (!confirmRow || !preview?.eligible) {
            return;
        }

        setIsRefunding(true);
        refundOrderReturn(confirmRow.returnId)
            .then((response: OrderReturnRefundResponse) => {
                setToast({ message: "Hoàn tiền thành công.", type: "success" });
                setConfirmRow(null);
                setPreview(null);
                reloadList();
                if (selectedReturnId === confirmRow.returnId) {
                    setSelectedDetail((current) =>
                        current
                            ? {
                                ...current,
                                method: response.method,
                                paymentStatus: response.paymentStatus,
                                refundAmount: response.refundAmount,
                                refundedAt: response.refundedAt,
                                refundStatus: "REFUNDED",
                                returnStatus: response.returnStatus ?? "COMPLETED",
                                totalAmountRefunded: response.totalAmountRefunded
                            }
                            : current
                    );
                }
            })
            .catch((error: unknown) => {
                setToast({ message: getErrorMessage(error), type: "error" });
            })
            .finally(() => setIsRefunding(false));
    };

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        setCurrentPage(0);
    };

    const clearTimeRange = () => {
        setSelectedTimeNode(null);
        setCurrentPage(0);
    };

    return (
        <section className={`${styles.page} ${isTimeCollapsed ? styles.pageTimeCollapsed : ""}`}>
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={clearTimeRange}
                onSelect={selectTimeNode}
                onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                selectedNode={selectedTimeNode}
            />

            <div className={styles.content}>
                <header className={styles.header}>
                    <div className={styles.title}>
                        <h1>Hoàn tiền</h1>
                        <p>Quản lý và xử lý hoàn tiền cho các phiếu hoàn hàng</p>
                    </div>
                </header>

                <div className={styles.body}>
                    <div className={styles.filterRows}>
                        <div className={styles.filterToolbar}>
                            <label className={styles.searchBox}>
                                <Search size={20} aria-hidden="true" />
                                <input
                                    aria-label="Tìm kiếm phiếu hoàn tiền"
                                    onChange={(event) => {
                                        setSearchTerm(event.target.value);
                                        setCurrentPage(0);
                                    }}
                                    onKeyDown={(event) => {
                                        if (event.key === "Escape") {
                                            setSearchTerm("");
                                            setCurrentPage(0);
                                        }
                                    }}
                                    placeholder="Tìm mã đơn, phiếu hoàn, khách hàng..."
                                    value={searchTerm}
                                />
                            </label>

                            <div className={styles.filterGroup}>
                                <span className={styles.filterLabel}>Trạng thái:</span>
                                <div className={styles.quickFilters}>
                                    {FILTERS.map((filter) => {
                                        const isActive = activeFilter === filter.key;
                                        const count = stats[filter.countKey] ?? 0;
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
                                                onClick={() => {
                                                    setActiveFilter(filter.key);
                                                    setCurrentPage(0);
                                                }}
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
                            <table className={styles.refundTable}>
                                <colgroup>
                                    <col style={{ width: "18%" }} />
                                    <col style={{ width: "17%" }} />
                                    <col style={{ width: "16%" }} />
                                    <col style={{ width: "20%" }} />
                                    <col style={{ width: "16%" }} />
                                    <col style={{ width: "13%" }} />
                                </colgroup>
                                <thead>
                                    <tr>
                                        <th>Mã phiếu hoàn</th>
                                        <th>Trạng thái</th>
                                        <th>Số tiền hoàn</th>
                                        <th>Khách hàng</th>
                                        <th>Mã đơn hàng</th>
                                        <th>Ngày tạo</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {isListLoading ? (
                                        <RefundTableMessage
                                            description="Dữ liệu đang được tải từ Order-Service"
                                            title="Đang tải phiếu hoàn tiền"
                                        />
                                    ) : null}
                                    {!isListLoading && listError ? (
                                        <RefundTableMessage
                                            description={listError}
                                            title="Không tải được danh sách hoàn tiền"
                                        />
                                    ) : null}
                                    {!isListLoading && !listError
                                        ? visibleRefunds.map((row, index) => (
                                            <tr
                                                className={index % 2 === 0 ? styles.zebraFirstRow : styles.zebraSecondRow}
                                                key={row.returnId}
                                                onClick={() => openDetail(row)}
                                                onKeyDown={(event) => {
                                                    if (event.key === "Enter" || event.key === " ") {
                                                        event.preventDefault();
                                                        openDetail(row);
                                                    }
                                                }}
                                                tabIndex={0}
                                            >
                                                <td className={styles.code}>
                                                    {displayText(row.returnCode)}
                                                </td>
                                                <td>
                                                    <RefundStatusBadge row={row} />
                                                </td>
                                                <td>
                                                    {row.refundAmount === null || row.refundAmount === undefined
                                                        ? <span className={styles.unknownAmount}>Chưa xác định</span>
                                                        : <span className={styles.refundAmount}>{formatCurrency(row.refundAmount)}</span>}
                                                </td>
                                                <td className={styles.customerCell}>
                                                    <strong>{displayText(row.customerName, "Khách lẻ")}</strong>
                                                    {row.customerPhone?.trim() ? (
                                                        <span>{row.customerPhone.trim()}</span>
                                                    ) : null}
                                                </td>
                                                <td className={styles.code}>{displayText(row.orderCode)}</td>
                                                <td>{formatDateTime(row.returnCreatedAt)}</td>
                                            </tr>
                                        ))
                                        : null}
                                    {!isListLoading && !listError && visibleRefunds.length === 0 ? (
                                        <RefundTableMessage
                                            description="Thử đổi bộ lọc thời gian, trạng thái hoặc từ khóa"
                                            title={
                                                activeFilter === "PENDING"
                                                    ? "Không có phiếu nào đang chờ hoàn tiền."
                                                    : debouncedKeyword
                                                        ? "Không tìm thấy phiếu hoàn tiền phù hợp."
                                                        : "Không có phiếu hoàn tiền."
                                            }
                                        />
                                    ) : null}
                                </tbody>
                            </table>
                        </div>

                        <div className={styles.pagination}>
                            <span>
                                Hiển thị <strong>{fromRecord} - {toRecord}</strong> trong {totalElements.toLocaleString("vi-VN")} phiếu hoàn tiền
                            </span>
                            <div className={styles.pages}>
                                <button
                                    className={styles.pageButton}
                                    disabled={currentPage === 0}
                                    onClick={() => setCurrentPage((page) => Math.max(0, page - 1))}
                                    type="button"
                                >
                                    <ChevronLeft size={18} aria-hidden="true" />
                                </button>
                                {getPageNumbers(totalPages, currentPage).map((page) => (
                                    <button
                                        className={`${styles.pageButton} ${page === currentPage ? styles.activePage : ""}`}
                                        key={page}
                                        onClick={() => setCurrentPage(page)}
                                        type="button"
                                    >
                                        {page + 1}
                                    </button>
                                ))}
                                {totalPages > 5 ? <span>...</span> : null}
                                <button
                                    className={styles.pageButton}
                                    disabled={currentPage >= totalPages - 1}
                                    onClick={() => setCurrentPage((page) => Math.min(totalPages - 1, page + 1))}
                                    type="button"
                                >
                                    <ChevronRight size={18} aria-hidden="true" />
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {selectedReturnId ? (
                <RefundDetailModal
                    detail={selectedDetail}
                    error={detailError}
                    loading={isDetailLoading}
                    onClose={closeDetail}
                    onRefund={(row) => {
                        closeDetail();
                        openConfirm(row);
                    }}
                />
            ) : null}

            {confirmRow ? (
                <RefundConfirmModal
                    error={previewError}
                    loading={isPreviewLoading}
                    onCancel={closeConfirm}
                    onConfirm={submitRefund}
                    preview={preview}
                    refunding={isRefunding}
                    row={confirmRow}
                />
            ) : null}

            {toast ? (
                <div className={`${styles.toast} ${toast.type === "success" ? styles.toastSuccess : styles.toastError}`}>
                    {toast.message}
                </div>
            ) : null}
        </section>
    );
}

function RefundTableMessage({
    title,
    description
}: {
    title: string;
    description?: string;
}) {
    return (
        <tr>
            <td className={styles.emptyCell} colSpan={6}>
                <strong>{title}</strong>
                {description ? <span>{description}</span> : null}
            </td>
        </tr>
    );
}

function RefundStatusBadge({ row }: { row: OrderRefundSummaryResponse }) {
    const progress = getRefundProgress(row);

    return <span className={`${styles.statusBadge} ${progress.className}`}>{progress.label}</span>;
}

function RefundDetailModal({
    detail,
    error,
    loading,
    onClose,
    onRefund
}: {
    detail: OrderRefundDetailResponse | null;
    error: string | null;
    loading: boolean;
    onClose: () => void;
    onRefund: (row: OrderRefundDetailResponse) => void;
}) {
    return (
        <div className={styles.modalBackdrop} onClick={onClose} role="presentation">
            <aside
                aria-modal="true"
                className={styles.detailModal}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.modalHeader}>
                    <div>
                        <h2>Chi tiết hoàn tiền</h2>
                        <p>{displayText(detail?.returnCode, "Đang tải phiếu hoàn")}</p>
                    </div>
                    <button className={styles.iconButton} onClick={onClose} type="button">
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {loading ? <div className={styles.modalState}>Đang tải chi tiết...</div> : null}
                {!loading && error ? <div className={styles.modalState}>{error}</div> : null}
                {!loading && !error && detail ? (
                    <div className={styles.detailContent}>
                        <section>
                            <h3>Thông tin phiếu hoàn</h3>
                            <InfoGrid
                                items={[
                                    ["Mã phiếu hoàn", displayText(detail.returnCode)],
                                    ["Mã đơn", displayText(detail.orderCode)],
                                    ["Khách hàng", displayText(detail.customerName, "Khách lẻ")],
                                    ["Số điện thoại", displayText(detail.customerPhone)],
                                    ["Trạng thái Return", displayText(detail.returnStatus)],
                                    ["Chính sách hoàn tiền", detail.refundRequired === false ? "Không hoàn tiền" : "Có hoàn tiền"],
                                    ["QC", getQcStatusLabel(detail.qcStatus)]
                                ]}
                            />
                        </section>

                        <section>
                            <h3>Thông tin Refund</h3>
                            <InfoGrid
                                items={[
                                    ["Số tiền hoàn", detail.refundAmount ? formatCurrency(detail.refundAmount) : "Chưa xác định"],
                                    ["Tổng đã hoàn", detail.totalAmountRefunded ? formatCurrency(detail.totalAmountRefunded) : "--"],
                                    ["Phương thức", getMethodLabel(detail.method)],
                                    ["Payment status", getPaymentStatusLabel(detail.paymentStatus)],
                                    ["Refund status", getRefundStatusLabel(detail.refundStatus)],
                                    ["Thời gian hoàn", formatDateTime(detail.refundedAt)]
                                ]}
                            />
                        </section>

                        <section>
                            <h3>Sản phẩm hoàn</h3>
                            <div className={styles.itemList}>
                                {(detail.items ?? []).map((item) => (
                                    <div className={styles.itemRow} key={item.id ?? item.orderItemId}>
                                        <div>
                                            <strong>{displayText(item.productNameSnapshot)}</strong>
                                            <span>{displayText(item.skuSnapshot)} · {displayText(item.variantNameSnapshot)}</span>
                                        </div>
                                        <div>
                                            <strong>{formatQuantity(item.quantity)}</strong>
                                            <span>{displayText(item.reason)} · {displayText(item.condition)}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <div className={styles.detailActions}>
                            <button
                                className={styles.secondaryButton}
                                onClick={onClose}
                                type="button"
                            >
                                Đóng
                            </button>
                            {canRefund(detail) ? (
                                <button
                                    className={styles.refundButton}
                                    onClick={() => onRefund(detail)}
                                    type="button"
                                >
                                    <WalletCards size={16} aria-hidden="true" />
                                    Thực hiện hoàn tiền
                                </button>
                            ) : null}
                        </div>
                    </div>
                ) : null}
            </aside>
        </div>
    );
}

function RefundConfirmModal({
    error,
    loading,
    onCancel,
    onConfirm,
    preview,
    refunding,
    row
}: {
    error: string | null;
    loading: boolean;
    onCancel: () => void;
    onConfirm: () => void;
    preview: OrderReturnRefundPreviewResponse | null;
    refunding: boolean;
    row: OrderRefundSummaryResponse;
}) {
    const amount = preview?.refundAmount ?? row.refundAmount;
    const canSubmit = Boolean(preview?.eligible) && !loading && !refunding;

    return (
        <div className={styles.modalBackdrop} onClick={onCancel} role="presentation">
            <section
                aria-modal="true"
                className={styles.confirmModal}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.modalHeader}>
                    <div>
                        <h2>Xác nhận hoàn tiền?</h2>
                        <p>Số tiền hoàn được hệ thống tính theo giá trị thực tế khách đã thanh toán.</p>
                    </div>
                </div>

                <InfoGrid
                    items={[
                        ["Mã phiếu hoàn", displayText(row.returnCode)],
                        ["Mã đơn", displayText(row.orderCode)],
                        ["Khách hàng", displayText(row.customerName, "Khách lẻ")],
                        ["Số tiền hoàn", loading ? "Đang tải..." : amount ? formatCurrency(amount) : "Chưa xác định"],
                        ["Phương thức hoàn", getMethodLabel(preview?.paymentMethod ?? row.method)]
                    ]}
                />

                {error ? <div className={styles.errorBox}>{error}</div> : null}

                <div className={styles.modalFooter}>
                    <button className={styles.secondaryButton} disabled={refunding} onClick={onCancel} type="button">
                        Hủy
                    </button>
                    <button className={styles.primaryButton} disabled={!canSubmit} onClick={onConfirm} type="button">
                        {refunding ? (
                            <>
                                <RefreshCw size={16} aria-hidden="true" />
                                Đang hoàn tiền...
                            </>
                        ) : (
                            "Xác nhận hoàn tiền"
                        )}
                    </button>
                </div>
            </section>
        </div>
    );
}

function InfoGrid({ items }: { items: [string, string][] }) {
    return (
        <dl className={styles.infoGrid}>
            {items.map(([label, value]) => (
                <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                </div>
            ))}
        </dl>
    );
}
