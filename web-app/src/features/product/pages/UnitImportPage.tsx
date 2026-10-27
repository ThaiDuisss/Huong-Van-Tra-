import { ImportUploadPage } from "./ImportUploadPage";
import { unitApi } from "../services/unit.api";
import { routePaths } from "../../../routes/path";
import { UnitImportItemsList } from "../components/UnitImportItemsList";
import { IMPORT_ITEMS_LIST_REGISTRY } from "../import-batch-registry";

export function UnitImportPage() {
    return (
        <ImportUploadPage
            backLabel="← Quay lại danh sách đơn vị"
            backTo={routePaths.units}
            description="Tải file Excel lên, hệ thống sẽ phân tích thành danh sách để bạn kiểm tra/sửa trước khi gửi quản lý duyệt. Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi được duyệt."
            downloadTemplate={() => unitApi.downloadTemplate()}
            importType="UNIT"
            itemsListRegistry={{ ...IMPORT_ITEMS_LIST_REGISTRY, UNIT: UnitImportItemsList }}
            templateFileName="mau_nhap_don_vi.xlsx"
            title="Nhập Excel — Đơn vị"
        />
    );
}
