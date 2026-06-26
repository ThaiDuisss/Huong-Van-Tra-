import { apiClient } from "../../../api/api-client";
import { posInventoryApi } from "./posInventory.api";
import type { SaleProductPreview } from "../types/sale.types";
import type { PosInventoryBalance } from "./posInventory.api";

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
        availableQuantity: null,
        quantityOnHand: null,
        quantityReserved: null
    };
}

function mergeInventory(
    products: SaleProductPreview[],
    inventories: PosInventoryBalance[]
) {
    return products.map((product) => {
        const matchedInventories = inventories.filter((item) => {
            if (
                item.variantId &&
                product.productVariantId &&
                item.variantId === product.productVariantId
            ) {
                return true;
            }

            return Boolean(item.sku && item.sku === product.sku);
        });

        if (matchedInventories.length === 0) {
            return product;
        }

        const stock = matchedInventories.reduce(
            (total, item) => ({
                availableQuantity:
                    total.availableQuantity +
                    Number(item.availableQuantity ?? 0),
                quantityOnHand:
                    total.quantityOnHand + Number(item.quantityOnHand ?? 0),
                quantityReserved:
                    total.quantityReserved +
                    Number(item.quantityReserved ?? 0)
            }),
            {
                availableQuantity: 0,
                quantityOnHand: 0,
                quantityReserved: 0
            }
        );

        return {
            ...product,
            availableQuantity: stock.availableQuantity,
            quantityOnHand: stock.quantityOnHand,
            quantityReserved: stock.quantityReserved
        };
    });
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
            "/catalog/product-variants/search-view-pos",
            {
                query: {
                    keyword: query
                }
            }
        );

        const products = uniqueProducts(
            variants.map(mapPosVariantToSaleProduct)
        );

        const inventoryResults = await Promise.allSettled(
            products.map((product) =>
                posInventoryApi.searchInventoryForPos({
                    sku: product.sku
                })
            )
        );
        const inventories = inventoryResults.flatMap((result) =>
            result.status === "fulfilled" ? result.value : []
        );

        return mergeInventory(products, inventories);
    },

    async getPosView(variantId: string) {
        const variant = await apiClient.get<PosVariantResponse>(
            `/catalog/product-variants/${encodeURIComponent(variantId)}/pos-view`
        );

        return mapPosVariantToSaleProduct(variant);
    }
};
