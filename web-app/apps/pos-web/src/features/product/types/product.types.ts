import type { QueryParams } from "@hvt/api-client";

export type CatalogStatus = "ACTIVE" | "INACTIVE";
export type EntityId = number | string;
export type UnitType = "BASE" | "PACKAGING";

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
    /** Phân loại: BASE (đơn vị gốc) hoặc PACKAGING (đơn vị đóng gói) */
    type: UnitType;
    /** ID đơn vị gốc. Chỉ có giá trị khi type = PACKAGING */
    baseUnitId: EntityId | null;
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
    variantName: string;
    sellingPrice: number;
    costPrice: number;
    /** ID đơn vị bán thương mại (thay thế baseUnitId + packQuantity cũ) */
    unitId: EntityId;
    inventoryPolicy: InventoryPolicy;
    status: CatalogStatus;
    productStatus: CatalogStatus;
    sellable: boolean;
}

export interface ProductVariantInput {
    productId: EntityId;
    variantName: string;
    sellingPrice: number;
    costPrice: number;
    /** ID đơn vị bán thương mại */
    unitId: EntityId;
    inventoryPolicy: InventoryPolicy;
    status: CatalogStatus;
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

export interface Ingredient {
    id: EntityId;
    ingredientCode: string;
    name: string;
    status: CatalogStatus;
}

export interface IngredientInput {
    ingredientCode: string;
    name: string;
    status: CatalogStatus;
}
