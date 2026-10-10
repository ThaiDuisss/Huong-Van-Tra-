import { defineConfig } from "vitest/config";

/*
 * Unit test thuần (không render React), chạy môi trường node.
 * Chạy: corepack pnpm test
 */
export default defineConfig({
    test: {
        environment: "node",
        include: ["src/**/*.test.ts"]
    }
});
