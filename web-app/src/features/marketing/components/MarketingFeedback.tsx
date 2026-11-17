import { AlertCircle, CheckCircle2, X } from "lucide-react";
import { useEffect } from "react";

import { TableCell, TableRow } from "../../../components/ui/Table";
import styles from "./discount-programs/DiscountProgramPage.module.css";

export interface MarketingNotice {
    type: "success" | "error";
    message: string;
}

export function MarketingToast({
    notice,
    onClose
}: {
    notice: MarketingNotice | null;
    onClose(): void;
}) {
    useEffect(() => {
        if (!notice) return;
        const timeoutId = window.setTimeout(onClose, 3500);
        return () => window.clearTimeout(timeoutId);
    }, [notice, onClose]);

    if (!notice) return null;

    const success = notice.type === "success";
    const Icon = success ? CheckCircle2 : AlertCircle;

    return (
        <div
            className={`${styles.toast} ${
                success ? styles.toastSuccess : styles.toastError
            }`}
            role="status"
            aria-live="polite"
        >
            <Icon size={20} aria-hidden="true" />
            <span>{notice.message}</span>
            <button
                type="button"
                className={styles.toastClose}
                onClick={onClose}
                aria-label="Đóng thông báo"
            >
                <X size={16} aria-hidden="true" />
            </button>
        </div>
    );
}

export function MarketingTableSkeleton({
    columns,
    rows = 8
}: {
    columns: number;
    rows?: number;
}) {
    return (
        <>
            {Array.from({ length: rows }, (_, rowIndex) => (
                <TableRow
                    key={`marketing-loading-${rowIndex}`}
                    className={styles.loadingRow}
                    striped
                >
                    {Array.from({ length: columns }, (__, columnIndex) => (
                        <TableCell key={columnIndex}>
                            <span
                                className={`${styles.skeletonBlock} ${
                                    columnIndex === 0
                                        ? styles.skeletonTitle
                                        : columnIndex === columns - 1
                                          ? styles.skeletonIcon
                                          : styles.skeletonStatus
                                }`}
                            />
                        </TableCell>
                    ))}
                </TableRow>
            ))}
        </>
    );
}
