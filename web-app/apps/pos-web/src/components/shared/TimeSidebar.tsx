import { CalendarDays, PanelLeftClose, PanelLeftOpen } from "lucide-react";

import { TimeHierarchyFilter } from "./TimeHierarchyFilter";
import styles from "./TimeSidebar.module.css";

interface TimeSidebarProps {
    activeDate?: string;
    collapsed: boolean;
    selectedYear?: number | null;
    onToggleCollapse: () => void;
    onYearChange?: (year: number) => void;
}

export function TimeSidebar({
    activeDate,
    collapsed,
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
                        selectedYear={selectedYear}
                        onYearChange={onYearChange}
                    />
                </>
            )}
        </div>
    );
}
