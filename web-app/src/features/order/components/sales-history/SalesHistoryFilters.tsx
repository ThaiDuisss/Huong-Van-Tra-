import { Search } from "lucide-react";

import {
    SALES_PAYMENT_METHOD_OPTIONS,
    SALES_STATUS_FILTER_OPTIONS
} from "../../constants/sales-history-options";
import type {
    SalesHistoryFilters as SalesHistoryFilterValues,
    SalesHistoryPaymentFilter,
    SalesHistoryStatusFilter
} from "../../types/sales-history.types";
import styles from "../../pages/SalesHistoryPage.module.css";

interface SalesHistoryFiltersProps {
    searchValue: string;
    value: SalesHistoryFilterValues;
    paymentCounts: Partial<Record<SalesHistoryPaymentFilter, number>>;
    statusCounts: Partial<Record<SalesHistoryStatusFilter, number>>;
    onSearchChange: (value: string) => void;
    onChange: (value: SalesHistoryFilterValues) => void;
}

interface FilterChipProps<T extends string> {
    active: boolean;
    count?: number;
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
            <span>{count ?? 0}</span>
        </button>
    );
}

export function SalesHistoryFilters({
    searchValue,
    value,
    paymentCounts,
    statusCounts,
    onSearchChange,
    onChange
}: SalesHistoryFiltersProps) {
    return (
        <section
            className={styles.filtersPanel}
            aria-label="Bộ lọc lịch sử bán hàng"
        >
            <div className={styles.filterTopRow}>
                <label className={styles.searchBox}>
                    <Search size={19} aria-hidden="true" />
                    <input
                        aria-label="Tìm kiếm hóa đơn bán hàng hôm nay"
                        onChange={(event) =>
                            onSearchChange(event.target.value)
                        }
                        onKeyDown={(event) => {
                            if (event.key === "Escape") {
                                onSearchChange("");
                            }
                        }}
                        placeholder="Tìm mã đơn, khách hàng, SĐT..."
                        value={searchValue}
                    />
                </label>

                <div className={styles.filterRow}>
                    <span className={styles.filterLabel}>Trạng thái:</span>
                    <div className={styles.chipGroup}>
                        {SALES_STATUS_FILTER_OPTIONS.map((option) => (
                            <FilterChip<SalesHistoryStatusFilter>
                                active={value.status === option.key}
                                count={statusCounts[option.key]}
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
            </div>

            <div className={styles.filterBottomRow}>
                <div className={styles.filterRow}>
                    <span className={styles.filterLabel}>
                        Phương thức thanh toán:
                    </span>
                    <div className={styles.chipGroup}>
                        {SALES_PAYMENT_METHOD_OPTIONS.map((option) => (
                            <FilterChip<SalesHistoryPaymentFilter>
                                active={value.paymentMethod === option.key}
                                count={paymentCounts[option.key]}
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
            </div>
        </section>
    );
}
