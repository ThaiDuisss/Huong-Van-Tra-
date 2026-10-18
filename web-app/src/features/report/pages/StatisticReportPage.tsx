import { useState } from "react";

import { TopSalesStaffPanel } from "../components/TopSalesStaffPanel";
import { TopSellingProductsPanel } from "../components/TopSellingProductsPanel";
import { getMonthRange } from "../utils/statistic-report.utils";
import styles from "./StatisticReportPage.module.css";

export type StatisticReportTab = "staff" | "products";

interface StatisticReportPageProps {
    /** Tab mở mặc định; cho phép gắn 2 route riêng vào cùng một trang. */
    initialTab?: StatisticReportTab;
}

const TABS: Array<{ key: StatisticReportTab; label: string }> = [
    { key: "staff", label: "Nhân viên bán hàng" },
    { key: "products", label: "Sản phẩm bán chạy" }
];

const LIMIT_OPTIONS = [10, 20, 50, 100];

export function StatisticReportPage({ initialTab = "staff" }: StatisticReportPageProps) {
    const [tab, setTab] = useState<StatisticReportTab>(initialTab);
    const [range, setRange] = useState(() => getMonthRange(new Date()));
    const [limit, setLimit] = useState(LIMIT_OPTIONS[0]);

    const { fromDate, toDate } = range;
    const rangeError = !fromDate || !toDate
        ? "Vui lòng chọn đủ từ ngày và đến ngày."
        : fromDate > toDate
          ? "Từ ngày không được lớn hơn đến ngày."
          : null;
    const panelProps = { enabled: rangeError === null, fromDate, limit, toDate };

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <div className={styles.title}>
                    <h1>Báo cáo thống kê</h1>
                    <p>Xếp hạng nhân viên bán hàng và sản phẩm bán chạy theo kỳ.</p>
                </div>
                <div className={styles.tabs} role="tablist">
                    {TABS.map((item) => (
                        <button
                            aria-selected={tab === item.key}
                            className={`${styles.tab} ${tab === item.key ? styles.tabActive : ""}`}
                            key={item.key}
                            onClick={() => setTab(item.key)}
                            role="tab"
                            type="button"
                        >
                            {item.label}
                        </button>
                    ))}
                </div>
            </header>

            <section className={styles.filterPanel}>
                <label className={styles.field}>
                    Từ ngày
                    <input
                        max={toDate || undefined}
                        onChange={(event) =>
                            setRange((current) => ({ ...current, fromDate: event.target.value }))
                        }
                        type="date"
                        value={fromDate}
                    />
                </label>
                <label className={styles.field}>
                    Đến ngày
                    <input
                        min={fromDate || undefined}
                        onChange={(event) =>
                            setRange((current) => ({ ...current, toDate: event.target.value }))
                        }
                        type="date"
                        value={toDate}
                    />
                </label>
                <label className={styles.field}>
                    Số dòng xếp hạng
                    <select onChange={(event) => setLimit(Number(event.target.value))} value={limit}>
                        {LIMIT_OPTIONS.map((option) => (
                            <option key={option} value={option}>
                                Top {option}
                            </option>
                        ))}
                    </select>
                </label>
                <div className={styles.presets}>
                    <button onClick={() => setRange(getMonthRange(new Date()))} type="button">
                        Tháng này
                    </button>
                    <button onClick={() => setRange(getMonthRange(new Date(), -1))} type="button">
                        Tháng trước
                    </button>
                </div>
            </section>

            {rangeError ? <div className={styles.errorBox}>{rangeError}</div> : null}

            {tab === "staff" ? (
                <TopSalesStaffPanel {...panelProps} />
            ) : (
                <TopSellingProductsPanel {...panelProps} />
            )}
        </section>
    );
}

export function TopSalesStaffReportPage() {
    return <StatisticReportPage initialTab="staff" />;
}

export function TopSellingProductsReportPage() {
    return <StatisticReportPage initialTab="products" />;
}
