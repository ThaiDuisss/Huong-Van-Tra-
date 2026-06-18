import { ChevronRight, Funnel } from "lucide-react";

import styles from "./TimeHierarchyFilter.module.css";

interface TimeHierarchyFilterProps {
    activeDate?: string;
}

const years = ["2026", "2025", "2024"] as const;
const quarters = ["Quý 1", "Quý 2", "Quý 3", "Quý 4"] as const;
const months = ["Tháng 4", "Tháng 5", "Tháng 6"] as const;
const dates = ["17/06/2026", "16/06/2026", "15/06/2026"] as const;

export function TimeHierarchyFilter({
    activeDate = "17/06/2026"
}: TimeHierarchyFilterProps) {
    return (
        <aside className={styles.timeline} aria-label="Bộ lọc thời gian">
            <div className={styles.header}>
                <span>Thời gian</span>
                <Funnel size={20} strokeWidth={1.8} aria-hidden="true" />
            </div>

            <div className={styles.list}>
                {years.map((year) => {
                    const activeYear = year === "2026";

                    return (
                        <div key={year}>
                            <button
                                className={`${styles.item} ${
                                    activeYear ? styles.open : ""
                                }`}
                                type="button"
                            >
                                <ChevronRight size={16} aria-hidden="true" />
                                {year}
                            </button>

                            {activeYear
                                ? quarters.map((quarter) => {
                                      const activeQuarter =
                                          quarter === "Quý 2";

                                      return (
                                          <div key={quarter}>
                                              <button
                                                  className={`${styles.item} ${styles.quarter} ${
                                                      activeQuarter
                                                          ? styles.open
                                                          : ""
                                                  }`}
                                                  type="button"
                                              >
                                                  <ChevronRight
                                                      size={15}
                                                      aria-hidden="true"
                                                  />
                                                  {quarter}
                                              </button>

                                              {activeQuarter
                                                  ? months.map(
                                                        (month) => {
                                                            const activeMonth =
                                                                month ===
                                                                "Tháng 6";

                                                            return (
                                                                <div
                                                                    key={
                                                                        month
                                                                    }
                                                                >
                                                                    <button
                                                                        className={`${styles.item} ${styles.month} ${
                                                                            activeMonth
                                                                                ? styles.open
                                                                                : ""
                                                                        }`}
                                                                        type="button"
                                                                    >
                                                                        {activeMonth ? (
                                                                            <ChevronRight
                                                                                size={
                                                                                    14
                                                                                }
                                                                                aria-hidden="true"
                                                                            />
                                                                        ) : null}
                                                                        {
                                                                            month
                                                                        }
                                                                    </button>

                                                                    {activeMonth
                                                                        ? dates.map(
                                                                              (
                                                                                  date
                                                                              ) => (
                                                                                  <button
                                                                                      className={`${styles.item} ${styles.date} ${styles.dateRail} ${
                                                                                          date ===
                                                                                          activeDate
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
