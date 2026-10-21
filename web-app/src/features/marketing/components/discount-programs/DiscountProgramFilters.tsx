import {
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    ChevronsLeft,
    ChevronsRight,
    Search,
    SlidersHorizontal,
    X
} from "lucide-react";
import { useMemo, useState, type FormEvent } from "react";

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
    const selectedDate = parseDateValue(value);
    const [open, setOpen] = useState(false);
    const [yearPickerOpen, setYearPickerOpen] = useState(false);
    const [cursor, setCursor] = useState(() => selectedDate ?? new Date());
    const days = useMemo(() => buildCalendarDays(cursor), [cursor]);
    const years = useMemo(() => buildYearOptions(cursor), [cursor]);

    function selectDate(day: Date) {
        onChange(toDateValue(day));
        setCursor(day);
        setOpen(false);
        setYearPickerOpen(false);
    }

    function moveMonth(offset: number) {
        setYearPickerOpen(false);
        setCursor(
            (current) =>
                new Date(current.getFullYear(), current.getMonth() + offset, 1)
        );
    }

    function moveYear(offset: number) {
        setCursor(
            (current) =>
                new Date(current.getFullYear() + offset, current.getMonth(), 1)
        );
    }

    function selectYear(year: number) {
        setCursor(
            (current) => new Date(year, current.getMonth(), 1)
        );
        setYearPickerOpen(false);
    }

    return (
        <label className={styles.filterField}>
            <span className={styles.filterFieldLabel}>{label}</span>
            <span className={styles.datePickerAnchor}>
                <button
                    type="button"
                    className={`${styles.dateWrap} ${
                        open ? styles.dateWrapActive : ""
                    }`}
                    aria-label={label}
                    aria-expanded={open}
                    onClick={() => setOpen((current) => !current)}
                >
                    <CalendarDays size={17} aria-hidden="true" />
                    <span className={value ? styles.dateValue : styles.datePlaceholder}>
                        {value ? formatDateValue(value) : "Chọn ngày"}
                    </span>
                </button>

                {open ? (
                    <div className={styles.datePopover}>
                        <header className={styles.datePopoverHeader}>
                            <button
                                type="button"
                                className={styles.dateNavButton}
                                onClick={() => moveYear(-1)}
                                aria-label="Năm trước"
                            >
                                <ChevronsLeft size={16} aria-hidden="true" />
                            </button>
                            <button
                                type="button"
                                className={styles.dateNavButton}
                                onClick={() => moveMonth(-1)}
                                aria-label="Tháng trước"
                            >
                                <ChevronLeft size={16} aria-hidden="true" />
                            </button>
                            <button
                                type="button"
                                className={styles.dateTitleButton}
                                onClick={() =>
                                    setYearPickerOpen((current) => !current)
                                }
                            >
                                {formatMonthLabel(cursor)}
                            </button>
                            <button
                                type="button"
                                className={styles.dateNavButton}
                                onClick={() => moveMonth(1)}
                                aria-label="Tháng sau"
                            >
                                <ChevronRight size={16} aria-hidden="true" />
                            </button>
                            <button
                                type="button"
                                className={styles.dateNavButton}
                                onClick={() => moveYear(1)}
                                aria-label="Năm sau"
                            >
                                <ChevronsRight size={16} aria-hidden="true" />
                            </button>
                        </header>

                        {yearPickerOpen ? (
                            <div className={styles.yearGrid}>
                                {years.map((year) => (
                                    <button
                                        key={year}
                                        type="button"
                                        className={`${styles.yearButton} ${
                                            year === cursor.getFullYear()
                                                ? styles.yearButtonSelected
                                                : ""
                                        }`}
                                        onClick={() => selectYear(year)}
                                    >
                                        {year}
                                    </button>
                                ))}
                            </div>
                        ) : (
                            <>
                                <div className={styles.weekdayGrid}>
                                    {[
                                        "T2",
                                        "T3",
                                        "T4",
                                        "T5",
                                        "T6",
                                        "T7",
                                        "CN"
                                    ].map((weekday) => (
                                        <span key={weekday}>{weekday}</span>
                                    ))}
                                </div>

                                <div className={styles.calendarGrid}>
                                    {days.map((day) => {
                                        const dateValue = toDateValue(day);
                                        const selected = value === dateValue;
                                        const muted =
                                            day.getMonth() !==
                                            cursor.getMonth();

                                        return (
                                            <button
                                                key={dateValue}
                                                type="button"
                                                className={`${
                                                    styles.calendarDay
                                                } ${
                                                    selected
                                                        ? styles.calendarDaySelected
                                                        : ""
                                                } ${
                                                    muted
                                                        ? styles.calendarDayMuted
                                                        : ""
                                                }`}
                                                onClick={() => selectDate(day)}
                                            >
                                                {day.getDate()}
                                            </button>
                                        );
                                    })}
                                </div>
                            </>
                        )}

                        <footer className={styles.datePopoverFooter}>
                            <button
                                type="button"
                                onClick={() => {
                                    onChange("");
                                    setOpen(false);
                                }}
                            >
                                Xóa
                            </button>
                            <button
                                type="button"
                                onClick={() => selectDate(new Date())}
                            >
                                Hôm nay
                            </button>
                        </footer>
                    </div>
                ) : null}
            </span>
        </label>
    );
}

function parseDateValue(value: string) {
    if (!value) return null;

    const [year, month, day] = value.split("-").map(Number);
    if (!year || !month || !day) return null;

    return new Date(year, month - 1, day);
}

function toDateValue(date: Date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function formatDateValue(value: string) {
    const date = parseDateValue(value);
    if (!date) return value;

    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
    }).format(date);
}

function formatMonthLabel(date: Date) {
    return new Intl.DateTimeFormat("vi-VN", {
        month: "long",
        year: "numeric"
    }).format(date);
}

function buildCalendarDays(cursor: Date) {
    const firstOfMonth = new Date(
        cursor.getFullYear(),
        cursor.getMonth(),
        1
    );
    const mondayBasedOffset = (firstOfMonth.getDay() + 6) % 7;
    const firstVisible = new Date(firstOfMonth);
    firstVisible.setDate(firstOfMonth.getDate() - mondayBasedOffset);

    return Array.from({ length: 42 }, (_, index) => {
        const day = new Date(firstVisible);
        day.setDate(firstVisible.getDate() + index);
        return day;
    });
}

function buildYearOptions(cursor: Date) {
    const currentYear = cursor.getFullYear();
    const startYear = currentYear - 5;

    return Array.from({ length: 12 }, (_, index) => startYear + index);
}
