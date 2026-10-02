import { apiClient } from "../../../api/api-client";
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

function mapPosVariantToSaleProduct(
    variant: PosVariantResponse
): SaleProductPreview {
    // Tồn khả dụng của Inventory tách làm hai phần: trên kệ bán và trong kho. Tổng hai phần là số có thể bán.
    const shelfQuantity = toNumber(variant.shelfQuantity);
    const warehouseQuantity = toNumber(variant.warehouseQuantity);
    const availableQuantity =
        variant.shelfQuantity == null && variant.warehouseQuantity == null
            ? toNumber(variant.availableQuantity)
            : shelfQuantity + warehouseQuantity;

    return {
        id: String(variant.variantId),
        productVariantId: String(variant.variantId),
        name: variant.productName,
        sku: variant.sku,
        barcode: variant.barcode ?? "",
        category: variant.categoryName ?? "",
        variant: variant.variantName,
        price: toNumber(variant.sellingPrice ?? variant.selling_price),
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

        const items = response?.content ?? [];
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
            content: uniqueProducts((response.content ?? []).map(mapPosVariantToSaleProduct))
        };
    },

    async getPosView(variantId: string) {
        const variant = await apiClient.get<PosVariantResponse>(
            `/pos/products/${encodeURIComponent(variantId)}`
        );

        return mapPosVariantToSaleProduct(variant);
    }
};
