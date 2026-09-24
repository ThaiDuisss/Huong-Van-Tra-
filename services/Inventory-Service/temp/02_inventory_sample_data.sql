-- ==============================================================================
-- HUONG VAN TRA - SAMPLE DATA SCRIPT: INVENTORY SERVICE
-- Cau truc kho: 1 Kho tong duy nhat (WAREHOUSE) va ben trong gom cac Ke hang (SHELF)
-- Co so du lieu: inventory_db
-- Luu y: Tat ca UUID phai la chuoi HEX hop le [0-9a-fA-F] de ham uuid_to_bin khong loi
-- ==============================================================================

USE inventory_db;

-- -----------------------------------------------------------------------------
-- 0. XOA DU LIEU CU TRUOC KHI KHOI TAO LAI (CLEAR DATA)
-- Tat ca cac bang lien quan duoc lam sach de dam bao khong xung dot du lieu cu
-- -----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM inventory_balances;
DELETE FROM inventory_batches;
DELETE FROM inventory_items;
DELETE FROM inventory_locations;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. BANG VI TRI KHO (INVENTORY_LOCATIONS)
-- Gồm: 1 Kho Tổng duy nhất (type: WAREHOUSE, parent: NULL)
-- và 8 Kệ hàng con bên trong (type: SHELF, parent_location_id: Kho Tổng)
-- Prefix ID: 10000000-...
-- -----------------------------------------------------------------------------
INSERT INTO inventory_locations (
    id, branch_id, parent_location_id, code, name, type, status, created_at, is_deleted
)
VALUES
    -- Kho Tổng duy nhất của hệ thống
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        NULL,
        'LOC-KHO-TONG',
        'Kho Tổng Hương Vân Trà (Trung tâm)',
        'WAREHOUSE',
        'ACTIVE',
        NOW(),
        FALSE
    ),

    -- Các Kệ (SHELF) nằm bên trong Kho Tổng
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-A1',
        'Kệ A1 - Trà Xanh Đinh Ngọc Tân Cương',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-A2',
        'Kệ A2 - Trà Sen Tây Hồ Bách Diệp',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000013'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-B1',
        'Kệ B1 - Trà Ô Long Thuần Tinh Hoa',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000014'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-B2',
        'Kệ B2 - Trà Cổ Thụ Shan Tuyết Suối Giàng',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000015'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-C1',
        'Kệ C1 - Trà Thảo Mộc Cúc Gừng Mật',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000016'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-D1',
        'Kệ D1 - Kho Nguyên Liệu Búp Chè Thô',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000017'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-D2',
        'Kệ D2 - Kho Nguyên Liệu Hoa & Hương Liệu Sấy',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    ),
    (
        UUID_TO_BIN('10000000-0000-0000-0000-000000000018'),
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        'LOC-KE-QC',
        'Kệ K1 - Khu Vực Cách Ly & Chờ Kiểm Định QC',
        'SHELF',
        'ACTIVE',
        NOW(),
        FALSE
    )
ON DUPLICATE KEY UPDATE
    parent_location_id = VALUES(parent_location_id),
    name = VALUES(name),
    type = VALUES(type),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 2. BANG MAT HANG KHO (INVENTORY_ITEMS)
-- Gom: 12 bien the san pham (PRODUCT_VARIANT) va 7 nguyen lieu (INGREDIENT)
-- Tham chieu chinh xac reference_id (b1000000-..., d1000000-...) tu catalog_db
-- -----------------------------------------------------------------------------
INSERT INTO inventory_items (
    id, reference_id, reference_code, name, item_type, unit_id, status, reorder_point, batch_tracking_enabled, expiry_tracking_enabled, created_at, is_deleted
)
VALUES
    -- 12 Biến thể thành phẩm trà (PRODUCT_VARIANT) - Prefix ID: 20000000-...
    -- Item 1: SKU-TDN-100G (reorder_point = 40, tồn thực tế = 25 -> DƯỚI ĐỊNH MỨC)
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000001'),
        'SKU-TDN-100G', 'Trà Đinh Ngọc Tân Cương Hộp Thiếc 100g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 40, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000002'),
        'SKU-TDN-250G', 'Trà Đinh Ngọc Tân Cương Hộp Bát Giác 250g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 20, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000003'),
        'SKU-TDN-500G-GIFT', 'Trà Đinh Ngọc Hộp Quà Gỗ Hương 500g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 15, TRUE, TRUE, NOW(), FALSE
    ),

    -- Item 4: SKU-TS-BONG-TUOI (reorder_point = 60, tồn thực tế = 35 -> DƯỚI ĐỊNH MỨC)
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000004'),
        'SKU-TS-BONG-TUOI', 'Trà Bông Sen Tây Hồ Sấy Thăng Hoa (Bông)',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000005'),
        'ACTIVE', 60, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000005'),
        'SKU-TS-HOP-200G', 'Trà Ướp Hoa Sen Bách Diệp Truyền Thống Hộp 200g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 25, TRUE, TRUE, NOW(), FALSE
    ),

    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000006'),
        'SKU-OL-150G', 'Trà Ô Long Thuần Hộp Thiếc 150g Hút Chân Không',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 30, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000007'),
        'SKU-OL-300G', 'Trà Ô Long Thuần Thượng Hạng Hộp 300g (2x150g)',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 20, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000008'),
        'SKU-OL-THUNG-24H', 'Trà Ô Long Thuần Thùng Carton 24 Hộp 150g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000002'),
        'ACTIVE', 10, TRUE, TRUE, NOW(), FALSE
    ),

    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000009'),
        'SKU-ST-BANH-357G', 'Bánh Trà Phổ Nhĩ Shan Tuyết Cổ Thụ 357g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000002'),
        'ACTIVE', 15, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000010'),
        'SKU-ST-BACH-100G', 'Bạch Trà Shan Tuyết Cổ Thụ Suối Giàng Hộp 100g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 20, TRUE, TRUE, NOW(), FALSE
    ),

    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000011'),
        'SKU-TM-20TL', 'Trà Cúc Gừng Mật Hộp 20 Túi Lọc Kim Tự Tháp',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'ACTIVE', 50, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('20000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000012'),
        'SKU-TM-LO-120G', 'Trà Cúc Gừng Mật Lọ Thủy Tinh Cao Cấp 120g',
        'PRODUCT_VARIANT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000006'),
        'ACTIVE', 30, TRUE, TRUE, NOW(), FALSE
    ),

    -- 7 Loại nguyên liệu sản xuất trà (INGREDIENT) - Prefix ID: 30000000-...
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000001'),
        'ING-BUP-TAN-CUONG', 'Búp chè non Tân Cương tuyển chọn',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 80, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000002'),
        'ING-SEN-BACH-DIEP', 'Gạo sen Bách Diệp Quảng An Tây Hồ',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 30, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000003'),
        'ING-BUP-SHAN-TUYET', 'Búp chè Shan Tuyết cổ thụ 1 tôm 2 lá',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 50, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000004'),
        'ING-BUP-O-LONG', 'Búp chè Ô Long Mộc Châu tươi chọn lọc',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 60, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000005'),
        'ING-HOA-CUC-KHO', 'Hoa cúc vàng sấy lạnh tiệt trùng',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 30, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000006'),
        'ING-GUNG-SAY', 'Gừng lát gió nướng sấy khô',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 25, TRUE, TRUE, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('30000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000007'),
        'ING-CO-NGOT', 'Cỏ ngọt Stevia tự nhiên sấy khô',
        'INGREDIENT', UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        'ACTIVE', 15, TRUE, TRUE, NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    reorder_point = VALUES(reorder_point),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 3. BANG LO HANG (INVENTORY_BATCHES)
-- Prefix ID: 40000000-...
-- -----------------------------------------------------------------------------
INSERT INTO inventory_batches (
    id, inventory_item_id, batch_code, supplier_batch_code, manufacture_date, expiry_date, status, source_type, source_reference_id, received_at, created_at, is_deleted
)
VALUES
    -- Case 1: Lô Bông Sen Tươi CẬN HẠN 5 NGÀY (<= 7 ngày) -> Kích hoạt expiry7Days & FEFO Rất gấp
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000004'),
        'LOT-TS-EXP05D', 'SUP-LOT-SEN-01',
        DATE_SUB(CURRENT_DATE, INTERVAL 60 DAY),
        DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        DATE_SUB(NOW(), INTERVAL 60 DAY), NOW(), FALSE
    ),

    -- Case 2: Lô Trà Sen Hộp 200g CẬN HẠN 18 NGÀY (<= 30 ngày) -> Kích hoạt expiry30Days & FEFO Gấp
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000005'),
        'LOT-TS-EXP18D', 'SUP-LOT-SEN-02',
        DATE_SUB(CURRENT_DATE, INTERVAL 90 DAY),
        DATE_ADD(CURRENT_DATE, INTERVAL 18 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        DATE_SUB(NOW(), INTERVAL 90 DAY), NOW(), FALSE
    ),

    -- Case 3: Lô Nguyên liệu Hoa cúc CẬN HẠN 24 NGÀY (<= 30 ngày) -> Kích hoạt expiry30Days
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000005'),
        'LOT-HC-EXP24D', 'SUP-LOT-CUC-03',
        DATE_SUB(CURRENT_DATE, INTERVAL 180 DAY),
        DATE_ADD(CURRENT_DATE, INTERVAL 24 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        DATE_SUB(NOW(), INTERVAL 180 DAY), NOW(), FALSE
    ),

    -- Case 4: Lô Trà Đinh Ngọc ĐÃ HẾT HẠN CHƯA TIÊU HỦY (hết hạn 8 ngày trước) -> Kích hoạt expiredUnhandled
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        'LOT-TDN-EXPIRED-OLD', 'SUP-LOT-DINH-OLD',
        DATE_SUB(CURRENT_DATE, INTERVAL 370 DAY),
        DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY),
        'EXPIRED', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        DATE_SUB(NOW(), INTERVAL 370 DAY), NOW(), FALSE
    ),

    -- Case 5: Lô Nguyên liệu Chè Shan Tuyết ĐANG CÁCH LY KIỂM ĐỊNH QUÁ HẠN SLA 7 NGÀY (nhận 14 ngày trước) -> Kích hoạt quarantineOverSla
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000003'),
        'LOT-SHAN-SLA-DELAY', 'SUP-LOT-SHAN-RAW',
        DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY),
        DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY),
        'QUARANTINED', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        DATE_SUB(NOW(), INTERVAL 14 DAY), NOW(), FALSE
    ),

    -- Case 6..15: Các lô hàng bình thường còn hạn dài (6 tháng - 2 năm)
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        'LOT-TDN-2026-NEW', 'SUP-DINH-2026',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 360 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000002'),
        'LOT-TDN250-2026', 'SUP-DINH-2026B',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 360 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000003'),
        'LOT-TDN-GIFT-2026', 'SUP-DINH-GIFT',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 360 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000006'),
        'LOT-OL150-2026', 'SUP-OL-2026',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 720 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000007'),
        'LOT-OL300-2026', 'SUP-OL-2026B',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 720 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000008'),
        'LOT-OLTHUNG-2026', 'SUP-OL-2026C',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 720 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000009'),
        'LOT-STBANH-2026', 'SUP-SHAN-PUER',
        DATE_SUB(CURRENT_DATE, INTERVAL 100 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 1800 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000013'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000010'),
        'LOT-STBACH-2026', 'SUP-SHAN-WHITE',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 720 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000014'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000011'),
        'LOT-TM20TL-2026', 'SUP-TM-2026',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 360 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000015'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000012'),
        'LOT-TMLO-2026', 'SUP-TM-2026B',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 360 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    -- Lô nguyên liệu bình thường
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000016'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000001'),
        'LOT-RAW-TC-2026', 'SUP-MAT-TC',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 180 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    ),
    (
        UUID_TO_BIN('40000000-0000-0000-0000-000000000017'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000002'),
        'LOT-RAW-SEN-2026', 'SUP-MAT-SEN',
        CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY),
        'AVAILABLE', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        NOW(), NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    expiry_date = VALUES(expiry_date),
    manufacture_date = VALUES(manufacture_date);

-- -----------------------------------------------------------------------------
-- 4. BANG SO DU TON KHO (INVENTORY_BALANCES)
-- Prefix ID: 50000000-...
-- Hàng hóa được đặt chính xác tại các KỆ (SHELF) bên trong Kho Tổng:
--  - Kệ A1: Trà Xanh Đinh Ngọc
--  - Kệ A2: Trà Sen Tây Hồ
--  - Kệ B1: Trà Ô Long
--  - Kệ B2: Trà Shan Tuyết
--  - Kệ C1: Trà Thảo Mộc
--  - Kệ D1, D2: Nguyên liệu thô
--  - Kệ QC: Khu vực cách ly kiểm định
-- -----------------------------------------------------------------------------
INSERT INTO inventory_balances (
    id, inventory_item_id, inventory_batch_id, location_id, quantity_on_hand, quantity_reserved, quantity_blocked, created_at, is_deleted
)
VALUES
    -- KỆ A2: Trà Bông Sen tươi (cận hạn 5 ngày)
    -- Tồn 35, đang tạm giữ 5, khả dụng 30. (reorder_point = 60 -> DƯỚI ĐỊNH MỨC)
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000012'),
        35.00, 5.00, 0.00, NOW(), FALSE
    ),

    -- KỆ A2: Trà Sen 200g (cận hạn 18 ngày)
    -- Tồn 80, đang tạm giữ 10, khả dụng 70
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000012'),
        80.00, 10.00, 0.00, NOW(), FALSE
    ),

    -- KỆ D2: Nguyên liệu Hoa cúc (cận hạn 24 ngày)
    -- Tồn 60 kg, khả dụng 60 kg
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000017'),
        60.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ A1: Trà Đinh cũ (ĐÃ HẾT HẠN)
    -- Tồn 12, bị khóa toàn bộ 12 (blocked = 12), khả dụng = 0
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        12.00, 0.00, 12.00, NOW(), FALSE
    ),

    -- KỆ QC (Khu cách ly): Chè Shan Tuyết (ĐANG CÁCH LY QUÁ HẠN SLA)
    -- Tồn 150 kg, bị khóa toàn bộ 150 kg (blocked = 150), khả dụng = 0
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000018'),
        150.00, 0.00, 150.00, NOW(), FALSE
    ),

    -- KỆ A1: Trà Đinh 100g (lô mới)
    -- Tồn 25, tạm giữ 2, khả dụng 23 (reorder_point = 40 -> DƯỚI ĐỊNH MỨC)
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        25.00, 2.00, 0.00, NOW(), FALSE
    ),

    -- KỆ A1: Trà Đinh 250g
    -- Tồn 120, tạm giữ 15, khả dụng 105
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        120.00, 15.00, 0.00, NOW(), FALSE
    ),

    -- KỆ A1: Trà Đinh Quà Gỗ 500g
    -- Tồn 45, khả dụng 45
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        45.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ B1: Trà Ô Long 150g
    -- Tồn 150, tạm giữ 20, khả dụng 130
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000013'),
        150.00, 20.00, 0.00, NOW(), FALSE
    ),

    -- KỆ B1: Trà Ô Long 300g
    -- Tồn 90, khả dụng 90
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000013'),
        90.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ B1: Trà Ô Long Thùng 24H
    -- Tồn 30, tạm giữ 2, khả dụng 28
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000013'),
        30.00, 2.00, 0.00, NOW(), FALSE
    ),

    -- KỆ B2: Bánh Trà Phổ Nhĩ 357g
    -- Tồn 55, tạm giữ 5, khả dụng 50
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000014'),
        55.00, 5.00, 0.00, NOW(), FALSE
    ),

    -- KỆ B2: Bạch Trà Shan Tuyết 100g
    -- Tồn 70, khả dụng 70
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000013'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000013'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000014'),
        70.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ C1: Trà Cúc Gừng Mật Túi Lọc
    -- Tồn 200, tạm giữ 30, khả dụng 170
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000014'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000014'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000015'),
        200.00, 30.00, 0.00, NOW(), FALSE
    ),

    -- KỆ C1: Trà Cúc Gừng Mật Lọ Thủy Tinh
    -- Tồn 85, khả dụng 85
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000015'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000015'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000015'),
        85.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ D1: Nguyên liệu Búp chè Tân Cương tươi
    -- Tồn 350 kg, khả dụng 350 kg
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000016'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000016'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000016'),
        350.00, 0.00, 0.00, NOW(), FALSE
    ),

    -- KỆ D2: Nguyên liệu Gạo sen Bách Diệp
    -- Tồn 45 kg, khả dụng 45 kg
    (
        UUID_TO_BIN('50000000-0000-0000-0000-000000000017'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('40000000-0000-0000-0000-000000000017'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000017'),
        45.00, 0.00, 0.00, NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    quantity_on_hand = VALUES(quantity_on_hand),
    quantity_reserved = VALUES(quantity_reserved),
    quantity_blocked = VALUES(quantity_blocked);

-- -----------------------------------------------------------------------------
-- 5. BANG CHUNG TU KHO (INVENTORY_DOCUMENTS & LINES)
-- Prefix ID Documents: 60000000-...
-- Prefix ID Document Lines: 70000000-...
-- -----------------------------------------------------------------------------
INSERT INTO inventory_documents (
    id, document_code, document_type, status, branch_id, location_id, source_location_id, destination_location_id,
    reference_type, reference_id, note, created_at, is_deleted
)
VALUES
    -- Doc 1: Phiếu nhập mua ban đầu vào Kho Tổng (ĐÃ HẠCH TOÁN - POSTED)
    (
        UUID_TO_BIN('60000000-0000-0000-0000-000000000001'),
        'DOC-PNK-2026-001', 'PURCHASE_RECEIPT', 'POSTED',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        NULL, UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        'PURCHASE_ORDER', 'PO-HVT-2026-88',
        'Nhập kho lô trà và nguyên liệu vụ thu 2026 vào Kho Tổng',
        NOW(), FALSE
    ),

    -- Doc 2: Phiếu chuyển kệ nội bộ CHỜ DUYỆT (PENDING_APPROVAL) -> Kích hoạt pendingDocumentsCount
    (
        UUID_TO_BIN('60000000-0000-0000-0000-000000000002'),
        'DOC-CK-2026-002', 'TRANSFER_ISSUE', 'PENDING_APPROVAL',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000012'),
        'STOCK_TRANSFER', 'TR-2026-0901',
        'Chuyển điều phối trà Đinh Ngọc từ Kệ A1 sang Kệ A2 để tối ưu sắp xếp kho',
        NOW(), FALSE
    ),

    -- Doc 3: Phiếu XUẤT TIÊU HỦY CHỜ DUYỆT (WRITE_OFF & PENDING_APPROVAL) -> Kích hoạt pendingDisposalApprovals
    (
        UUID_TO_BIN('60000000-0000-0000-0000-000000000003'),
        'DOC-XH-2026-003', 'WRITE_OFF', 'PENDING_APPROVAL',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'), NULL,
        'MANUAL', 'WO-OLD-LOT-01',
        'Đề nghị tiêu hủy 12 hộp Trà Đinh Ngọc lô cũ LOT-TDN-EXPIRED-OLD tại Kệ A1 đã hết hạn',
        NOW(), FALSE
    ),

    -- Doc 4: Phiếu xuất sản xuất (PRODUCTION_ISSUE) ĐÃ HẠCH TOÁN -> Kích hoạt Báo cáo hao hụt sản xuất
    (
        UUID_TO_BIN('60000000-0000-0000-0000-000000000004'),
        'DOC-SX-ISSUE-001', 'PRODUCTION_ISSUE', 'POSTED',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000016'), NULL,
        'PRODUCTION_ORDER', 'MO-2026-09-001',
        'Xuất nguyên liệu từ Kệ D1 & D2 đóng gói trà Đinh Ngọc và Trà Thảo Mộc (có phát sinh hao hụt)',
        NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    note = VALUES(note);

-- Chi tiết dòng chứng từ kho (Document Lines)
INSERT INTO inventory_document_lines (
    id, document_id, line_no, inventory_item_id, unit_id, source_location_id, destination_location_id,
    ordered_quantity, received_quantity, shortage_quantity, excess_quantity, accepted_quantity, rejected_quantity,
    note, created_at, is_deleted
)
VALUES
    -- Dòng cho Doc 2 (Chuyển kệ chờ duyệt)
    (
        UUID_TO_BIN('70000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000002'),
        1,
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000012'),
        20.00, 0.00, 0.00, 0.00, 0.00, 0.00,
        'Chuyển 20 hộp Trà Đinh 100g từ Kệ A1 sang Kệ A2', NOW(), FALSE
    ),

    -- Dòng cho Doc 3 (Xuất hủy chờ duyệt)
    (
        UUID_TO_BIN('70000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000003'),
        1,
        UUID_TO_BIN('20000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'), NULL,
        12.00, 12.00, 0.00, 0.00, 12.00, 0.00,
        'Xuất hủy 12 hộp hết date tại Kệ A1', NOW(), FALSE
    ),

    -- Các dòng cho Doc 4 (Xuất sản xuất: Có độ lệch giữa Kế hoạch định mức vs Thực xuất)
    -- Nguyên liệu 1: Búp chè Tân Cương: Định mức 100 kg, Thực tế xuất 106.8 kg -> Hao hụt 6.8% (VƯỢT NGƯỠNG)
    (
        UUID_TO_BIN('70000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000004'),
        1,
        UUID_TO_BIN('30000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000016'), NULL,
        100.00, 106.80, 0.00, 6.80, 106.80, 0.00,
        'Búp chè Tân Cương hao hụt do sàng lọc cẫng chè già', NOW(), FALSE
    ),
    -- Nguyên liệu 2: Hoa cúc vàng: Định mức 30 kg, Thực tế xuất 31.2 kg -> Hao hụt 4.0% (THEO DÕI)
    (
        UUID_TO_BIN('70000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000004'),
        2,
        UUID_TO_BIN('30000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000017'), NULL,
        30.00, 31.20, 0.00, 1.20, 31.20, 0.00,
        'Hoa cúc sấy tỷ lệ vụn nhẹ', NOW(), FALSE
    ),
    -- Nguyên liệu 3: Gừng lát sấy: Định mức 20 kg, Thực tế xuất 20.3 kg -> Hao hụt 1.5% (BÌNH THƯỜNG)
    (
        UUID_TO_BIN('70000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000004'),
        3,
        UUID_TO_BIN('30000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000017'), NULL,
        20.00, 20.30, 0.00, 0.30, 20.30, 0.00,
        'Gừng lát hao hụt chuẩn', NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    ordered_quantity = VALUES(ordered_quantity),
    received_quantity = VALUES(received_quantity);

-- -----------------------------------------------------------------------------
-- 6. BANG GIAO DICH BIEN DONG KHO (INVENTORY_TRANSACTIONS & LINES)
-- Prefix ID Transactions: 80000000-...
-- Prefix ID Lines: 90000000-...
-- -----------------------------------------------------------------------------
INSERT INTO inventory_transactions (
    id, transaction_code, type, reference_type, reference_id, branch_id, inventory_document_id, created_at, is_deleted
)
VALUES
    (
        UUID_TO_BIN('80000000-0000-0000-0000-000000000001'),
        'TX-PNK-2026-001', 'PURCHASE_RECEIPT', 'INVENTORY_DOCUMENT', 'DOC-PNK-2026-001',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000001'),
        NOW(), FALSE
    ),
    (
        UUID_TO_BIN('80000000-0000-0000-0000-000000000002'),
        'TX-SX-ISSUE-001', 'PRODUCTION_ISSUE', 'INVENTORY_DOCUMENT', 'DOC-SX-ISSUE-001',
        UUID_TO_BIN('00000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('60000000-0000-0000-0000-000000000004'),
        NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    type = VALUES(type);

INSERT INTO inventory_transaction_lines (
    id, transaction_id, inventory_item_id, location_id, quantity, before_quantity, after_quantity, created_at, is_deleted
)
VALUES
    (
        UUID_TO_BIN('90000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('80000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('20000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000011'),
        120.00, 0.00, 120.00, NOW(), FALSE
    ),
    (
        UUID_TO_BIN('90000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('80000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('30000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('10000000-0000-0000-0000-000000000016'),
        -106.80, 456.80, 350.00, NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    quantity = VALUES(quantity);
