import { Link, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { ImportBatchPanel } from "../../product/components/ImportBatchPanel";
import { GoodsReceiptImportItemsList } from "../components/GoodsReceiptImportItemsList";
import { importBatchApi } from "../services/importBatch.api";

const ITEMS_LIST_REGISTRY = { GOODS_RECEIPT: GoodsReceiptImportItemsList };

export function GoodsReceiptImportReviewPage() {
    const { batchId } = useParams();

    if (!batchId) {
        return (
            <div className="p-6 text-sm font-bold text-red-600">Thiếu mã phiếu import.</div>
        );
    }

    return (
        <section className="grid gap-4">
            <Link className="text-sm font-black text-[#159734] hover:underline" to={routePaths.importExcelRequests}>
                ← Quay lại danh sách yêu cầu nhập
            </Link>
            <ImportBatchPanel api={importBatchApi} batchId={batchId} itemsListRegistry={ITEMS_LIST_REGISTRY} />
        </section>
    );
}
