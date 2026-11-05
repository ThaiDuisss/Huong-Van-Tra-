import { apiClient } from "../../../api/api-client";
import type { RequestOptions } from "@/lib/api-client";
import type { SaleProductPreview } from "../types/sale.types";

const fallbackImageUrl =
    "https://images.unsplash.com/photo-1544787219-7f47ccb76574?auto=format&fit=crop&w=360&q=80";

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface PosVariantResponse {
    productId: string;
    variantId: string;
    sku: string;
    barcode?: string | null;
    productName: string;
    variantName: string;
    categoryName?: string | null;
    sellingPrice?: number | string | null;
    selling_price?: number | string | null;
    imageUrl?: string | null;
    inventoryLocationId?: string | null;
    locationId?: string | null;
    availableQuantity?: number | string | null;
    sellableQuantity?: number | string | null;
    quantityOnHand?: number | string | null;
    reservedQuantity?: number | string | null;
    quantityReserved?: number | string | null;
    stockStatus?: string | null;
    shelfQuantity?: number | string | null;
    warehouseQuantity?: number | string | null;
    shelfLocationId?: string | null;
    warehouseLocationId?: string | null;
}

function toNumber(value: number | string | null | undefined) {
    const numberValue = Number(value ?? 0);

    return Number.isFinite(numberValue) ? numberValue : 0;
}

function isVariantResponse(value: unknown): value is PosVariantResponse {
    return typeof value === "object" && value !== null && !Array.isArray(value)
        && "variantId" in value && typeof value.variantId === "string" && value.variantId.trim() !== ""
        && "productId" in value && typeof value.productId === "string" && value.productId.trim() !== "";
}

function invalidVariantResponse(): Error {
    return new Error("Phản hồi thông tin bán hàng không hợp lệ. Vui lòng tải lại.");
}

function variantContent(response: unknown): PosVariantResponse[] {
    if (typeof response !== "object" || response === null || !("content" in response)
        || !Array.isArray(response.content)
        || !response.content.every(isVariantResponse)) {
        throw invalidVariantResponse();
    }
    return response.content as PosVariantResponse[];
}

function mapPosVariantToSaleProduct(
    variant: PosVariantResponse
): SaleProductPreview {
    // Tồn khả dụng của Inventory tách làm hai phần: trên kệ bán và trong kho. Tổng hai phần là số có thể bán.
    const shelfQuantity = toNumber(variant.shelfQuantity);
    const sellingPrice = variant.sellingPrice ?? variant.selling_price;
    const warehouseQuantity = toNumber(variant.warehouseQuantity);
    const availableQuantity =
        variant.shelfQuantity == null && variant.warehouseQuantity == null
            ? toNumber(variant.availableQuantity)
            : shelfQuantity + warehouseQuantity;

    return {
        id: String(variant.variantId),
        productId: variant.productId ? String(variant.productId) : undefined,
        productVariantId: String(variant.variantId),
        name: variant.productName,
        sku: variant.sku,
        barcode: variant.barcode ?? "",
        category: variant.categoryName ?? "",
        variant: variant.variantName,
        price: toNumber(sellingPrice),
        priceAvailable: sellingPrice != null && String(sellingPrice).trim() !== ""
            && Number.isFinite(Number(sellingPrice)) && Number(sellingPrice) >= 0,
        imageUrl: variant.imageUrl ?? fallbackImageUrl,
        inventoryLocationId:
            variant.inventoryLocationId ?? variant.locationId ?? null,
        availableQuantity,
        sellableQuantity: availableQuantity,
        shelfQuantity,
        warehouseQuantity,
        shelfLocationId: variant.shelfLocationId ?? null,
        warehouseLocationId: variant.warehouseLocationId ?? null,
        quantityOnHand: toNumber(variant.quantityOnHand),
        quantityReserved: toNumber(
            variant.reservedQuantity ?? variant.quantityReserved
        )
    };
}

function uniqueProducts(products: SaleProductPreview[]) {
    const seen = new Set<string>();

    return products.filter((product) => {
        const key = product.productVariantId ?? product.id;

        if (seen.has(key)) {
            return false;
        }

        seen.add(key);
        return true;
    });
}

export const posProductApi = {
    async search(keyword: string, page = 0, size = 20): Promise<SaleProductPreview[]> {
        const query = keyword.trim();

        if (!query) {
            return [];
        }

        const response = await apiClient.get<PageResponse<PosVariantResponse>>(
            "/pos/products",
            {
                query: {
                    keyword: query,
                    page,
                    size
                }
            }
        );

        const items = variantContent(response);
        return uniqueProducts(items.map(mapPosVariantToSaleProduct));
    },

    async searchPage(
        keyword: string,
        page = 0,
        size = 20
    ): Promise<PageResponse<SaleProductPreview>> {
        const query = keyword.trim();

        if (!query) {
            return {
                content: [],
                page: 0,
                size,
                totalElements: 0,
                totalPages: 0,
                first: true,
                last: true
            };
        }

        const response = await apiClient.get<PageResponse<PosVariantResponse>>(
            "/pos/products",
            {
                query: {
                    keyword: query,
                    page,
                    size
                }
            }
        );

        return {
            ...response,
            content: uniqueProducts(variantContent(response).map(mapPosVariantToSaleProduct))
        };
    },

    /** Các biến thể đang bán của cùng một sản phẩm (đơn vị đóng gói khác nhau), kèm tồn trên kệ / trong kho. */
    async listProductVariants(productId: string, options?: RequestOptions): Promise<SaleProductPreview[]> {
        const response = await apiClient.get<PageResponse<PosVariantResponse>>(
            "/pos/products",
            {
                ...options,
                query: {
                    ...options?.query,
                    productId,
                    page: 0,
                    size: 50,
                    _fresh: options?.cache === "no-store" ? 1 : undefined
                }
            }
        );

        return uniqueProducts(variantContent(response).map(mapPosVariantToSaleProduct));
    },

    async getPosView(variantId: string, options?: RequestOptions) {
        const variant = await apiClient.get<PosVariantResponse>(
            `/pos/products/${encodeURIComponent(variantId)}`,
            options?.cache === "no-store" ? { ...options, query: { ...options.query, _fresh: 1 } } : options
        );

        if (!isVariantResponse(variant)) throw invalidVariantResponse();
        return mapPosVariantToSaleProduct(variant);
    }
};
