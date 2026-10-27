import { AlertTriangle, Download, FileUp, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { SalesHistoryFilters } from "../components/sales-history/SalesHistoryFilters";
import { SalesOrderDetailDrawer } from "../components/sales-history/SalesOrderDetailDrawer";
import { SalesOrdersTable } from "../components/sales-history/SalesOrdersTable";
import {
    getSalesOrderDetail,
    getTodaySales,
    getTodaySalesSummary
} from "../services/sales-history.api";
import type {
    SalesHistoryFilters as SalesHistoryFilterValues,
    SalesHistoryOrder,
    SalesHistoryPageResponse,
    SalesHistoryPaymentFilter,
    SalesHistoryStatusFilter,
    SalesHistorySummary,
    SalesOrderDetail
} from "../types/sales-history.types";
import { printReceipt } from "../utils/receipt-print";
import styles from "./SalesHistoryPage.module.css";

const PAGE_SIZE = 10;

const defaultFilters: SalesHistoryFilterValues = {
    status: "ALL",
    paymentMethod: "ALL"
};

const emptyPage: SalesHistoryPageResponse<SalesHistoryOrder> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: PAGE_SIZE
};

const emptyStatusCounts: Record<SalesHistoryStatusFilter, number> = {
    ALL: 0,
    PENDING_PAYMENT: 0,
    COMPLETED: 0,
    CANCELLED: 0
};

const emptyPaymentCounts: Record<SalesHistoryPaymentFilter, number> = {
    ALL: 0,
    CASH: 0,
    BANK_TRANSFER: 0,
    DEBT: 0
};

function getErrorMessage(error: unknown) {
    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string"
    ) {
        return error.message;
    }

    return "Không thể tải lịch sử bán hàng. Vui lòng thử lại.";
}

function normalizeSearchValue(value?: string | null) {
    return value?.trim().toLowerCase() ?? "";
}

function matchesSearchTerm(order: SalesHistoryOrder, keyword: string) {
    if (!keyword) {
        return true;
    }

    return [
        order.orderCode,
        order.customerName,
        order.customerPhone
    ].some((value) => normalizeSearchValue(value).includes(keyword));
}

function isPendingPayment(order: SalesHistoryOrder) {
    return (
        order.status === "PENDING_PAYMENT" ||
        order.paymentStatus === "UNPAID" ||
        order.paymentStatus === "WAITING_PAYMENT" ||
        order.paymentStatus === "PENDING_PAYMENT" ||
        order.paymentStatus === "PENDING"
    );
}

function isCompleted(order: SalesHistoryOrder) {
    return order.status === "COMPLETED" || order.paymentStatus === "PAID";
}

function isCancelled(order: SalesHistoryOrder) {
    return order.status === "CANCELLED";
}

function getPaymentCountKey(
    order: SalesHistoryOrder
): SalesHistoryPaymentFilter | null {
    if (order.paymentMethod === "CASH") {
        return "CASH";
    }

    if (
        order.paymentMethod === "BANK_TRANSFER" ||
        order.paymentMethod === "TRANSFER" ||
        order.paymentMethod === "QR_CODE"
    ) {
        return "BANK_TRANSFER";
    }

    if (order.paymentMethod === "DEBT") {
        return "DEBT";
    }

    return null;
}

export function SalesHistoryPage() {
    const importInputRef = useRef<HTMLInputElement | null>(null);
    const [filters, setFilters] =
        useState<SalesHistoryFilterValues>(defaultFilters);
    const [searchTerm, setSearchTerm] = useState("");
    const [currentPage, setCurrentPage] = useState(0);
    const [ordersPage, setOrdersPage] = useState(emptyPage);
    const [summary, setSummary] = useState<SalesHistorySummary | null>(null);
    const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
    const [selectedOrder, setSelectedOrder] =
        useState<SalesOrderDetail | null>(null);
    const [listLoading, setListLoading] = useState(true);
    const [detailLoading, setDetailLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    const loadOrders = useCallback(() => {
        setListLoading(true);
        setListError(null);

        return getTodaySales({
            filters,
            page: currentPage,
            size: PAGE_SIZE
        })
            .then((response) => {
                setOrdersPage({
                    ...emptyPage,
                    ...response,
                    content: response.content ?? []
                });
            })
            .catch((error: unknown) => {
                setOrdersPage(emptyPage);
                setListError(getErrorMessage(error));
            })
            .finally(() => {
                setListLoading(false);
            });
    }, [currentPage, filters]);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            loadOrders().then(() => {
                if (ignore) {
                    return;
                }
            });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [loadOrders, refreshKey]);

    useEffect(() => {
        let ignore = false;

        getTodaySalesSummary()
            .then((response) => {
                if (!ignore) {
                    setSummary(response);
                }
            })
            .catch(() => {
                if (!ignore) {
                    setSummary(null);
                }
            });

        return () => {
            ignore = true;
        };
    }, [refreshKey]);

    useEffect(() => {
        if (!selectedOrderId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setDetailLoading(true);
            setDetailError(null);
            setSelectedOrder(null);

            getSalesOrderDetail(selectedOrderId)
                .then((response) => {
                    if (!ignore) {
                        setSelectedOrder(response);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setDetailError(getErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setDetailLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [selectedOrderId]);

    const closeDetail = () => {
        setSelectedOrderId(null);
        setSelectedOrder(null);
        setDetailError(null);
        setDetailLoading(false);
    };

    const handlePrintDetail = (order: SalesOrderDetail) => {
        try {
            printReceipt(order);
        } catch (error) {
            window.alert(
                error instanceof Error ? error.message : "Không in được hóa đơn."
            );
        }
    };

    const handlePrintFromTable = (order: SalesHistoryOrder) => {
        getSalesOrderDetail(order.orderId)
            .then(handlePrintDetail)
            .catch(() => {
                window.alert("Không tải được hóa đơn để in. Vui lòng thử lại.");
            });
    };

    const handleClickImport = () => {
        importInputRef.current?.click();
    };

    const handleImportFile = (file?: File) => {
        if (!file) {
            return;
        }

        console.log("TODO: Nhập file bán hàng hôm nay", file);
        window.alert("Chức năng nhập file đang được phát triển");
    };

    const handleExportFile = () => {
        console.log("TODO: Xuất file bán hàng hôm nay");
        window.alert("Chức năng xuất file đang được phát triển");
    };

    const handleFilterChange = (nextFilters: SalesHistoryFilterValues) => {
        setFilters(nextFilters);
        setCurrentPage(0);
    };

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setCurrentPage(0);
    };

    const normalizedSearchTerm = normalizeSearchValue(searchTerm);
    const visibleOrders = useMemo(
        () =>
            ordersPage.content.filter((order) =>
                matchesSearchTerm(order, normalizedSearchTerm)
            ),
        [normalizedSearchTerm, ordersPage.content]
    );
    const statusCounts = useMemo(() => {
        const counts = { ...emptyStatusCounts };

        ordersPage.content.forEach((order) => {
            if (isPendingPayment(order)) {
                counts.PENDING_PAYMENT += 1;
            }

            if (isCompleted(order)) {
                counts.COMPLETED += 1;
            }

            if (isCancelled(order)) {
                counts.CANCELLED += 1;
            }
        });

        counts.ALL = summary?.totalOrders ?? ordersPage.totalElements ?? 0;
        counts.PENDING_PAYMENT =
            summary?.pendingPaymentOrders ?? counts.PENDING_PAYMENT;
        counts.COMPLETED = summary?.completedOrders ?? counts.COMPLETED;

        return counts;
    }, [ordersPage.content, ordersPage.totalElements, summary]);
    const paymentCounts = useMemo(() => {
        const counts = { ...emptyPaymentCounts };

        ordersPage.content.forEach((order) => {
            const paymentKey = getPaymentCountKey(order);

            if (paymentKey) {
                counts[paymentKey] += 1;
            }
        });

        counts.ALL = ordersPage.content.length;

        return counts;
    }, [ordersPage.content]);
    const totalPages = Math.max(ordersPage.totalPages ?? 0, 1);
    const error = listError;

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1>Bán hàng hôm nay</h1>
                    <p>
                        Theo dõi doanh thu, hóa đơn và giao dịch trong ngày.
                    </p>
                </div>
                <div className={styles.headerActions}>
                    <input
                        ref={importInputRef}
                        accept=".xlsx,.xls,.csv"
                        className={styles.hiddenFileInput}
                        onChange={(event) => {
                            handleImportFile(event.target.files?.[0]);
                            event.target.value = "";
                        }}
                        type="file"
                    />
                    <button
                        className={styles.secondaryButton}
                        onClick={handleClickImport}
                        type="button"
                    >
                        <FileUp size={17} aria-hidden="true" />
                        Nhập file
                    </button>
                    <button
                        className={styles.secondaryButton}
                        onClick={handleExportFile}
                        type="button"
                    >
                        <Download size={17} aria-hidden="true" />
                        Xuất file
                    </button>
                    <button
                        className={styles.refreshButton}
                        onClick={() => setRefreshKey((key) => key + 1)}
                        type="button"
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Làm mới
                    </button>
                </div>
            </div>

            {error ? (
                <div className={styles.alert} role="alert">
                    <AlertTriangle size={18} aria-hidden="true" />
                    {error}
                </div>
            ) : null}

            <div className={styles.salesWorkspace}>
                <SalesHistoryFilters
                    paymentCounts={paymentCounts}
                    searchValue={searchTerm}
                    statusCounts={statusCounts}
                    value={filters}
                    onSearchChange={handleSearchChange}
                    onChange={handleFilterChange}
                />

                <SalesOrdersTable
                    currentPage={ordersPage.number ?? currentPage}
                    orders={visibleOrders}
                    pageSize={ordersPage.size ?? PAGE_SIZE}
                    loading={listLoading}
                    totalItems={ordersPage.totalElements ?? 0}
                    totalPages={totalPages}
                    onPageChange={setCurrentPage}
                    onOpenDetail={setSelectedOrderId}
                    onPrint={handlePrintFromTable}
                />
            </div>

            <SalesOrderDetailDrawer
                order={selectedOrder}
                loading={detailLoading}
                error={detailError}
                open={Boolean(selectedOrderId)}
                onClose={closeDetail}
                onPrint={handlePrintDetail}
            />
        </section>
    );
}
