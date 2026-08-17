import { useMemo, useState } from "react";
import { CalendarDays, ChevronRight, Funnel } from "lucide-react";

import { CalendarMonthPicker } from "./CalendarMonthPicker";
import {
    formatLegacyDate,
    getDaysInMonth,
    getQuarterFromMonth,
    getTimeNodeLabel,
    legacyDateToIsoDate,
    type TimeNode
} from "./TimeHierarchyFilter.utils";
import styles from "./TimeHierarchyFilter.module.css";

interface TimeHierarchyFilterProps {
    activeDate?: string;
    enableCalendar?: boolean;
    selectedNode?: TimeNode | null;
    title?: string;
    onClear?: () => void;
    onSelect?: (node: TimeNode) => void;
    onYearChange?: (year: number) => void;
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
    quarters: QuarterNode[];
    year: number;
}

interface ActiveOpenPath {
    monthId: string | null;
    quarterId: string | null;
    year: number;
}

const visibleYearCount = 5;
const monthsPerQuarter = 3;

function buildTimeTree(today: Date): YearNode[] {
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth() + 1;
    const currentDay = today.getDate();

    return Array.from({ length: visibleYearCount }, (_, yearIndex) => {
        const year = currentYear - yearIndex;
        const maxMonth = year === currentYear ? currentMonth : 12;
        const quarterCount = Math.ceil(maxMonth / monthsPerQuarter);

        return {
            quarters: Array.from({ length: quarterCount }, (_, quarterIndex) => {
                const quarter = quarterIndex + 1;
                const quarterStartMonth = quarterIndex * monthsPerQuarter + 1;
                const quarterEndMonth = Math.min(
                    quarterStartMonth + monthsPerQuarter - 1,
                    maxMonth
                );

                return {
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
                    ).reverse(),
                    quarter
                };
            }).reverse(),
            year
        };
    });
}

function getNodeIds(year: number, quarter?: number, month?: number) {
    const quarterId = quarter ? `${year}-${quarter}` : null;

    return {
        monthId: quarterId && month ? `${quarterId}-${month}` : null,
        quarterId
    };
}

export function TimeHierarchyFilter({
    activeDate,
    enableCalendar = false,
    onClear,
    onSelect,
    onYearChange,
    selectedNode,
    title = "Thời gian"
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
        selectedNode || enableCalendar
            ? null
            : formatLegacyDate(
                  today.getDate(),
                  today.getMonth() + 1,
                  today.getFullYear()
              );
    const selectedDate =
        activeDate ?? (selectedNode?.type === "day" ? selectedNode.date : null);
    const selectedLabel = selectedNode ? getTimeNodeLabel(selectedNode) : null;
    const activeOpenPath = useMemo<ActiveOpenPath | null>(() => {
        if (!selectedNode) {
            return null;
        }

        if (selectedNode.type === "year") {
            return {
                monthId: null,
                quarterId: null,
                year: selectedNode.year
            };
        }

        const quarter = selectedNode.quarter;
        const month =
            selectedNode.type === "month" || selectedNode.type === "day"
                ? selectedNode.month
                : undefined;
        const { monthId, quarterId } = getNodeIds(
            selectedNode.year,
            quarter,
            month
        );

        return {
            monthId:
                selectedNode.type === "month" || selectedNode.type === "day"
                    ? monthId
                    : null,
            quarterId,
            year: selectedNode.year
        };
    }, [selectedNode]);

    const openSelectedYear = (year: number) => {
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
    };

    const openSelectedQuarter = (year: number, quarter: number) => {
        const { quarterId } = getNodeIds(year, quarter);

        openSelectedYear(year);
        setClosedQuarters((current) => {
            if (!quarterId || !current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(quarterId);
            return next;
        });
        setOpenQuarters((current) => {
            if (!quarterId || current.has(quarterId)) {
                return current;
            }

            const next = new Set(current);
            next.add(quarterId);
            return next;
        });
    };

    const openSelectedMonth = (year: number, quarter: number, month: number) => {
        const { monthId } = getNodeIds(year, quarter, month);

        openSelectedQuarter(year, quarter);
        setClosedMonths((current) => {
            if (!monthId || !current.has(monthId)) {
                return current;
            }

            const next = new Set(current);
            next.delete(monthId);
            return next;
        });
        setOpenMonths((current) => {
            if (!monthId || current.has(monthId)) {
                return current;
            }

            const next = new Set(current);
            next.add(monthId);
            return next;
        });
    };

    const selectYear = (year: number) => {
        openSelectedYear(year);
        onSelect?.({ type: "year", year });
    };

    const selectQuarter = (year: number, quarter: number) => {
        openSelectedQuarter(year, quarter);
        onSelect?.({ quarter, type: "quarter", year });
    };

    const selectMonth = (year: number, month: number) => {
        const quarter = getQuarterFromMonth(month);

        openSelectedMonth(year, quarter, month);
        onSelect?.({ month, quarter, type: "month", year });
    };

    const selectDate = (isoDate: string) => {
        const [year, month] = isoDate.split("-").map(Number);
        const quarter = getQuarterFromMonth(month);

        openSelectedMonth(year, quarter, month);
        onSelect?.({
            date: isoDate,
            month,
            quarter,
            type: "day",
            year
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
                const next = new Set(current);
                next.add(year);
                return next;
            });
            return;
        }

        openSelectedYear(year);
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
            const next = new Set(current);
            next.delete(id);
            return next;
        });
    };

    return (
        <aside className={styles.timeline} aria-label="Bộ lọc thời gian">
            <div className={styles.header}>
                <div>
                    <span>{title}</span>
                    {selectedLabel ? <strong>{selectedLabel}</strong> : null}
                </div>
                <Funnel size={20} strokeWidth={1.8} aria-hidden="true" />
            </div>

            {selectedNode ? (
                <button
                    className={styles.clearButton}
                    onClick={onClear}
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
                                aria-expanded={yearOpen}
                                className={`${styles.item} ${
                                    yearOpen ? styles.open : ""
                                } ${
                                    yearSelected ? styles.activeItem : ""
                                }`}
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
                                                  aria-expanded={quarterOpen}
                                                  className={`${styles.item} ${styles.quarter} ${
                                                      quarterOpen
                                                          ? styles.open
                                                          : ""
                                                  } ${
                                                      quarterSelected
                                                          ? styles.activeItem
                                                          : ""
                                                  }`}
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
                                                                        aria-expanded={
                                                                            monthOpen
                                                                        }
                                                                        className={`${styles.item} ${styles.month} ${
                                                                            monthOpen
                                                                                ? styles.open
                                                                                : ""
                                                                        } ${
                                                                            monthSelected
                                                                                ? styles.activeItem
                                                                                : ""
                                                                        }`}
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
                                                                                  const daySelected =
                                                                                      selectedNode?.type ===
                                                                                          "day" &&
                                                                                      selectedNode.date ===
                                                                                          isoDate;

                                                                                  return (
                                                                                      <button
                                                                                          className={`${styles.item} ${styles.date} ${styles.dateRail} ${
                                                                                              date ===
                                                                                                  fallbackDate ||
                                                                                              daySelected
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
