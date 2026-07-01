-- 1. Phân loại khách hàng: Cá nhân / Doanh nghiệp
ALTER TABLE customers
    ADD COLUMN customer_type VARCHAR(30) NOT NULL DEFAULT 'INDIVIDUAL'
        AFTER date_of_birth;

-- 2. Thêm ngưỡng số đơn tối thiểu để đạt tier
ALTER TABLE customer_tiers
    ADD COLUMN min_order_count INT UNSIGNED NOT NULL DEFAULT 0
        AFTER min_spending;