# HVT POS Web

Ứng dụng bán hàng POS và quản lý hệ thống trong monorepo HVT.

## Chạy dự án

Yêu cầu: Node.js `>= 22`, pnpm `11`.

```bash
cd web-app
corepack enable
pnpm install
pnpm --filter pos-web dev
```

Hoặc:

```bash
cd apps/pos-web
pnpm run dev
```

Kiểm tra trước khi commit:

```bash
pnpm --filter pos-web build
pnpm --filter pos-web lint
```

## Biến môi trường

Tạo `apps/pos-web/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=hvt
VITE_KEYCLOAK_CLIENT_ID=pos-web
```

Không lưu password hoặc client secret trong biến `VITE_*`.

## Cấu trúc source

```text
src/
├── api/          # Cấu hình API client
├── app/          # Router và provider
├── auth/         # Keycloak
├── components/   # Component dùng chung và layout
├── features/     # Các chức năng nghiệp vụ
├── routes/       # Đường dẫn
└── style/        # CSS chung
```

Cấu trúc một feature:

```text
features/<feature>/
├── pages/        # Màn hình
├── components/   # Component của feature
├── services/     # Gọi API
├── types/        # TypeScript type
├── hooks/        # Custom hook
├── store/        # State
└── mock/         # Dữ liệu giả
```

Feature hiện tại:

- `sales`: màn bán hàng POS.
- `manufacturing`: BOM và lệnh sản xuất.

## Route hiện tại

```text
/pos/sales
/system/manufacturing/boms
/system/manufacturing/production-orders
```

Thêm route tại:

- `src/routes/path.ts`
- `src/app/AppRouter.tsx`

## Gọi API

Dùng `apiClient` trong `src/api/api-client.ts`. Không gọi `fetch()` trực tiếp trong component.

```ts
import { apiClient } from "../../../api/api-client";
import type { BomListItem } from "../types/bom.types";

export function getBoms() {
  return apiClient.get<BomListItem[]>("/manufacturing/boms");
}

export function createBom(input: CreateBomInput) {
  return apiClient.post<BomListItem, CreateBomInput>(
    "/manufacturing/boms",
    input
  );
}
```

`apiClient` tự thêm base URL, access token Keycloak, refresh token, timeout và xử lý `401`.

## Quy tắc đặt code

- Code nghiệp vụ đặt trong `features/<feature>`.
- Component dùng chung app đặt trong `components/common` hoặc `components/shared`.
- Component dùng chung nhiều app đặt trong `package/ui`.
- Type dùng chung nhiều app đặt trong `package/types`.
- Không tạo thêm `<BrowserRouter>` vì app đã có router.
