import { ImportUploadPage } from "./ImportUploadPage";
import { productVariantApi } from "../services/productVariant.api";
import { routePaths } from "../../../routes/path";

export function ProductVariantImportPage() {
    return (
        <ImportUploadPage
            backLabel="← Quay lại danh sách mặt hàng"
            backTo={routePaths.productVariants}
            description="Tải file Excel lên (kèm sheet BOM nếu có), hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Dữ liệu (mặt hàng + BOM) chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={() => productVariantApi.downloadTemplate()}
            importType="PRODUCT_VARIANT"
            templateFileName="mau_nhap_mat_hang.xlsx"
            title="Nhập Excel — Mặt hàng (kèm BOM)"
        />
    );
}
