export function formatCurrency(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0
  }).format(Math.max(0, value));
}

export function parseCurrencyInput(value: string) {
  const numericValue = Number(value.replace(/\D/g, ""));

  return Number.isFinite(numericValue) ? numericValue : 0;
}
