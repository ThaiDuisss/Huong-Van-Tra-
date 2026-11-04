# Java Code Style Rules
- Do not use fully qualified class names directly in method signatures, fields, or variables (e.g., `PageResponse<org.hvtra.catalog.dto.response.LookupResponse>`).
- Always use standard Java imports and simple class names instead (e.g., `import org.hvtra.catalog.dto.response.LookupResponse;` and `PageResponse<LookupResponse>`).
- Exception: Fully qualified names are allowed when required by JPQL queries (e.g., `SELECT new org.hvtra.catalog.dto...`) inside `@Query` annotations because JPA syntax demands it.

# Frontend UI & API Lookup Rules
- **Không hiển thị ID (UUID / Technical ID) thô cho người dùng:** Trên giao diện (dropdown, select, autocomplete, bảng), người dùng chỉ được nhìn thấy Tên (Name), Mã nghiệp vụ (Code / SKU / Barcode). UUID chỉ được dùng làm giá trị `value` ngầm.
- **Quy chuẩn gọi API Lookup giữa các Service:**
  - Khi cần dữ liệu danh mục để phục vụ dropdown/select từ service khác: Phải tìm xem service đó có endpoint `lookup` (chỉ trả về `id`, `name`, `code`) hay chưa.
  - Nếu chưa có: Tạo mới API lookup tinh gọn ở backend service tương ứng, không gọi API `findAll` cồng kềnh đầy đủ quan hệ để lấy dropdown.

# Service Code Graph & Architecture Rules
- Trước khi thực hiện tác vụ trên bất kỳ service nào (`Catalog-Service`, `Inventory-Service`, `Order-Service`,...):
  - Agent **bắt buộc phải đọc Code Graph** của service tại `.local/codegraph/<service-name>.md` hoặc tự động quét controllers, service interfaces, entities, external clients để nắm trọn vẹn quan hệ kiến trúc, không được phỏng đoán.


