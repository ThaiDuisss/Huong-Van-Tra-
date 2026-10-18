import { ImportUploadPage } from "../../product/pages/ImportUploadPage";
import { routePaths } from "../../../routes/path";
import { GoodsReceiptImportItemsList } from "../components/GoodsReceiptImportItemsList";
import { goodsReceiptImportTemplateApi } from "../services/goodsReceiptImportTemplate.api";
import { importBatchApi } from "../services/importBatch.api";

const ITEMS_LIST_REGISTRY = { GOODS_RECEIPT: GoodsReceiptImportItemsList };

export function GoodsReceiptImportPage() {
    return (
        <ImportUploadPage
            api={importBatchApi}
            backLabel="← Quay lại danh sách phiếu nhập kho"
            backTo={routePaths.goodsReceipts}
            description="Tải file Excel lên (sheet Phiếu nhập liên kết với sheet Nguyên liệu qua cột Ref), hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={() => goodsReceiptImportTemplateApi.downloadTemplate()}
            importType="GOODS_RECEIPT"
            itemsListRegistry={ITEMS_LIST_REGISTRY}
            templateFileName="mau_nhap_phieu_nhap_kho.xlsx"
            title="Nhập Excel — Phiếu nhập kho"
        />
    );
}
