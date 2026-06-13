import type {
    BomFiltersState,
    BomListItem
} from "../types/bom.types";

export const defaultBomFilters: BomFiltersState = {
    productTypes: [],
    statuses: [],
    versions: [],
    updatedRange: "ALL"
};

export function filterBoms(
    items: BomListItem[],
    query: string,
    filters: BomFiltersState
) {
    const normalizedQuery = query.trim().toLowerCase();

    return items.filter((item) => {
        const queryMatched =
            normalizedQuery.length === 0 ||
            item.code.toLowerCase().includes(normalizedQuery) ||
            item.name.toLowerCase().includes(normalizedQuery) ||
            item.productName
                .toLowerCase()
                .includes(normalizedQuery) ||
            item.productSku
                .toLowerCase()
                .includes(normalizedQuery);

        const productTypeMatched =
            filters.productTypes.length === 0 ||
            filters.productTypes.includes(item.productType);

        const statusMatched =
            filters.statuses.length === 0 ||
            filters.statuses.includes(item.status);

        const versionMatched =
            filters.versions.length === 0 ||
            filters.versions.includes(item.version);

        const updatedMatched = isUpdatedInRange(
            item.updatedAt,
            filters.updatedRange
        );

        return (
            queryMatched &&
            productTypeMatched &&
            statusMatched &&
            versionMatched &&
            updatedMatched
        );
    });
}

function isUpdatedInRange(
    updatedAt: string,
    range: BomFiltersState["updatedRange"]
) {
    if (range === "ALL" || range === "CUSTOM") {
        return true;
    }

    const updatedDate = new Date(`${updatedAt}T00:00:00`);
    const currentDate = new Date("2026-06-05T00:00:00");
    const differenceInDays = Math.floor(
        (currentDate.getTime() - updatedDate.getTime()) /
            86_400_000
    );

    if (range === "TODAY") {
        return differenceInDays === 0;
    }

    if (range === "LAST_7_DAYS") {
        return differenceInDays >= 0 && differenceInDays <= 7;
    }

    return differenceInDays >= 0 && differenceInDays <= 30;
}
