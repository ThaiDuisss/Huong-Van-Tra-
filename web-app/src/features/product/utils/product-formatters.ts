export function formatDate(value: string | null) {
    if (!value) {
        return "Không giới hạn";
    }

    return new Intl.DateTimeFormat("vi-VN").format(
        new Date(value.includes("T") ? value : `${value}T00:00:00`)
    );
}

export function formatCurrency(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}
