import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

import { createCatalogRuntimeCaching } from "./src/app/pwa/runtime-caching";

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  plugins: [
    react(),
    tailwindcss(),
    /*
     * PWA: chỉ hoạt động ở bản build production (devOptions.enabled = false).
     * - Precache app shell (js/css/html/svg/png/woff2) để POS vẫn mở được khi
     *   mất mạng.
     * - Runtime cache NetworkFirst cho API danh mục sản phẩm: online lấy
     *   dữ liệu mới, offline (hoặc mạng chậm > 5s) trả bản đã cache.
     * - Không cache khách hàng/đơn hàng/thanh toán/health ping.
     */
    VitePWA({
      registerType: "autoUpdate",
      injectRegister: false,
      includeAssets: ["favicon.svg", "pwa-icon.svg", "silent-check-sso.html"],
      manifest: {
        name: "Hương Vân Trà POS",
        short_name: "HVTra POS",
        description: "Bán hàng tại quầy Hương Vân Trà",
        lang: "vi",
        start_url: "/",
        display: "standalone",
        background_color: "#f7f8fb",
        theme_color: "#159734",
        icons: [
          { src: "pwa-192.png", sizes: "192x192", type: "image/png" },
          { src: "pwa-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "pwa-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable"
          },
          { src: "pwa-icon.svg", sizes: "any", type: "image/svg+xml" }
        ]
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico,woff,woff2}"],
        // Bundle POS lớn hơn ngưỡng 2MB mặc định của Workbox.
        maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        skipWaiting: true,
        // Trang Keycloak dùng để check SSO phải đi thẳng, không rơi vào index.html.
        navigateFallbackDenylist: [/^\/silent-check-sso\.html/],
        runtimeCaching: createCatalogRuntimeCaching()
      },
      devOptions: {
        enabled: false
      }
    })
  ],
});
