# Quy tắc cho agent / người mới

## Phạm vi

- Đây là một app duy nhất (React + TypeScript + Vite + Tailwind v4 + CSS Modules), không có workspace con.
- Code dùng chung nằm trong `src/lib` và `src/components`; không tạo package riêng.

## Kiến trúc

- Page đặt trong `src/features/<module>/pages`, component riêng của module trong `src/features/<module>/components`.
- Gọi API trong `src/features/<module>/services/*.api.ts` qua `apiClient` (`src/api/api-client.ts`), không fetch trực tiếp trong component.
- Đường dẫn khai báo trong `src/routes/path.ts`, mount trong `src/app/AppRouter.tsx`.
- Dùng alias `@/` thay cho `../../..` khi import từ `src/lib`.
- Khi nối API thật cho một màn còn mock, xóa luôn file mock tương ứng trong `features/<module>/mocks`.

## Giao diện dùng chung

- Nút, popup, drawer, bảng, phân trang, ô nhập, ô tìm kiếm, huy hiệu trạng thái, menu ⋮ lấy từ `src/components/ui` và `src/components/shared` (bảng tra trong `src/components/README.md`).
- Không tự dựng lớp phủ `fixed inset-0`, không viết `<button className="…">` cho nút hành động thông thường, không chép kiểu dáng của component chung vào feature.
- Thiếu biến thể thì thêm prop vào component chung rồi dùng, không tạo bản sao trong feature.

## POS

- Desktop-first, tối thiểu 1280px, trang phải lấp đầy viewport.
- Phím tắt không được bắt khi đang gõ trong input.

## Xác thực

- Chỉ có một instance Keycloak (`src/auth/keycloak.ts`). Không tự lưu token vào localStorage, không tự viết refresh token.

## Trước khi hoàn thành

```bash
corepack pnpm build
corepack pnpm lint
corepack pnpm test
```

Lint hiện còn lỗi cũ (`no-explicit-any`, `set-state-in-effect`) trong code feature; không thêm lỗi mới.
