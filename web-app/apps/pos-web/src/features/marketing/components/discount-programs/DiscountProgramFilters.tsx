import {
    CalendarDays,
    Funnel,
    Search
} from "lucide-react";

import { MarketingSelectField } from "../MarketingSelectField";
import styles from "./DiscountProgramPage.module.css";
import type {
    DiscountApplicationType,
    DiscountProgramFilters as DiscountProgramFilterState,
    DiscountProgramStatus
} from "../../types/discount-program.types";
import { applicationTypeLabels } from "../../utils/discount-programs";

interface DiscountProgramFiltersProps {
    query: string;
    filters: DiscountProgramFilterState;
    eventOptions: string[];
    onQueryChange(value: string): void;
    onFiltersChange(value: DiscountProgramFilterState): void;
}

const statusOptions: Array<{
    label: string;
    value: "ALL" | DiscountProgramStatus;
}> = [
    { label: "Tất cả trạng thái", value: "ALL" },
    { label: "ACTIVE", value: "ACTIVE" },
    { label: "SCHEDULED", value: "SCHEDULED" },
    { label: "INACTIVE", value: "INACTIVE" }
];

const applicationOptions: Array<{
    label: string;
    value: "ALL" | DiscountApplicationType;
}> = [
    { label: "Tất cả cách áp dụng", value: "ALL" },
    {
        label: applicationTypeLabels.DISCOUNT_CODE,
        value: "DISCOUNT_CODE"
    },
    {
        label: applicationTypeLabels.FREE_SHIPPING,
        value: "FREE_SHIPPING"
    },
    {
        label: applicationTypeLabels.BUY_X_GET_Y,
        value: "BUY_X_GET_Y"
    },
    { label: applicationTypeLabels.AMOUNT_OFF, value: "AMOUNT_OFF" },
    { label: applicationTypeLabels.GIFT, value: "GIFT" }
];

export function DiscountProgramFilters({
    query,
    filters,
    eventOptions,
    onQueryChange,
    onFiltersChange
}: DiscountProgramFiltersProps) {
    return (
        <section className={styles.filtersCard}>
            <div className={styles.filters}>
                <label className={styles.search}>
                    <Search size={18} aria-hidden="true" />
                    <span className="sr-only">
                        Tìm kiếm chương trình giảm giá
                    </span>
                    <input
                        className={styles.searchInput}
                        value={query}
                        placeholder="Tìm kiếm chương trình giảm giá..."
                        onChange={(event) =>
                            onQueryChange(event.currentTarget.value)
                        }
                    />
                </label>

                <MarketingSelectField
                    label="Sự kiện"
                    value={filters.eventName}
                    onChange={(eventName) =>
                        onFiltersChange({ ...filters, eventName })
                    }
                    options={[
                        { label: "Tất cả sự kiện", value: "ALL" },
                        ...eventOptions.map((eventName) => ({
                            label: eventName,
                            value: eventName
                        }))
                    ]}
                />

                <MarketingSelectField
                    label="Trạng thái"
                    value={filters.status}
                    onChange={(status) =>
                        onFiltersChange({
                            ...filters,
                            status: status as
                                | "ALL"
                                | DiscountProgramStatus
                        })
                    }
                    options={statusOptions}
                />

                <MarketingSelectField
                    label="Cách áp dụng"
                    value={filters.applicationType}
                    onChange={(applicationType) =>
                        onFiltersChange({
                            ...filters,
                            applicationType: applicationType as
                                | "ALL"
                                | DiscountApplicationType
                        })
                    }
                    options={applicationOptions}
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
