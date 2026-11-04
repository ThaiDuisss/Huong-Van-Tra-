import styles from "../pages/CreateReturnPage.module.css";

interface ReturnSelectedSummaryProps {
    selectedCount: number;
    totalQuantity: number;
    inline?: boolean;
    className?: string;
}

export function ReturnSelectedSummary({
    selectedCount,
    totalQuantity,
    inline = false,
    className
}: ReturnSelectedSummaryProps) {
    if (inline) {
        return (
            <div className={`${styles.summaryInlineRow} ${className ?? ""}`.trim()}>
                <span>
                    Đã chọn <strong>{selectedCount}</strong> sản phẩm
                </span>
                <span className={styles.summaryBullet} aria-hidden="true">
                    —
                </span>
                <span>
                    Tổng SL hoàn: <strong>{totalQuantity}</strong>
                </span>
            </div>
        );
    }

    return (
        <div className={`${styles.selectionSummaryRow} ${className ?? ""}`.trim()}>
            <span className={styles.summaryLeft}>
                Đã chọn <strong>{selectedCount}</strong> sản phẩm
            </span>
            <span className={styles.summaryRight}>
                Tổng SL hoàn: <strong>{totalQuantity}</strong>
            </span>
        </div>
    );
}
