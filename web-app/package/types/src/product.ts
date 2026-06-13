export interface Product {
    id: string;
    name: string;
    description?: string;
    imageUrl?: string;
    status: ProductStatus;
    variants: ProductVariant[];
}

export interface ProductVariant {
    id: string;
    productId: string;
    sku: string;
    variantName: string;
    sellingPrice: number;
    stockQuantity?: number;
}

export type ProductStatus = "ACTIVE" | "INACTIVE";