# Hương Vân Trà – POS & quản lý kho (web)

Một ứng dụng React + Vite duy nhất, không còn monorepo. Gồm hai khu vực:

- `/pos/*` – màn bán hàng tại quầy cho thu ngân (hỗ trợ offline, PWA).
- `/system/*`, `/management/*` – back-office: sản phẩm, kho, sản xuất, đơn hàng, khách hàng, khuyến mãi, báo cáo, tài khoản.

Đăng nhập qua Keycloak (realm `hvt`, client `hvtra-pos`). Mọi API đi qua API Gateway.

## Yêu cầu

- Node >= 22 (máy dev đang dùng Node 24 qua nvm: `nvm use 24`)
- pnpm qua corepack: `corepack enable`

## Chạy local

```bash
cp .env.example .env        # sửa VITE_API_BASE_URL nếu gateway không ở localhost:8080
corepack pnpm install
corepack pnpm dev            # http://localhost:5173
```

Lệnh khác:

```bash
corepack pnpm build          # tsc + vite build -> dist/
corepack pnpm preview        # xem thử bản build (PWA chỉ bật ở bản build)
corepack pnpm lint
corepack pnpm test           # vitest, unit test thuần node
corepack pnpm typecheck
```

## Biến môi trường (đọc lúc build)

| Biến | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Base URL API Gateway | `http://localhost:8080/api` hoặc `/api` khi deploy sau nginx |
| `VITE_KEYCLOAK_URL` | URL Keycloak | `https://auth.hvtra.id.vn` |
| `VITE_KEYCLOAK_REALM` | Realm | `hvt` |
| `VITE_KEYCLOAK_CLIENT_ID` | Client public (PKCE) | `hvtra-pos` |

Vite nhúng các biến này vào bundle, nên đổi giá trị là phải build lại.

## Deploy

### Cách 1: Docker (khuyến nghị)

Image build xong chỉ còn nginx phục vụ file tĩnh và proxy `/api/` sang gateway.

```bash
docker build -t hvtra/pos-web \
  --build-arg VITE_API_BASE_URL=/api \
  --build-arg VITE_KEYCLOAK_URL=https://auth.hvtra.id.vn .

docker run -d --name pos-web -p 3000:80 \
  -e API_GATEWAY_URL=http://<ip-gateway>:8080 \
  hvtra/pos-web
```

Hoặc `docker compose up -d --build` (xem `docker-compose.yml`, mặc định map cổng 3000).

`API_GATEWAY_URL` được đọc lúc container khởi động, không cần build lại khi đổi gateway.

### Cách 2: File tĩnh

```bash
corepack pnpm build
# copy thư mục dist/ lên nginx / Caddy / S3...
```

Web server phải:

1. Fallback mọi đường dẫn về `index.html` (SPA).
2. Trả đúng file `/silent-check-sso.html` (Keycloak check SSO).
3. Không cache `sw.js` (service worker), để bản mới được nhận ngay.

Mẫu cấu hình nginx nằm ở `nginx.conf.template`.

### Keycloak

Client `hvtra-pos` phải có `Valid redirect URIs` và `Web origins` chứa domain deploy, ví dụ `https://pos.hvtra.id.vn/*`.

## Cấu trúc thư mục

```
src/
  main.tsx, App.tsx      khởi tạo Keycloak, router, provider
  app/                   router, react-query, trạng thái mạng, PWA
  auth/                  Keycloak, AuthProvider, ProtectedRoute, role
  api/                   apiClient dùng chung (baseUrl, token, retry)
  lib/api-client/        HTTP client thuần (fetch, retry, timeout, ApiError)
  components/            layout POS / System, component dùng chung
  features/<module>/     pages, components, services (gọi API), types, hooks
  routes/path.ts         toàn bộ đường dẫn
  style/index.css        Tailwind v4 + token màu thương hiệu
public/                  favicon, icon PWA, silent-check-sso.html
```

Alias `@/` trỏ tới `src/` (ví dụ `import { ApiError } from "@/lib/api-client"`).

## Lưu ý

- PWA: service worker tự cập nhật (`autoUpdate`). Nếu thấy giao diện cũ sau deploy, tải lại trang một lần.
- Offline: đơn bán khi mất mạng được xếp hàng trong IndexedDB (Dexie) và tự đồng bộ khi có mạng.
- Một số màn kho vẫn dùng dữ liệu mock (chưa nối API): cảnh báo tồn, chính sách đặt hàng lại, phân tích tồn theo nhóm, chất lượng, chuyển kho, trả nhà cung cấp, bundle tự chọn, phiếu kho tổng hợp, dashboard. Xem các file `features/inventory/services/*.api.ts` còn import từ `../mocks/`.
