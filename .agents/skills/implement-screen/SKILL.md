---
name: implement-screen
description: >-
  Use this skill when implementing, extending, or refactoring frontend screens and features
  in web-app/apps/pos-web. Covers business documentation lookup (.local/doc, .local/diagram),
  layout architecture (PosShell vs SystemShell), state management, API integration, and styling.
---

# Implement Screen Skill (Frontend POS & Management)

Tài liệu hướng dẫn quy trình tiêu chuẩn khi phát triển hoặc cập nhật màn hình trong ứng dụng `web-app/apps/pos-web` thuộc hệ thống **Hương Vân Trà**.

---

## 1. Quy trình chuẩn bị & Tra cứu tài liệu nghiệp vụ (Bắt buộc)

Trước khi viết mã giao diện hoặc logic frontend:

1. **Tra cứu tài liệu đặc tả nghiệp vụ trong [`.local/doc/`](file:///Users/nguyenan/Documents/Spring%20boot/Huong-Van-Tra-/.local/doc):**
   - Kiểm tra các file đặc tả yêu cầu (SRS, Word/Docx, PDF) trong `.local/doc/` liên quan đến nghiệp vụ (ví dụ: `Quản lý kho.docx`, `Report_3.0_SRS.docx`).
   - Đọc kỹ các quy tắc nghiệp vụ (Business Rules), ràng buộc dữ liệu (Validation), quyền hạn vai trò người dùng và các luồng xử lý chính.

2. **Tra cứu & sử dụng sơ đồ luồng trong [`.local/diagram/`](file:///Users/nguyenan/Documents/Spring%20boot/Huong-Van-Tra-/.local/diagram):**
   - Kiểm tra xem tính năng có sơ đồ quy trình nghiệp vụ / sequence / state diagram (file `.drawio`, `.drawio.xml`, image) trong `.local/diagram/` không (ví dụ: `Import Ingredient.drawio.xml`).
   - **Nếu có sơ đồ:** Bắt buộc bám sát luồng hoạt động, các bước chuyển đổi trạng thái của đơn hàng/phiếu/lệnh, và luồng tương tác giữa các màn hình.

3. **Tra cứu thiết kế trực quan (Figma / Design Reference):**
   - Tham khảo các ảnh mẫu giao diện trong `web-app/apps/pos-web/design-reference/` hoặc node ID Figma được cung cấp.
   - Giữ nguyên tỷ lệ, bố cục và hệ màu thương hiệu.

---

## 2. Ngôn ngữ hiển thị vs Quy ước đặt tên mã nguồn

> [!IMPORTANT]
> - **Giao diện người dùng (UI Text):** 100% hiển thị bằng **Tiếng Việt** có dấu (Tiêu đề trang, nhãn cột bảng, placeholder, thông báo lỗi/thành công, nút bấm, modal).
> - **Tên mã nguồn kỹ thuật (Code Elements):** 100% bằng **Tiếng Anh** (TypeScript types, tên Component, URL API endpoint, Enum keys, tên biến/hàm).

**Ví dụ:**
- URL: `/system/inventory/locations` -> Tiêu đề UI: `"Quản lý vị trí kệ hàng"`
- URL: `/pos/sales` -> Tiêu đề UI: `"Bán hàng POS"`
- Field: `availableQuantity` -> Nhãn cột: `"Tồn khả dụng"`
- Button: `handleCreateGoodsReceipt` -> Nhãn nút: `"Tạo phiếu nhập"`

---

## 3. Kiến trúc Layout & Workspace

Ứng dụng có hai không gian làm việc chính với layout riêng biệt:

### A. POS Sales Workspace (`PosShell`)
- Áp dụng cho các route bán hàng tại quầy (`/pos/sales`).
- **Đặc điểm:**
  - Desktop-first, chiếm toàn bộ màn hình (`h-dvh`), khóa thanh cuộn cấp trang.
  - Bố cục 3 cột:
    - Sidebar cố định chiều rộng (menu nhanh, chuyển ca, cài đặt).
    - Vùng chọn sản phẩm co giãn linh hoạt (danh mục tabs, search barcode/SKU, grid sản phẩm kèm tồn khả dụng).
    - Panel thanh toán (Checkout panel) có độ rộng cố định ổn định (giỏ hàng, khách hàng, voucher, tổng tiền, tiền thừa, phương thức thanh toán).
  - Tối ưu cho màn hình $\ge 1280\text{px}$.
  - Hỗ trợ phím tắt bàn phím (lưu ý: không bắt phím tắt khi người dùng đang focus gõ trong input).

### B. System Management Workspace (`SystemShell`)
- Áp dụng cho các route quản trị (`/system/*`: danh mục, sản phẩm, tồn kho, BOM, lệnh sản xuất, bảng giá, báo cáo...).
- **Đặc điểm:**
  - Cấu trúc: Sidebar menu điều hướng bên trái + Topbar (Breadcrumbs, Search, User Menu, Workspace Switcher) + Vùng nội dung cuộn dọc.
  - Cấu trúc trang chuẩn:
    1. Header trang (Tiêu đề, Breadcrumbs, các nút hành động chính như Tạo mới, Xuất Excel, Nhập Excel).
    2. Card chỉ số tổng quan (Overview KPI cards nếu có).
    3. Bộ lọc tìm kiếm (Keyword search, Select trạng thái, Range ngày, Reset filter).
    4. Bảng dữ liệu phân trang (Data Table với checkbox chọn nhiều dòng, Badge trạng thái, Thao tác nhanh).
    5. Detail Drawer / Modal chi tiết khi click vào từng dòng.

---

## 4. Cấu trúc thư mục một Feature

Khi tạo mới một phân hệ hoặc tính năng, đặt trong `web-app/apps/pos-web/src/features/<feature_name>/`:

```text
features/<feature_name>/
├── components/       # Các sub-components thuộc riêng feature (Filters, Forms, Modals, Badges)
├── pages/            # Các trang view (ListPage, DetailPage, CreatePage)
├── services/         # API client calls (gọi apiClient từ @hvt/api-client)
├── types/            # TypeScript interfaces & types dành riêng cho feature
├── hooks/            # Custom React hooks (useFilters, useDebounceSearch, ...)
├── utils/            # Helper functions thuần túy (formatters, data mappers)
└── mock/             # Dữ liệu mock phục vụ fallback hoặc kiểm thử độc lập
```

---

## 5. Quy chuẩn API Client & Quản lý State

1. **Gọi API:**
   - Sử dụng `apiClient` từ `@hvt/api-client` (đã tích hợp tự động Token từ Keycloak và header `X-Client-Type: POS_WEB`).
   - Tuyệt đối không gọi `fetch()` hoặc `axios` tự do trong presentational components.
   - Định nghĩa hàm API rõ ràng trong `services/<feature>.api.ts`:
   ```typescript
   import { apiClient } from "@/api/api-client";
   import type { PageResponse, ApiResponse } from "@hvt/types";
   import type { ProductResponse, ProductFilterParams } from "../types/product.types";

   export async function getProducts(params?: ProductFilterParams): Promise<PageResponse<ProductResponse>> {
       return apiClient.get<PageResponse<ProductResponse>>("/catalog/products", {
           query: params
       });
   }
   ```

2. **Quy tắc hiển thị dữ liệu chọn lựa (Dropdown / Select / Autocomplete):**
   > [!IMPORTANT]
   > - **TUYỆT ĐỐI KHÔNG hiển thị ID (UUID / Technical ID thô) cho người dùng chọn trên giao diện UI.**
   > - Người dùng chỉ được nhìn thấy **Tên (Name)**, **Mã nghiệp vụ (Code / SKU / Barcode)** (ví dụ: `Trà Ô Long Túi Lọc (NL-OL-01)`, `Kho tổng Hương Vân Trà`).
   > - `id` (UUID) chỉ được giữ ngầm làm `value` của dropdown/select, không bao giờ render thô lên text hiển thị (tránh các chuỗi như `Item #123e4567-e89b...` hoặc `ID: 00000000-...`).

3. **Quy chuẩn gọi API Lookup giữa các Service:**
   > [!IMPORTANT]
   > - Khi cần dữ liệu phục vụ dropdown/select/autocomplete từ một microservice:
   >   1. **Kiểm tra trước:** Tìm xem service mục tiêu đã có endpoint `lookup` (ví dụ: `GET /api/.../lookup`) hay chưa.
   >   2. **Nếu chưa có:** **Bắt buộc tạo mới API lookup** tinh gọn ở backend service tương ứng, chỉ trả về các trường tối thiểu cần thiết cho dropdown (thường là `id`, `name`, `code` qua `LookupResponse`).
   >   3. **Tránh:** Tuyệt đối không gọi các API `findAll` đầy đủ dữ liệu cồng kềnh với hàng chục quan hệ JPA chỉ để phục vụ render 1 danh sách dropdown nhỏ.

4. **Xử lý các trạng thái giao diện:**
   - **Loading:** Hiển thị skeleton hoặc spinner mượt mà, disable nút submit để tránh duplicate requests.
   - **Empty:** Khi không có dữ liệu, sử dụng component `EmptyState` (`@/components/common/EmptyState` hoặc `@/components/EmptyState`).
   - **Error:** Bắt lỗi từ API và hiển thị toast/alert bằng Tiếng Việt thân thiện.
   - **Pagination:** Quản lý state `page`, `size`, `totalElements`, `totalPages`.

---

## 6. Styling & Thiết kế Giao diện

- Sử dụng **Tailwind CSS v4** kết hợp các theme token được định nghĩa trong `src/style/index.css` (màu sắc thương hiệu, surface, border, gold, shadows).
- Tái sử dụng các UI components dùng chung từ:
  - `src/components/common/`: `AppButton`, `AppInput`, `AppModal`, `MoneyText`, `IconButton`.
  - `src/components/ui/`: `dialog.tsx`, `select.tsx`.
- Định dạng tiền tệ VND: Luôn format rõ ràng (ví dụ: `150.000 ₫` hoặc dùng `MoneyText`).

---

## 7. Quy định về Database & Flyway Migration (Bắt buộc)

> [!IMPORTANT]
> **Bắt buộc viết Migration khi có thay đổi Database:**
> - Bất kỳ thay đổi nào liên quan đến cấu trúc cơ sở dữ liệu (tạo bảng mới, thêm/sửa/xóa cột, thêm foreign key, tạo index) **BẮT BUỘC phải viết file Flyway Migration script** trong `services/<ServiceName>/src/main/resources/db/migration/` (hoặc `migration/`).
> - **Quy tắc đặt tên:** `V<Số_tiếp_theo>__<mo_ta_ngan_gon_bang_tieng_anh>.sql` (ví dụ: `V9__add_ingredient_batch_tracking.sql`, `V15__update_receipt_qc_status.sql`).
> - **Quy chuẩn SQL:**
>   - Đảm bảo có giá trị `DEFAULT` hoặc migration data an toàn nếu thêm cột `NOT NULL` vào bảng đã có dữ liệu.
>   - Tạo `INDEX` cho các trường foreign keys và các cột thường xuyên query/filter/sort (ví dụ: `status`, `created_at`, `sku`, `ingredient_code`).
>   - Không dựa vào `spring.jpa.hibernate.ddl-auto=update` để tự sinh bảng trong môi trường thực tế.

---

## 8. Quality Gates

> [!NOTE]
> **Quy định về Unit Test:** Trong giai đoạn hiện tại, **chưa viết unit test** khi triển khai màn hình/tính năng trừ khi người dùng có yêu cầu rõ ràng. Tập trung tối đa vào hoàn thiện UI, luồng nghiệp vụ, API integration, build và lint.

Trước khi hoàn tất việc triển khai màn hình, chạy các lệnh kiểm tra:

```bash
# 1. Kiểm tra Backend & Migration (nếu có sửa DB/API)
mvn -pl services/<ServiceName> test-compile

# 2. Kiểm tra Frontend Build & Lint (từ thư mục web-app)
pnpm --filter pos-web build
pnpm --filter pos-web lint
```
Đảm bảo không có lỗi TypeScript, biên dịch Java hoặc cảnh báo ESLint nghiêm trọng.
