# Phân quyền theo permission (Keycloak)

## Mô hình

| Khái niệm | Keycloak | Ví dụ |
| --- | --- | --- |
| Vai trò nghiệp vụ | Realm role (composite) | `ADMIN`, `MANAGER`, `INVENTORY_STAFF`, `SALE_STAFF`, `QC`, `ACCOUNTANT`, `DELIVERY` |
| Permission | Client role của client `hvtra-pos` | `order:create`, `inventory:receive`, `permission:manage` |
| Gán quyền cho vai trò | Composite của realm role chứa client role | `SALE_STAFF` ⊃ `order:create` |
| Gán vai trò cho user | User realm role mapping | user `lan` → `SALE_STAFF` |

Metadata của permission nằm ngay trên client role: `description` = mô tả, attribute `displayName` = tên hiển thị.
Không có bảng DB riêng; Keycloak là nguồn duy nhất. `SUPER_ADMIN` không bao giờ bị API này chỉnh sửa.

Luồng runtime:

1. Keycloak đưa permission hiệu lực (gồm kế thừa qua composite) vào claim `resource_access.hvtra-pos.roles`.
2. Api-Gateway đọc claim đó, relay sang service qua header `X-User-Permissions` (CSV) kèm `X-Permissions-Signature`
   (HMAC riêng, nên service chưa build lại vẫn xác thực được như cũ).
3. `InternalHeaderAuthenticationFilter` (Common-Service) biến mỗi permission thành một `GrantedAuthority` giữ nguyên tên
   và đưa vào `InternalUserPrincipal.permissions()`.
4. Service kiểm tra bằng `SecurityUtils.requirePermission("order:create", fallbackRoles...)`.
   Gateway đã relay header (kể cả rỗng) → chỉ permission được tính. Header vắng (gateway cũ) → tạm chấp nhận
   `fallbackRoles` để không khóa chức năng đang chạy.

## Cấu hình Keycloak (làm tay, một lần)

1. **Client `hvtra-pos`** (client FE, cũng là audience gateway kiểm tra):
   - Tab *Client scopes* → `hvtra-pos-dedicated` → *Scope* → bật **Full scope allowed**
     (để client role của chính nó và role kế thừa vào token).
   - Đảm bảo scope mặc định `roles` còn mapper *client roles* (`resource_access.${client_id}.roles`). Mặc định đã có.
2. **Service account của Identity-Service** (client confidential, đã dùng cho màn Tài khoản):
   Tab *Service account roles* → gán thêm client role của `realm-management`:
   `view-clients`, `manage-clients` (tạo/sửa/xóa client role), `view-realm`, `manage-realm` (sửa composite của realm role),
   `view-users`, `manage-users` (đã có). Không gán `realm-admin`.
3. Không cần tạo client mới. Nếu muốn tách client API riêng, đặt `KEYCLOAK_PERMISSION_CLIENT_ID` ở cả gateway và Identity-Service.

## Biến môi trường

| Biến | Service | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` | Identity | (bắt buộc) | Service account gọi Admin REST API (đã có) |
| `KEYCLOAK_PERMISSION_CLIENT_ID` | Identity, Api-Gateway | `hvtra-pos` | Client chứa client role dùng làm permission |

## Khởi tạo danh mục quyền

`POST /api/identity/permissions/bootstrap` (yêu cầu realm role `ADMIN` hoặc `SUPER_ADMIN`) tạo 21 permission mặc định
(`PermissionCatalog`) còn thiếu và thêm vào composite của từng vai trò theo bộ mặc định. Chỉ thêm, không gỡ.
Trên giao diện: *Cài đặt → Phân quyền → Khởi tạo quyền mặc định*.

Sau bootstrap, admin bấm *Làm mới quyền của tôi* (hoặc đăng nhập lại) để token mới mang `permission:manage`.

## API (Identity-Service, qua gateway `/api/identity`)

| Method | Path | Quyền | Ghi chú |
| --- | --- | --- | --- |
| GET | `/permissions?keyword=` | `permission:manage` hoặc `role:manage` | kèm `assignedRoles`, `builtIn` |
| GET | `/permissions/{name}` | như trên | |
| POST | `/permissions` | `permission:manage` | 409 nếu trùng; tên `module:action` |
| PUT | `/permissions/{name}` | `permission:manage` | chỉ `displayName`, `description`; không đổi tên |
| DELETE | `/permissions/{name}` | `permission:manage` | 409 `PERMISSION_IN_USE` nếu còn trong vai trò hoặc gán trực tiếp |
| POST | `/permissions/bootstrap` | role ADMIN/SUPER_ADMIN | |
| GET | `/roles` | `role:manage` / `user:manage` / `permission:manage` | `editable=false` với vai trò mình đang giữ |
| GET | `/roles/{role}/permissions` | `role:manage` hoặc `permission:manage` | |
| PUT | `/roles/{role}/permissions` | `role:manage` | body `{permissions:[...]}`; chỉ thêm/gỡ phần chênh lệch; cấm sửa vai trò mình đang giữ |
| GET | `/users/{userId}/roles` | `user:manage` | kèm `effectivePermissions` từ Keycloak |
| PUT | `/users/{userId}/roles` | `user:manage` | body `{roles:[...]}`; cấm sửa chính mình, cấm SUPER_ADMIN |
| GET | `/me/permissions` | đăng nhập | `source` = `keycloak` (tươi) hoặc `token` (fallback) |

`SUPER_ADMIN` luôn được phép (tài khoản gốc). Mọi thao tác ghi được ghi nhật ký hoạt động (`@UserAction`).

API đã bắt permission: `POST /api/orders/pos/checkout`, `/pos/offline-checkout`, `/pos/{id}/checkout` → `order:create`
(fallback vai trò `SALE_STAFF/MANAGER/ADMIN/SUPER_ADMIN` khi gateway cũ).

## Token và thời điểm hiệu lực

Access token Keycloak chứa role tại lúc cấp. Đổi vai trò/quyền chỉ có hiệu lực khi user nhận token mới:
FE tự refresh định kỳ (`AuthProvider`, tối đa vài phút) hoặc bấm *Làm mới quyền của tôi* (`keycloak.updateToken(-1)`).
Muốn thu hồi tức thì: khóa tài khoản ở màn Tài khoản (Identity gọi `logout` mọi phiên của user).
`/me/permissions` đọc trực tiếp từ Keycloak nên FE thấy quyền mới ngay cả khi token chưa đổi; backend vẫn theo token
(header do gateway relay) cho tới khi refresh.

## Kiểm tra tay

1. Keycloak: cấu hình như mục trên, deploy Common-Service → Api-Gateway → Identity-Service → Order-Service.
2. Đăng nhập ADMIN, vào *Cài đặt → Phân quyền*, bấm *Khởi tạo quyền mặc định*, rồi *Làm mới quyền của tôi*.
   Keycloak Admin Console → Clients → hvtra-pos → Roles phải thấy 21 role; Realm roles → SALE_STAFF → Associated roles
   phải chứa `order:create`.
3. Tab *Danh mục quyền*: tạo `report:export`, sửa mô tả, xóa (xóa được vì chưa gán). Tạo trùng tên → báo lỗi 409.
4. Tab *Quyền theo vai trò*: chọn SALE_STAFF, bỏ tick `order:create`, lưu. Đăng nhập tài khoản SALE_STAFF (token mới),
   bán hàng ở POS → checkout trả 403 `FORBIDDEN`. Tick lại → bán được.
5. Tab *Vai trò của người dùng*: tìm nhân viên, đổi vai trò, lưu; chọn chính mình → nút lưu ẩn và API trả 403
   `USER_SELF_ROLE_CHANGE` nếu gọi tay.
6. Gọi API không có token qua gateway → 401; token của MANAGER không có `permission:manage` gọi `POST /permissions` → 403.
