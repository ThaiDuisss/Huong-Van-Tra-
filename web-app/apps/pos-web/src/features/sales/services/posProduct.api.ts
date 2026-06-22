import { apiClient } from "../../../api/api-client";
import type {
    PageResult,
    Product,
    ProductSummary,
    ProductVariant
} from "../../product/types/product.types";
import type { SaleProductPreview } from "../types/sale.types";

const fallbackImageUrl =
    "https://images.unsplash.com/photo-1544787219-7f47ccb76574?auto=format&fit=crop&w=360&q=80";

function mapVariantToSaleProduct(variant: ProductVariant): SaleProductPreview {
    return {
        id: String(variant.id),
        productVariantId: String(variant.id),
        name: variant.productName,
        sku: variant.sku,
        barcode: "",
        category: "",
        variant: variant.variantName,
        price: Number(variant.sellingPrice ?? 0),
        imageUrl: fallbackImageUrl,
        availableQuantity: 999
    };
}

function mapProductVariantToSaleProduct(
    product: Product,
    variant: ProductVariant
): SaleProductPreview {
    return {
        id: String(variant.id),
        productVariantId: String(variant.id),
        name: product.name,
        sku: variant.sku,
        barcode: "",
        category: product.category?.name ?? "",
        variant: variant.variantName,
        price: Number(variant.sellingPrice ?? 0),
        imageUrl: product.imageUrl ?? fallbackImageUrl,
        availableQuantity: 999
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

        const [skuResult, variantResult, productResult] =
            await Promise.allSettled([
                apiClient.get<PageResult<ProductVariant>>(
                    "/catalog/product-variants",
                    {
                        query: {
                            page: 0,
                            size: 10,
                            sku: query,
                            status: "ACTIVE"
                        }
                    }
                ),
                apiClient.get<PageResult<ProductVariant>>(
                    "/catalog/product-variants",
                    {
                        query: {
                            page: 0,
                            size: 10,
                            variantName: query,
                            status: "ACTIVE"
                        }
                    }
                ),
                apiClient
                    .get<PageResult<ProductSummary>>("/catalog/products", {
                        query: {
                            page: 0,
                            size: 5,
                            name: query,
                            status: "ACTIVE"
                        }
                    })
                    .then(async (response) => {
                        const products = await Promise.all(
                            response.content.map((product) =>
                                apiClient.get<Product>(
                                    `/catalog/products/${encodeURIComponent(product.id)}`
                                )
                            )
                        );

                        return products.flatMap((product) =>
                            product.variants
                                .filter((variant) => variant.sellable)
                                .map((variant) =>
                                    mapProductVariantToSaleProduct(
                                        product,
                                        variant
                                    )
                                )
                        );
                    })
            ]);

        const variantProducts = [skuResult, variantResult].flatMap((result) =>
            result.status === "fulfilled"
                ? result.value.content.map(mapVariantToSaleProduct)
                : []
        );
        const productNameProducts =
            productResult.status === "fulfilled" ? productResult.value : [];

        return uniqueProducts([...variantProducts, ...productNameProducts]);
    }
};
