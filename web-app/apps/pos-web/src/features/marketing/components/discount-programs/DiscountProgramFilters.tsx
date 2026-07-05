import {
    CalendarDays,
    Search,
    SlidersHorizontal,
    X
} from "lucide-react";
import { useState, type FormEvent } from "react";

import { MarketingSelectField } from "../MarketingSelectField";
import styles from "./DiscountProgramPage.module.css";
import {
    getEventAudienceLabel,
    getEventStatusLabel,
    getEventTypeLabel
} from "../../utils/event-labels";

export interface EventFilterFormState {
    keyword: string;
    eventType: string;
    audience: string;
    status: string;
    from: string;
    to: string;
}

interface DiscountProgramFiltersProps {
    filters: EventFilterFormState;
    eventTypes: string[];
    eventAudiences: string[];
    eventStatuses: string[];
    loading?: boolean;
    showClear?: boolean;
    onFiltersChange(value: EventFilterFormState): void;
    onSearch(): void;
    onClear(): void;
}

export function getInitialEventFilters(): EventFilterFormState {
    return {
        keyword: "",
        eventType: "ALL",
        audience: "ALL",
        status: "ALL",
        from: "",
        to: ""
    };
}

export const defaultEventListFilters = getInitialEventFilters();

export function DiscountProgramFilters({
    filters,
    eventTypes,
    eventAudiences,
    eventStatuses,
    loading = false,
    showClear = false,
    onFiltersChange,
    onSearch,
    onClear
}: DiscountProgramFiltersProps) {
    const [detailOpen, setDetailOpen] = useState(false);
    const hasDetailFilters =
        filters.eventType !== "ALL" ||
        filters.audience !== "ALL" ||
        filters.status !== "ALL" ||
        filters.from.length > 0 ||
        filters.to.length > 0;

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        onSearch();
    }

    return (
        <section className={styles.filtersCard}>
            <form className={styles.filters} onSubmit={handleSubmit}>
                <div className={styles.filterMainRow}>
                    <label className={styles.search}>
                        <Search size={18} aria-hidden="true" />
                        <span className="sr-only">
                            Tìm kiếm sự kiện khuyến mãi
                        </span>
                        <input
                            className={styles.searchInput}
                            value={filters.keyword}
                            placeholder="Tìm kiếm sự kiện khuyến mãi..."
                            onChange={(event) =>
                                onFiltersChange({
                                    ...filters,
                                    keyword: event.currentTarget.value
                                })
                            }
                        />
                    </label>

                    <button
                        className={`${styles.filterToggleButton} ${
                            detailOpen || hasDetailFilters
                                ? styles.filterToggleButtonActive
                                : ""
                        }`}
                        type="button"
                        aria-label="Lọc chi tiết"
                        aria-expanded={detailOpen}
                        onClick={() =>
                            setDetailOpen((current) => !current)
                        }
                    >
                        <SlidersHorizontal
                            size={18}
                            aria-hidden="true"
                        />
                    </button>

                    <button
                        className={styles.primaryButton}
                        type="submit"
                        disabled={loading}
                    >
                        <Search size={17} aria-hidden="true" />
                        Tìm kiếm
                    </button>

                    {showClear ? (
                        <button
                            className={styles.secondaryButton}
                            type="button"
                            disabled={loading}
                            onClick={onClear}
                        >
                            <X size={17} aria-hidden="true" />
                            Xóa lọc
                        </button>
                    ) : null}
                </div>

                {detailOpen ? (
                    <div className={styles.filterDetailPanel}>
                        <MarketingSelectField
                            label="Loại sự kiện"
                            value={filters.eventType}
                            onChange={(eventType) =>
                                onFiltersChange({ ...filters, eventType })
                            }
                            options={[
                                {
                                    label: "Tất cả loại sự kiện",
                                    value: "ALL"
                                },
                                ...eventTypes.map((eventType) => ({
                                    label: getEventTypeLabel(eventType),
                                    value: eventType
                                }))
                            ]}
                        />

                        <MarketingSelectField
                            label="Đối tượng áp dụng"
                            value={filters.audience}
                            onChange={(audience) =>
                                onFiltersChange({ ...filters, audience })
                            }
                            options={[
                                {
                                    label: "Tất cả đối tượng",
                                    value: "ALL"
                                },
                                ...eventAudiences.map((audience) => ({
                                    label: getEventAudienceLabel(audience),
                                    value: audience
                                }))
                            ]}
                        />

                        <MarketingSelectField
                            label="Trạng thái"
                            value={filters.status}
                            onChange={(status) =>
                                onFiltersChange({ ...filters, status })
                            }
                            options={[
                                {
                                    label: "Tất cả trạng thái",
                                    value: "ALL"
                                },
                                ...eventStatuses.map((status) => ({
                                    label: getEventStatusLabel(status),
                                    value: status
                                }))
                            ]}
                        />

                        <div className={styles.filterDateGroup}>
                            <DateFilterField
                                label="Từ ngày"
                                value={filters.from}
                                onChange={(from) =>
                                    onFiltersChange({ ...filters, from })
                                }
                            />

                            <DateFilterField
                                label="Đến ngày"
                                value={filters.to}
                                onChange={(to) =>
                                    onFiltersChange({ ...filters, to })
                                }
                            />
                        </div>
                    </div>
                ) : null}
            </form>
        </section>
    );
}

function DateFilterField({
    label,
    value,
    onChange
}: {
    label: string;
    value: string;
    onChange(value: string): void;
}) {
    return (
        <label className={styles.filterField}>
            <span className={styles.filterFieldLabel}>{label}</span>
            <span className={styles.dateWrap}>
                <CalendarDays size={17} aria-hidden="true" />
                <input
                    type="date"
                    className={styles.dateInput}
                    value={value}
                    aria-label={label}
                    onChange={(event) =>
                        onChange(event.currentTarget.value)
                    }
                />
            </span>
        </label>
    );
}
