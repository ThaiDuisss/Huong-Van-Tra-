import { ImportUploadPage } from "./ImportUploadPage";
import { productApi } from "../services/product.api";
import { routePaths } from "../../../routes/path";

export function ProductImportPage() {
    return (
        <ImportUploadPage
            backLabel="← Quay lại danh sách sản phẩm"
            backTo={routePaths.products}
            description="Tải file Excel lên, hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={() => productApi.downloadTemplate()}
            importType="PRODUCT"
            templateFileName="mau_nhap_san_pham.xlsx"
            title="Nhập Excel — Sản phẩm"
        />
    );
}
