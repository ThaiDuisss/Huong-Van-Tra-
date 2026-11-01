export type SalePaymentMethod =
  | "CASH"
  | "BANK_TRANSFER"
  | "E_WALLET"
  | "QR_CODE"
  | "CARD"
  | "COMBINED";

export interface SaleProductPreview {
  id: string;
  /** Sản phẩm gốc trong Catalog; các biến thể cùng productId là các đơn vị đóng gói (hộp, thùng...). */
  productId?: string;
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
  sellableQuantity: number;
  /** Số đang trên kệ bán. Vượt số này thì cần phiếu nhập kệ từ kho. */
  shelfQuantity: number;
  /** Số còn trong kho, chưa lên kệ. */
  warehouseQuantity: number;
  shelfLocationId?: string | null;
  warehouseLocationId?: string | null;
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
  sellableQuantity?: number;
  quantityOnHand?: number | null;
  quantityReserved?: number | null;
  manualDiscount?: SaleManualDiscount | null;
  note?: string;
  /** Có giá trị khi dòng là trà đóng theo yêu cầu (không đổi số lượng, sửa bằng dialog đóng trà). */
  bundle?: SaleBundleInfo | null;
}

export interface SaleBundleComponent {
  ingredientId: string;
  ingredientName: string;
  unit?: string | null;
  quantity: number;
  lineTotal: number;
}

export interface SaleBundleInfo {
  components: SaleBundleComponent[];
  packingStatus?: string | null;
  packingCode?: string | null;
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
