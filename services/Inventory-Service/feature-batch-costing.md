# Đề xuất: Giá vốn theo lô (Batch Costing) cho Inventory-Service

> Trạng thái: **Đề xuất** · Ngày: 2026-09-23 · Phạm vi: Inventory-Service (chính), Catalog-Service (chỉ đọc giá bán), pos-web

## 1. Bối cảnh & vấn đề

Dashboard tồn kho (`InventoryTab`, `StockCategoryAnalysisPage`) hiển thị giá vốn / giá trị tồn kho cho **sản phẩm** và **nguyên liệu**:

- Sản phẩm: lấy `product_variants.cost_price` từ Catalog qua Feign — là giá **nhập tay, tĩnh**, không phản ánh giá mua thực tế.
- Nguyên liệu: `InventoryBalanceServiceImpl` gán cứng `costPrice = BigDecimal.ZERO` vì bảng `ingredients` không có giá → dashboard hiện **0**.
- `snapshot_unit_cost` của kiểm kê cũng lấy từ nguồn trên → nguyên liệu = 0.

Nguyên nhân gốc: **luồng nhập kho không ghi nhận giá mua**. `InventoryDocumentLine` và `InventoryBatch` hiện không có cột giá.

### Ràng buộc
- **Không** thêm cột giá vào bảng `ingredients` (Catalog).
- Không trộn khái niệm với `price_lists` / `price_list_items` hiện có (đó là **giá bán**).

## 2. Các phương án đã cân nhắc

| Phương án | Độ chính xác | Công sức | Nhận xét |
|---|---|---|---|
| A. Bảng giá vốn riêng ở Catalog, nhập tay | Thấp | Thấp | Trùng khái niệm với `price_lists`; dễ lệch thực tế; Inventory vẫn phải gọi Feign |
| B. Bảng giá vốn ở Inventory, nhập tay (standard cost) | Trung bình | Thấp | Nhanh, nhưng vẫn là số nhập tay |
| C. Giá bình quân gia quyền theo item, cập nhật khi nhập | Cao | Trung bình | Không phân biệt giá theo lô |
| **D. Giá vốn theo lô (đề xuất)** | **Rất cao** | **Trung bình** | Khớp cấu trúc tồn kho hiện tại; giá bình quân vẫn suy ra được |

### Vì sao chọn D
Mô hình dữ liệu hiện tại **đã quản lý theo lô**:

- `InventoryBalance` có `inventory_batch_id` **NOT NULL** → mọi tồn kho luôn thuộc một lô.
- `InventoryDocumentBatchAllocation`, `InventoryTransactionBatchAllocation` ghi rõ lấy/nhập bao nhiêu từ lô nào.
- `ProductionOrderMaterialAllocation.inventoryBatch` ghi rõ nguyên liệu đã dùng từ lô nào.

⇒ Chỉ cần **gắn giá vào lô**. Giá trị tồn = `Σ(quantity_on_hand × batch.unit_cost)`, tính hoàn toàn trong DB Inventory, không cần Feign sang Catalog.

## 3. Thiết kế dữ liệu

### 3.1 Migration (Flyway, Inventory-Service)

File đề xuất: `V<timestamp>__add_batch_costing.sql`

```sql
-- Lô: giá vốn / 1 đơn vị cơ bản (base unit) của item
ALTER TABLE inventory_batches
  ADD COLUMN unit_cost   DECIMAL(18,4) NULL,
  ADD COLUMN cost_source VARCHAR(30)   NULL;

-- Dòng phiếu: giá người dùng nhập, tính theo unit_id của dòng
ALTER TABLE inventory_document_lines
  ADD COLUMN unit_cost   DECIMAL(18,4) NULL,
  ADD COLUMN line_amount DECIMAL(18,2) NULL;

-- Snapshot giá tại thời điểm phát sinh giao dịch theo lô
ALTER TABLE inventory_transaction_batch_allocations
  ADD COLUMN unit_cost DECIMAL(18,4) NULL;

-- Lịch sử điều chỉnh giá lô
CREATE TABLE IF NOT EXISTS inventory_batch_cost_adjustments (
  id                 BINARY(16)    PRIMARY KEY,
  inventory_batch_id BINARY(16)    NOT NULL,
  old_unit_cost      DECIMAL(18,4) NULL,
  new_unit_cost      DECIMAL(18,4) NOT NULL,
  reason             VARCHAR(500)  NULL,
  created_by BINARY(16) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_by BINARY(16) NULL,
  updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
  version    BIGINT NULL,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_bca_batch FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),
  INDEX idx_bca_batch (inventory_batch_id)
);
```

### 3.2 Quy ước
- **`DECIMAL(18,4)`** cho đơn giá: nguyên liệu tính theo g/ml có đơn giá rất nhỏ (vd 0.35 đ/g).
- **`batch.unit_cost` luôn theo đơn vị cơ bản của item.** Khi duyệt phiếu:
  `unit_cost_base = line.unit_cost / hệ_số_quy_đổi(line.unit_id → base_unit)`.
- **`NULL` ≠ 0**: `NULL` = chưa có giá (cần cảnh báo), `0` = giá 0 thật (hàng tặng, khuyến mại).

### 3.3 Enum mới
`domain/enums/BatchCostSource`:

| Giá trị | Ý nghĩa |
|---|---|
| `PURCHASE` | Từ phiếu nhập mua |
| `PRODUCTION` | Tính từ nguyên liệu tiêu hao của lệnh sản xuất |
| `RETURN` | Khách trả hàng, lấy theo giá xuất gốc |
| `OPENING` | Giá đầu kỳ (import / migrate dữ liệu cũ) |
| `ESTIMATED` | Ước tính (bình quân / giá nhập gần nhất) khi không có nguồn giá |
| `ADJUSTED` | Đã được điều chỉnh thủ công |

### 3.4 Entity
- `InventoryBatch`: thêm `BigDecimal unitCost`, `BatchCostSource costSource`.
- `InventoryDocumentLine`: thêm `BigDecimal unitCost`, `BigDecimal lineAmount`.
- `InventoryTransactionBatchAllocation`: thêm `BigDecimal unitCost`.
- Entity mới `InventoryBatchCostAdjustment` (kế thừa `AuditableEntity`/`BaseEntity` của Common-Service).

## 4. Quy tắc gán giá theo loại chứng từ

| `InventoryDocumentType` | Lô | Quy tắc giá | `cost_source` |
|---|---|---|---|
| `PURCHASE_RECEIPT` | Tạo lô mới | `unit_cost` **bắt buộc** trên dòng; khi duyệt quy đổi về base unit và gán cho mọi lô tách từ dòng đó | `PURCHASE` |
| `PRODUCTION_RECEIPT` | Tạo lô thành phẩm | `Σ(consumed_quantity × batch_nguyên_liệu.unit_cost) / SL thành phẩm nhập` (từ `ProductionOrderMaterialAllocation`) | `PRODUCTION` |
| `TRANSFER_ISSUE` / `TRANSFER_RECEIPT` | **Giữ nguyên lô** | Không đổi — giá đi theo lô | — |
| `CUSTOMER_RETURN_RECEIPT` | Lô cũ hoặc lô mới | Lô cũ: giữ giá. Lô mới: lấy `unit_cost` snapshot của giao dịch bán gốc; không có → ước tính | `RETURN` |
| `ADJUSTMENT_IN`, thừa kiểm kê | Tạo lô điều chỉnh | Ước tính (xem §5.3) | `ESTIMATED` |
| `SALE_ISSUE`, `PRODUCTION_ISSUE`, `CUSTOM_BUNDLE_ISSUE`, `WRITE_OFF`, `SUPPLIER_RETURN`, `ADJUSTMENT_OUT` | Trừ lô có sẵn | Snapshot `batch.unit_cost` → `transaction_batch_allocation.unit_cost` | — |

### Quy tắc nghiệp vụ bổ sung
1. **Giá lô bất biến sau khi phiếu nhập đã duyệt.** Sửa giá chỉ qua chức năng "Điều chỉnh giá lô" (§5.4); các giao dịch xuất đã snapshot **không** bị tính lại.
2. **Chỉ tính giá cho số lượng chấp nhận** (`accepted_quantity`) khi phiếu có QC. `line_amount = accepted_quantity × unit_cost`.
3. **Chi phí phụ** (vận chuyển, thuế nhập): giai đoạn 1 chưa phân bổ. Giai đoạn sau có thể thêm `additional_cost` ở header phiếu, phân bổ theo tỷ lệ giá trị dòng.
4. **Sản xuất:** nếu có lô nguyên liệu chưa có giá → dùng giá ước tính cho lô đó, đánh dấu thành phẩm `ESTIMATED` và log cảnh báo.
5. **Chọn lô khi xuất** giữ nguyên logic hiện tại (FEFO/FIFO). Giá xuất = giá của lô được chọn → phương pháp **đích danh theo lô**.

## 5. Thiết kế service

### 5.1 `BatchCostService` (package mới `costing/`)

```
costing/
  controller/BatchCostController
  dto/request/{AdjustBatchCostRequest}
  dto/response/{BatchCostResponse, ItemCostSummaryResponse}
  repository/InventoryBatchCostAdjustmentRepository
  service/BatchCostService
  service/impl/BatchCostServiceImpl
```

API nội bộ:

| Method | Mô tả | Dùng ở |
|---|---|---|
| `assignCost(InventoryBatch, BigDecimal unitCostBase, BatchCostSource)` | Gán giá cho lô vừa tạo | Mọi luồng nhập |
| `toBaseUnitCost(BigDecimal lineUnitCost, UUID lineUnitId, InventoryItem)` | Quy đổi đơn giá dòng → base unit | Duyệt phiếu nhập |
| `snapshotCost(InventoryTransactionBatchAllocation)` | Copy `batch.unitCost` vào allocation | Mọi luồng xuất |
| `computeProductionCost(ProductionOrderLine)` | Tính giá thành phẩm | Nhập thành phẩm |
| `estimateCost(UUID inventoryItemId)` | Bình quân tồn hiện tại → giá nhập mua gần nhất → `product_variants.cost_price` (nếu là sản phẩm) → `null` | Điều chỉnh, fallback |
| `adjustCost(UUID batchId, AdjustBatchCostRequest)` | Điều chỉnh thủ công + ghi lịch sử | API |

### 5.2 Điểm tích hợp trong code hiện tại

| File | Thay đổi |
|---|---|
| `document/service/impl/InventoryDocumentServiceImpl` | Validate `unitCost` bắt buộc với `PURCHASE_RECEIPT`; khi duyệt gọi `assignCost`; snapshot giá khi tạo allocation xuất |
| `movement/service/InventoryTransactionServiceImpl` | Gọi `snapshotCost` khi tạo `InventoryTransactionBatchAllocation`; lô tạo mới → `assignCost` |
| `production/service/impl/ProductionOrderServiceImpl` | Khi hoàn thành: `computeProductionCost` → `assignCost(..., PRODUCTION)` |
| `customerreturn/service/impl/CustomerReturnInspectionServiceImpl` | Giá lô trả về theo snapshot giao dịch bán |
| `counting/service/InventoryAdjustmentServiceImpl` | Lô điều chỉnh tăng → `estimateCost` + `ESTIMATED` |
| `counting/service/InventoryTakeServiceImpl#snapshotUnitCosts` | Lấy giá bình quân theo lô (§6) thay cho Catalog |
| `stock/service/InventoryBalanceServiceImpl` | Cả nhánh INGREDIENT & PRODUCT dùng `avgUnitCost` từ lô; bỏ `costPrice` từ `VariantInventorySummaryResponse` (vẫn giữ `sellingPrice`) |
| `dashboard/service/impl/InventoryDashboardServiceImpl`, `report/service/impl/InventoryReportServiceImpl` | Giá trị tồn / hàng tồn lâu tính từ giá lô |

### 5.3 Ước tính giá (fallback)
Thứ tự:
1. Bình quân gia quyền các lô còn tồn có giá của item.
2. `unit_cost` của lô `PURCHASE` gần nhất (theo `received_at`).
3. Sản phẩm: `product_variants.cost_price` (Feign, chỉ dùng như dữ liệu dự phòng).
4. `null` → cảnh báo.

### 5.4 Điều chỉnh giá lô
- `PATCH /api/inventory/batches/{id}/cost` — body `{ newUnitCost, reason }`.
- Phân quyền: chỉ vai trò quản lý kho / kế toán.
- Ghi `inventory_batch_cost_adjustments`, set `cost_source = ADJUSTED`, gắn `@UserAction`.
- Không cập nhật lại snapshot của giao dịch xuất đã phát sinh.

## 6. Truy vấn giá trị tồn kho

```sql
SELECT b.inventory_item_id,
       b.location_id,
       SUM(b.quantity_on_hand)                                            AS qty,
       SUM(b.quantity_on_hand * bt.unit_cost)                             AS stock_value,
       SUM(b.quantity_on_hand * bt.unit_cost)
         / NULLIF(SUM(CASE WHEN bt.unit_cost IS NOT NULL
                           THEN b.quantity_on_hand END), 0)               AS avg_unit_cost,
       SUM(CASE WHEN bt.unit_cost IS NULL THEN b.quantity_on_hand ELSE 0 END) AS qty_without_cost
FROM inventory_balances b
JOIN inventory_batches bt ON bt.id = b.inventory_batch_id
WHERE b.quantity_on_hand > 0
GROUP BY b.inventory_item_id, b.location_id;
```

- Viết dưới dạng JPQL projection (`SELECT new org.hvtra...`) hoặc interface projection trong repository.
- `InventoryBalanceViewResponse` thêm: `stockValue`, `hasMissingCost` (`qty_without_cost > 0`).
- Dashboard tổng giá trị tồn = `Σ stock_value`; hiển thị số item thiếu giá để người dùng bổ sung.

## 7. Giá vốn hàng bán (COGS) — lợi ích đi kèm
Vì mọi giao dịch xuất đều snapshot `unit_cost`:

```sql
SELECT SUM(a.quantity * a.unit_cost) AS cogs
FROM inventory_transaction_batch_allocations a
JOIN inventory_transaction_lines l ON l.id = a.transaction_line_id
JOIN inventory_transactions t      ON t.id = l.transaction_id
WHERE t.type = 'SALE_OUT' AND t.created_at BETWEEN :from AND :to;
```

> Lưu ý: `inventory_transactions.type` dùng `InventoryTransactionType` (bán hàng = `SALE_OUT`), khác với `InventoryDocumentType` (`SALE_ISSUE`).

Tương tự cho giá trị hao hụt (`WRITE_OFF`), trả NCC (`SUPPLIER_RETURN`), tiêu hao sản xuất (`PRODUCTION_ISSUE`). Có thể phát outbox event kèm `cogs` cho Reporting-Service sau này.

## 8. Chuyển đổi dữ liệu cũ

| Đối tượng | Cách xử lý |
|---|---|
| Lô sản phẩm hiện có | Job chạy một lần (endpoint admin / `ApplicationRunner` có cờ bật): lấy `cost_price` từ Catalog → gán lô `unit_cost IS NULL`, `cost_source = OPENING`. Không làm được trong SQL migration vì khác DB. |
| Lô nguyên liệu hiện có | **Import giá đầu kỳ** qua `common_importing`: thêm `ImportType.OPENING_BATCH_COST` + `ImportTypeHandler` trong Inventory. Cột Excel: mã nguyên liệu/SKU, mã lô (tuỳ chọn — bỏ trống = áp cho mọi lô còn tồn chưa có giá), đơn giá theo base unit. Luồng draft → submit → approve có sẵn. |
| Trong giai đoạn chuyển đổi | Dashboard hiển thị cảnh báo "chưa có giá vốn" thay vì 0. |

## 9. Frontend (pos-web)

| Màn hình | Thay đổi |
|---|---|
| Form phiếu nhập mua | Cột **Đơn giá** (bắt buộc, theo đơn vị của dòng), **Thành tiền** tự tính, tổng tiền phiếu |
| Chi tiết phiếu / lô | Hiển thị giá vốn, nguồn giá (`cost_source`), lịch sử điều chỉnh |
| Điều chỉnh giá lô | Dialog nhập giá mới + lý do (theo phân quyền) |
| Dashboard `InventoryTab`, `StockCategoryAnalysisPage` | Giá vốn bình quân, giá trị tồn, icon cảnh báo khi `hasMissingCost` |
| Import | Mẫu Excel giá đầu kỳ |

Types cập nhật trong `features/inventory/types/inventory.types.ts`; gọi API qua `apiClient` trong `features/inventory/services`.

## 10. Lộ trình triển khai

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| **1** | Migration + entity + enum `BatchCostSource` | Schema sẵn sàng |
| **2** | `BatchCostService` + tích hợp `PURCHASE_RECEIPT` + cột Đơn giá trên form | Nhập mới có giá |
| **3** | Query §6, sửa `InventoryBalanceServiceImpl`, dashboard, report, kiểm kê | **Dashboard hết hiển thị 0** |
| **4** | Import giá đầu kỳ + job migrate giá sản phẩm | Dữ liệu cũ có giá |
| **5** | Snapshot giá khi xuất (mọi loại xuất) | Có COGS, giá trị hao hụt |
| **6** | Giá thành phẩm từ lệnh sản xuất, trả hàng, điều chỉnh tăng, API điều chỉnh giá lô | Hoàn thiện |
| 7 (tuỳ chọn) | Phân bổ chi phí phụ, outbox event COGS cho Reporting | Mở rộng |

Quality gate mỗi giai đoạn: `mvn -pl services/Inventory-Service -am compile`, `pnpm --filter pos-web build`, `pnpm --filter pos-web lint`.

## 11. Rủi ro & lưu ý

| Rủi ro | Giảm thiểu |
|---|---|
| Người dùng nhập sai đơn giá / sai đơn vị | Hiển thị đơn giá quy đổi về base unit ngay trên form; chức năng điều chỉnh giá lô có lịch sử |
| Lô chưa có giá làm sai giá trị tồn | `NULL` + cờ `hasMissingCost`, không coi là 0 |
| Giá ước tính chưa chính xác | Đánh dấu `ESTIMATED`, cho phép lọc/điều chỉnh |
| Sai số làm tròn khi quy đổi đơn vị | Lưu 4 chữ số thập phân, chỉ làm tròn khi hiển thị/tổng tiền |
| Hiệu năng query giá trị tồn | Đã có index trên `inventory_balances`; nếu cần thêm index `(inventory_item_id, location_id)` và cache ngắn hạn cho dashboard |
| Catalog `cost_price` còn tồn tại gây nhầm | Ghi chú rõ: chỉ là giá tham khảo/fallback; về lâu dài có thể ẩn trên UI Catalog |

## 12. Ngoài phạm vi
- Kế toán tổng hợp (bút toán, sổ cái).
- Đa tiền tệ.
- Tính lại giá hồi tố cho giao dịch đã phát sinh.
