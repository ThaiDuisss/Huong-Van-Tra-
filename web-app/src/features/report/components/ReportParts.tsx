import type { ReactNode } from "react";

import { TableCell, TableRow } from "../../../components/ui/Table";
import styles from "../pages/StatisticReportPage.module.css";

export function Metric({
    label,
    loading,
    value
}: {
    label: string;
    loading: boolean;
    value: string;
}) {
    return (
        <div className={styles.metricCard}>
            <span>{label}</span>
            <strong>{loading ? "..." : value}</strong>
        </div>
    );
}

/** Giá trị kèm thanh ngang tỉ lệ với giá trị lớn nhất trong bảng. */
export function BarValue({
    children,
    max,
    value
}: {
    children: ReactNode;
    max: number;
    value: number;
}) {
    const width = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0;

    return (
        <div className={styles.barValue}>
            <strong>{children}</strong>
            <div className={styles.bar} aria-hidden="true">
                <span style={{ width: `${width}%` }} />
            </div>
        </div>
    );
}

export function RankBadge({ rank }: { rank: number }) {
    return (
        <span className={`${styles.rank} ${rank <= 3 ? styles.rankTop : ""}`}>{rank}</span>
    );
}

export function StateRow({ colSpan, message }: { colSpan: number; message: string }) {
    return (
        <TableRow>
            <TableCell colSpan={colSpan}>
                <div className={styles.emptyCell}>{message}</div>
            </TableCell>
        </TableRow>
    );
}
