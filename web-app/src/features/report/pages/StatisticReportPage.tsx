import { useState } from "react";

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { Button } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
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
/** Bề rộng ô lọc; dưới 720px các ô co giãn đều theo hàng. */
const FIELD_CLASS = "min-w-[170px] max-[720px]:min-w-0 max-[720px]:flex-[1_1_140px]";

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
            <ListPageHeader
                actions={
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
                }
                description="Xếp hạng nhân viên bán hàng và sản phẩm bán chạy theo kỳ."
                title="Báo cáo thống kê"
            />

            <section className={styles.filterPanel}>
                <Field className={FIELD_CLASS} label="Từ ngày">
                    <TextInput
                        max={toDate || undefined}
                        onChange={(event) =>
                            setRange((current) => ({ ...current, fromDate: event.target.value }))
                        }
                        type="date"
                        value={fromDate}
                    />
                </Field>
                <Field className={FIELD_CLASS} label="Đến ngày">
                    <TextInput
                        min={fromDate || undefined}
                        onChange={(event) =>
                            setRange((current) => ({ ...current, toDate: event.target.value }))
                        }
                        type="date"
                        value={toDate}
                    />
                </Field>
                <Field className={FIELD_CLASS} label="Số dòng xếp hạng">
                    <NativeSelect onChange={(event) => setLimit(Number(event.target.value))} value={limit}>
                        {LIMIT_OPTIONS.map((option) => (
                            <option key={option} value={option}>
                                Top {option}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <div className={styles.presets}>
                    <Button onClick={() => setRange(getMonthRange(new Date()))}>Tháng này</Button>
                    <Button onClick={() => setRange(getMonthRange(new Date(), -1))}>Tháng trước</Button>
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
