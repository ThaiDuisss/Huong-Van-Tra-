import { ImportUploadPage } from "./ImportUploadPage";
import { productVariantApi } from "../services/productVariant.api";
import { routePaths } from "../../../routes/path";

export function ProductVariantImportPage() {
    return (
        <ImportUploadPage
            backLabel="← Quay lại danh sách mặt hàng"
            backTo={routePaths.productVariants}
            description="Tải file Excel lên (kèm sheet BOM nếu có), hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Chọn Kiểu import: “Tạo mới” dùng file mẫu (SKU để trống thì hệ thống tự sinh, điền thì dùng SKU đó); “Cập nhật” dùng file xuất để cập nhật (bắt buộc cột SKU, ô trống giữ nguyên, có dòng BOM thì thay toàn bộ BOM). Dữ liệu chỉ được ghi chính thức sau khi được duyệt."
            downloadTemplate={() => productVariantApi.downloadTemplate()}
            supportsUpdate
            updateDownloads={[
                {
                    label: "Xuất để cập nhật (mặt hàng chưa có BOM)",
                    fileName: "cap_nhat_mat_hang_chua_co_bom.xlsx",
                    download: () => productVariantApi.downloadUpdateTemplate(true)
                },
                {
                    label: "Xuất để cập nhật (tất cả mặt hàng)",
                    fileName: "cap_nhat_mat_hang.xlsx",
                    download: () => productVariantApi.downloadUpdateTemplate(false)
                }
            ]}
            importType="PRODUCT_VARIANT"
            templateFileName="mau_nhap_mat_hang.xlsx"
            title="Nhập Excel — Mặt hàng (tạo mới / cập nhật, kèm BOM)"
        />
    );
}
