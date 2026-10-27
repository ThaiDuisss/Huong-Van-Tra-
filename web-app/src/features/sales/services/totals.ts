import type {
  SaleCartItem,
  SaleDiscountOption,
  SaleManualDiscount,
  SaleTotals
} from "../types/sale.types";

export function calculateSubtotal(items: SaleCartItem[]) {
  return items.reduce(
    (total, item) =>
      total + Math.max(0, item.quantity) * item.unitPrice,
    0
  );
}

export function calculateManualDiscountAmount(
  amount: number,
  discount: SaleManualDiscount | null | undefined
) {
  if (!discount || discount.discountValue <= 0 || amount <= 0) {
    return 0;
  }

  if (discount.discountType === "PERCENTAGE") {
    return Math.min(amount, Math.round((amount * discount.discountValue) / 100));
  }

  return Math.min(amount, Math.round(discount.discountValue));
}

export function calculateCartItemGrossTotal(item: SaleCartItem) {
  return Math.max(0, item.quantity) * Math.max(0, item.unitPrice);
}

export function calculateCartItemDiscountAmount(item: SaleCartItem) {
  return calculateManualDiscountAmount(
    calculateCartItemGrossTotal(item),
    item.manualDiscount
  );
}

export function calculateNetSubtotal(items: SaleCartItem[]) {
  return items.reduce(
    (total, item) =>
      total +
      Math.max(0, calculateCartItemGrossTotal(item) - calculateCartItemDiscountAmount(item)),
    0
  );
}

export function calculateItemDiscountAmount(items: SaleCartItem[]) {
  return items.reduce(
    (total, item) => total + calculateCartItemDiscountAmount(item),
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

  if (discount.estimatedDiscount !== undefined) {
    return Math.min(subtotal, Math.max(0, discount.estimatedDiscount));
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
  discount: SaleDiscountOption,
  manualOrderDiscount?: SaleManualDiscount | null
): SaleTotals {
  const subtotal = calculateSubtotal(items);
  const itemDiscountAmount = calculateItemDiscountAmount(items);
  const discountableSubtotal = calculateNetSubtotal(items);
  const promotionDiscountAmount = calculateDiscountAmount(
    discountableSubtotal,
    discount
  );
  const manualOrderDiscountAmount = calculateManualDiscountAmount(
    Math.max(0, discountableSubtotal - promotionDiscountAmount),
    manualOrderDiscount
  );
  const orderDiscountAmount =
    promotionDiscountAmount + manualOrderDiscountAmount;
  const discountAmount = itemDiscountAmount + orderDiscountAmount;

  return {
    subtotal,
    itemDiscountAmount,
    orderDiscountAmount,
    discountAmount,
    total: Math.max(0, subtotal - discountAmount)
  };
}
