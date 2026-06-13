import { QueryParams } from "../core/query-params";
import type { ApiClient } from "../core/types";

export type ProductStatus =
    | "ACTIVE"
    | "INACTIVE"
    | "OUT_OF_STOCK";

export interface Product {
    id: string;
    name: string;
    description?: string;
    categoryId: string;
    imageUrl?: string;
    status: ProductStatus;
    createdAt: string;
    updatedAt: string;
}

export interface ProductVariant {
    id: string;
    productId: string;
    sku: string;
    barcode?: string;
    variantName: string;
    sellingPrice: number;
    costPrice: number;
    status: ProductStatus;
}

export interface ProductDetail extends Product {
    variants: ProductVariant[];
}

export interface CreateProductInput {
    name: string;
    description?: string;
    categoryId: string;
    imageUrl?: string;
}

export interface UpdateProductInput {
    name?: string;
    description?: string;
    categoryId?: string;
    imageUrl?: string;
    status?: ProductStatus;
}

export interface ProductQuery extends QueryParams {
    page?: number;
    size?: number;
    keyword?: string;
    categoryId?: string;
    status?: ProductStatus;
    sort?: string[];
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export function createProductApi(apiClient: ApiClient) {
    return {
        getProducts(query: ProductQuery = {}) {
            return apiClient.get<PageResponse<Product>>(
                "/products",
                {
                    query
                }
            );
        },

        getProductById(id: string) {
            return apiClient.get<ProductDetail>(
                `/products/${encodeURIComponent(id)}`
            );
        },

        createProduct(input: CreateProductInput) {
            return apiClient.post<
                Product,
                CreateProductInput
            >("/products", input);
        },

        updateProduct(
            id: string,
            input: UpdateProductInput
        ) {
            return apiClient.put<
                Product,
                UpdateProductInput
            >(
                `/products/${encodeURIComponent(id)}`,
                input
            );
        },

        deleteProduct(id: string) {
            return apiClient.delete<void>(
                `/products/${encodeURIComponent(id)}`
            );
        },

        uploadProductImage(
            productId: string,
            file: File
        ) {
            const formData = new FormData();

            formData.append("file", file);

            return apiClient.upload<{
                imageUrl: string;
            }>(
                `/products/${encodeURIComponent(productId)}/image`,
                formData
            );
        },

        exportProducts(query: ProductQuery = {}) {
            return apiClient.download(
                "/products/export",
                {
                    query
                }
            );
        }
    };
}

export type ProductApi =
    ReturnType<typeof createProductApi>;