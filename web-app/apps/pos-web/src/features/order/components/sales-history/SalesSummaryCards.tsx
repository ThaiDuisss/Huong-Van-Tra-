import {
    Calculator,
    CheckCircle,
    Clock3,
    CircleDollarSign,
    HandCoins,
    ReceiptText
} from "lucide-react";

import type { SalesHistorySummary } from "../../types/sales-history.types";
import { formatCurrency } from "../../utils/sales-history-formatters";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesSummaryCardsProps {
    summary: SalesHistorySummary;
    loading: boolean;
}

const metrics = [
    {
        key: "todayRevenue",
        label: "Doanh thu hôm nay",
        icon: CircleDollarSign,
        tone: "green",
        getValue: (summary: SalesHistorySummary) =>
            formatCurrency(summary.todayRevenue)
    },
    {
        key: "totalOrders",
        label: "Tổng đơn",
        icon: ReceiptText,
        tone: "gray",
        getValue: (summary: SalesHistorySummary) =>
            summary.totalOrders.toLocaleString("vi-VN")
    },
    {
        key: "completedOrders",
        label: "Đã hoàn thành",
        icon: CheckCircle,
        tone: "greenSoft",
        getValue: (summary: SalesHistorySummary) =>
            summary.completedOrders.toLocaleString("vi-VN")
    },
    {
        key: "pendingPaymentOrders",
        label: "Chờ thanh toán",
        icon: Clock3,
        tone: "orange",
        getValue: (summary: SalesHistorySummary) =>
            summary.pendingPaymentOrders.toLocaleString("vi-VN")
    },
    {
        key: "averageOrderValue",
        label: "Trung bình / đơn",
        icon: Calculator,
        tone: "gray",
        getValue: (summary: SalesHistorySummary) =>
            formatCurrency(summary.averageOrderValue)
    },
    {
        key: "pendingAmount",
        label: "Chờ thu",
        icon: HandCoins,
        tone: "orange",
        getValue: (summary: SalesHistorySummary) =>
            formatCurrency(summary.pendingAmount)
    }
] as const;

export function SalesSummaryCards({
    summary,
    loading
}: SalesSummaryCardsProps) {
    return (
        <div className={styles.summaryGrid}>
            {metrics.map((metric) => {
                const Icon = metric.icon;

                return (
                    <article
                        className={styles.metricCard}
                        key={metric.key}
                    >
                        <span
                            className={`${styles.metricIcon} ${
                                styles[metric.tone]
                            }`}
                        >
                            <Icon size={20} aria-hidden="true" />
                        </span>
                        <span className={styles.metricLabel}>
                            {metric.label}
                        </span>
                        {loading ? (
                            <span className={styles.metricSkeleton} />
                        ) : (
                            <strong>{metric.getValue(summary)}</strong>
                        )}
                    </article>
                );
            })}
        </div>
    );
}
