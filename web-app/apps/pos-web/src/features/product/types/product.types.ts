import type { QueryParams } from "@hvt/api-client";

export type CatalogStatus = "ACTIVE" | "INACTIVE";
export type EntityId = number | string;
export type UnitType = "WEIGHT" | "VOLUME" | "COUNT";

export interface PageResult<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface CatalogQuery extends QueryParams {
    page?: number;
    size?: number;
    keyword?: string;
    status?: CatalogStatus;
    type?: UnitType;
}

export interface Category {
    id: EntityId;
    parentId: EntityId | null;
    /** Tên danh mục cha — có ở API danh sách/chi tiết */
    parentName?: string | null;
    name: string;
    status: CatalogStatus;
    categoryCode: string;
    defaultReorderPoint: number;
}

export interface CategoryInput {
    parentId: EntityId | null;
    name: string;
    status: CatalogStatus;
    categoryCode?: string;
    defaultReorderPoint: number;
}

export interface ProductSummary {
    id: EntityId;
    productCode: string;
    name: string;
    category: Category;
    imageUrl: string | null;
    status: CatalogStatus;
    totalVariants: number;
}

export interface Product {
    id: EntityId;
    productCode: string;
    category: Category;
    name: string;
    description: string | null;
    imageUrl: string | null;
    status: CatalogStatus;
    variants: ProductVariant[];
}

export interface ProductInput {
    categoryId: EntityId;
    productCode?: string;
    name: string;
    description: string;
    imageUrl: string | null;
    status: CatalogStatus;
    fieldId?: string | null;
}

export type InventoryPolicy = "TRACK_STOCK" | "DO_NOT_TRACK";

export interface Unit {
    id: EntityId;
    name: string;
    symbol: string;
    precisionScale: number;
    // Phân loại: 
    type: UnitType;
    // ID đơn vị gốc. 
    baseUnitId: EntityId | null;
    /** Tên đơn vị gốc — có ở API danh sách/chi tiết */
    baseUnitName?: string | null;
    /** Tỷ lệ quy đổi so với đơn vị gốc. Ví dụ: 1 Hộp = 100g -> conversionRate = 100 */
    conversionRate: number;
    status: CatalogStatus;
}

export interface UnitInput {
    name: string;
    symbol: string;
    precisionScale: number;
    type: UnitType;
    baseUnitId: EntityId | null;
    conversionRate: number;
    status: CatalogStatus;
}

export interface ProductVariant {
    id: EntityId;
    productId: EntityId;
    productName: string;
    sku: string;
    /** Tên hiển thị, hệ thống tự sinh = "{Tên sản phẩm} {Định lượng} {Tên đơn vị}", không nhập tay */
    variantName: string;
    /** Định lượng, ví dụ 100, 250, 500 */
    quantity: number;
    sellingPrice: number;
    costPrice: number;
    sellableQuantity: number;
    productionThreshold: number;
    productionBatchQuantity: number;
    /** ID đơn vị bán thương mại (thay thế baseUnitId + packQuantity cũ) */
    unitId: EntityId;
    unitName?: string | null;
    unitSymbol?: string | null;
    inventoryPolicy: InventoryPolicy;
    status: CatalogStatus;
    productStatus: CatalogStatus;
    sellable: boolean;
}

export interface ProductVariantInput {
    productId: EntityId;
    /** Định lượng, ví dụ 100, 250, 500 — variantName được server tự sinh từ trường này */
    quantity: number;
    sellingPrice: number;
    costPrice: number;
    /** ID đơn vị bán thương mại */
    unitId: EntityId;
    inventoryPolicy: InventoryPolicy;
    status: CatalogStatus;
}

/** Cấu hình bán hàng của Mặt hàng — chỉ SUPER_ADMIN được thay đổi, tách khỏi biểu mẫu sửa Mặt hàng. */
export interface SellableConfigInput {
    sellableQuantity: number;
    productionThreshold: number;
    productionBatchQuantity: number;
}

export interface PriceListItem {
    id: EntityId;
    productVariant: ProductVariant;
    sellingPrice: number;
}

export interface PriceList {
    id: EntityId;
    branchId: EntityId;
    name: string;
    effectiveFrom: string | null;
    effectiveTo: string | null;
    status: CatalogStatus;
    items: PriceListItem[];
}

export interface PriceListInput {
    branchId: EntityId;
    name: string;
    effectiveFrom: string | null;
    effectiveTo: string | null;
    status: CatalogStatus;
    items: Array<{
        productVariantId: EntityId;
        sellingPrice: number;
    }>;
}

export type PriceChangeType =
    | "CREATED"
    | "UPDATED"
    | "REMOVED"
    | "STATUS_CHANGED";

export interface PriceChangeHistory {
    id: EntityId;
    priceListId: EntityId;
    priceListName: string;
    /** null khi changeType = STATUS_CHANGED */
    productVariantId: EntityId | null;
    sku: string | null;
    variantName: string | null;
    changeType: PriceChangeType;
    oldPrice: number | null;
    newPrice: number | null;
    /** newPrice - oldPrice; null khi thiếu một trong hai */
    priceDifference: number | null;
    oldStatus: CatalogStatus | null;
    newStatus: CatalogStatus | null;
    changedBy: string | null;
    changedByName: string | null;
    /** ISO-8601 UTC */
    changedAt: string;
}

export interface PriceChangeHistoryQuery extends QueryParams {
    page?: number;
    size?: number;
    variantId?: string;
    changeType?: PriceChangeType;
    /** ISO-8601 có múi giờ, vd 2026-10-01T00:00:00Z */
    from?: string;
    to?: string;
}

export interface Ingredient {
    id: EntityId;
    ingredientCode: string;
    name: string;
    imageUrl?: string | null;
    unitId?: EntityId | null;
    unitName?: string | null;
    unitSymbol?: string | null;
    reorderPoint?: number | null;
    status: CatalogStatus;
}

export interface IngredientInput {
    ingredientCode?: string;
    name: string;
    imageUrl?: string | null;
    unitId?: EntityId | null;
    reorderPoint?: number | null;
    status: CatalogStatus;
}
