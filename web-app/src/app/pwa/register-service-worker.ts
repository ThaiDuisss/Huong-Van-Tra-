import { registerSW } from "virtual:pwa-register";

/**
 * Đăng ký service worker của PWA. Chỉ có ở bản build production
 * (vite-plugin-pwa devOptions.enabled = false), nên ở dev hàm này không làm gì.
 */
export function registerServiceWorker(): void {
    if (!import.meta.env.PROD || !("serviceWorker" in navigator)) {
        return;
    }

    registerSW({
        immediate: true,
        onOfflineReady() {
            console.info("POS đã sẵn sàng chạy khi mất mạng (app shell + danh mục đã cache).");
        },
        onRegisterError(error) {
            console.error("Không đăng ký được service worker", error);
        }
    });
}
