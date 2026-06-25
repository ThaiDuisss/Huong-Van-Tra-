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
  name: string;
  sku: string;
  barcode: string;
  category: string;
  variant: string;
  price: number;
  imageUrl: string;
  availableQuantity: number;
}

export interface SaleCartItem {
  productId: string;
  productVariantId?: string;
  name: string;
  sku: string;
  variant: string;
  quantity: number;
  unitPrice: number;
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
  description: string;
  status: SalePromotionStatus;
  condition: string;
  minSubtotal: number | null;
  expiresIn: string | null;
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
