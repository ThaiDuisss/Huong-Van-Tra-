import type {
  SaleCartItem,
  SaleDiscountOption,
  SaleTotals
} from "../types/sale.types";

export function calculateSubtotal(items: SaleCartItem[]) {
  return items.reduce(
    (total, item) =>
      total + Math.max(0, item.quantity) * item.unitPrice,
    0
  );
}

export function calculateDiscountAmount(
  subtotal: number,
  discount: SaleDiscountOption
) {
  if (
    discount.status === "EXPIRED" ||
    discount.status === "INELIGIBLE" ||
    subtotal < (discount.minSubtotal ?? 0)
  ) {
    return 0;
  }

  if (discount.type === "PERCENT") {
    return Math.round((subtotal * discount.value) / 100);
  }

  if (discount.type === "AMOUNT") {
    return Math.min(subtotal, discount.value);
  }

  return 0;
}

export function calculateSaleTotals(
  items: SaleCartItem[],
  discount: SaleDiscountOption
): SaleTotals {
  const subtotal = calculateSubtotal(items);
  const discountAmount = calculateDiscountAmount(
    subtotal,
    discount
  );

  return {
    subtotal,
    discountAmount,
    total: Math.max(0, subtotal - discountAmount)
  };
}
