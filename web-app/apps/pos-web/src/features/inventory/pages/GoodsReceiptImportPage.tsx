import { ImportUploadPage } from "../../product/pages/ImportUploadPage";
import { routePaths } from "../../../routes/path";
import { GoodsReceiptImportItemsList } from "../components/GoodsReceiptImportItemsList";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import { importBatchApi } from "../services/importBatch.api";
import { generateGoodsReceiptTemplate } from "../utils/goodsReceiptExcel";

const ITEMS_LIST_REGISTRY = { GOODS_RECEIPT: GoodsReceiptImportItemsList };

export function GoodsReceiptImportPage() {
    return (
        <ImportUploadPage
            api={importBatchApi}
            backTo={routePaths.goodsReceiptImportRequests}
            description="Tải file Excel lên — các dòng cùng Nhà cung cấp, Kho nhận và Mã PO sẽ tự động gộp thành 1 phiếu nhập. Kiểm tra/sửa danh sách trước khi gửi quản lý duyệt; dữ liệu chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={async () => {
                const options = await goodsReceiptApi.getGoodsReceiptOptions();
                return generateGoodsReceiptTemplate(options);
            }}
            importType="GOODS_RECEIPT"
            itemsListRegistry={ITEMS_LIST_REGISTRY}
            templateFileName="mau_nhap_phieu_nhap_kho.xlsx"
            title="Nhập Excel — Phiếu nhập kho"
        />
    );
}
