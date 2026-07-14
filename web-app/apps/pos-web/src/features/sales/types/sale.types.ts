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
}

export type SaleOrderType = "QUICK_SALE" | "DELIVERY";

export type SaleDiscountType = "NONE" | "PERCENT" | "AMOUNT";

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
}

export interface SaleTotals {
  subtotal: number;
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
