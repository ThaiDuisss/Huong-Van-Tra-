export type SalePaymentMethod =
  | "CASH"
  | "BANK_TRANSFER"
  | "E_WALLET"
  | "QR_CODE"
  | "CARD"
  | "COMBINED";

export interface SaleProductPreview {
  id: string;
  productVariantId?: string;
  inventoryLocationId?: string | null;
  name: string;
  sku: string;
  barcode: string;
  category: string;
  variant: string;
  price: number;
  imageUrl: string;
  availableQuantity: number | null;
  quantityOnHand?: number | null;
  quantityReserved?: number | null;
}

export interface SaleCartItem {
  itemId?: string;
  productId: string;
  productVariantId?: string;
  inventoryLocationId?: string | null;
  name: string;
  sku: string;
  variant: string;
  quantity: number;
  unitPrice: number;
  availableQuantity?: number | null;
  quantityOnHand?: number | null;
  quantityReserved?: number | null;
  manualDiscount?: SaleManualDiscount | null;
  note?: string;
}

export type SaleOrderType = "QUICK_SALE" | "DELIVERY";

export type SaleDiscountType = "NONE" | "PERCENT" | "AMOUNT" | "GIFT";

export type SaleBackendDiscountType =
  | "FIXED_AMOUNT"
  | "PERCENTAGE"
  | "OVERRIDE_PRICE";

export type SaleDiscountSource =
  | "MANUAL"
  | "PROMOTION"
  | "COUPON"
  | "VOUCHER"
  | "LOYALTY"
  | "LEGACY";

export interface SaleManualDiscount {
  discountType: Extract<
    SaleBackendDiscountType,
    "FIXED_AMOUNT" | "PERCENTAGE"
  >;
  discountValue: number;
  reasonCode: string;
  note?: string;
}

export type SalePromotionStatus =
  | "RECOMMENDED"
  | "AVAILABLE"
  | "INELIGIBLE"
  | "EXPIRED";

export interface SaleDiscountOption {
  id: string;
  label: string;
  code: string;
  type: SaleDiscountType;
  value: number;
  estimatedDiscount?: number;
  description: string;
  status: SalePromotionStatus;
  condition: string;
  minSubtotal: number | null;
  expiresIn: string | null;
  promotionId?: string;
  applyMethod?: string;
  benefitType?: string;
  requiresCoupon?: boolean;
  couponId?: string;
  couponCode?: string;
  giftProductId?: string;
  giftQuantity?: number;
  buyQuantity?: number;
}

export interface SaleTotals {
  subtotal: number;
  itemDiscountAmount: number;
  orderDiscountAmount: number;
  discountAmount: number;
  total: number;
}

export interface SaleCustomer {
  id: string;
  customerCode?: string;
  name: string;
  phone: string;
  email?: string | null;
  tier: string;
  points: number;
  orderCount: number;
  totalSpent: number;
  avatarUrl?: string;
}
