import { Link, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { ImportBatchPanel } from "../components/ImportBatchPanel";

export function ProductImportReviewPage() {
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
            <ImportBatchPanel batchId={batchId} />
        </section>
    );
}
