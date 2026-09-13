# Quy Trình Kiểm Kê & Điều Chỉnh Tồn Kho (Inventory Counting)

## Mục Lục

- [1. Tổng Quan](#1-tổng-quan)
- [2. Thuật Ngữ](#2-thuật-ngữ)
- [3. Quy Trình Kiểm Kê (Inventory Take)](#3-quy-trình-kiểm-kê-inventory-take)
- [4. Quy Trình Điều Chỉnh (Adjustment)](#4-quy-trình-điều-chỉnh-adjustment)
- [5. Batch Tracking trong Kiểm Kê](#5-batch-tracking-trong-kiểm-kê)
- [6. Mô Tả Thuộc Tính Entity](#6-mô-tả-thuộc-tính-entity)
- [7. API Reference](#7-api-reference)
- [8. Ví Dụ Thực Tế](#8-ví-dụ-thực-tế)

---

## 1. Tổng Quan

Kiểm kê tồn kho là quy trình **đối chiếu số lượng hàng hoá thực tế** tại kho/cửa hàng với **số lượng ghi nhận trên hệ thống**. Mục đích:

- Phát hiện chênh lệch (thất thoát, hao hụt, nhập liệu sai, hàng hỏng).
- Cập nhật tồn kho chính xác theo thực tế.
- Tạo bằng chứng kiểm toán (audit trail) cho mọi thay đổi.

Quy trình gồm **2 giai đoạn chính**:

```
┌─────────────────────┐         ┌─────────────────────┐
│   KIỂM KÊ           │         │   ĐIỀU CHỈNH         │
│   (Inventory Take)   │ ──────▶ │   (Adjustment)       │
│                      │         │                      │
│  Đếm hàng thực tế   │  Tạo   │  Cập nhật tồn kho   │
│  So sánh với hệ thống│  từ    │  theo chênh lệch    │
│  Tính chênh lệch     │ variance│  Tạo giao dịch kho  │
└─────────────────────┘         └─────────────────────┘
```

---

## 2. Thuật Ngữ

### 2.1. Kiểm kê (Inventory Take / Stock Count)

| Thuật ngữ | Tiếng Anh | Giải thích |
|---|---|---|
| **Phiên kiểm kê** | Inventory Take / Inventory Count | Một lần kiểm kê tồn kho, có mã riêng (VD: `CT-20260822-A1B2C3D4`), gắn với một chi nhánh cụ thể |
| **Phạm vi kiểm kê** | Scope Type | Xác định **đếm những gì**: toàn bộ kho, theo vị trí, hay theo danh sách mặt hàng |
| **Snapshot** | Snapshot | "Chụp" số tồn kho trên hệ thống tại thời điểm bắt đầu kiểm kê. Đây là con số **hệ thống ghi nhận**, dùng làm mốc so sánh |
| **Số lượng đếm** | Counted Quantity | Số lượng **thực tế đếm được** bởi nhân viên kiểm kê |
| **Chênh lệch** | Variance | Hiệu số giữa số đếm thực tế và snapshot: `variance = countedQuantity − snapshotQuantity` |
| **Dòng kiểm kê** | Count Line | Một dòng ghi nhận kiểm kê cho **một mặt hàng** tại **một vị trí** (và có thể **một lô hàng**) cụ thể |

### 2.2. Phạm vi kiểm kê (Scope Type)

| Scope | Ý nghĩa | Khi nào dùng |
|---|---|---|
| `FULL` | Kiểm kê **toàn bộ** hàng hoá tại chi nhánh | Kiểm kê định kỳ cuối tháng/quý/năm |
| `LOCATION` | Kiểm kê **theo vị trí** (kệ, tủ, kho con) | Kiểm kê xoay vòng (cycle count) theo khu vực |
| `PARTIAL` | Kiểm kê **theo danh sách** mặt hàng chỉ định | Kiểm kê đột xuất cho mặt hàng nghi ngờ thất thoát |

### 2.3. Batch / Lô hàng

| Thuật ngữ | Tiếng Anh | Giải thích |
|---|---|---|
| **Lô hàng** | Batch | Một nhóm sản phẩm cùng loại được nhập/sản xuất cùng đợt, có mã lô (`batchCode`), ngày sản xuất, hạn sử dụng riêng |
| **Mã lô** | Batch Code | Mã định danh duy nhất của lô, VD: `LOT-2026-01`, `NCC-BATCH-A123` |
| **Hạn sử dụng** | Expiry Date | Ngày hết hạn của lô hàng — dùng cho thực phẩm, dược phẩm, mỹ phẩm... |
| **Tồn kho theo lô** | Batch Balance | Số lượng tồn kho phân bổ **chi tiết theo từng lô** tại từng vị trí, khác với tổng tồn kho (InventoryBalance) |

### 2.4. Điều chỉnh (Adjustment)

| Thuật ngữ | Tiếng Anh | Giải thích |
|---|---|---|
| **Phiếu điều chỉnh** | Adjustment Document | Chứng từ ghi nhận việc tăng/giảm tồn kho dựa trên chênh lệch kiểm kê hoặc lý do khác |
| **Hạch toán** | Post | Hành động **áp dụng thực tế** phiếu điều chỉnh vào tồn kho — cập nhật balance và tạo giao dịch kho |
| **Giao dịch kho** | Inventory Transaction | Bản ghi audit trail, lưu lại `trước − sau` cho mỗi dòng điều chỉnh |
| **Adjustment In** | Adjustment In | Điều chỉnh **tăng** tồn kho (phát hiện hàng thừa so với hệ thống) |
| **Adjustment Out** | Adjustment Out | Điều chỉnh **giảm** tồn kho (phát hiện hàng thiếu so với hệ thống) |

### 2.5. Trạng thái kiểm kê (Inventory Count Status)

| Status | Ý nghĩa | Chuyển từ | Chuyển sang |
|---|---|---|---|
| `DRAFT` | Phiên kiểm kê vừa tạo, chưa bắt đầu | *(khởi tạo)* | `COUNTING` |
| `COUNTING` | Đang tiến hành đếm hàng, snapshot đã chụp | `DRAFT` | `COMPLETED` |
| `COMPLETED` | Đếm xong, chênh lệch đã tính, sẵn sàng tạo phiếu điều chỉnh | `COUNTING` | *(kết thúc)* |

### 2.6. Trạng thái phiếu điều chỉnh (Document Status)

| Status | Ý nghĩa | Chuyển từ | Chuyển sang |
|---|---|---|---|
| `DRAFT` | Phiếu mới tạo, chưa duyệt | *(khởi tạo)* | `APPROVED`, `REJECTED` |
| `APPROVED` | Đã được phê duyệt, chờ hạch toán | `DRAFT` | `POSTED`, `REJECTED` |
| `POSTED` | Đã hạch toán — tồn kho đã cập nhật, giao dịch đã ghi | `APPROVED` | *(kết thúc)* |
| `REJECTED` | Bị từ chối (bởi người duyệt) | `DRAFT`, `APPROVED` | *(kết thúc)* |

---

## 3. Quy Trình Kiểm Kê (Inventory Take)

```
  ① TẠO            ② BẮT ĐẦU           ③ ĐẾM              ④ HOÀN TẤT
┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  DRAFT   │───▶│   COUNTING   │───▶│   COUNTING   │───▶│  COMPLETED   │
│          │    │              │    │              │    │              │
│ Chọn     │    │ Snapshot     │    │ Nhân viên    │    │ Tính variance│
│ scope,   │    │ balance +    │    │ nhập số      │    │ cho tất cả   │
│ branch   │    │ batch balance│    │ đếm thực tế  │    │ dòng         │
│          │    │ generate     │    │              │    │              │
│          │    │ lines        │    │              │    │              │
└──────────┘    └──────────────┘    └──────────────┘    └──────────────┘
POST /            POST /              PUT /               POST /
inventorytakes    {id}/start          {id}/counts         {id}/finish
```

### Bước 1: Tạo phiên kiểm kê (`POST /inventorytakes`)

- Chọn **chi nhánh** (`branchId`) và **phạm vi** (`scopeType`).
- Nếu `LOCATION` → cung cấp danh sách `locationIds`.
- Nếu `PARTIAL` → cung cấp danh sách `inventoryItemIds`.
- Hệ thống tạo phiên với `status = DRAFT`, chưa tạo dòng kiểm kê.

### Bước 2: Bắt đầu đếm (`POST /{id}/start`)

Hệ thống thực hiện **2 loại snapshot song song**:

**a) Snapshot tồn kho tổng (InventoryBalance):**
- Đọc `quantityOnHand` từ `InventoryBalance` cho các item **không có** batch tracking.
- Mỗi balance → 1 dòng kiểm kê (`inventoryBatch = null`).

**b) Snapshot tồn kho theo lô (InventoryBatchBalance):**
- Đọc `quantityOnHand` từ `InventoryBatchBalance` cho các item **có** batch tracking.
- Mỗi batch balance → 1 dòng kiểm kê (`inventoryBatch ≠ null`).
- Nếu một item+location có batch balance, **không tạo thêm dòng tổng** cho item+location đó.

```
InventoryBalance (item A, location X)          → 1 line (no batch)
InventoryBatchBalance (item B, location X, LOT-01) → 1 line (batch LOT-01)
InventoryBatchBalance (item B, location X, LOT-02) → 1 line (batch LOT-02)
                                                   ↑ Không tạo line tổng cho item B + location X
```

- Ghi nhận `snapshotAt` (thời điểm chụp) và `countingStartedAt`.
- Chuyển trạng thái sang `COUNTING`.

### Bước 3: Nhập số đếm (`PUT /{id}/counts`)

- Nhân viên kiểm kê **đi đếm hàng thực tế** rồi nhập vào hệ thống.
- Đối với hàng theo lô, nhân viên đếm **từng lô riêng** (dựa vào `batchCode` và `expiryDate` hiển thị trên mỗi dòng).
- Có thể nhập **từng phần** (không cần đếm xong tất cả cùng lúc).
- Với mỗi dòng, hệ thống **tự động tính chênh lệch**:

```
varianceQuantity = countedQuantity − snapshotQuantity
```

| Kết quả | Ý nghĩa |
|---|---|
| `variance > 0` | Thực tế **nhiều hơn** hệ thống (hàng thừa) |
| `variance < 0` | Thực tế **ít hơn** hệ thống (hàng thiếu/thất thoát) |
| `variance = 0` | Khớp, không cần điều chỉnh |

- Có thể ghi `reasonCode` (mã lý do) và `note` (ghi chú) cho từng dòng.

### Bước 4: Hoàn tất (`POST /{id}/finish`)

- **Yêu cầu**: tất cả dòng kiểm kê phải đã nhập `countedQuantity` (không bỏ sót).
- Hệ thống **recalculate variance** lần cuối cho tất cả dòng.
- Ghi nhận `countingCompletedAt`, chuyển sang `COMPLETED`.
- Phiên kiểm kê kết thúc → sẵn sàng tạo phiếu điều chỉnh.

---

## 4. Quy Trình Điều Chỉnh (Adjustment)

```
  ① TẠO            ② DUYỆT            ③ HẠCH TOÁN
┌──────────┐    ┌──────────────┐    ┌──────────────┐
│  DRAFT   │───▶│   APPROVED   │───▶│    POSTED    │
│          │    │              │    │              │
│ Từ kiểm  │    │ Người có     │    │ Cập nhật     │
│ kê hoặc  │    │ quyền phê    │    │ balance,     │
│ standalone│    │ duyệt       │    │ tạo giao     │
│          │    │              │    │ dịch kho     │
└──────────┘    └──────────────┘    └──────────────┘
      │
      │         ┌──────────────┐
      └────────▶│   REJECTED   │ (từ DRAFT hoặc APPROVED)
                └──────────────┘
```

### Cách 1: Tạo từ kiểm kê (`inventoryTakeId` có giá trị)

- Hệ thống lấy **các dòng có chênh lệch** (`variance ≠ 0`) từ phiên kiểm kê.
- Tự động tạo phiếu điều chỉnh với:
  - `referenceType = STOCK_COUNT`
  - `referenceId = id của phiên kiểm kê`
  - Mỗi dòng có `requestedQuantity = varianceQuantity`
- Phiên kiểm kê được liên kết với phiếu điều chỉnh (qua `adjustmentDocument`).
- Mỗi phiên kiểm kê chỉ tạo được **một phiếu điều chỉnh**.

### Cách 2: Tạo độc lập (standalone)

- Dùng cho các trường hợp điều chỉnh **không qua kiểm kê**: hàng hỏng, cho tặng, phát hiện nhầm lẫn...
- Người dùng tự nhập danh sách dòng với `adjustmentQuantity` (dương = tăng, âm = giảm).
- `referenceType = MANUAL`.

### Phê duyệt (`POST /{id}/approve`)

- Người có quyền (quản lý kho, kế toán) xem xét và phê duyệt.
- Ghi nhận `approvedBy` (ai duyệt) và `approvedAt` (duyệt lúc nào).

### Hạch toán (`POST /{id}/post`)

Đây là bước **quan trọng nhất** — thay đổi tồn kho thực sự:

1. **Lock balance**: Khoá bản ghi tồn kho bằng `PESSIMISTIC_WRITE` (tránh race condition).
2. **Cập nhật tồn kho**: `quantityOnHand = quantityOnHand + requestedQuantity`.
3. **Validate**: Đảm bảo tồn kho sau điều chỉnh ≥ 0 (không cho phép tồn âm).
4. **Tạo giao dịch kho** (`InventoryTransaction`): Ghi lại `beforeQuantity`, `afterQuantity`, `quantity` cho audit trail.
5. Chuyển phiếu sang `POSTED`.

### Từ chối (`POST /{id}/reject`)

- Có thể từ chối ở trạng thái `DRAFT` hoặc `APPROVED` (chưa hạch toán).
- Phiếu đã `POSTED` **không thể** từ chối (tồn kho đã thay đổi).

---

## 5. Batch Tracking trong Kiểm Kê

### 5.1. Tại sao cần kiểm kê theo lô?

| Vấn đề | Kiểm kê không theo lô | Kiểm kê theo lô |
|---|---|---|
| Độ chính xác | Chỉ biết tổng chênh lệch | Biết chính xác **lô nào** thừa/thiếu |
| Hàng hết hạn | Không phát hiện được | Phát hiện lô sắp/đã hết hạn khi đếm |
| Truy xuất nguồn gốc | Không hỗ trợ | Truy xuất được nhà cung cấp, ngày nhập cho lô bị lỗi |
| FIFO/FEFO | Không kiểm soát | Đảm bảo xuất hàng đúng thứ tự |

### 5.2. Cách hoạt động

```
                    InventoryBalance                InventoryBatchBalance
                    (tồn kho tổng)                  (tồn kho theo lô)
                    ┌───────────────┐               ┌───────────────────┐
                    │ Item A @ Kệ 1 │               │ Item B @ Kệ 1     │
                    │ qty = 100     │               │ LOT-01: qty = 60  │
                    │ (không có lô)  │               │ LOT-02: qty = 40  │
                    └───────┬───────┘               └───────┬───────────┘
                            │                               │
                     ┌──────▼──────┐               ┌────────▼──────────┐
                     │ Count Line  │               │ Count Line (LOT-01)│
                     │ batch: null │               │ batch: LOT-01     │
                     │ snapshot:100│               │ snapshot: 60      │
                     └─────────────┘               ├────────────────────┤
                                                   │ Count Line (LOT-02)│
                                                   │ batch: LOT-02     │
                                                   │ snapshot: 40      │
                                                   └────────────────────┘
```

- **Hàng không theo lô**: 1 dòng kiểm kê per (item × location), `inventoryBatch = null`.
- **Hàng theo lô**: N dòng kiểm kê per (item × location × batch), mỗi dòng có `batchCode` và `expiryDate`.
- Hệ thống tự phân loại khi `start()` — không cần user chỉ định.

### 5.3. Thông tin batch trong response

Khi query API, mỗi dòng kiểm kê trả về:

```json
{
  "id": "line-001",
  "inventoryItemId": "item-B",
  "locationId": "shelf-1",
  "inventoryBatchId": "batch-uuid-01",
  "batchCode": "LOT-2026-01",
  "expiryDate": "2026-09-30",
  "snapshotQuantity": 60,
  "countedQuantity": 58,
  "varianceQuantity": -2,
  "reasonCode": "DAMAGED",
  "note": "2 hộp bị móp"
}
```

- `inventoryBatchId`, `batchCode`, `expiryDate` = `null` cho hàng không theo lô.

---

## 6. Mô Tả Thuộc Tính Entity

### 6.1. InventoryCount (Phiên kiểm kê)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `countCode` | `String` | Mã phiên kiểm kê, unique (VD: `CT-20260822-A1B2C3D4`) |
| `branchId` | `UUID` | Chi nhánh thực hiện kiểm kê |
| `scopeType` | `Enum` | Phạm vi: `FULL`, `LOCATION`, `PARTIAL` |
| `status` | `Enum` | Trạng thái: `DRAFT` → `COUNTING` → `COMPLETED` |
| `snapshotAt` | `Instant` | Thời điểm chụp snapshot tồn kho |
| `countingStartedAt` | `Instant` | Thời điểm bắt đầu đếm |
| `countingCompletedAt` | `Instant` | Thời điểm hoàn tất đếm |
| `approvedBy` | `UUID` | Người phê duyệt (nếu có workflow duyệt) |
| `approvedAt` | `Instant` | Thời điểm phê duyệt |
| `adjustmentDocument` | `InventoryDocument` | Phiếu điều chỉnh liên kết (FK, nullable) |
| `note` | `String` | Ghi chú tự do |
| `lines` | `List` | Danh sách dòng kiểm kê |

### 6.2. InventoryCountLine (Dòng kiểm kê)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `inventoryCount` | `InventoryCount` | Phiên kiểm kê cha |
| `location` | `InventoryLocation` | Vị trí lưu trữ (kệ, kho) |
| `inventoryItem` | `InventoryItem` | Mặt hàng được kiểm kê |
| `inventoryBatch` | `InventoryBatch` | **Lô hàng** (nullable — `null` nếu item không tracking theo lô) |
| `snapshotQuantity` | `BigDecimal(18,2)` | Số tồn kho **hệ thống ghi nhận** tại thời điểm snapshot |
| `countedQuantity` | `BigDecimal(18,2)` | Số lượng **thực tế đếm được** (null cho đến khi nhập) |
| `varianceQuantity` | `BigDecimal(18,2)` | Chênh lệch = `counted − snapshot` (null cho đến khi tính) |
| `reasonCode` | `String(100)` | Mã lý do chênh lệch (VD: `DAMAGED`, `THEFT`, `MISCOUNT`, `EXPIRED`) |
| `note` | `String(500)` | Ghi chú cho dòng kiểm kê |

### 6.3. InventoryBatch (Lô hàng)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `inventoryItem` | `InventoryItem` | Mặt hàng mà lô thuộc về |
| `batchCode` | `String(100)` | Mã lô, unique per item (VD: `LOT-2026-01`) |
| `supplierBatchCode` | `String(100)` | Mã lô từ nhà cung cấp (nullable) |
| `manufactureDate` | `LocalDate` | Ngày sản xuất |
| `expiryDate` | `LocalDate` | Hạn sử dụng |
| `status` | `Enum` | `AWAITING_QC`, `AVAILABLE`, `BLOCKED`, `EXPIRED` |
| `sourceType` | `Enum` | Nguồn nhập: `PURCHASE_ORDER`, `PRODUCTION_ORDER`... |
| `sourceReferenceId` | `String` | ID đơn hàng/lệnh sản xuất nguồn |
| `receivedAt` | `Instant` | Thời điểm nhận hàng |

### 6.4. InventoryBatchBalance (Tồn kho theo lô)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `inventoryBatch` | `InventoryBatch` | Lô hàng |
| `location` | `InventoryLocation` | Vị trí lưu trữ |
| `quantityOnHand` | `BigDecimal(18,2)` | Số lượng hiện có trong lô tại vị trí |
| `quantityReserved` | `BigDecimal(18,2)` | Số lượng đã đặt trước |
| `quantityBlocked` | `BigDecimal(18,2)` | Số lượng bị chặn (chờ QC, hàng lỗi) |

### 6.5. InventoryDocument (Phiếu điều chỉnh)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `documentCode` | `String` | Mã phiếu, unique (VD: `ADJ-20260822-E5F6G7H8`) |
| `documentType` | `Enum` | `ADJUSTMENT_IN` (tăng) hoặc `ADJUSTMENT_OUT` (giảm) |
| `status` | `Enum` | `DRAFT` → `APPROVED` → `POSTED` (hoặc `REJECTED`) |
| `branchId` | `UUID` | Chi nhánh |
| `sourceLocation` | `InventoryLocation` | Vị trí nguồn (nullable) |
| `destinationLocation` | `InventoryLocation` | Vị trí đích (nullable) |
| `referenceType` | `Enum` | `STOCK_COUNT` (từ kiểm kê) hoặc `MANUAL` (độc lập) |
| `referenceId` | `String` | ID tham chiếu (ID phiên kiểm kê hoặc null) |
| `reasonCode` | `String(100)` | Mã lý do chung |
| `note` | `String` | Ghi chú |
| `approvedBy` | `UUID` | Người phê duyệt |
| `approvedAt` | `Instant` | Thời điểm phê duyệt |
| `postedAt` | `Instant` | Thời điểm hạch toán |
| `lines` | `List` | Danh sách dòng điều chỉnh |

### 6.6. InventoryDocumentLine (Dòng điều chỉnh)

| Thuộc tính | Kiểu dữ liệu | Mô tả |
|---|---|---|
| `id` | `UUID` | Mã định danh duy nhất |
| `document` | `InventoryDocument` | Phiếu điều chỉnh cha |
| `lineNo` | `Integer` | Số thứ tự dòng |
| `inventoryItem` | `InventoryItem` | Mặt hàng |
| `unitId` | `UUID` | Đơn vị tính |
| `sourceLocation` | `InventoryLocation` | Vị trí điều chỉnh |
| `requestedQuantity` | `BigDecimal(18,2)` | Số lượng yêu cầu điều chỉnh (dương = tăng, âm = giảm) |
| `processedQuantity` | `BigDecimal(18,2)` | Số lượng đã xử lý (set bằng `requestedQuantity` khi post) |
| `rejectedQuantity` | `BigDecimal(18,2)` | Số lượng bị từ chối (mặc định 0) |
| `note` | `String(500)` | Ghi chú dòng |

---

## 7. API Reference

### 7.1. Inventory Take (`/api/inventory/inventorytakes`)

| # | Method | Path | Mô tả | Input chính |
|---|---|---|---|---|
| 1 | `POST` | `/` | Tạo phiên kiểm kê | `branchId`, `scopeType`, `locationIds?`, `inventoryItemIds?` |
| 2 | `POST` | `/{id}/start` | Bắt đầu (snapshot balance + batch) | *chỉ cần id* |
| 3 | `PUT` | `/{id}/counts` | Nhập số đếm | `lines: [{lineId, countedQuantity, reasonCode?, note?}]` |
| 4 | `POST` | `/{id}/finish` | Hoàn tất | *chỉ cần id* |
| 5 | `GET` | `/` | Danh sách | `branchId?`, `status?`, `scopeType?`, `page`, `size` |
| 6 | `GET` | `/{id}` | Chi tiết | *chỉ cần id* |

### 7.2. Adjustment (`/api/inventory/adjustments`)

| # | Method | Path | Mô tả | Input chính |
|---|---|---|---|---|
| 1 | `POST` | `/` | Tạo phiếu | `branchId`, `inventoryTakeId?`, `lines?` |
| 2 | `POST` | `/{id}/approve` | Phê duyệt | *chỉ cần id* |
| 3 | `POST` | `/{id}/post` | Hạch toán | *chỉ cần id* |
| 4 | `POST` | `/{id}/reject` | Từ chối | *chỉ cần id* |
| 5 | `GET` | `/` | Danh sách | `branchId?`, `status?`, `page`, `size` |
| 6 | `GET` | `/{id}` | Chi tiết | *chỉ cần id* |

---

## 8. Ví Dụ Thực Tế

### Ví dụ: Kiểm kê cuối tháng — có hàng theo lô

**Bước 1: Tạo phiên kiểm kê**

```json
POST /api/inventory/inventorytakes
{
  "branchId": "550e8400-e29b-41d4-a716-446655440001",
  "scopeType": "FULL",
  "note": "Kiểm kê cuối tháng 08/2026"
}
```

**Bước 2: Bắt đầu — hệ thống tự snapshot cả balance lẫn batch**

```json
POST /api/inventory/inventorytakes/abc-123/start
```

Response — hệ thống tạo 5 dòng:

```json
{
  "id": "abc-123",
  "countCode": "CT-20260831-A1B2C3D4",
  "status": "COUNTING",
  "lines": [
    {
      "id": "line-001",
      "inventoryItemId": "item-phukien",
      "locationId": "shelf-A",
      "inventoryBatchId": null,
      "batchCode": null,
      "expiryDate": null,
      "snapshotQuantity": 100,
      "countedQuantity": null,
      "varianceQuantity": null,
      "note": null
    },
    {
      "id": "line-002",
      "inventoryItemId": "item-sua-tuoi",
      "locationId": "shelf-B",
      "inventoryBatchId": "batch-001",
      "batchCode": "LOT-2026-01",
      "expiryDate": "2026-09-30",
      "snapshotQuantity": 50,
      "countedQuantity": null,
      "varianceQuantity": null
    },
    {
      "id": "line-003",
      "inventoryItemId": "item-sua-tuoi",
      "locationId": "shelf-B",
      "inventoryBatchId": "batch-002",
      "batchCode": "LOT-2026-02",
      "expiryDate": "2026-12-31",
      "snapshotQuantity": 30,
      "countedQuantity": null,
      "varianceQuantity": null
    }
  ]
}
```

> **Lưu ý**: `item-phukien` không theo lô → 1 dòng, `batch = null`.
> `item-sua-tuoi` theo lô → 2 dòng riêng cho LOT-01 và LOT-02.

**Bước 3: Nhân viên đếm và nhập kết quả**

```json
PUT /api/inventory/inventorytakes/abc-123/counts
{
  "lines": [
    { "lineId": "line-001", "countedQuantity": 100 },
    { "lineId": "line-002", "countedQuantity": 48, "reasonCode": "DAMAGED", "note": "2 hộp bị rách" },
    { "lineId": "line-003", "countedQuantity": 30 }
  ]
}
```

→ Kết quả variance:
| Dòng | Item | Batch | Snapshot | Counted | **Variance** | Lý do |
|---|---|---|---|---|---|---|
| line-001 | Phụ kiện | *(không lô)* | 100 | 100 | **0** | Khớp |
| line-002 | Sữa tươi | LOT-2026-01 | 50 | 48 | **-2** | Hàng hỏng |
| line-003 | Sữa tươi | LOT-2026-02 | 30 | 30 | **0** | Khớp |

**Bước 4: Hoàn tất → Tạo phiếu điều chỉnh**

Chỉ dòng `line-002` (variance ≠ 0) được đưa vào phiếu điều chỉnh → `ADJUSTMENT_OUT` với `requestedQuantity = -2`.
