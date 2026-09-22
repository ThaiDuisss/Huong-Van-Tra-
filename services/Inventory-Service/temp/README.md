# DỮ LIỆU MẪU NGÀNH TRÀ / CHÈ CHO DASHBOARD HƯƠNG VÂN TRÀ

Thư mục này chứa các script SQL khởi tạo dữ liệu mẫu chuẩn ngành trà cho hệ thống Hương Vân Trà, phục vụ hiển thị và kiểm thử toàn diện các chỉ số trên **Dashboard** (Tổng quan, Kho hàng, Sản xuất, Cảnh báo & Hạn sử dụng).

---

## 1. Cấu trúc tệp tin

| Tệp tin | Cơ sở dữ liệu đích | Mô tả nội dung |
| :--- | :--- | :--- |
| `01_catalog_sample_data.sql` | `catalog_db` | Tạo 5 danh mục, 6 đơn vị tính, 5 sản phẩm trà cao cấp, 12 biến thể (variants) có giá vốn & giá bán, 7 loại nguyên liệu thô. |
| `02_inventory_sample_data.sql` | `inventory_db` | Tạo 1 Kho Tổng trung tâm (`WAREHOUSE`) chứa 8 Kệ hàng con (`SHELF`), 19 mặt hàng kho (items), 17 lô hàng (batches) mô phỏng các mốc date/QC, số dư tồn kho (balances) đặt trên từng kệ, chứng từ kho (documents), và giao dịch kho (transactions). |

---

## 2. Cấu trúc Kho & Kệ (Locations Architecture)

Theo mô hình quản lý thực tế của hệ thống, chỉ có **1 Kho vật lý duy nhất** (`type: 'WAREHOUSE'`) và bên trong đó phân chia thành **các Kệ lưu trữ** (`type: 'SHELF'`, có `parent_location_id` trỏ về Kho Tổng):

```
Kho Tổng Hương Vân Trà [WAREHOUSE] (LOC-KHO-TONG)
 ├── Kệ A1: Trà Xanh Đinh Ngọc Tân Cương [SHELF] (LOC-KE-A1)
 ├── Kệ A2: Trà Sen Tây Hồ Bách Diệp [SHELF] (LOC-KE-A2)
 ├── Kệ B1: Trà Ô Long Thuần Tinh Hoa [SHELF] (LOC-KE-B1)
 ├── Kệ B2: Trà Cổ Thụ Shan Tuyết Suối Giàng [SHELF] (LOC-KE-B2)
 ├── Kệ C1: Trà Thảo Mộc Cúc Gừng Mật [SHELF] (LOC-KE-C1)
 ├── Kệ D1: Kho Nguyên Liệu Búp Chè Thô [SHELF] (LOC-KE-D1)
 ├── Kệ D2: Kho Nguyên Liệu Hoa & Hương Liệu Sấy [SHELF] (LOC-KE-D2)
 └── Kệ K1: Khu Vực Cách Ly & Chờ Kiểm Định QC [SHELF] (LOC-KE-QC)
```

- **Khi lọc theo Kho Tổng:** Backend tự động tổng hợp số dư của cả Kho Tổng và tất cả các Kệ con bên trong (`parentLocation.id == locationId`).
- **Khi hiển thị phân bổ kho (Location breakdown):** Dashboard sẽ liệt kê chi tiết tồn kho theo từng Kệ thực tế.
- **Khi dùng API lookup kệ:** Hệ thống tự động lọc các vị trí có `type = 'SHELF'` thuộc kho tổng.

---

## 3. Thứ tự thực thi

Do `Inventory-Service` tham chiếu UUID của các `product_variants` và `ingredients` từ `Catalog-Service`, bạn cần chạy script theo thứ tự:

### Bước 1: Nạp dữ liệu vào `catalog_db`
```bash
mysql -h 100.93.252.63 -P 3306 -u <catalog_user> -p catalog_db < services/Inventory-Service/temp/01_catalog_sample_data.sql
```

### Bước 2: Nạp dữ liệu vào `inventory_db`
```bash
mysql -h 100.93.252.63 -P 3306 -u inventory_app -p'Hvt@2006' inventory_db < services/Inventory-Service/temp/02_inventory_sample_data.sql
```

---

## 4. Danh sách dữ liệu mẫu đã thiết lập

### A. 5 Sản phẩm & 12 Biến thể (Catalog -> Inventory)

1. **Trà Đinh Ngọc Tân Cương (`PROD-DINH-NGOC`)** - Đặt tại **Kệ A1**
   - `SKU-TDN-100G`: Hộp thiếc 100g (Giá bán: 450.000đ, Giá vốn: 280.000đ) $\rightarrow$ *Tồn kho 25 hộp, định mức 40 hộp (DƯỚI ĐỊNH MỨC).*
   - `SKU-TDN-250G`: Hộp bát giác 250g (Giá bán: 1.050.000đ, Giá vốn: 650.000đ) $\rightarrow$ *Tồn kho 120 hộp.*
   - `SKU-TDN-500G-GIFT`: Hộp quà gỗ hương 500g (Giá bán: 2.300.000đ, Giá vốn: 1.400.000đ) $\rightarrow$ *Tồn kho 45 hộp.*

2. **Trà Sen Tây Hồ Bách Diệp (`PROD-SEN-TAY-HO`)** - Đặt tại **Kệ A2**
   - `SKU-TS-BONG-TUOI`: Bông sen tươi sấy thăng hoa (Giá bán: 75.000đ, Giá vốn: 45.000đ) $\rightarrow$ *Lô cận hạn 5 ngày, tồn 35 bông, định mức 60 bông (DƯỚI ĐỊNH MỨC).*
   - `SKU-TS-HOP-200G`: Hộp 200g ướp truyền thống (Giá bán: 850.000đ, Giá vốn: 520.000đ) $\rightarrow$ *Lô cận hạn 18 ngày, tồn 80 hộp.*

3. **Trà Ô Long Thuần Tinh Hoa (`PROD-OLONG-THUAN`)** - Đặt tại **Kệ B1**
   - `SKU-OL-150G`: Hộp thiếc 150g hút chân không (Giá bán: 180.000đ, Giá vốn: 110.000đ) $\rightarrow$ *Tồn kho 150 hộp.*
   - `SKU-OL-300G`: Hộp 300g thượng hạng (Giá bán: 350.000đ, Giá vốn: 210.000đ) $\rightarrow$ *Tồn kho 90 hộp.*
   - `SKU-OL-THUNG-24H`: Thùng carton 24 hộp 150g (Giá bán: 4.000.000đ, Giá vốn: 2.450.000đ) $\rightarrow$ *Tồn kho 30 thùng.*

4. **Trà Shan Tuyết Cổ Thụ Suối Giàng (`PROD-SHAN-TUYET`)** - Đặt tại **Kệ B2**
   - `SKU-ST-BANH-357G`: Bánh trà Phổ Nhĩ sống 357g (Giá bán: 1.200.000đ, Giá vốn: 750.000đ) $\rightarrow$ *Tồn kho 55 bánh.*
   - `SKU-ST-BACH-100G`: Bạch trà Shan Tuyết 100g (Giá bán: 600.000đ, Giá vốn: 380.000đ) $\rightarrow$ *Tồn kho 70 hộp.*

5. **Trà Thảo Mộc Cúc La Mã & Gừng Mật (`PROD-CUC-GUNG-MAT`)** - Đặt tại **Kệ C1**
   - `SKU-TM-20TL`: Hộp 20 túi lọc kim tự tháp (Giá bán: 120.000đ, Giá vốn: 65.000đ) $\rightarrow$ *Tồn kho 200 hộp.*
   - `SKU-TM-LO-120G`: Lọ thủy tinh 120g cao cấp (Giá bán: 165.000đ, Giá vốn: 95.000đ) $\rightarrow$ *Tồn kho 85 lọ.*

### B. 7 Loại Nguyên Liệu (Ingredients) - Đặt tại Kệ D1, D2 & Kệ QC
1. `ING-BUP-TAN-CUONG`: Búp chè non Tân Cương (kg) $\rightarrow$ *Kệ D1, tồn 350 kg.*
2. `ING-SEN-BACH-DIEP`: Gạo sen Bách Diệp Tây Hồ (kg) $\rightarrow$ *Kệ D2, tồn 45 kg.*
3. `ING-BUP-SHAN-TUYET`: Búp chè Shan Tuyết (kg) $\rightarrow$ *Kệ QC, tồn 150 kg (đang cách ly kiểm định).*
4. `ING-BUP-O-LONG`: Búp chè Ô Long tươi chọn lọc (kg).
5. `ING-HOA-CUC-KHO`: Hoa cúc vàng sấy lạnh (kg) $\rightarrow$ *Kệ D2, tồn 60 kg (cận hạn 24 ngày).*
6. `ING-GUNG-SAY`: Gừng lát gió nướng sấy khô (kg).
7. `ING-CO-NGOT`: Cỏ ngọt Stevia tự nhiên sấy khô (kg).

---

## 5. Các kịch bản kiểm thử Dashboard được kích hoạt

1. **Tổng giá trị kho (`totalInventoryValue`):** Tự động tính chính xác nhờ `cost_price` khớp giữa `product_variants` và `inventory_balances`.
2. **Cảnh báo hàng dưới định mức (`belowReorderCount`):** Kích hoạt cho 2 SKU: `SKU-TDN-100G` và `SKU-TS-BONG-TUOI`.
3. **Cảnh báo hết hạn & cận hạn (Tab Alerts & Quality):**
   - **Hết hạn trong 7 ngày (`expiry7Days`):** Lô `LOT-TS-EXP05D` trên Kệ A2 (còn 5 ngày).
   - **Hết hạn trong 30 ngày (`expiry30Days`):** Lô `LOT-TS-EXP18D` trên Kệ A2 (còn 18 ngày) và `LOT-HC-EXP24D` trên Kệ D2 (còn 24 ngày).
   - **Đã hết hạn chưa xử lý (`expiredUnhandled`):** Lô `LOT-TDN-EXPIRED-OLD` trên Kệ A1 (hết hạn 8 ngày trước, tồn 12 hộp bị khóa `blocked = 12`).
   - **Cách ly quá hạn SLA 7 ngày (`quarantineOverSla`):** Lô `LOT-SHAN-SLA-DELAY` trên Kệ QC (trạng thái `QUARANTINED`, nhận 14 ngày trước).
4. **Phiếu chờ xử lý & Tiêu hủy:**
   - `pendingDocumentsCount`: Phiếu chuyển kệ `DOC-CK-2026-002` ở trạng thái `PENDING_APPROVAL`.
   - `pendingDisposalApprovals`: Phiếu tiêu hủy `DOC-XH-2026-003` (loại `WRITE_OFF`, trạng thái `PENDING_APPROVAL`).
5. **Báo cáo hao hụt sản xuất (Tab Production):**
   - Phiếu xuất sản xuất `DOC-SX-ISSUE-001` ghi nhận hao hụt thực tế: Búp chè Tân Cương hao hụt 6.8% (*Vượt ngưỡng*), Hoa cúc hao hụt 4.0% (*Theo dõi*), Gừng lát hao hụt 1.5% (*Bình thường*).
