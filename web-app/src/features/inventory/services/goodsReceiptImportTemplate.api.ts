import { apiClient } from "../../../api/api-client";

export const goodsReceiptImportTemplateApi = {
    downloadTemplate() {
        return apiClient.download("/inventory/goods-receipts/import/template");
    }
};
