# Component dùng chung

Mọi màn hình lấy giao diện cơ bản từ đây. Muốn đổi kiểu dáng (màu, bo góc, cỡ chữ) thì sửa ở đây,
không sửa riêng trong từng feature.

| Cần gì | Dùng | File |
|---|---|---|
| Nút bấm có chữ | `Button` (`variant`: primary / secondary / danger / dangerOutline / warning / info / ghost / link; `size`: xs / sm / md / search / lg; nhận `ref`; `aria-pressed` cho nút bật/tắt) | `ui/Button.tsx` |
| Nút chỉ có icon | `IconButton` (bắt buộc `aria-label`; `tone="danger"` cho icon xóa) | `ui/Button.tsx` |
| `<Link>` trông như nút | `buttonClassName({ variant, size })` | `ui/button-styles.ts` |
| Huy hiệu trạng thái, nhãn phân loại | `StatusBadge` (`tone`, `dot`, `icon`, `shape`, `size`, `truncate`, `style` cho màu theo dữ liệu) | `ui/StatusBadge.tsx` |
| Hộp thoại có tiêu đề / chân chuẩn | `Dialog`, `DialogContent`, `DialogHeader`, `DialogTitle`, `DialogFooter` | `ui/dialog.tsx` |
| Popup bố cục tự do | `ModalShell` (`scroll={false}` để tự ghim đầu/chân; `ariaLabel`) | `ui/Overlay.tsx` |
| Bảng chi tiết trượt từ phải | `Drawer` (`closeOnEscape`) | `ui/Overlay.tsx` |
| Ô tìm kiếm | `SearchInput` (`controlSize="md"` để cao 40px như ô nhập) | `ui/SearchInput.tsx` |
| Ô nhập của form | `Field` (`as="div"` cho điều khiển tự dựng), `TextInput`, `TextArea`, `NativeSelect` (`controlSize="sm"`, `invalid`, `fullWidth={false}`) | `ui/Field.tsx` |
| Danh sách chọn có tìm kiếm | `Select` | `ui/select.tsx` |
| Thông báo nổi | `Toast` | `ui/Toast.tsx` |
| Bảng danh sách (tải, rỗng, lỗi, thẻ mobile) | `DataTable` (`emptyAction`) | `shared/list/DataTable.tsx` |
| Bảng nhập liệu / bảng trong popup | `Table`, `TableHead`, `TableRow` (`interactive`, `striped`), `TableHeaderCell`, `TableCell` | `ui/Table.tsx` |
| Phân trang | `TablePagination` | `shared/list/TablePagination.tsx` |
| Đầu trang danh sách | `ListPageHeader` (`eyebrow`, `breadcrumb`) | `shared/list/ListPageHeader.tsx` |
| Thanh lọc + chip trạng thái | `ListFilterToolbar` | `shared/list/ListFilterToolbar.tsx` |
| Menu ⋮ cuối dòng | `RowActionMenu` (mục có `icon`, `tone="danger"`) | `shared/RowActionMenu.tsx` |

## Quy tắc

- Không viết lại `fixed inset-0 …` để dựng popup; dùng `Dialog`, `ModalShell` hoặc `Drawer`.
- Không viết `<button className="…">` cho nút hành động thông thường; dùng `Button` / `IconButton`.
  Chỉ giữ `<button>` thô cho điều khiển đặc thù (ô sản phẩm POS, bàn phím số, tab, chip, dòng bảng bấm được).
- Component trong `features/<module>/components` chỉ chứa nghiệp vụ của module (ánh xạ trạng thái → màu, cột bảng…),
  không tự định nghĩa lại kiểu dáng cơ bản.
- Thiếu biến thể thì thêm prop vào component chung, không sao chép component sang feature.
