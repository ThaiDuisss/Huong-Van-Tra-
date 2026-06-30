# Tổng quan Frontend POS

> Cập nhật theo mã nguồn ngày 14/06/2026. Phạm vi tài liệu là `apps/pos-web`
> và các package workspace mà ứng dụng đang khai báo sử dụng.

## 1. Mục tiêu ứng dụng

`pos-web` là frontend nội bộ cho hệ thống Hương Vân Trà, hiện cung cấp hai
không gian làm việc:

- **POS bán hàng:** tìm sản phẩm, tạo giỏ hàng, chọn khách hàng và khuyến mãi,
  tính tiền, chọn phương thức thanh toán.
- **System Management:** quản lý danh mục, sản phẩm, Mặt hàng, bảng giá, BOM và
  lệnh sản xuất.

Ứng dụng được thiết kế desktop-first, chiếm toàn bộ viewport và có hai layout
riêng cho khu vực POS và khu vực quản trị.

## 2. Công nghệ chính

| Nhóm | Công nghệ |
| --- | --- |
| UI | React 19, TypeScript |
| Build tool | Vite 8 |
| Routing | React Router DOM 7 |
| Styling | Tailwind CSS 4, theme token trong CSS |
| Icon | Lucide React |
| Authentication | Keycloak qua package `@hvt/auth` |
| HTTP client | Package workspace `@hvt/api-client` |
| Monorepo | pnpm workspace, Turborepo |
| Kiểm tra mã | ESLint, TypeScript |

Yêu cầu môi trường ở workspace root:

- Node.js `>= 22`
- pnpm `11.5.2`

## 3. Chạy dự án

Từ thư mục `web-app`:

```bash
pnpm install
pnpm --filter pos-web dev
```

Các lệnh kiểm tra:

```bash
pnpm --filter pos-web build
pnpm --filter pos-web lint
```

Build production được tạo trong `apps/pos-web/dist`.

## 4. Biến môi trường

File cấu hình local: `apps/pos-web/.env`.

| Biến | Vai trò |
| --- | --- |
| `VITE_API_BASE_URL` | Base URL của backend API |
| `VITE_KEYCLOAK_URL` | URL máy chủ Keycloak |
| `VITE_KEYCLOAK_REALM` | Realm của hệ thống |
| `VITE_KEYCLOAK_CLIENT_ID` | Client ID dành cho POS frontend |

Không đưa token truy cập hoặc thông tin bí mật vào biến môi trường frontend.
Các biến bắt đầu bằng `VITE_` sẽ được đóng gói và có thể đọc từ trình duyệt.

## 5. Luồng khởi động

```text
src/main.tsx
  -> khởi tạo Keycloak với login-required và PKCE S256
  -> src/App.tsx
  -> AppProviders (BrowserRouter)
  -> AppRouter
  -> page tương ứng với URL
```

- Keycloak client chỉ được tạo một lần tại `src/auth/keycloak.ts`.
- Khi refresh token, `src/api/api-client.ts` dùng token provider của
  `@hvt/auth`.
- HTTP client tự gắn header `X-Client-Type: POS_WEB`.
- Khi API trả về trạng thái chưa xác thực, người dùng được chuyển sang luồng
  đăng nhập Keycloak.

## 6. Routes hiện có

| URL | Màn hình |
| --- | --- |
| `/` | Chuyển hướng tới `/pos/sales` |
| `/pos/sales` | Màn hình bán hàng POS |
| `/system` | Chuyển hướng tới danh mục sản phẩm |
| `/system/categories` | Danh sách danh mục |
| `/system/products` | Danh sách sản phẩm |
| `/system/products/:id` | Chi tiết sản phẩm |
| `/system/products/:productId/variants` | Mặt hàng theo sản phẩm |
| `/system/product-variants` | Danh sách Mặt hàng |
| `/system/product-variants/:id` | Chi tiết Mặt hàng |
| `/system/price-lists` | Danh sách bảng giá |
| `/system/price-lists/:id` | Chi tiết bảng giá |
| `/system/manufacturing/boms` | Danh sách BOM |
| `/system/manufacturing/production-orders` | Danh sách lệnh sản xuất |
| URL không tồn tại | Trang 404 |

Hằng số URL nằm tại `src/routes/path.ts`; cây route thực tế nằm tại
`src/app/AppRouter.tsx`.

## 7. Cấu trúc thư mục

```text
apps/pos-web/
├── design-reference/       Ảnh tham chiếu giao diện POS và quản trị
├── public/                 Tài nguyên tĩnh
├── src/
│   ├── api/                Cấu hình HTTP client dùng chung
│   ├── app/                Provider, router và trang cấp ứng dụng
│   ├── auth/               Keycloak client và auth service
│   ├── components/
│   │   ├── common/         UI dùng chung trong riêng pos-web
│   │   ├── layout/         PosShell và SystemShell
│   │   └── shared/         User menu, profile, workspace switcher
│   ├── features/
│   │   ├── sales/          Miền bán hàng
│   │   ├── product/        Danh mục, sản phẩm, Mặt hàng và bảng giá
│   │   └── manufacturing/  Miền BOM và sản xuất
│   ├── routes/             Hằng số và metadata route
│   ├── style/              Theme và global style
│   ├── App.tsx             Ghép provider với router
│   └── main.tsx            Entry point và bootstrap authentication
├── .env
├── package.json
└── vite.config.ts
```

Quy ước feature hiện tại:

```text
features/<feature>/
├── components/
├── mock/
├── pages/
├── services/
├── types/
└── utils/ hoặc hooks/, store/
```

## 8. Feature bán hàng

Trang chính: `src/features/sales/pages/PosSalePage.tsx`.

Các nhóm chức năng đang có:

- Tìm sản phẩm theo tên, SKU hoặc barcode.
- Thêm, tăng, giảm và xóa sản phẩm khỏi giỏ.
- Giới hạn số lượng theo tồn khả dụng trong dữ liệu sản phẩm.
- Ghi chú pha chế và chọn loại đơn.
- Chọn khách hàng và chương trình giảm giá.
- Tính tạm tính, giảm giá, tổng tiền và tiền thừa.
- Cho phép điều chỉnh tạm tính kèm lý do.
- Chọn thanh toán bằng tiền mặt, chuyển khoản và các phương thức hiển thị khác.
- Mô phỏng trạng thái chờ, đã nhận hoặc hủy của chuyển khoản.
- Có dialog tìm sản phẩm, khách hàng, khuyến mãi, thanh toán và hóa đơn.

State của trang hiện được quản lý trực tiếp bằng `useState` và `useMemo`.
Các file trong `sales/store` và hook phím tắt hiện chưa có implementation và
chưa được nối vào trang.

## 9. Feature sản xuất

Hai trang chính:

- `src/features/manufacturing/pages/BomListPage.tsx`
- `src/features/manufacturing/pages/ProductionOrderListPage.tsx`

Chức năng chung:

- Hiển thị chỉ số tổng quan và danh sách cần chú ý.
- Tìm kiếm và lọc theo điều kiện.
- Chọn nhiều dòng.
- Phân trang và đổi kích thước trang.
- Xem panel chi tiết của BOM hoặc lệnh sản xuất.
- Hiển thị tiến độ và lịch sử của lệnh sản xuất.

Các nút tạo mới, import, export, tùy chỉnh cột và refresh hiện chủ yếu là phần
giao diện, chưa nối đầy đủ với nghiệp vụ backend.

## 10. Feature Product Management

Feature nằm tại `src/features/product` với cấu trúc `pages`, `components`,
`services`, `types`, `hooks`, `utils` và dữ liệu `mock`.

Các phân hệ:

- **Categories:** tìm kiếm, lọc trạng thái, xem chi tiết, tạo/sửa và đổi trạng
  thái danh mục.
- **Products:** lọc theo danh mục, quản lý thông tin nhóm sản phẩm và mở trang
  chi tiết có danh sách Mặt hàng.
- **Product Variants:** quản lý SKU, giá bán, giá vốn, chính sách tồn kho và
  vòng đời độc lập với sản phẩm.
- **Price Lists:** quản lý hiệu lực theo chi nhánh và danh sách mức giá cho
  từng Mặt hàng.

Các service API tương ứng là `category.api.ts`, `product.api.ts`,
`productVariant.api.ts` và `priceList.api.ts`; tất cả dùng `apiClient` hiện có.
UI hiện dùng dữ liệu mẫu để hoạt động độc lập trong giai đoạn backend chưa
được nối vào page.

## 11. Layout và giao diện

### POS layout

`PosShell` dùng grid ba cột:

- Sidebar có chiều rộng cố định.
- Khu sản phẩm co giãn theo không gian còn lại.
- Checkout panel có chiều rộng ổn định theo breakpoint.

Layout dùng `h-dvh` và khóa overflow cấp trang để luôn lấp đầy viewport.

### System layout

`SystemShell` gồm sidebar, topbar và vùng nội dung cuộn dọc. Các trang danh
sách dùng bố cục bộ lọc, bảng và detail panel.

### Theme

`src/style/index.css` định nghĩa:

- Bảng màu xanh thương hiệu.
- Màu nền, surface, border, gold và text của POS.
- Font stack.
- Shadow token cho card và panel.
- Global reset cơ bản.

Code hiện tại dùng Tailwind utility class trực tiếp trong component. Chưa có
file CSS Module trong `src`, dù CSS Modules là một hướng công nghệ được quy
định cho dự án.

## 12. Packages workspace liên quan

`pos-web` khai báo các package dùng chung:

| Package | Vai trò | Trạng thái sử dụng trong source |
| --- | --- | --- |
| `@hvt/auth` | Keycloak, auth service, token provider | Đang sử dụng |
| `@hvt/api-client` | HTTP client có token và timeout | Đang sử dụng |
| `@hvt/ui` | UI tái sử dụng giữa các app | Chưa import |
| `@hvt/types` | Kiểu dữ liệu dùng chung | Chưa import |
| `@hvt/schemas` | Schema validation dùng chung | Chưa import |

Component chỉ phục vụ POS nên tiếp tục đặt trong `apps/pos-web`. Chỉ chuyển
sang `package/ui` khi component thực sự tổng quát và được nhiều app dùng lại.

## 13. Trạng thái tích hợp backend

- Hạ tầng Keycloak và authenticated API client đã được cấu hình.
- Có lời gọi mẫu tới endpoint `GET /manufacturing/boms`.
- Các page bán hàng, Product Management, BOM và lệnh sản xuất hiện vẫn đọc dữ
  liệu từ thư mục `mock`.
- Chưa thấy luồng tạo đơn, hoàn tất thanh toán, trả tiền, ghi nợ, tạo BOM hoặc
  tạo lệnh sản xuất gọi backend.
- Chưa có loading state, error state và cơ chế cache/query cho dữ liệu page.

Vì vậy giao diện hiện phù hợp cho demo và phát triển luồng tương tác, nhưng
chưa phải một luồng nghiệp vụ production hoàn chỉnh.

## 14. Điểm cần lưu ý khi phát triển

- Giữ page trong `features/*/pages`, không dồn page vào `App.tsx`.
- Presentational component không gọi API trực tiếp; đặt lời gọi trong
  `features/*/services`.
- Tái sử dụng type hiện có, không tạo type trùng lặp.
- Không tạo thêm Keycloak client hoặc tự lưu token trong `localStorage`.
- Không viết custom refresh-token bên ngoài `@hvt/auth`.
- Phím tắt POS phải bỏ qua sự kiện khi người dùng đang nhập trong `input`,
  `textarea`, `select` hoặc vùng `contenteditable`.
- Duy trì trải nghiệm từ màn hình rộng 1280px trở lên và có hành vi hợp lý khi
  viewport nhỏ hơn.
- Ảnh lấy từ Figma phải được lưu trong repository, không dùng URL tạm.

## 15. Khoảng trống kỹ thuật hiện tại

- `README.md` vẫn là nội dung mặc định của Vite; file này là tài liệu tổng quan
  thực tế hơn cho `pos-web`.
- Chưa có test script hoặc test frontend trong package `pos-web`.
- Store/reducer/selectors và hook keyboard shortcut của feature sales đang là
  file rỗng.
- `production-order.api.ts` đang rỗng; `bom.api.ts` mới có lời gọi API ở cấp
  module và chưa được page sử dụng.
- Một số action quan trọng vẫn dùng callback rỗng, gồm hoàn tất thanh toán,
  trả tiền và ghi nợ.
- Route metadata trong `src/routes/index.tsx` chưa phải nguồn tạo route chính;
  `AppRouter.tsx` vẫn khai báo route riêng.
- Phần lớn component dùng Tailwind trực tiếp, chưa theo hướng chuyển code giao
  diện sang CSS Modules.

## 16. Hướng triển khai tiếp theo

1. Chuẩn hóa service cho sales, BOM và production order.
2. Thay mock bằng API theo từng page, kèm loading, empty và error state.
3. Hoàn thiện state management cho phiên bán hàng và nối keyboard shortcut.
4. Nối luồng thanh toán, hóa đơn, công nợ và trạng thái giao dịch thật.
5. Bổ sung test cho hàm tính tiền, reducer, filter và các luồng thanh toán.
6. Hợp nhất nguồn khai báo route để tránh metadata và router lệch nhau.
7. Rà soát responsive dưới 1280px và chiến lược CSS Modules theo quy ước dự án.
