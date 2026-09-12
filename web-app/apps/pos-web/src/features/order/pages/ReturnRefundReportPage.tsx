import {
    ChevronLeft,
    ChevronRight,
    Search
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import { temporaryBranchesFromLocations } from "../../inventory/services/branch.provider";
import type { InventoryLocationSummary } from "../../inventory/types/stock-document.types";
import {
    getReturnRefundReportDetails,
    getReturnRefundReportSummary
} from "../services/order-return-report.api";
import type { OrderPageResponse } from "../types/order.types";
import type {
    OrderReturnReportDetail,
    OrderReturnReportSummary,
    ReturnReportRefundStatus
} from "../types/order-return-report.types";
import type { ReturnStatus } from "../types/order-return.types";
import {
    CUSTOMER_RETURN_QC_RESULT_LABELS,
    CUSTOMER_RETURN_QC_STATUS_LABELS,
    RETURN_REASON_LABELS,
    RETURN_STATUS_LABELS
} from "../utils/order-return.utils";
import {
    formatCurrency,
    formatDateTime,
    formatQuantity,
    getPaymentStatusLabel
} from "../utils/order-formatters";
import styles from "./ReturnRefundReportPage.module.css";

type ReturnStatusFilter = "ALL" | ReturnStatus;
type RefundStatusFilter = "ALL" | ReturnReportRefundStatus;

const PAGE_SIZE = 20;

const emptySummary: OrderReturnReportSummary = {
    averageRefundAmount: 0,
    cancelledReturns: 0,
    completedReturns: 0,
    ordersWithReturns: 0,
    processingReturns: 0,
    qcBreakdown: [],
    reasonBreakdown: [],
    refundedReturnCount: 0,
    returnedQuantity: 0,
    statusBreakdown: [],
    totalRefundAmount: 0,
    totalReturns: 0
};

const emptyPage: OrderPageResponse<OrderReturnReportDetail> = {
    content: [],
    number: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0
};

const returnStatusOptions: Array<{ label: string; value: ReturnStatusFilter }> = [
    { label: "Tất cả trạng thái", value: "ALL" },
    { label: "Đã tạo", value: "CREATED" },
    { label: "Đã nhận hàng", value: "RECEIVED" },
    { label: "Hoàn tất", value: "COMPLETED" },
    { label: "Đã hủy", value: "CANCELLED" }
];

const refundStatusOptions: Array<{ label: string; value: RefundStatusFilter }> = [
    { label: "Tất cả hoàn tiền", value: "ALL" },
    { label: "Đã hoàn tiền", value: "REFUNDED" },
    { label: "Chưa hoàn tiền", value: "NOT_REFUNDED" }
];

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

function toNumber(value?: number | string | null) {
    if (value === null || value === undefined || value === "") {
        return 0;
    }

    const numericValue = typeof value === "number" ? value : Number(value);
    return Number.isFinite(numericValue) ? numericValue : 0;
}

function displayText(value?: string | null, fallback = "--") {
    return value?.trim() || fallback;
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

function getBranchLabel(branches: InventoryLocationSummary[], branchId?: string | null) {
    if (!branchId) {
        return "--";
    }

    return branches.find((branch) => branch.id === branchId)?.name ?? `Chi nhánh ${branchId.slice(0, 8)}`;
}

function getRefundStatusLabel(status?: string | null) {
    if (status === "REFUNDED") {
        return "Đã hoàn tiền";
    }
    if (status === "NOT_REFUNDED") {
        return "Chưa hoàn tiền";
    }
    return "--";
}

function getMethodLabel(method?: string | null) {
    if (method === "CASH") {
        return "Tiền mặt";
    }
    if (method === "BANK_TRANSFER") {
        return "Chuyển khoản";
    }
    return "--";
}

export function ReturnRefundReportPage() {
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [branches, setBranches] = useState<InventoryLocationSummary[]>([]);
    const [branchId, setBranchId] = useState("ALL");
    const [keyword, setKeyword] = useState("");
    const debouncedKeyword = useDebouncedValue(keyword.trim(), 400);
    const [returnStatus, setReturnStatus] = useState<ReturnStatusFilter>("ALL");
    const [refundStatus, setRefundStatus] = useState<RefundStatusFilter>("ALL");
    const [page, setPage] = useState(0);
    const [summary, setSummary] = useState<OrderReturnReportSummary>(emptySummary);
    const [reportPage, setReportPage] = useState(emptyPage);
    const [summaryLoading, setSummaryLoading] = useState(false);
    const [detailsLoading, setDetailsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);
    const selectedBranchId = branchId === "ALL" ? undefined : branchId;
    const selectedReturnStatus = returnStatus === "ALL" ? undefined : returnStatus;
    const selectedRefundStatus = refundStatus === "ALL" ? undefined : refundStatus;
    const rows = reportPage.content ?? [];
    const totalElements = reportPage.totalElements ?? 0;
    const totalPages = Math.max(reportPage.totalPages ?? 0, 1);
    const fromRecord = totalElements > 0 ? reportPage.number * reportPage.size + 1 : 0;
    const toRecord = Math.min((reportPage.number + 1) * reportPage.size, totalElements);

    const statusTotal = useMemo(
        () => Math.max(...(summary.statusBreakdown ?? []).map((item) => item.count), 1),
        [summary.statusBreakdown]
    );
    const qcTotal = useMemo(
        () => (summary.qcBreakdown ?? []).reduce((total, item) => total + toNumber(item.quantity), 0),
        [summary.qcBreakdown]
    );

    useEffect(() => {
        let ignore = false;

        inventoryLocationApi.list({ page: 0, size: 500, status: "ACTIVE" })
            .then((response) => {
                if (!ignore) {
                    setBranches(temporaryBranchesFromLocations(response.content ?? []));
                }
            })
            .catch(() => {
                if (!ignore) {
                    setBranches([]);
                }
            });

        return () => {
            ignore = true;
        };
    }, []);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setSummaryLoading(true);

            getReturnRefundReportSummary({
                branchId: selectedBranchId,
                fromDate: timeRange?.fromDate,
                toDate: timeRange?.toDate
            })
                .then((response) => {
                    if (!ignore) {
                        setSummary({ ...emptySummary, ...response });
                    }
                })
                .catch(() => {
                    if (!ignore) {
                        setSummary(emptySummary);
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setSummaryLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [selectedBranchId, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setDetailsLoading(true);
            setError(null);

            getReturnRefundReportDetails({
                branchId: selectedBranchId,
                fromDate: timeRange?.fromDate,
                keyword: debouncedKeyword,
                page,
                refundStatus: selectedRefundStatus,
                returnStatus: selectedReturnStatus,
                size: PAGE_SIZE,
                sort: "createdAt,desc",
                toDate: timeRange?.toDate
            })
                .then((response) => {
                    if (!ignore) {
                        setReportPage({ ...emptyPage, ...response, content: response.content ?? [] });
                    }
                })
                .catch(() => {
                    if (!ignore) {
                        setReportPage(emptyPage);
                        setError("Không thể tải dữ liệu báo cáo. Vui lòng thử lại.");
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setDetailsLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        debouncedKeyword,
        page,
        selectedBranchId,
        selectedRefundStatus,
        selectedReturnStatus,
        timeRange?.fromDate,
        timeRange?.toDate
    ]);

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        setPage(0);
    };

    return (
        <section className={`${styles.page} ${isTimeCollapsed ? styles.pageTimeCollapsed : ""}`}>
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={() => {
                    setSelectedTimeNode(null);
                    setPage(0);
                }}
                onSelect={selectTimeNode}
                onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                selectedNode={selectedTimeNode}
            />

            <div className={styles.content}>
                <header className={styles.header}>
                    <div className={styles.title}>
                        <h1>Báo cáo hoàn hàng & hoàn tiền</h1>
                        <p>Theo dõi phiếu hoàn, tiền đã hoàn và kết quả QC trong kỳ.</p>
                    </div>
                </header>

                <section className={styles.filterPanel}>
                    <label className={styles.field}>
                        Tìm kiếm
                        <span className={styles.searchBox}>
                            <Search size={18} aria-hidden="true" />
                            <input
                                onChange={(event) => {
                                    setKeyword(event.target.value);
                                    setPage(0);
                                }}
                                placeholder="Mã phiếu, mã đơn, khách hàng, SĐT..."
                                value={keyword}
                            />
                        </span>
                    </label>
                    <label className={styles.field}>
                        Chi nhánh
                        <select
                            onChange={(event) => {
                                setBranchId(event.target.value);
                                setPage(0);
                            }}
                            value={branchId}
                        >
                            <option value="ALL">Tất cả chi nhánh</option>
                            {branches.map((branch) => (
                                <option key={branch.id} value={branch.id}>
                                    {branch.name}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label className={styles.field}>
                        Trạng thái hoàn hàng
                        <select
                            onChange={(event) => {
                                setReturnStatus(event.target.value as ReturnStatusFilter);
                                setPage(0);
                            }}
                            value={returnStatus}
                        >
                            {returnStatusOptions.map((option) => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label className={styles.field}>
                        Trạng thái hoàn tiền
                        <select
                            onChange={(event) => {
                                setRefundStatus(event.target.value as RefundStatusFilter);
                                setPage(0);
                            }}
                            value={refundStatus}
                        >
                            {refundStatusOptions.map((option) => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    </label>
                </section>

                <section className={styles.summaryGrid}>
                    <Metric label="Tổng phiếu hoàn" loading={summaryLoading} value={summary.totalReturns} />
                    <Metric label="Hoàn tất" loading={summaryLoading} value={summary.completedReturns} />
                    <Metric label="Đang xử lý" loading={summaryLoading} value={summary.processingReturns} />
                    <Metric label="Đã hủy" loading={summaryLoading} value={summary.cancelledReturns} />
                    <Metric label="Tổng tiền hoàn" loading={summaryLoading} value={formatCurrency(summary.totalRefundAmount)} />
                    <Metric label="Số đơn có hoàn" loading={summaryLoading} value={summary.ordersWithReturns} />
                    <Metric label="SL sản phẩm trả" loading={summaryLoading} value={formatQuantity(summary.returnedQuantity)} />
                    <Metric label="Trung bình tiền hoàn" loading={summaryLoading} value={formatCurrency(summary.averageRefundAmount)} />
                </section>

                <section className={styles.breakdownGrid}>
                    <div className={styles.card}>
                        <h2>Trạng thái hoàn hàng</h2>
                        {(summary.statusBreakdown ?? []).map((item) => (
                            <BreakdownBar
                                key={item.status}
                                label={RETURN_STATUS_LABELS[item.status] ?? item.status}
                                max={statusTotal}
                                value={item.count}
                            />
                        ))}
                    </div>

                    <div className={styles.card}>
                        <h2>Lý do hoàn hàng</h2>
                        <table className={styles.reasonTable}>
                            <thead>
                                <tr>
                                    <th>Lý do</th>
                                    <th>Dòng</th>
                                    <th>Số lượng</th>
                                </tr>
                            </thead>
                            <tbody>
                                {(summary.reasonBreakdown ?? []).map((item) => (
                                    <tr key={item.reason}>
                                        <td>{RETURN_REASON_LABELS[item.reason] ?? item.reason}</td>
                                        <td>{item.itemCount}</td>
                                        <td>{formatQuantity(item.quantity)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.card}>
                        <h2>Kết quả QC</h2>
                        {(summary.qcBreakdown ?? []).map((item) => (
                            <BreakdownBar
                                key={item.result}
                                label={CUSTOMER_RETURN_QC_RESULT_LABELS[item.result] ?? item.result}
                                max={Math.max(qcTotal, 1)}
                                suffix={`${toNumber(item.percentage).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`}
                                value={toNumber(item.quantity)}
                            />
                        ))}
                    </div>
                </section>

                <section className={styles.tableCard}>
                    <div className={styles.tableHeader}>
                        <h2>Chi tiết phiếu hoàn</h2>
                        <span className={styles.muted}>Nguồn tổng hợp từ Order, Payment và Inventory</span>
                    </div>

                    {error ? <div className={styles.errorBox}>{error}</div> : null}

                    <div className={styles.tableWrap}>
                        <table className={styles.reportTable}>
                            <thead>
                                <tr>
                                    <th>Mã phiếu hoàn</th>
                                    <th>Mã đơn hàng</th>
                                    <th>Chi nhánh</th>
                                    <th>Khách hàng</th>
                                    <th>Trạng thái hoàn hàng</th>
                                    <th>Trạng thái QC</th>
                                    <th>Tiền hoàn</th>
                                    <th>Phương thức</th>
                                    <th>Trạng thái thanh toán</th>
                                    <th>Ngày tạo</th>
                                    <th>Ngày hoàn tiền</th>
                                </tr>
                            </thead>
                            <tbody>
                                {detailsLoading ? (
                                    <EmptyRow message="Đang tải dữ liệu báo cáo..." />
                                ) : null}
                                {!detailsLoading && !error
                                    ? rows.map((row) => (
                                          <tr key={row.returnId}>
                                              <td>
                                                  <strong>{displayText(row.returnCode)}</strong>
                                              </td>
                                              <td>{displayText(row.orderCode)}</td>
                                              <td>{getBranchLabel(branches, row.branchId)}</td>
                                              <td>
                                                  <strong>{displayText(row.customerName, "Khách lẻ")}</strong>
                                                  <div className={styles.muted}>{displayText(row.customerPhone)}</div>
                                              </td>
                                              <td>
                                                  <Pill tone={row.returnStatus === "COMPLETED" ? "green" : row.returnStatus === "CANCELLED" ? "gray" : "amber"}>
                                                      {row.returnStatus ? RETURN_STATUS_LABELS[row.returnStatus] : "--"}
                                                  </Pill>
                                              </td>
                                              <td>
                                                  <Pill tone={row.qcStatus === "COMPLETED" ? "green" : row.qcStatus === "PARTIAL" ? "amber" : "gray"}>
                                                      {row.qcStatus ? CUSTOMER_RETURN_QC_STATUS_LABELS[row.qcStatus] : "Chưa kiểm tra"}
                                                  </Pill>
                                              </td>
                                              <td>{formatCurrency(row.refundAmount)}</td>
                                              <td>{getMethodLabel(row.method)}</td>
                                              <td>
                                                  <div>{getPaymentStatusLabel(row.paymentStatus)}</div>
                                                  <div className={styles.muted}>{getRefundStatusLabel(row.refundStatus)}</div>
                                              </td>
                                              <td>{formatDateTime(row.returnCreatedAt)}</td>
                                              <td>{formatDateTime(row.refundedAt)}</td>
                                          </tr>
                                      ))
                                    : null}
                                {!detailsLoading && !error && rows.length === 0 ? (
                                    <EmptyRow message="Không có dữ liệu hoàn hàng trong khoảng thời gian này." />
                                ) : null}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.pagination}>
                        <span>
                            Hiển thị <strong>{fromRecord} - {toRecord}</strong> trong {totalElements} dòng
                        </span>
                        <div className={styles.pageTools}>
                            <button
                                className={styles.pageButton}
                                disabled={page === 0}
                                onClick={() => setPage((current) => Math.max(0, current - 1))}
                                type="button"
                            >
                                <ChevronLeft size={15} aria-hidden="true" />
                            </button>
                            {getPageNumbers(totalPages, page).map((pageNumber) => (
                                <button
                                    className={`${styles.pageButton} ${pageNumber === page ? styles.activePage : ""}`}
                                    key={pageNumber}
                                    onClick={() => setPage(pageNumber)}
                                    type="button"
                                >
                                    {pageNumber + 1}
                                </button>
                            ))}
                            <button
                                className={styles.pageButton}
                                disabled={page >= totalPages - 1}
                                onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                                type="button"
                            >
                                <ChevronRight size={15} aria-hidden="true" />
                            </button>
                        </div>
                    </div>
                </section>
            </div>
        </section>
    );
}

function Metric({
    label,
    loading,
    value
}: {
    label: string;
    loading: boolean;
    value: number | string;
}) {
    return (
        <div className={styles.metricCard}>
            <span>{label}</span>
            <strong>{loading ? "..." : value}</strong>
        </div>
    );
}

function BreakdownBar({
    label,
    max,
    suffix,
    value
}: {
    label: string;
    max: number;
    suffix?: string;
    value: number;
}) {
    const width = Math.max(0, Math.min(100, (value / Math.max(max, 1)) * 100));

    return (
        <div className={styles.breakdownRow}>
            <span>{label}</span>
            <strong>{suffix ?? value.toLocaleString("vi-VN")}</strong>
            <div className={styles.bar}>
                <span style={{ width: `${width}%` }} />
            </div>
        </div>
    );
}

function Pill({
    children,
    tone
}: {
    children: string;
    tone: "amber" | "blue" | "gray" | "green";
}) {
    return <span className={`${styles.pill} ${styles[tone]}`}>{children}</span>;
}

function EmptyRow({ message }: { message: string }) {
    return (
        <tr>
            <td className={styles.emptyCell} colSpan={11}>
                {message}
            </td>
        </tr>
    );
}
