Phân tích từ code hiện tại:

## Bảng dữ liệu module Kiểm kê sử dụng

### Ghi / Thay đổi dữ liệu (WRITE)

| Bảng | Thao tác | Khi nào | Service |
|---|---|---|---|
| `inventory_counts` | **INSERT / UPDATE** | Tạo phiên, `start()`, `finish()`, link adjustment | `InventoryTakeServiceImpl` |
| `inventory_count_lines` | **INSERT / UPDATE** | Generate lines khi `start()`, cập nhật `countedQuantity`/`variance` khi đếm | cascade từ `InventoryCount` |
| `inventory_documents` | **INSERT / UPDATE** | Tạo phiếu điều chỉnh, `approve()`, `post()`, `reject()` | `InventoryAdjustmentServiceImpl` |
| `inventory_document_lines` | **INSERT / UPDATE** | Tạo dòng điều chỉnh, `markProcessed()` khi post | cascade từ `InventoryDocument` |
| `inventory_balances` | **UPDATE** | `post()` → cập nhật `quantity_on_hand` (PESSIMISTIC_WRITE lock) | `InventoryAdjustmentServiceImpl` |
| `inventory_transactions` | **INSERT** | `post()` → tạo giao dịch audit trail | `InventoryAdjustmentServiceImpl` |
| `inventory_transaction_lines` | **INSERT** | `post()` → ghi `before/after quantity` per dòng | cascade từ `InventoryTransaction` |

### Chỉ đọc (READ only)

| Bảng | Mục đích | Service |
|---|---|---|
| `inventory_items` | Validate item tồn tại & `ACTIVE` | cả 2 services |
| `inventory_locations` | Validate location tồn tại & `ACTIVE`, lấy `branchId` | cả 2 services |

### Tổng hợp theo flow

```
TẠO KIỂM KÊ        → inventory_counts (INSERT)

START               → inventory_balances (READ - snapshot)
                    → inventory_counts (UPDATE status)
                    → inventory_count_lines (INSERT - generate lines)

ĐẾM                → inventory_count_lines (UPDATE countedQuantity, variance)

FINISH              → inventory_count_lines (UPDATE - recalc variance)
                    → inventory_counts (UPDATE status → COMPLETED)

TẠO ĐIỀU CHỈNH     → inventory_counts (READ variance lines, UPDATE link)
                    → inventory_documents (INSERT)
                    → inventory_document_lines (INSERT)

APPROVE             → inventory_documents (UPDATE status → APPROVED)

POST                → inventory_balances (UPDATE quantity_on_hand) ← THAY ĐỔI TỒN KHO
                    → inventory_document_lines (UPDATE processedQuantity)
                    → inventory_documents (UPDATE status → POSTED)
                    → inventory_transactions (INSERT)
                    → inventory_transaction_lines (INSERT)

REJECT              → inventory_documents (UPDATE status → REJECTED)
```

**Tóm lại**: 7 bảng **ghi/sửa**, 2 bảng **chỉ đọc**. Bảng quan trọng nhất bị thay đổi là `inventory_balances` (chỉ xảy ra ở bước **POST** — hạch toán).