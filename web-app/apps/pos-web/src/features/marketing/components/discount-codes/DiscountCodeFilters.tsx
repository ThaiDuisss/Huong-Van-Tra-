import {
    CalendarDays,
    Funnel,
    Search
} from "lucide-react";

import { MarketingSelectField } from "../MarketingSelectField";
import styles from "../discount-programs/DiscountProgramPage.module.css";
import type {
    DiscountCodeFilters as DiscountCodeFilterState,
    DiscountCodeScope,
    DiscountCodeStatus
} from "../../types/discount-code.types";

interface DiscountCodeFiltersProps {
    query: string;
    filters: DiscountCodeFilterState;
    programOptions: string[];
    onQueryChange(value: string): void;
    onFiltersChange(value: DiscountCodeFilterState): void;
}

const statusOptions: Array<{
    label: string;
    value: "ALL" | DiscountCodeStatus;
}> = [
    { label: "Tất cả trạng thái", value: "ALL" },
    { label: "ACTIVE", value: "ACTIVE" },
    { label: "USED", value: "USED" },
    { label: "SCHEDULED", value: "SCHEDULED" },
    { label: "EXPIRED", value: "EXPIRED" }
];

const scopeOptions: Array<{
    label: string;
    value: "ALL" | DiscountCodeScope;
}> = [
    { label: "Tất cả phạm vi", value: "ALL" },
    { label: "Công khai", value: "PUBLIC" },
    { label: "Cá nhân", value: "PERSONAL" }
];

export function DiscountCodeFilters({
    query,
    filters,
    programOptions,
    onQueryChange,
    onFiltersChange
}: DiscountCodeFiltersProps) {
    return (
        <section className={styles.filtersCard}>
            <div className={styles.filters}>
                <label className={styles.search}>
                    <Search size={18} aria-hidden="true" />
                    <span className="sr-only">Tìm mã giảm giá</span>
                    <input
                        className={styles.searchInput}
                        value={query}
                        placeholder="Tìm mã coupon, chương trình hoặc khách hàng..."
                        onChange={(event) =>
                            onQueryChange(event.currentTarget.value)
                        }
                    />
                </label>

                <MarketingSelectField
                    label="Khuyến mãi"
                    value={filters.programName}
                    onChange={(programName) =>
                        onFiltersChange({ ...filters, programName })
                    }
                    options={[
                        { label: "Tất cả khuyến mãi", value: "ALL" },
                        ...programOptions.map((programName) => ({
                            label: programName,
                            value: programName
                        }))
                    ]}
                />

                <MarketingSelectField
                    label="Trạng thái"
                    value={filters.status}
                    onChange={(status) =>
                        onFiltersChange({
                            ...filters,
                            status: status as "ALL" | DiscountCodeStatus
                        })
                    }
                    options={statusOptions}
                />

                <MarketingSelectField
                    label="Phạm vi"
                    value={filters.scope}
                    onChange={(scope) =>
                        onFiltersChange({
                            ...filters,
                            scope: scope as "ALL" | DiscountCodeScope
                        })
                    }
                    options={scopeOptions}
                />

                <label className={styles.dateWrap}>
                    <CalendarDays size={17} aria-hidden="true" />
                    <span className="sr-only">
                        Chọn khoảng thời gian
                    </span>
                    <input
                        className={styles.dateInput}
                        value={filters.dateRange}
                        placeholder="Chọn khoảng thời gian"
                        onChange={(event) =>
                            onFiltersChange({
                                ...filters,
                                dateRange: event.currentTarget.value
                            })
                        }
                    />
                </label>

                <button className={styles.filterButton} type="button">
                    <Funnel size={17} aria-hidden="true" />
                    Bộ lọc
                </button>
            </div>
        </section>
    );
}
