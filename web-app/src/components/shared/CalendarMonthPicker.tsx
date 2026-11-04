import { buildTodayTimeNode } from "./TimeHierarchyFilter.utils";
import styles from "./TimeHierarchyFilter.module.css";

interface CalendarMonthPickerProps {
    month: number;
    selectedDate?: string | null;
    year: number;
    timeZone?: string;
    onSelectDate: (date: string) => void;
}

interface CalendarDay {
    date: Date;
    isoDate: string;
}

function pad(value: number) {
    return value.toString().padStart(2, "0");
}

function toIsoDate(date: Date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
        date.getDate()
    )}`;
}

function buildCalendarDays(year: number, month: number): CalendarDay[] {
    const monthIndex = month - 1;
    const daysInMonth = new Date(year, month, 0).getDate();

    return Array.from({ length: daysInMonth }, (_, index) => {
        const date = new Date(year, monthIndex, index + 1);

        return {
            date,
            isoDate: toIsoDate(date)
        };
    });
}

export function CalendarMonthPicker({
    month,
    selectedDate,
    year,
    onSelectDate,
    timeZone
}: CalendarMonthPickerProps) {
    const todayIso = buildTodayTimeNode(timeZone).date;
    const days = buildCalendarDays(year, month);

    return (
        <div className={styles.calendarPanel}>
            <div className={styles.dayGrid}>
                {days.map((day) => (
                    <button
                        className={`${styles.dayButton} ${
                            day.isoDate === selectedDate
                                ? styles.selectedDay
                                : ""
                        } ${
                            day.isoDate === todayIso ? styles.todayDay : ""
                        }`}
                        key={day.isoDate}
                        onClick={() => onSelectDate(day.isoDate)}
                        type="button"
                    >
                        {day.date.getDate()}
                    </button>
                ))}
            </div>
        </div>
    );
}
