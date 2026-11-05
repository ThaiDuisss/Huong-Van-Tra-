import { describe, expect, it } from "vitest";

import {
    CATALOG_READ_PATTERN,
    POS_PRODUCT_PATTERN,
    createCatalogRuntimeCaching,
    isCatalogReadUrl
} from "./runtime-caching";

const API = "http://localhost:8080/api";

describe("luật cache danh mục cho service worker", () => {
    it("bypasses cached POS metadata for explicit fresh AI price/stock reads", () => {
        expect(POS_PRODUCT_PATTERN.test(`${API}/pos/products?productId=tea&_fresh=1`)).toBe(false);
        expect(POS_PRODUCT_PATTERN.test(`${API}/orders/pos/products/variant?_fresh=1&other=1`)).toBe(false);
        expect(POS_PRODUCT_PATTERN.test(`${API}/pos/products?productId=tea&_fresh=0`)).toBe(true);
    });
    it("khớp tìm kiếm và chi tiết mặt hàng POS ở cả hai prefix", () => {
        expect(POS_PRODUCT_PATTERN.test(`${API}/pos/products?keyword=tr%C3%A0&page=0&size=20`)).toBe(true);
        expect(POS_PRODUCT_PATTERN.test(`${API}/orders/pos/products?keyword=sen`)).toBe(true);
        expect(POS_PRODUCT_PATTERN.test(`${API}/pos/products/4f2a0b1e-1111-2222-3333-444455556666`)).toBe(true);
        expect(POS_PRODUCT_PATTERN.test(`${API}/orders/pos/products/4f2a0b1e-1111-2222-3333-444455556666/pos-view`)).toBe(true);
    });

    it("khớp catalog đọc nhưng bỏ qua export/import/template", () => {
        expect(CATALOG_READ_PATTERN.test(`${API}/catalog/products?page=0`)).toBe(true);
        expect(CATALOG_READ_PATTERN.test(`${API}/catalog/product-variants/abc`)).toBe(true);
        expect(CATALOG_READ_PATTERN.test(`${API}/catalog/units/export?format=xlsx`)).toBe(false);
        expect(CATALOG_READ_PATTERN.test(`${API}/catalog/products/import/template`)).toBe(false);
        expect(CATALOG_READ_PATTERN.test(`${API}/catalog/ingredients/import`)).toBe(false);
    });

    it("không chạm vào health ping, đơn hàng, khách hàng, thanh toán", () => {
        expect(isCatalogReadUrl("http://localhost:8080/actuator/health")).toBe(false);
        expect(isCatalogReadUrl(`${API}/orders/pos/draft/items`)).toBe(false);
        expect(isCatalogReadUrl(`${API}/orders/pos/checkout`)).toBe(false);
        expect(isCatalogReadUrl(`${API}/orders/backorders?keyword=x`)).toBe(false);
        expect(isCatalogReadUrl(`${API}/customer?keyword=0909`)).toBe(false);
        expect(isCatalogReadUrl(`${API}/payments/abc`)).toBe(false);
        expect(isCatalogReadUrl(`${API}/notifications/stream`)).toBe(false);
    });

    it("cấu hình Workbox chỉ dùng GET, NetworkFirst, RegExp (serialize được)", () => {
        const rules = createCatalogRuntimeCaching();

        expect(rules).toHaveLength(2);
        for (const rule of rules) {
            expect(rule.urlPattern).toBeInstanceOf(RegExp);
            expect(rule.method).toBe("GET");
            expect(rule.handler).toBe("NetworkFirst");
            expect(rule.options.cacheableResponse.statuses).toEqual([200]);
            expect(rule.options.networkTimeoutSeconds).toBeGreaterThan(0);
        }
    });
});
