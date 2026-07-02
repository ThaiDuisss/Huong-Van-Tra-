import { useMemo, useState } from "react";
import { CalendarDays, ChevronRight, Funnel } from "lucide-react";

import { CalendarMonthPicker } from "./CalendarMonthPicker";
import styles from "./TimeHierarchyFilter.module.css";

interface TimeHierarchyFilterProps {
    activeDate?: string;
    activeRange?: TimeRange | null;
    enableCalendar?: boolean;
    selectedYear?: number | null;
    onClearRange?: () => void;
    onRangeChange?: (range: TimeRange) => void;
    onYearChange?: (year: number) => void;
}

export interface TimeRange {
    fromDate: string;
    label: string;
    selectedDate?: string | null;
    toDate: string;
}

interface MonthNode {
    dates: string[];
    month: number;
}

interface QuarterNode {
    quarter: number;
    months: MonthNode[];
}

interface YearNode {
    year: number;
    quarters: QuarterNode[];
}

interface ActiveOpenPath {
    monthId: string | null;
    quarterId: string | null;
    year: number;
}

interface SelectedNode {
    date?: string;
    month?: number;
    quarter?: number;
    type: "year" | "quarter" | "month" | "day";
    year: number;
}

const visibleYearCount = 5;
const monthsPerQuarter = 3;

function padDatePart(value: number) {
    return String(value).padStart(2, "0");
}

function toIsoDate(year: number, month: number, day: number) {
    return `${year}-${padDatePart(month)}-${padDatePart(day)}`;
}

function formatDisplayDate(isoDate: string) {
    const [year, month, day] = isoDate.split("-");

    return `${day}/${month}/${year}`;
}

function formatLegacyDate(day: number, month: number, year: number) {
    return `${padDatePart(day)}/${padDatePart(month)}/${year}`;
}

function legacyDateToIsoDate(date: string) {
    const [day, month, year] = date.split("/");

    return `${year}-${month}-${day}`;
}

function getDaysInMonth(year: number, month: number) {
    return new Date(year, month, 0).getDate();
}

function buildTimeTree(today: Date): YearNode[] {
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth() + 1;
    const currentDay = today.getDate();

    return Array.from({ length: visibleYearCount }, (_, yearIndex) => {
        const year = currentYear - yearIndex;
        const maxMonth = year === currentYear ? currentMonth : 12;
        const quarterCount = Math.ceil(maxMonth / monthsPerQuarter);

        return {
            year,
            quarters: Array.from({ length: quarterCount }, (_, quarterIndex) => {
                const quarter = quarterIndex + 1;
                const quarterStartMonth = quarterIndex * monthsPerQuarter + 1;
                const quarterEndMonth = Math.min(
                    quarterStartMonth + monthsPerQuarter - 1,
                    maxMonth
                );

                return {
                    quarter,
                    months: Array.from(
                        { length: quarterEndMonth - quarterStartMonth + 1 },
                        (_, monthIndex) => {
                            const month = quarterStartMonth + monthIndex;
                            const daysInMonth =
                                year === currentYear && month === currentMonth
                                    ? currentDay
                                    : getDaysInMonth(year, month);

                            return {
                                dates: Array.from(
                                    { length: daysInMonth },
                                    (_, dayIndex) =>
                                        formatLegacyDate(
                                            daysInMonth - dayIndex,
                                            month,
                                            year
                                        )
                                ),
                                month
                            };
                        }
                    ).reverse()
                };
            }).reverse()
        };
    });
}

export function TimeHierarchyFilter({
    activeDate,
    activeRange,
    enableCalendar = false,
    onClearRange,
    onRangeChange,
    selectedYear,
    onYearChange
}: TimeHierarchyFilterProps) {
    const [openYears, setOpenYears] = useState<Set<number>>(new Set());
    const [openQuarters, setOpenQuarters] = useState<Set<string>>(new Set());
    const [openMonths, setOpenMonths] = useState<Set<string>>(new Set());
    const [closedYears, setClosedYears] = useState<Set<number>>(new Set());
    const [closedQuarters, setClosedQuarters] = useState<Set<string>>(
        new Set()
    );
    const [closedMonths, setClosedMonths] = useState<Set<string>>(new Set());
    const today = useMemo(() => new Date(), []);
    const timeTree = useMemo(() => buildTimeTree(today), [today]);
    const fallbackDate =
        activeRange || enableCalendar
            ? null
            : formatLegacyDate(
                  today.getDate(),
                  today.getMonth() + 1,
                  today.getFullYear()
              );
    const selectedDate = activeDate ?? activeRange?.selectedDate ?? fallbackDate;
    const selectedNode = useMemo<SelectedNode | null>(() => {
        if (!activeRange) {
            return null;
        }

        const dateToSelect = activeRange.selectedDate ?? activeRange.fromDate;
        const [year, month] = dateToSelect.split("-").map(Number);

        if (!year || !month) {
            return null;
        }

        const quarter = Math.ceil(month / monthsPerQuarter);

        if (activeRange.selectedDate) {
            return {
                date: activeRange.selectedDate,
                month,
                quarter,
                type: "day",
                year
            };
        }

        if (activeRange.label.startsWith("Tháng")) {
            return { month, quarter, type: "month", year };
        }

        if (activeRange.label.startsWith("Quý")) {
            return { quarter, type: "quarter", year };
        }

        return { type: "year", year };
    }, [activeRange]);
    const activeOpenPath = useMemo<ActiveOpenPath | null>(() => {
        const dateToOpen = selectedDate ?? activeRange?.fromDate;

        if (!activeRange || !dateToOpen) {
            return null;
        }

        const [year, month] = dateToOpen.split("-").map(Number);

        if (!year || !month) {
            return null;
        }

        const quarter = Math.ceil(month / monthsPerQuarter);
        const quarterId = `${year}-${quarter}`;
        const monthId = `${quarterId}-${month}`;
        const shouldOpenQuarter =
            Boolean(selectedDate) ||
            activeRange.label.startsWith("Quý") ||
            activeRange.label.startsWith("Tháng");
        const shouldOpenMonth =
            Boolean(selectedDate) || activeRange.label.startsWith("Tháng");

        return {
            monthId: shouldOpenMonth ? monthId : null,
            quarterId: shouldOpenQuarter ? quarterId : null,
            year
        };
    }, [activeRange, selectedDate]);

    const selectYear = (year: number) => {
        setClosedYears((current) => {
            if (!current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.delete(year);
            return next;
        });
        onRangeChange?.({
            fromDate: toIsoDate(year, 1, 1),
            label: `${year}`,
            selectedDate: null,
            toDate: toIsoDate(year, 12, 31)
        });
    };

    const selectQuarter = (year: number, quarter: number) => {
        const startMonth = (quarter - 1) * monthsPerQuarter + 1;
        const endMonth = startMonth + monthsPerQuarter - 1;
        const quarterId = `${year}-${quarter}`;

        setClosedYears((current) => {
            if (!current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.delete(year);
            return next;
        });
        setOpenYears((current) => {
            if (current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.add(year);
            return next;
        });
        setClosedQuarters((current) => {
            if (!current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(quarterId);
            return next;
        });

        onRangeChange?.({
            fromDate: toIsoDate(year, startMonth, 1),
            label: `Quý ${quarter}/${year}`,
            selectedDate: null,
            toDate: toIsoDate(year, endMonth, getDaysInMonth(year, endMonth))
        });
    };

    const selectMonth = (year: number, month: number) => {
        const quarter = Math.ceil(month / monthsPerQuarter);
        const quarterId = `${year}-${quarter}`;
        const monthId = `${quarterId}-${month}`;

        setClosedYears((current) => {
            if (!current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.delete(year);
            return next;
        });
        setClosedQuarters((current) => {
            if (!current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(quarterId);
            return next;
        });
        setOpenYears((current) => {
            if (current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.add(year);
            return next;
        });
        setOpenQuarters((current) => {
            if (current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.add(quarterId);
            return next;
        });
        setClosedMonths((current) => {
            if (!current.has(monthId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(monthId);
            return next;
        });

        onRangeChange?.({
            fromDate: toIsoDate(year, month, 1),
            label: `Tháng ${month}/${year}`,
            selectedDate: null,
            toDate: toIsoDate(year, month, getDaysInMonth(year, month))
        });
    };

    const selectDate = (isoDate: string) => {
        const [year, month] = isoDate.split("-").map(Number);
        const quarter = Math.ceil(month / monthsPerQuarter);
        const quarterId = `${year}-${quarter}`;
        const monthId = `${quarterId}-${month}`;

        setClosedYears((current) => {
            if (!current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.delete(year);
            return next;
        });
        setClosedQuarters((current) => {
            if (!current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(quarterId);
            return next;
        });
        setClosedMonths((current) => {
            if (!current.has(monthId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(monthId);
            return next;
        });
        setOpenYears((current) => {
            if (current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.add(year);
            return next;
        });
        setOpenQuarters((current) => {
            if (current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.add(quarterId);
            return next;
        });
        setOpenMonths((current) => {
            if (current.has(monthId)) {
                return current;
            }

            const next = new Set(current);
            next.add(monthId);
            return next;
        });

        onRangeChange?.({
            fromDate: isoDate,
            label: formatDisplayDate(isoDate),
            selectedDate: isoDate,
            toDate: isoDate
        });
    };

    const toggleYear = (year: number) => {
        onYearChange?.(year);
        const isOpen =
            (openYears.has(year) || activeOpenPath?.year === year) &&
            !closedYears.has(year);

        if (isOpen) {
            setOpenYears((current) => {
                const next = new Set(current);
                next.delete(year);
                return next;
            });
            setClosedYears((current) => {
                if (current.has(year)) {
                    return current;
                }

                const next = new Set(current);
                next.add(year);
                return next;
            });
            return;
        }

        setOpenYears((current) => {
            const next = new Set(current);
            next.add(year);
            return next;
        });
        setClosedYears((current) => {
            if (!current.has(year)) {
                return current;
            }

            const next = new Set(current);
            next.delete(year);
            return next;
        });
    };

    const toggleQuarter = (id: string) => {
        const isOpen =
            (openQuarters.has(id) || activeOpenPath?.quarterId === id) &&
            !closedQuarters.has(id);

        if (isOpen) {
            setOpenQuarters((current) => {
                const next = new Set(current);
                next.delete(id);
                return next;
            });
            setClosedQuarters((current) => {
                if (current.has(id)) {
                    return current;
                }

                const next = new Set(current);
                next.add(id);
                return next;
            });
            return;
        }

        setOpenQuarters((current) => {
            const next = new Set(current);
            next.add(id);
            return next;
        });
        setClosedQuarters((current) => {
            if (!current.has(id)) {
                return current;
            }

            const next = new Set(current);
            next.delete(id);
            return next;
        });
    };

    const toggleMonth = (id: string) => {
        const isOpen =
            (openMonths.has(id) || activeOpenPath?.monthId === id) &&
            !closedMonths.has(id);

        if (isOpen) {
            setOpenMonths((current) => {
                const next = new Set(current);
                next.delete(id);
                return next;
            });
            setClosedMonths((current) => {
                if (current.has(id)) {
                    return current;
                }

                const next = new Set(current);
                next.add(id);
                return next;
            });
            return;
        }

        setOpenMonths((current) => {
            const next = new Set(current);
            next.add(id);
            return next;
        });
        setClosedMonths((current) => {
            if (!current.has(id)) {
                return current;
            }

            const next = new Set(current);
            next.delete(id);
            return next;
        });
    };

    return (
        <aside className={styles.timeline} aria-label="Bộ lọc thời gian">
            <div className={styles.header}>
                <div>
                    <span>Thời gian</span>
                    {activeRange ? (
                        <strong>{activeRange.label}</strong>
                    ) : null}
                </div>
                <Funnel size={20} strokeWidth={1.8} aria-hidden="true" />
            </div>

            {activeRange ? (
                <button
                    className={styles.clearButton}
                    onClick={onClearRange}
                    type="button"
                >
                    Clear
                </button>
            ) : null}

            <div className={styles.list}>
                {timeTree.map((yearNode) => {
                    const yearOpen =
                        (openYears.has(yearNode.year) ||
                            activeOpenPath?.year === yearNode.year) &&
                        !closedYears.has(yearNode.year);
                    const yearSelected =
                        selectedNode?.type === "year" &&
                        selectedNode.year === yearNode.year;

                    return (
                        <div key={yearNode.year}>
                            <div
                                className={`${styles.item} ${
                                    yearOpen ? styles.open : ""
                                } ${
                                    yearSelected ||
                                    (!selectedNode &&
                                        selectedYear === yearNode.year)
                                        ? styles.activeItem
                                        : ""
                                }`}
                                aria-expanded={yearOpen}
                            >
                                <button
                                    aria-label={`${
                                        yearOpen ? "Đóng" : "Mở"
                                    } năm ${yearNode.year}`}
                                    className={styles.chevronButton}
                                    onClick={(event) => {
                                        event.stopPropagation();
                                        toggleYear(yearNode.year);
                                    }}
                                    type="button"
                                >
                                    <ChevronRight
                                        size={16}
                                        aria-hidden="true"
                                    />
                                </button>
                                <button
                                    className={styles.itemLabel}
                                    onClick={() => selectYear(yearNode.year)}
                                    type="button"
                                >
                                    {yearNode.year}
                                </button>
                            </div>

                            {yearOpen
                                ? yearNode.quarters.map((quarterNode) => {
                                      const quarterId = `${yearNode.year}-${quarterNode.quarter}`;
                                      const quarterOpen =
                                          (openQuarters.has(quarterId) ||
                                              activeOpenPath?.quarterId ===
                                                  quarterId) &&
                                          !closedQuarters.has(quarterId);
                                      const quarterSelected =
                                          selectedNode?.type === "quarter" &&
                                          selectedNode.year ===
                                              yearNode.year &&
                                          selectedNode.quarter ===
                                              quarterNode.quarter;

                                      return (
                                          <div key={quarterId}>
                                              <div
                                                  className={`${styles.item} ${styles.quarter} ${
                                                      quarterOpen
                                                          ? styles.open
                                                          : ""
                                                  } ${
                                                      quarterSelected
                                                          ? styles.activeItem
                                                          : ""
                                                  }`}
                                                  aria-expanded={quarterOpen}
                                              >
                                                  <button
                                                      aria-label={`${
                                                          quarterOpen
                                                              ? "Đóng"
                                                              : "Mở"
                                                      } quý ${
                                                          quarterNode.quarter
                                                      }`}
                                                      className={
                                                          styles.chevronButton
                                                      }
                                                      onClick={(event) => {
                                                          event.stopPropagation();
                                                          toggleQuarter(
                                                              quarterId
                                                          );
                                                      }}
                                                      type="button"
                                                  >
                                                      <ChevronRight
                                                          size={15}
                                                          aria-hidden="true"
                                                      />
                                                  </button>
                                                  <button
                                                      className={
                                                          styles.itemLabel
                                                      }
                                                      onClick={() =>
                                                          selectQuarter(
                                                              yearNode.year,
                                                              quarterNode.quarter
                                                          )
                                                      }
                                                      type="button"
                                                  >
                                                      Quý {quarterNode.quarter}
                                                  </button>
                                              </div>

                                              {quarterOpen
                                                  ? quarterNode.months.map(
                                                        (monthNode) => {
                                                            const monthId = `${quarterId}-${monthNode.month}`;
                                                            const monthOpen =
                                                                (openMonths.has(
                                                                    monthId
                                                                ) ||
                                                                    activeOpenPath?.monthId ===
                                                                        monthId) &&
                                                                !closedMonths.has(
                                                                    monthId
                                                                );
                                                            const monthSelected =
                                                                selectedNode?.type ===
                                                                    "month" &&
                                                                selectedNode.year ===
                                                                    yearNode.year &&
                                                                selectedNode.month ===
                                                                    monthNode.month;

                                                            return (
                                                                <div
                                                                    key={
                                                                        monthId
                                                                    }
                                                                >
                                                                    <div
                                                                        className={`${styles.item} ${styles.month} ${
                                                                            monthOpen
                                                                                ? styles.open
                                                                                : ""
                                                                        } ${
                                                                            monthSelected
                                                                                ? styles.activeItem
                                                                                : ""
                                                                        }`}
                                                                        aria-expanded={
                                                                            monthOpen
                                                                        }
                                                                    >
                                                                        <button
                                                                            aria-label={`${
                                                                                monthOpen
                                                                                    ? "Đóng"
                                                                                    : "Mở"
                                                                            } tháng ${
                                                                                monthNode.month
                                                                            }`}
                                                                            className={
                                                                                styles.chevronButton
                                                                            }
                                                                            onClick={(
                                                                                event
                                                                            ) => {
                                                                                event.stopPropagation();
                                                                                toggleMonth(
                                                                                    monthId
                                                                                );
                                                                            }}
                                                                            type="button"
                                                                        >
                                                                            <ChevronRight
                                                                                size={
                                                                                    14
                                                                                }
                                                                                aria-hidden="true"
                                                                            />
                                                                        </button>
                                                                        <button
                                                                            className={
                                                                                styles.itemLabel
                                                                            }
                                                                            onClick={() =>
                                                                                selectMonth(
                                                                                    yearNode.year,
                                                                                    monthNode.month
                                                                                )
                                                                            }
                                                                            type="button"
                                                                        >
                                                                            Tháng{" "}
                                                                            {
                                                                                monthNode.month
                                                                            }
                                                                            <CalendarDays
                                                                                size={
                                                                                    14
                                                                                }
                                                                                aria-hidden="true"
                                                                            />
                                                                        </button>
                                                                    </div>

                                                                    {monthOpen &&
                                                                    enableCalendar ? (
                                                                              <CalendarMonthPicker
                                                                                  month={
                                                                                      monthNode.month
                                                                                  }
                                                                                  selectedDate={
                                                                                      selectedDate
                                                                                  }
                                                                                  year={
                                                                                      yearNode.year
                                                                                  }
                                                                                  onSelectDate={
                                                                                      selectDate
                                                                                  }
                                                                              />
                                                                          ) : null}

                                                                    {monthOpen &&
                                                                    !enableCalendar
                                                                        ? monthNode.dates.map(
                                                                          (
                                                                                  date
                                                                              ) => {
                                                                                  const isoDate =
                                                                                      legacyDateToIsoDate(
                                                                                          date
                                                                                      );

                                                                                  return (
                                                                                      <button
                                                                                          className={`${styles.item} ${styles.date} ${styles.dateRail} ${
                                                                                              date ===
                                                                                                  selectedDate ||
                                                                                              isoDate ===
                                                                                                  selectedNode?.date
                                                                                                  ? styles.activeDate
                                                                                                  : ""
                                                                                          }`}
                                                                                          key={
                                                                                              date
                                                                                          }
                                                                                          onClick={() =>
                                                                                              selectDate(
                                                                                                  isoDate
                                                                                              )
                                                                                          }
                                                                                          type="button"
                                                                                      >
                                                                                          {
                                                                                              date
                                                                                          }
                                                                                      </button>
                                                                                  );
                                                                              }
                                                                          )
                                                                        : null}
                                                                </div>
                                                            );
                                                        }
                                                    )
                                                  : null}
                                          </div>
                                      );
                                  })
                                : null}
                        </div>
                    );
                })}
            </div>
        </aside>
    );
}
