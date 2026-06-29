import { AlertTriangle, ChevronRight, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

import { SalesHistoryFilters } from "../components/sales-history/SalesHistoryFilters";
import { SalesOrderDetailDrawer } from "../components/sales-history/SalesOrderDetailDrawer";
import { SalesOrdersTable } from "../components/sales-history/SalesOrdersTable";
import { SalesSummaryCards } from "../components/sales-history/SalesSummaryCards";
import { getTodaySalesHistory } from "../services/sales-history.api";
import type {
    SalesHistoryFilters as SalesHistoryFilterValues,
    SalesHistoryOrder,
    SalesHistoryResponse
} from "../types/sales-history.types";
import styles from "./SalesHistoryPage.module.css";

const defaultFilters: SalesHistoryFilterValues = {
    status: "ALL",
    paymentMethod: "ALL"
};

const emptyResponse: SalesHistoryResponse = {
    summary: {
        totalOrders: 0,
        completedOrders: 0,
        pendingPaymentOrders: 0,
        cancelledOrders: 0,
        grossAmount: 0,
        discountAmount: 0,
        netRevenue: 0,
        averageOrderValue: 0,
        totalItemsSold: 0,
        receivableAmount: 0
    },
    filterCounts: {
        status: {
            ALL: 0,
            UNPAID: 0,
            PAID: 0,
            CANCELLED: 0
        },
        paymentMethod: {
            ALL: 0,
            CASH: 0,
            BANK_TRANSFER: 0,
            DEBT: 0
        }
    },
    orders: []
};

export function SalesHistoryPage() {
    const [filters, setFilters] =
        useState<SalesHistoryFilterValues>(defaultFilters);
    const [data, setData] = useState<SalesHistoryResponse>(emptyResponse);
    const [selectedOrder, setSelectedOrder] =
        useState<SalesHistoryOrder | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setLoading(true);
            setError(null);

            getTodaySalesHistory(filters)
                .then((response) => {
                    if (!ignore) {
                        setData(response);
                    }
                })
                .catch(() => {
                    if (!ignore) {
                        setData(emptyResponse);
                        setError(
                            "Không thể tải lịch sử bán hàng. Vui lòng thử lại."
                        );
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [filters]);

    const handlePrint = (order: SalesHistoryOrder) => {
        console.log("TODO: In lại hóa đơn", order.orderCode);
    };

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <div>
                    <div className={styles.breadcrumbs}>
                        <span>Đơn hàng</span>
                        <ChevronRight size={16} aria-hidden="true" />
                        <span>Bán hàng hôm nay</span>
                    </div>
                    <h1>Bán hàng hôm nay</h1>
                    <p>
                        Theo dõi doanh thu, hóa đơn và giao dịch trong ngày.
                    </p>
                </div>
                <button
                    className={styles.refreshButton}
                    onClick={() => setFilters({ ...filters })}
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

            <SalesSummaryCards
                summary={data.summary}
                loading={loading}
            />

            <SalesHistoryFilters
                value={filters}
                counts={data.filterCounts}
                onChange={setFilters}
            />

            <SalesOrdersTable
                orders={data.orders}
                loading={loading}
                onOpenDetail={setSelectedOrder}
                onPrint={handlePrint}
            />

            <SalesOrderDetailDrawer
                order={selectedOrder}
                onClose={() => setSelectedOrder(null)}
                onPrint={handlePrint}
            />
        </section>
    );
}
