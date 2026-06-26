import { useMemo, useState } from "react";
import { ChevronRight, Funnel } from "lucide-react";

import styles from "./TimeHierarchyFilter.module.css";

interface TimeHierarchyFilterProps {
    activeDate?: string;
    selectedYear?: number | null;
    onYearChange?: (year: number) => void;
}

interface MonthNode {
    month: number;
    dates: string[];
}

interface QuarterNode {
    quarter: number;
    months: MonthNode[];
}

interface YearNode {
    year: number;
    quarters: QuarterNode[];
}

const visibleYearCount = 5;
const monthsPerQuarter = 3;

function padDatePart(value: number) {
    return String(value).padStart(2, "0");
}

function formatDate(day: number, month: number, year: number) {
    return `${padDatePart(day)}/${padDatePart(month)}/${year}`;
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
                                month,
                                dates: Array.from(
                                    { length: daysInMonth },
                                    (_, dayIndex) =>
                                        formatDate(
                                            daysInMonth - dayIndex,
                                            month,
                                            year
                                        )
                                )
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
    selectedYear,
    onYearChange
}: TimeHierarchyFilterProps) {
    const [openYears, setOpenYears] = useState<Set<number>>(new Set());
    const [openQuarters, setOpenQuarters] = useState<Set<string>>(new Set());
    const [openMonths, setOpenMonths] = useState<Set<string>>(new Set());
    const today = useMemo(() => new Date(), []);
    const timeTree = useMemo(() => buildTimeTree(today), [today]);
    const selectedDate =
        activeDate ??
        formatDate(today.getDate(), today.getMonth() + 1, today.getFullYear());

    const toggleYear = (year: number) => {
        onYearChange?.(year);
        setOpenYears((current) => {
            const next = new Set(current);

            if (next.has(year)) {
                next.delete(year);
            } else {
                next.add(year);
            }

            return next;
        });
    };

    const toggleQuarter = (id: string) => {
        setOpenQuarters((current) => {
            const next = new Set(current);

            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }

            return next;
        });
    };

    const toggleMonth = (id: string) => {
        setOpenMonths((current) => {
            const next = new Set(current);

            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }

            return next;
        });
    };

    return (
        <aside className={styles.timeline} aria-label="Bộ lọc thời gian">
            <div className={styles.header}>
                <span>Thời gian</span>
                <Funnel size={20} strokeWidth={1.8} aria-hidden="true" />
            </div>

            <div className={styles.list}>
                {timeTree.map((yearNode) => {
                    const yearOpen = openYears.has(yearNode.year);

                    return (
                        <div key={yearNode.year}>
                            <button
                                className={`${styles.item} ${
                                    yearOpen ? styles.open : ""
                                } ${
                                    selectedYear === yearNode.year
                                        ? styles.activeItem
                                        : ""
                                }`}
                                aria-expanded={yearOpen}
                                onClick={() => toggleYear(yearNode.year)}
                                type="button"
                            >
                                <ChevronRight size={16} aria-hidden="true" />
                                {yearNode.year}
                            </button>

                            {yearOpen
                                ? yearNode.quarters.map((quarterNode) => {
                                      const quarterId = `${yearNode.year}-${quarterNode.quarter}`;
                                      const quarterOpen =
                                          openQuarters.has(quarterId);

                                      return (
                                          <div key={quarterId}>
                                              <button
                                                  className={`${styles.item} ${styles.quarter} ${
                                                      quarterOpen
                                                          ? styles.open
                                                          : ""
                                                  }`}
                                                  aria-expanded={quarterOpen}
                                                  onClick={() =>
                                                      toggleQuarter(quarterId)
                                                  }
                                                  type="button"
                                              >
                                                  <ChevronRight
                                                      size={15}
                                                      aria-hidden="true"
                                                  />
                                                  Quý {quarterNode.quarter}
                                              </button>

                                              {quarterOpen
                                                  ? quarterNode.months.map(
                                                        (monthNode) => {
                                                            const monthId = `${quarterId}-${monthNode.month}`;
                                                            const monthOpen =
                                                                openMonths.has(
                                                                    monthId
                                                                );

                                                            return (
                                                                <div
                                                                    key={
                                                                        monthId
                                                                    }
                                                                >
                                                                    <button
                                                                        className={`${styles.item} ${styles.month} ${
                                                                            monthOpen
                                                                                ? styles.open
                                                                                : ""
                                                                        }`}
                                                                        aria-expanded={
                                                                            monthOpen
                                                                        }
                                                                        onClick={() =>
                                                                            toggleMonth(
                                                                                monthId
                                                                            )
                                                                        }
                                                                        type="button"
                                                                    >
                                                                        <ChevronRight
                                                                            size={
                                                                                14
                                                                            }
                                                                            aria-hidden="true"
                                                                        />
                                                                        Tháng{" "}
                                                                        {
                                                                            monthNode.month
                                                                        }
                                                                    </button>

                                                                    {monthOpen
                                                                        ? monthNode.dates.map(
                                                                              (
                                                                                  date
                                                                              ) => (
                                                                                  <button
                                                                                      className={`${styles.item} ${styles.date} ${styles.dateRail} ${
                                                                                          date ===
                                                                                          selectedDate
                                                                                              ? styles.activeDate
                                                                                              : ""
                                                                                      }`}
                                                                                      key={
                                                                                          date
                                                                                      }
                                                                                      type="button"
                                                                                  >
                                                                                      {
                                                                                          date
                                                                                      }
                                                                                  </button>
                                                                              )
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
