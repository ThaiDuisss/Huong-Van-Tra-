import { CalendarDays, PanelLeftClose, PanelLeftOpen } from "lucide-react";

import {
    TimeHierarchyFilter,
    type TimeRange
} from "./TimeHierarchyFilter";
import styles from "./TimeSidebar.module.css";

interface TimeSidebarProps {
    activeDate?: string;
    activeRange?: TimeRange | null;
    collapsed: boolean;
    enableCalendar?: boolean;
    selectedYear?: number | null;
    onClearRange?: () => void;
    onRangeChange?: (range: TimeRange) => void;
    onToggleCollapse: () => void;
    onYearChange?: (year: number) => void;
}

export function TimeSidebar({
    activeDate,
    activeRange,
    collapsed,
    enableCalendar = false,
    onClearRange,
    onRangeChange,
    selectedYear,
    onToggleCollapse,
    onYearChange
}: TimeSidebarProps) {
    return (
        <div className={styles.pane}>
            {collapsed ? (
                <button
                    aria-label="Mở bộ lọc thời gian"
                    className={styles.collapsedButton}
                    onClick={onToggleCollapse}
                    type="button"
                >
                    <PanelLeftOpen size={18} aria-hidden="true" />
                    <CalendarDays size={18} aria-hidden="true" />
                </button>
            ) : (
                <>
                    <button
                        aria-label="Thu gọn bộ lọc thời gian"
                        className={styles.collapseButton}
                        onClick={onToggleCollapse}
                        type="button"
                    >
                        <PanelLeftClose size={17} aria-hidden="true" />
                    </button>
                    <TimeHierarchyFilter
                        activeDate={activeDate}
                        activeRange={activeRange}
                        enableCalendar={enableCalendar}
                        onClearRange={onClearRange}
                        onRangeChange={onRangeChange}
                        selectedYear={selectedYear}
                        onYearChange={onYearChange}
                    />
                </>
            )}
        </div>
    );
}
