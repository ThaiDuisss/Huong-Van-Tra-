import { describe, expect, it } from "vitest";

import { appendQueryParams, buildQueryString } from "./query-params";

describe("buildQueryString", () => {
    it("returns an empty string without parameters", () => {
        expect(buildQueryString()).toBe("");
        expect(buildQueryString({})).toBe("");
        expect(buildQueryString({ a: null, b: undefined })).toBe("");
    });

    it("skips null and undefined but keeps false and zero", () => {
        expect(buildQueryString({ enabled: false, page: 0, keyword: undefined, role: null })).toBe(
            "?enabled=false&page=0"
        );
    });

    it("repeats array values and encodes special characters", () => {
        expect(buildQueryString({ status: ["PAID", "DRAFT"], q: "trà sen & mật" })).toBe(
            "?status=PAID&status=DRAFT&q=tr%C3%A0+sen+%26+m%E1%BA%ADt"
        );
    });

    it("serializes dates as ISO strings", () => {
        expect(buildQueryString({ from: new Date("2026-10-26T00:00:00.000Z") })).toBe(
            "?from=2026-10-26T00%3A00%3A00.000Z"
        );
    });
});

describe("appendQueryParams", () => {
    it("keeps the path unchanged without parameters", () => {
        expect(appendQueryParams("/orders")).toBe("/orders");
        expect(appendQueryParams("/orders", { page: undefined })).toBe("/orders");
    });

    it("starts or extends the query string", () => {
        expect(appendQueryParams("/orders", { page: 1 })).toBe("/orders?page=1");
        expect(appendQueryParams("/orders?size=20", { page: 1 })).toBe("/orders?size=20&page=1");
    });
});
