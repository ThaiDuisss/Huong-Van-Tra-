import { apiClient } from "../../../api/api-client";
import type { SaleProductPreview } from "../types/sale.types";

const fallbackImageUrl =
    "https://images.unsplash.com/photo-1544787219-7f47ccb76574?auto=format&fit=crop&w=360&q=80";

interface PosVariantResponse {
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
    quantityOnHand?: number | string | null;
    quantityReserved?: number | string | null;
}

function toNumber(value: number | string | null | undefined) {
    const numberValue = Number(value ?? 0);

    return Number.isFinite(numberValue) ? numberValue : 0;
}

function mapPosVariantToSaleProduct(
    variant: PosVariantResponse
): SaleProductPreview {
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
        availableQuantity: toNumber(variant.availableQuantity),
        quantityOnHand: toNumber(variant.quantityOnHand),
        quantityReserved: toNumber(variant.quantityReserved)
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
    async search(keyword: string) {
        const query = keyword.trim();

        if (!query) {
            return [];
        }

        const variants = await apiClient.get<PosVariantResponse[]>(
            "/pos/products",
            {
                query: {
                    keyword: query
                }
            }
        );

        const products = uniqueProducts(
            variants.map(mapPosVariantToSaleProduct)
        );

        return products;
    },

    async getPosView(variantId: string) {
        const variant = await apiClient.get<PosVariantResponse>(
            `/pos/products/${encodeURIComponent(variantId)}`
        );

        return mapPosVariantToSaleProduct(variant);
    }
};
