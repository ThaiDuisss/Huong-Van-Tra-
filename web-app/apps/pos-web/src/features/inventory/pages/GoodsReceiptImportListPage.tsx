import { routePaths } from "../../../routes/path";
import { ImportBatchListPage } from "../../product/pages/ImportBatchListPage";
import { importBatchApi } from "../services/importBatch.api";

const IMPORT_TYPE_OPTIONS = [{ label: "Phiếu nhập kho", value: "GOODS_RECEIPT" as const }];
const TYPE_LABELS = { GOODS_RECEIPT: "Phiếu nhập kho" };

export function GoodsReceiptImportListPage() {
    return (
        <ImportBatchListPage
            api={importBatchApi}
            detailRoute={routePaths.goodsReceiptImportDetail}
            importTypeOptions={IMPORT_TYPE_OPTIONS}
            title="Yêu cầu nhập Excel — Phiếu nhập kho"
            typeLabels={TYPE_LABELS}
        />
    );
}
