-- ==============================================================================
-- HUONG VAN TRA - SAMPLE DATA SCRIPT: CATALOG SERVICE
-- Muc tieu: Them 5 san pham tra/che, moi san pham 2-3 variants, va 7 loai nguyen lieu
-- Co so du lieu: catalog_db
-- Luu y: Tat ca UUID phai la chuoi HEX hop le [0-9a-fA-F] de ham uuid_to_bin khong loi
-- ==============================================================================

USE catalog_db;

-- -----------------------------------------------------------------------------
-- 0. XOA DU LIEU CU TRUOC KHI KHOI TAO LAI (CLEAR DATA)
-- -----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM barcodes;
DELETE FROM bom_items;
DELETE FROM price_list_items;
DELETE FROM price_lists;
DELETE FROM product_variants;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM ingredients;
DELETE FROM units;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. BANG DON VI TINH (UNITS)
-- Prefix ID: e1000000-...
-- -----------------------------------------------------------------------------
INSERT INTO units (id, name, symbol, precision_scale, `type`, conversion_rate, status, created_at, is_deleted)
VALUES
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'), 'Hộp', 'hộp', 0, 'COUNT', 1.0000, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000002'), 'Gói', 'gói', 0, 'COUNT', 1.0000, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 'Kilogram', 'kg', 2, 'WEIGHT', 1.0000, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000004'), 'Gram', 'g', 1, 'WEIGHT', 1.0000, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000005'), 'Bông', 'bông', 0, 'COUNT', 1.0000, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('e1000000-0000-0000-0000-000000000006'), 'Lọ', 'lọ', 0, 'COUNT', 1.0000, 'ACTIVE', NOW(), FALSE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    symbol = VALUES(symbol),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 2. BANG DANH MUC (CATEGORIES)
-- Prefix ID: c1000000-...
-- -----------------------------------------------------------------------------
INSERT INTO categories (id, category_code, name, default_reorder_point, status, created_at, is_deleted)
VALUES
    (UUID_TO_BIN('c1000000-0000-0000-0000-000000000001'), 'CAT-TRA-XANH', 'Trà Xanh Thái Nguyên', 30, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('c1000000-0000-0000-0000-000000000002'), 'CAT-TRA-SEN', 'Trà Sen Tây Hồ Thượng Hạng', 20, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('c1000000-0000-0000-0000-000000000003'), 'CAT-TRA-OLONG', 'Trà Ô Long Đặc Sản', 25, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('c1000000-0000-0000-0000-000000000004'), 'CAT-TRA-SHAN', 'Trà Cổ Thụ Shan Tuyết', 20, 'ACTIVE', NOW(), FALSE),
    (UUID_TO_BIN('c1000000-0000-0000-0000-000000000005'), 'CAT-TRA-THAO-MOC', 'Trà Thảo Mộc & Hoa Tự Nhiên', 30, 'ACTIVE', NOW(), FALSE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    default_reorder_point = VALUES(default_reorder_point),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 3. BANG SAN PHAM (PRODUCTS) - 5 San pham
-- Prefix ID: f1000000-...
-- -----------------------------------------------------------------------------
INSERT INTO products (id, category_id, product_code, name, description, status, created_at, is_deleted)
VALUES
    (
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('c1000000-0000-0000-0000-000000000001'),
        'PROD-DINH-NGOC',
        'Trà Đinh Ngọc Tân Cương',
        'Thu hoạch từ những búp chè non ngậm sương sớm tại vùng đất Tân Cương, hương thơm cốm non thuần khiết, vị tiền chát hậu ngọt sâu.',
        'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('c1000000-0000-0000-0000-000000000002'),
        'PROD-SEN-TAY-HO',
        'Trà Sen Tây Hồ Bách Diệp',
        'Nghệ thuật ướp trà đỉnh cao thủ công với gạo sen Bách Diệp Quảng An Tây Hồ, đượm đà thanh tao đậm chất Hà Thành.',
        'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('c1000000-0000-0000-0000-000000000003'),
        'PROD-OLONG-THUAN',
        'Trà Ô Long Thuần Tinh Hoa',
        'Trà Ô Long cao nguyên lên men bán phần 30%, hương thơm hoa cỏ mùa xuân, nước vàng óng mượt và vị ngọt bùi dịu êm.',
        'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('c1000000-0000-0000-0000-000000000004'),
        'PROD-SHAN-TUYET',
        'Trà Shan Tuyết Cổ Thụ Suối Giàng',
        'Cây chè cổ thụ hàng trăm năm tuổi trên đỉnh núi cao mờ sương Yên Bái, búp chè phủ lớp lông tuyết trắng bạc quý hiếm.',
        'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('c1000000-0000-0000-0000-000000000005'),
        'PROD-CUC-GUNG-MAT',
        'Trà Thảo Mộc Cúc La Mã & Gừng Mật',
        'Sự kết hợp ấm áp giữa hoa cúc vàng sấy lạnh tiệt trùng, gừng lát nướng thơm và vị ngọt thanh tự nhiên của cỏ ngọt.',
        'ACTIVE', NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 4. BANG BIEN THE SAN PHAM (PRODUCT VARIANTS) - 12 Variants
-- Prefix ID: b1000000-...
-- -----------------------------------------------------------------------------
INSERT INTO product_variants (
    id, product_id, sku, variant_name, selling_price, cost_price, unit_id, inventory_policy, status, created_at, is_deleted
)
VALUES
    -- Product 1: Trà Đinh Ngọc Tân Cương (3 variants)
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000001'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000001'),
        'SKU-TDN-100G', 'Trà Đinh Ngọc Tân Cương Hộp Thiếc 100g',
        450000.00, 280000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000002'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000001'),
        'SKU-TDN-250G', 'Trà Đinh Ngọc Tân Cương Hộp Bát Giác 250g',
        1050000.00, 650000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000003'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000001'),
        'SKU-TDN-500G-GIFT', 'Trà Đinh Ngọc Hộp Quà Gỗ Hương 500g',
        2300000.00, 1400000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),

    -- Product 2: Trà Sen Tây Hồ Bách Diệp (2 variants)
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000004'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000002'),
        'SKU-TS-BONG-TUOI', 'Trà Bông Sen Tây Hồ Sấy Thăng Hoa (Bông)',
        75000.00, 45000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000005'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000005'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000002'),
        'SKU-TS-HOP-200G', 'Trà Ướp Hoa Sen Bách Diệp Truyền Thống Hộp 200g',
        850000.00, 520000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),

    -- Product 3: Trà Ô Long Thuần Tinh Hoa (3 variants)
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000006'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000003'),
        'SKU-OL-150G', 'Trà Ô Long Thuần Hộp Thiếc 150g Hút Chân Không',
        180000.00, 110000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000007'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000003'),
        'SKU-OL-300G', 'Trà Ô Long Thuần Thượng Hạng Hộp 300g (2x150g)',
        350000.00, 210000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000008'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000003'),
        'SKU-OL-THUNG-24H', 'Trà Ô Long Thuần Thùng Carton 24 Hộp 150g',
        4000000.00, 2450000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000002'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),

    -- Product 4: Trà Shan Tuyết Cổ Thụ Suối Giàng (2 variants)
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000009'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000004'),
        'SKU-ST-BANH-357G', 'Bánh Trà Phổ Nhĩ Shan Tuyết Cổ Thụ 357g',
        1200000.00, 750000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000002'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000010'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000004'),
        'SKU-ST-BACH-100G', 'Bạch Trà Shan Tuyết Cổ Thụ Suối Giàng Hộp 100g',
        600000.00, 380000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),

    -- Product 5: Trà Thảo Mộc Cúc La Mã & Gừng Mật (2 variants)
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000011'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000005'),
        'SKU-TM-20TL', 'Trà Cúc Gừng Mật Hộp 20 Túi Lọc Kim Tự Tháp',
        120000.00, 65000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000001'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('b1000000-0000-0000-0000-000000000012'),
        UUID_TO_BIN('f1000000-0000-0000-0000-000000000005'),
        'SKU-TM-LO-120G', 'Trà Cúc Gừng Mật Lọ Thủy Tinh Cao Cấp 120g',
        165000.00, 95000.00,
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000006'),
        'TRACK_STOCK', 'ACTIVE', NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    variant_name = VALUES(variant_name),
    selling_price = VALUES(selling_price),
    cost_price = VALUES(cost_price),
    status = VALUES(status);

-- -----------------------------------------------------------------------------
-- 5. BANG NGUYEN LIEU (INGREDIENTS) - 7 Loai nguyen lieu
-- Prefix ID: d1000000-...
-- -----------------------------------------------------------------------------
INSERT INTO ingredients (id, ingredient_code, name, unit_id, reorder_point, status, created_at, is_deleted)
VALUES
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000001'),
        'ING-BUP-TAN-CUONG', 'Búp chè non Tân Cương tuyển chọn',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 100, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000002'),
        'ING-SEN-BACH-DIEP', 'Gạo sen Bách Diệp Quảng An Tây Hồ',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 30, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000003'),
        'ING-BUP-SHAN-TUYET', 'Búp chè Shan Tuyết cổ thụ 1 tôm 2 lá',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 50, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000004'),
        'ING-BUP-O-LONG', 'Búp chè Ô Long Mộc Châu tươi chọn lọc',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 80, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000005'),
        'ING-HOA-CUC-KHO', 'Hoa cúc vàng sấy lạnh tiệt trùng',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 40, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000006'),
        'ING-GUNG-SAY', 'Gừng lát gió nướng sấy khô',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 30, 'ACTIVE', NOW(), FALSE
    ),
    (
        UUID_TO_BIN('d1000000-0000-0000-0000-000000000007'),
        'ING-CO-NGOT', 'Cỏ ngọt Stevia tự nhiên sấy khô',
        UUID_TO_BIN('e1000000-0000-0000-0000-000000000003'), 20, 'ACTIVE', NOW(), FALSE
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    reorder_point = VALUES(reorder_point),
    status = VALUES(status);
