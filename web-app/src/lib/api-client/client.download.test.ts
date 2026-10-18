import { afterEach, describe, expect, it, vi } from "vitest";

import { createApiClient } from "./client";

describe("api client download", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("keeps the filename exposed by Content-Disposition", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue(
                new Response("excel-content", {
                    headers: {
                        "Content-Disposition":
                            'attachment; filename="hoa-don-theo-bo-loc-20260730-142500.xlsx"',
                        "Content-Type":
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    },
                    status: 200
                })
            )
        );
        const client = createApiClient({
            baseUrl: "http://localhost/api"
        });

        const blob = await client.download("/orders/invoices/export/excel");

        expect((blob as Blob & { name?: string }).name).toBe(
            "hoa-don-theo-bo-loc-20260730-142500.xlsx"
        );
        expect(blob.type).toBe(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    });

    it("decodes an RFC 5987 filename", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue(
                new Response("excel-content", {
                    headers: {
                        "Content-Disposition":
                            "attachment; filename*=UTF-8''hoa-don-th%C3%A1ng-7.xlsx"
                    },
                    status: 200
                })
            )
        );
        const client = createApiClient({
            baseUrl: "http://localhost/api"
        });

        const blob = await client.download("/orders/invoices/export/excel");

        expect((blob as Blob & { name?: string }).name).toBe(
            "hoa-don-tháng-7.xlsx"
        );
    });
});
