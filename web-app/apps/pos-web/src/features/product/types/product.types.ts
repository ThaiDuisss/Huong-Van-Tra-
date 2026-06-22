import type { QueryParams } from "@hvt/api-client";

export type CatalogStatus = "ACTIVE" | "INACTIVE";
export type EntityId = number | string;

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
}

export interface Category {
    id: EntityId;
    parentId: EntityId | null;
    name: string;
    status: CatalogStatus;
}

export interface CategoryInput {
    parentId: EntityId | null;
    name: string;
    status: CatalogStatus;
}

export interface ProductSummary {
    id: EntityId;
    name: string;
    category: Category;
    imageUrl: string | null;
    status: CatalogStatus;
    totalVariants: number;
}

export interface Product {
    id: EntityId;
    category: Category;
    name: string;
    description: string | null;
    imageUrl: string | null;
    status: CatalogStatus;
    variants: ProductVariant[];
}

export interface ProductInput {
    categoryId: EntityId;
    name: string;
    description: string;
    imageUrl: string | null;
    status: CatalogStatus;
}

export type InventoryPolicy = "TRACK_STOCK" | "DO_NOT_TRACK";

export interface Unit {
    id: EntityId;
    name: string;
    symbol: string;
    precisionScale: number;
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
    baseUnitId: EntityId;
    inventoryPolicy: InventoryPolicy;
    status: CatalogStatus;
    productStatus: CatalogStatus;
    sellable: boolean;
}

export interface ProductVariantInput {
    productId: EntityId;
    sku: string;
    variantName: string;
    sellingPrice: number;
    costPrice: number;
    baseUnitId: EntityId;
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
