export type TimeNode =
    | { type: "year"; year: number }
    | { quarter: number; type: "quarter"; year: number }
    | { month: number; quarter: number; type: "month"; year: number }
    | {
          date: string;
          month: number;
          quarter: number;
          type: "day";
          year: number;
      };

export interface TimeRange {
    fromDate: string;
    label: string;
    selectedDate?: string | null;
    toDate: string;
}

function padDatePart(value: number) {
    return String(value).padStart(2, "0");
}

export function formatLocalDate(date: Date) {
    return `${date.getFullYear()}-${padDatePart(
        date.getMonth() + 1
    )}-${padDatePart(date.getDate())}`;
}

export function toIsoDate(year: number, month: number, day: number) {
    return `${year}-${padDatePart(month)}-${padDatePart(day)}`;
}

export function getDaysInMonth(year: number, month: number) {
    return new Date(year, month, 0).getDate();
}

export function getQuarterFromMonth(month: number) {
    return Math.ceil(month / 3);
}

export function formatDisplayDate(isoDate: string) {
    const [year, month, day] = isoDate.split("-");

    return `${day}/${month}/${year}`;
}

export function formatLegacyDate(day: number, month: number, year: number) {
    return `${padDatePart(day)}/${padDatePart(month)}/${year}`;
}

export function legacyDateToIsoDate(date: string) {
    const [day, month, year] = date.split("/");

    return `${year}-${month}-${day}`;
}

export function buildTodayTimeNode(): TimeNode {
    const today = new Date();
    const month = today.getMonth() + 1;

    return {
        date: formatLocalDate(today),
        month,
        quarter: getQuarterFromMonth(month),
        type: "day",
        year: today.getFullYear()
    };
}

export function getTimeNodeLabel(node: TimeNode) {
    if (node.type === "day") {
        return formatDisplayDate(node.date);
    }

    if (node.type === "month") {
        return `Tháng ${node.month}/${node.year}`;
    }

    if (node.type === "quarter") {
        return `Quý ${node.quarter}/${node.year}`;
    }

    return `${node.year}`;
}

export function getDateRangeFromTimeNode(node: TimeNode | null): TimeRange | null {
    if (!node) {
        return null;
    }

    if (node.type === "day") {
        return {
            fromDate: node.date,
            label: getTimeNodeLabel(node),
            selectedDate: node.date,
            toDate: node.date
        };
    }

    if (node.type === "month") {
        return {
            fromDate: toIsoDate(node.year, node.month, 1),
            label: getTimeNodeLabel(node),
            selectedDate: null,
            toDate: toIsoDate(
                node.year,
                node.month,
                getDaysInMonth(node.year, node.month)
            )
        };
    }

    if (node.type === "quarter") {
        const startMonth = (node.quarter - 1) * 3 + 1;
        const endMonth = startMonth + 2;

        return {
            fromDate: toIsoDate(node.year, startMonth, 1),
            label: getTimeNodeLabel(node),
            selectedDate: null,
            toDate: toIsoDate(
                node.year,
                endMonth,
                getDaysInMonth(node.year, endMonth)
            )
        };
    }

    return {
        fromDate: toIsoDate(node.year, 1, 1),
        label: getTimeNodeLabel(node),
        selectedDate: null,
        toDate: toIsoDate(node.year, 12, 31)
    };
}
