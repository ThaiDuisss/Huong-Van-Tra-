import { useState } from "react";

import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode,
    type TimeRange
} from "./TimeHierarchyFilter.utils";

export const APP_TIME_ZONE = "Asia/Ho_Chi_Minh";

export interface UseTimeFilterOptions {
    defaultToToday?: boolean;
    timeZone?: string;
    onFilterChange?: () => void;
}

export function useTimeFilter(
    optionsOrChangeHandler?: (() => void) | UseTimeFilterOptions
) {
    const isHandler = typeof optionsOrChangeHandler === "function";
    const onFilterChange = isHandler ? optionsOrChangeHandler : optionsOrChangeHandler?.onFilterChange;
    const defaultToToday = isHandler ? true : (optionsOrChangeHandler?.defaultToToday ?? true);
    const timeZone = isHandler ? APP_TIME_ZONE : (optionsOrChangeHandler?.timeZone ?? APP_TIME_ZONE);

    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(() =>
        defaultToToday ? buildTodayTimeNode(timeZone) : null
    );
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        onFilterChange?.();
    };

    const clearTimeRange = () => {
        setSelectedTimeNode(null);
        onFilterChange?.();
    };

    const toggleCollapse = () => {
        setIsTimeCollapsed((collapsed) => !collapsed);
    };

    const timeRange: TimeRange | null = getDateRangeFromTimeNode(selectedTimeNode);

    return {
        selectedTimeNode,
        setSelectedTimeNode,
        selectTimeNode,
        clearTimeRange,
        isTimeCollapsed,
        setIsTimeCollapsed,
        toggleCollapse,
        timeZone,
        timeRange
    };
}
