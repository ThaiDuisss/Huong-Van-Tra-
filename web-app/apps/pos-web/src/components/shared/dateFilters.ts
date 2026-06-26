export const DATE_FILTERS = [
    { key: "ALL", label: "Tất cả ngày" },
    { key: "TODAY", label: "Hôm nay" },
    { key: "YESTERDAY", label: "Hôm qua" },
    { key: "LAST_7_DAYS", label: "7 ngày" },
    { key: "THIS_MONTH", label: "Tháng này" }
] as const;

export type DateFilterKey = (typeof DATE_FILTERS)[number]["key"];

export function parseDateValue(dateValue: Date | string) {
    if (dateValue instanceof Date) {
        return Number.isNaN(dateValue.getTime()) ? null : dateValue;
    }

    const trimmedValue = dateValue.trim();
    const vietnameseDateMatch = trimmedValue.match(
        /^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:\s+\d{1,2}:\d{2})?$/
    );

    if (vietnameseDateMatch) {
        const [, day, month, year] = vietnameseDateMatch;
        const parsedDate = new Date(Number(year), Number(month) - 1, Number(day));

        return Number.isNaN(parsedDate.getTime()) ? null : parsedDate;
    }

    const parsedDate = new Date(trimmedValue);

    return Number.isNaN(parsedDate.getTime()) ? null : parsedDate;
}

function isSameDay(left: Date, right: Date) {
    return (
        left.getFullYear() === right.getFullYear() &&
        left.getMonth() === right.getMonth() &&
        left.getDate() === right.getDate()
    );
}

export function matchDateFilter(
    dateValue: Date | string,
    filterKey: DateFilterKey,
    today = new Date()
) {
    if (filterKey === "ALL") {
        return true;
    }

    const itemDate = parseDateValue(dateValue);

    if (!itemDate) {
        return false;
    }

    if (filterKey === "TODAY") {
        return isSameDay(itemDate, today);
    }

    if (filterKey === "YESTERDAY") {
        const yesterday = new Date(today);
        yesterday.setDate(today.getDate() - 1);

        return isSameDay(itemDate, yesterday);
    }

    if (filterKey === "THIS_MONTH") {
        return (
            itemDate.getFullYear() === today.getFullYear() &&
            itemDate.getMonth() === today.getMonth()
        );
    }

    const sevenDaysAgo = new Date(today);
    sevenDaysAgo.setDate(today.getDate() - 6);
    sevenDaysAgo.setHours(0, 0, 0, 0);

    const endOfToday = new Date(today);
    endOfToday.setHours(23, 59, 59, 999);

    return itemDate >= sevenDaysAgo && itemDate <= endOfToday;
}

export function matchYearFilter(dateValue: Date | string, year?: number | null) {
    if (!year) {
        return true;
    }

    const itemDate = parseDateValue(dateValue);

    return itemDate ? itemDate.getFullYear() === year : false;
}
