import { ImportUploadPage } from "./ImportUploadPage";
import { ingredientApi } from "../services/ingredient.api";
import { routePaths } from "../../../routes/path";

export function IngredientImportPage() {
    return (
        <ImportUploadPage
            backLabel="← Quay lại danh sách nguyên liệu"
            backTo={routePaths.ingredient}
            description="Chọn Kiểu import: “Tạo mới” dùng file mẫu (Mã nguyên liệu để trống thì tự sinh); “Cập nhật” dùng file xuất để cập nhật (bắt buộc Mã nguyên liệu, ô trống giữ nguyên). Tải file Excel lên, hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={() => ingredientApi.downloadTemplate()}
            supportsUpdate
            updateDownloads={[
                {
                    label: "Xuất để cập nhật",
                    fileName: "cap_nhat_nguyen_lieu.xlsx",
                    download: () => ingredientApi.downloadUpdateTemplate()
                }
            ]}
            importType="INGREDIENT"
            templateFileName="mau_nhap_nguyen_lieu.xlsx"
            title="Nhập Excel — Nguyên liệu"
        />
    );
}
