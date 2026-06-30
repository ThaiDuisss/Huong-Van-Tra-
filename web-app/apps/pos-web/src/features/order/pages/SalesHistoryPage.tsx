import { AlertTriangle, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { SalesHistoryFilters } from "../components/sales-history/SalesHistoryFilters";
import { SalesOrderDetailDrawer } from "../components/sales-history/SalesOrderDetailDrawer";
import { SalesOrdersTable } from "../components/sales-history/SalesOrdersTable";
import {
    getSalesOrderDetail,
    getTodaySales
} from "../services/sales-history.api";
import type {
    SalesHistoryFilters as SalesHistoryFilterValues,
    SalesHistoryOrder,
    SalesHistoryPageResponse,
    SalesOrderDetail
} from "../types/sales-history.types";
import styles from "./SalesHistoryPage.module.css";

const PAGE_SIZE = 20;

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

export function SalesHistoryPage() {
    const [filters, setFilters] =
        useState<SalesHistoryFilterValues>(defaultFilters);
    const [searchTerm, setSearchTerm] = useState("");
    const [ordersPage, setOrdersPage] = useState(emptyPage);
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
            page: 0,
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
    }, [filters]);

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
        console.log("TODO: In lại hóa đơn", order.orderCode || order.orderId);
    };

    const handlePrintFromTable = (order: SalesHistoryOrder) => {
        console.log("TODO: In lại hóa đơn", order.orderCode);
    };

    const normalizedSearchTerm = normalizeSearchValue(searchTerm);
    const visibleOrders = useMemo(
        () =>
            ordersPage.content.filter((order) =>
                matchesSearchTerm(order, normalizedSearchTerm)
            ),
        [normalizedSearchTerm, ordersPage.content]
    );
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
                <button
                    className={styles.refreshButton}
                    onClick={() => setRefreshKey((key) => key + 1)}
                    type="button"
                >
                    <RefreshCw size={17} aria-hidden="true" />
                    Làm mới
                </button>
            </div>

            {error ? (
                <div className={styles.alert} role="alert">
                    <AlertTriangle size={18} aria-hidden="true" />
                    {error}
                </div>
            ) : null}

            <div className={styles.salesWorkspace}>
                <SalesHistoryFilters
                    searchValue={searchTerm}
                    value={filters}
                    onSearchChange={setSearchTerm}
                    onChange={setFilters}
                />

                <SalesOrdersTable
                    orders={visibleOrders}
                    loading={listLoading}
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
