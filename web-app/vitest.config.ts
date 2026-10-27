import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vitest/config";

/*
 * Unit test thuần (không render React), chạy môi trường node.
 * Chạy: corepack pnpm test
 */
export default defineConfig({
    resolve: {
        alias: {
            "@": fileURLToPath(new URL("./src", import.meta.url))
        }
    },
    test: {
        environment: "node",
        include: ["src/**/*.test.ts"]
    }
});
