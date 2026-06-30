import type {
    DiscountApplicationType,
    DiscountProgram,
    DiscountProgramFilters,
    DiscountProgramStatus
} from "../types/discount-program.types";

export const defaultDiscountProgramFilters: DiscountProgramFilters = {
    eventName: "ALL",
    status: "ALL",
    applicationType: "ALL",
    dateRange: ""
};

export const discountStatusLabels: Record<DiscountProgramStatus, string> = {
    ACTIVE: "ACTIVE",
    SCHEDULED: "SCHEDULED",
    INACTIVE: "INACTIVE"
};

export const applicationTypeLabels: Record<
    DiscountApplicationType,
    string
> = {
    DISCOUNT_CODE: "Mã giảm giá",
    FREE_SHIPPING: "Miễn phí vận chuyển",
    BUY_X_GET_Y: "Mua X tặng Y",
    AMOUNT_OFF: "Giảm tiền",
    GIFT: "Tặng sản phẩm"
};

export function getUniqueEvents(programs: DiscountProgram[]) {
    return Array.from(
        new Set(programs.map((program) => program.eventName))
    );
}

export function formatDiscountDateTime(value: string) {
    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23"
    }).format(new Date(value));
}

export function formatDiscountDateTimeCompact(value: string) {
    return formatDiscountDateTime(value).replace(",", "");
}

export function filterDiscountPrograms(
    programs: DiscountProgram[],
    query: string,
    filters: DiscountProgramFilters
) {
    const normalizedQuery = query.trim().toLowerCase();
    const normalizedDateRange = filters.dateRange.trim().toLowerCase();

    return programs.filter((program) => {
        const matchesQuery =
            normalizedQuery.length === 0 ||
            program.name.toLowerCase().includes(normalizedQuery) ||
            program.eventName.toLowerCase().includes(normalizedQuery);

        const matchesEvent =
            filters.eventName === "ALL" ||
            program.eventName === filters.eventName;

        const matchesStatus =
            filters.status === "ALL" ||
            program.status === filters.status;

        const matchesApplication =
            filters.applicationType === "ALL" ||
            program.applicationType === filters.applicationType;

        const dateText = `${formatDiscountDateTime(
            program.startAt
        )} ${formatDiscountDateTime(program.endAt)}`.toLowerCase();
        const matchesDateRange =
            normalizedDateRange.length === 0 ||
            dateText.includes(normalizedDateRange);

        return (
            matchesQuery &&
            matchesEvent &&
            matchesStatus &&
            matchesApplication &&
            matchesDateRange
        );
    });
}
