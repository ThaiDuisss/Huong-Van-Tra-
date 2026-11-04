import { useState } from "react";

import { buildTodayTimeNode, getDateRangeFromTimeNode, type TimeNode } from "./TimeHierarchyFilter.utils";

const TIME_ZONE = "Asia/Ho_Chi_Minh";

export function useTimeFilter(onFilterChange: () => void) {
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        () => buildTodayTimeNode(TIME_ZONE)
    );

    const selectTimeNode = (node: TimeNode | null) => {
        setSelectedTimeNode(node);
        onFilterChange();
    };

    return {
        selectedTimeNode,
        selectTimeNode,
        timeZone: TIME_ZONE,
        timeRange: getDateRangeFromTimeNode(selectedTimeNode)
    };
}
