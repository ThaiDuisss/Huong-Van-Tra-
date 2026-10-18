import { apiClient } from "../../../api/api-client";
import type { BomItemInput, BomItemResponse } from "../types/bom.types";

export const bomApi = {
    listByVariant(productVariantId: string) {
        return apiClient.get<BomItemResponse[]>(
            `/catalog/product-variants/${encodeURIComponent(productVariantId)}/bom-items`
        );
    },
    replaceForVariant(productVariantId: string, items: BomItemInput[]) {
        return apiClient.put<BomItemResponse[], BomItemInput[]>(
            `/catalog/product-variants/${encodeURIComponent(productVariantId)}/bom-items`,
            items
        );
    }
};
