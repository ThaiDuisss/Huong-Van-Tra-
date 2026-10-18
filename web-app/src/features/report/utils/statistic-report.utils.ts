import type { DecimalValue } from "../types/statistic-report.types";

function pad(value: number) {
    return value.toString().padStart(2, "0");
}

export function toDateInputValue(date: Date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Từ ngày 1 đến ngày cuối của tháng chứa `today`. */
export function getMonthRange(today: Date, monthOffset = 0) {
    const first = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1);
    const last = new Date(today.getFullYear(), today.getMonth() + monthOffset + 1, 0);

    return { fromDate: toDateInputValue(first), toDate: toDateInputValue(last) };
}

export function toNumber(value?: DecimalValue | null) {
    if (value === null || value === undefined || value === "") {
        return 0;
    }

    const numericValue = typeof value === "number" ? value : Number(value);
    return Number.isFinite(numericValue) ? numericValue : 0;
}

export function formatCurrency(value?: DecimalValue | null) {
    return `${Math.round(toNumber(value)).toLocaleString("vi-VN")}đ`;
}

export function formatQuantity(value?: DecimalValue | null) {
    return toNumber(value).toLocaleString("vi-VN", { maximumFractionDigits: 3 });
}

export function formatCount(value?: number | null) {
    return (value ?? 0).toLocaleString("vi-VN");
}

export function getErrorMessage(error: unknown, fallback: string) {
    if (
        typeof error === "object" &&
        error !== null &&
        "message" in error &&
        typeof error.message === "string" &&
        error.message.trim()
    ) {
        return error.message;
    }

    return fallback;
}

export function saveBlob(blob: Blob, fallbackFilename: string) {
    const blobName = (blob as Blob & { name?: unknown }).name;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    try {
        link.href = url;
        link.download =
            typeof blobName === "string" && blobName.trim() ? blobName : fallbackFilename;
        document.body.appendChild(link);
        link.click();
    } finally {
        link.remove();
        URL.revokeObjectURL(url);
    }
}

export function exportTimestamp(date: Date) {
    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(
        date.getHours()
    )}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}
