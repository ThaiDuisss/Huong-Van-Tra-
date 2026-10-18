export function formatVnd(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        currency: "VND",
        maximumFractionDigits: 0,
        style: "currency"
    }).format(value);
}

export function formatVietnamNumber(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 2
    }).format(value);
}

export function formatVietnamDateTime(value: string) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        hour: "2-digit",
        hour12: false,
        minute: "2-digit",
        month: "2-digit",
        year: "numeric"
    }).format(date);
}

export function formatExpiryDaysText(expiryDate: string) {
    const days = Math.ceil((new Date(expiryDate).getTime() - Date.now()) / 86400000);

    if (days < 0) return `Đã hết hạn ${Math.abs(days)} ngày`;
    if (days === 0) return "Hết hạn hôm nay";

    return `Còn ${days} ngày`;
}

export function toInputDateTime(value: string) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");

    return `${year}-${month}-${day}T${hour}:${minute}`;
}
