import {
    SALES_PAYMENT_METHOD_OPTIONS,
    SALES_PAYMENT_STATUS_OPTIONS
} from "../../constants/sales-history-options";
import type {
    SalesHistoryFilterCounts,
    SalesHistoryFilters as SalesHistoryFilterValues,
    SalesHistoryPaymentFilter,
    SalesHistoryStatusFilter
} from "../../types/sales-history.types";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesHistoryFiltersProps {
    value: SalesHistoryFilterValues;
    counts: SalesHistoryFilterCounts;
    onChange: (value: SalesHistoryFilterValues) => void;
}

interface FilterChipProps<T extends string> {
    active: boolean;
    count: number;
    label: string;
    value: T;
    onSelect: (value: T) => void;
}

function FilterChip<T extends string>({
    active,
    count,
    label,
    value,
    onSelect
}: FilterChipProps<T>) {
    return (
        <button
            className={`${styles.filterChip} ${
                active ? styles.activeChip : ""
            }`}
            onClick={() => onSelect(value)}
            type="button"
        >
            {label}
            <span>{count.toLocaleString("vi-VN")}</span>
        </button>
    );
}

export function SalesHistoryFilters({
    value,
    counts,
    onChange
}: SalesHistoryFiltersProps) {
    return (
        <section
            className={styles.filtersPanel}
            aria-label="Bộ lọc lịch sử bán hàng"
        >
            <div className={styles.filterRow}>
                <span className={styles.filterLabel}>Trạng thái:</span>
                <div className={styles.chipGroup}>
                    {SALES_PAYMENT_STATUS_OPTIONS.map((option) => (
                        <FilterChip<SalesHistoryStatusFilter>
                            active={value.status === option.key}
                            count={counts.status[option.key]}
                            key={option.key}
                            label={option.label}
                            onSelect={(status) =>
                                onChange({
                                    ...value,
                                    status
                                })
                            }
                            value={option.key}
                        />
                    ))}
                </div>
            </div>

            <div className={styles.filterRow}>
                <span className={styles.filterLabel}>
                    Phương thức thanh toán:
                </span>
                <div className={styles.chipGroup}>
                    {SALES_PAYMENT_METHOD_OPTIONS.map((option) => (
                        <FilterChip<SalesHistoryPaymentFilter>
                            active={value.paymentMethod === option.key}
                            count={counts.paymentMethod[option.key]}
                            key={option.key}
                            label={option.label}
                            onSelect={(paymentMethod) =>
                                onChange({
                                    ...value,
                                    paymentMethod
                                })
                            }
                            value={option.key}
                        />
                    ))}
                </div>
            </div>
        </section>
    );
}
