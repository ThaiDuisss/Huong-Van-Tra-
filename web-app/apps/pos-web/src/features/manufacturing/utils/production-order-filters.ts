import type {
    ProductionOrderFiltersState,
    ProductionOrderListItem
} from "../types/production-order.types";

export const defaultProductionOrderFilters: ProductionOrderFiltersState =
    {
        statuses: [],
        workshops: [],
        dateRange: "ALL"
    };

export function filterProductionOrders(
    items: ProductionOrderListItem[],
    query: string,
    filters: ProductionOrderFiltersState
) {
    const normalizedQuery = query.trim().toLowerCase();

    return items.filter((item) => {
        const queryMatched =
            normalizedQuery.length === 0 ||
            item.code.toLowerCase().includes(normalizedQuery) ||
            item.productName
                .toLowerCase()
                .includes(normalizedQuery) ||
            item.bomCode.toLowerCase().includes(normalizedQuery);
        const statusMatched =
            filters.statuses.length === 0 ||
            filters.statuses.includes(item.status);
        const workshopMatched =
            filters.workshops.length === 0 ||
            filters.workshops.includes(item.workshop);
        const dateMatched = isInRange(
            item.startDate,
            filters.dateRange
        );

        return (
            queryMatched &&
            statusMatched &&
            workshopMatched &&
            dateMatched
        );
    });
}

function isInRange(
    dateValue: string,
    range: ProductionOrderFiltersState["dateRange"]
) {
    if (range === "ALL" || range === "CUSTOM") {
        return true;
    }

    const date = new Date(`${dateValue}T00:00:00`);
    const currentDate = new Date("2026-06-07T00:00:00");
    const difference = Math.floor(
        (currentDate.getTime() - date.getTime()) / 86_400_000
    );

    if (range === "TODAY") {
        return difference === 0;
    }

    if (range === "LAST_7_DAYS") {
        return difference >= 0 && difference <= 7;
    }

    return difference >= 0 && difference <= 30;
}
