import type {
    DiscountCode,
    DiscountCodeFilters,
    DiscountCodeScope,
    DiscountCodeStatus
} from "../types/discount-code.types";

export const defaultDiscountCodeFilters: DiscountCodeFilters = {
    programName: "ALL",
    status: "ALL",
    scope: "ALL",
    dateRange: ""
};

export const discountCodeStatusLabels: Record<
    DiscountCodeStatus,
    string
> = {
    ACTIVE: "ACTIVE",
    USED: "USED",
    SCHEDULED: "SCHEDULED",
    EXPIRED: "EXPIRED"
};

export const discountCodeScopeLabels: Record<DiscountCodeScope, string> = {
    PUBLIC: "Công khai",
    PERSONAL: "Cá nhân"
};

export function getUniquePrograms(codes: DiscountCode[]) {
    return Array.from(new Set(codes.map((code) => code.programName)));
}

export function formatDiscountCodeDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
    }).format(new Date(value));
}

export function filterDiscountCodes(
    codes: DiscountCode[],
    query: string,
    filters: DiscountCodeFilters
) {
    const normalizedQuery = query.trim().toLowerCase();
    const normalizedDateRange = filters.dateRange.trim().toLowerCase();

    return codes.filter((code) => {
        const matchesQuery =
            normalizedQuery.length === 0 ||
            code.code.toLowerCase().includes(normalizedQuery) ||
            code.programName.toLowerCase().includes(normalizedQuery) ||
            code.issueType.toLowerCase().includes(normalizedQuery) ||
            Boolean(
                code.customerName
                    ?.toLowerCase()
                    .includes(normalizedQuery)
            );

        const matchesProgram =
            filters.programName === "ALL" ||
            code.programName === filters.programName;

        const matchesStatus =
            filters.status === "ALL" || code.status === filters.status;

        const matchesScope =
            filters.scope === "ALL" || code.scope === filters.scope;

        const dateText = `${formatDiscountCodeDate(
            code.startAt
        )} ${formatDiscountCodeDate(code.endAt)}`.toLowerCase();
        const matchesDateRange =
            normalizedDateRange.length === 0 ||
            dateText.includes(normalizedDateRange);

        return (
            matchesQuery &&
            matchesProgram &&
            matchesStatus &&
            matchesScope &&
            matchesDateRange
        );
    });
}
