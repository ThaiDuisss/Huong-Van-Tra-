import { buildTodayTimeNode, formatDisplayDate, type TimeRange } from "../TimeHierarchyFilter.utils";

const TIME_ZONE = "Asia/Ho_Chi_Minh";

export const TIME_PRESETS = [
    { value: "all", label: "Tất cả thời gian" },
    { value: "today", label: "Hôm nay" },
    { value: "7d", label: "7 ngày qua" },
    { value: "30d", label: "30 ngày qua" },
    { value: "month", label: "Tháng này" },
    { value: "custom", label: "Tùy chọn ngày" }
] as const;

export type TimePreset = (typeof TIME_PRESETS)[number]["value"];

export interface TimeRangeValue {
    preset: TimePreset;
    from?: string;
    to?: string;
}

export function isTimePreset(value: string | null | undefined): value is TimePreset {
    return TIME_PRESETS.some((preset) => preset.value === value);
}

function shiftIsoDate(isoDate: string, days: number) {
    const [year, month, day] = isoDate.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day + days));
    return date.toISOString().slice(0, 10);
}

/** Đổi lựa chọn thời gian sang khoảng ngày gửi lên API (`null` = không lọc). */
export function resolveTimeRange({ preset, from, to }: TimeRangeValue): TimeRange | null {
    const today = buildTodayTimeNode(TIME_ZONE).date;

    switch (preset) {
        case "today":
            return { fromDate: today, toDate: today, label: "Hôm nay", selectedDate: today };
        case "7d":
            return { fromDate: shiftIsoDate(today, -6), toDate: today, label: "7 ngày qua" };
        case "30d":
            return { fromDate: shiftIsoDate(today, -29), toDate: today, label: "30 ngày qua" };
        case "month":
            return { fromDate: `${today.slice(0, 7)}-01`, toDate: today, label: "Tháng này" };
        case "custom": {
            if (!from && !to) return null;
            const fromDate = from || to!;
            const toDate = to || from!;
            return {
                fromDate,
                toDate,
                label: `${formatDisplayDate(fromDate)} - ${formatDisplayDate(toDate)}`
            };
        }
        default:
            return null;
    }
}
