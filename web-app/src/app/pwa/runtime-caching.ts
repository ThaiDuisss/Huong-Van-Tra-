/**
 * Luật cache API cho service worker (Workbox generateSW).
 *
 * Chỉ dùng RegExp, không dùng hàm: generateSW chuyển cấu hình thành chuỗi để
 * nhúng vào sw.js, hàm tham chiếu biến ngoài sẽ hỏng khi chạy trong worker.
 *
 * Chỉ cache dữ liệu đọc của danh mục sản phẩm (không nhạy cảm, cần để POS vẫn
 * tìm và xem hàng khi mất mạng). Không cache khách hàng, đơn hàng, báo cáo,
 * health ping hay bất kỳ request ghi nào.
 */

/** POS catalog; explicit _fresh=1 reads must reach the network (AI price/stock recheck). */
export const POS_PRODUCT_PATTERN =
    /\/api\/(?:orders\/)?pos\/products(?![^#]*[?&]_fresh=1(?:&|$))(?:\/[^/?]+)?(?:\/pos-view|\/search-view-pos)?(?:\?.*)?$/;

/** Catalog service, trừ export/import/template là file tải về. */
export const CATALOG_READ_PATTERN =
    /\/api\/catalog\/(?!.*\/(?:export|import)(?:\/|\?|$))[^?]*(?:\?.*)?$/;

export const CATALOG_CACHE_NAME = "hvt-catalog-api-v1";

/** Mạng chậm quá mức này thì trả bản cache rồi cập nhật nền. */
export const CATALOG_NETWORK_TIMEOUT_SECONDS = 5;

export const CATALOG_CACHE_MAX_ENTRIES = 300;

export const CATALOG_CACHE_MAX_AGE_SECONDS = 24 * 60 * 60;

export function isCatalogReadUrl(url: string): boolean {
    return POS_PRODUCT_PATTERN.test(url) || CATALOG_READ_PATTERN.test(url);
}

/**
 * Cấu hình runtimeCaching cho Workbox. Kiểu để lỏng (không import workbox-build)
 * để file này chạy được cả trong vitest lẫn trong vite.config.ts.
 */
export function createCatalogRuntimeCaching() {
    return [POS_PRODUCT_PATTERN, CATALOG_READ_PATTERN].map((urlPattern) => ({
        urlPattern,
        method: "GET" as const,
        handler: "NetworkFirst" as const,
        options: {
            cacheName: CATALOG_CACHE_NAME,
            networkTimeoutSeconds: CATALOG_NETWORK_TIMEOUT_SECONDS,
            cacheableResponse: { statuses: [200] },
            expiration: {
                maxEntries: CATALOG_CACHE_MAX_ENTRIES,
                maxAgeSeconds: CATALOG_CACHE_MAX_AGE_SECONDS
            }
        }
    }));
}
