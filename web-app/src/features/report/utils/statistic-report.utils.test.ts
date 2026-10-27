import { describe, expect, it } from "vitest";

import {
    exportTimestamp,
    formatCount,
    formatCurrency,
    formatQuantity,
    getErrorMessage,
    getMonthRange,
    toDateInputValue,
    toNumber
} from "./statistic-report.utils";

describe("date helpers", () => {
    it("formats a date for an input[type=date]", () => {
        expect(toDateInputValue(new Date(2026, 0, 5))).toBe("2026-01-05");
    });

    it("returns the first and last day of the month", () => {
        expect(getMonthRange(new Date(2026, 9, 26))).toEqual({ fromDate: "2026-10-01", toDate: "2026-10-31" });
        expect(getMonthRange(new Date(2028, 1, 10))).toEqual({ fromDate: "2028-02-01", toDate: "2028-02-29" });
    });

    it("moves across years with the month offset", () => {
        expect(getMonthRange(new Date(2026, 0, 15), -1)).toEqual({ fromDate: "2025-12-01", toDate: "2025-12-31" });
        expect(getMonthRange(new Date(2026, 11, 15), 1)).toEqual({ fromDate: "2027-01-01", toDate: "2027-01-31" });
    });

    it("builds a sortable export timestamp", () => {
        expect(exportTimestamp(new Date(2026, 9, 6, 7, 8, 9))).toBe("20261006-070809");
    });
});

describe("number helpers", () => {
    it("reads BigDecimal values sent as number or string", () => {
        expect(toNumber(1500)).toBe(1500);
        expect(toNumber("1500.50")).toBe(1500.5);
    });

    it("treats empty and invalid values as zero", () => {
        expect(toNumber(null)).toBe(0);
        expect(toNumber(undefined)).toBe(0);
        expect(toNumber("")).toBe(0);
        expect(toNumber("abc")).toBe(0);
    });

    it("formats money rounded to whole dong", () => {
        expect(formatCurrency("1250000.6")).toBe("1.250.001đ");
        expect(formatCurrency(null)).toBe("0đ");
    });

    it("formats quantities with up to three decimals and counts", () => {
        expect(formatQuantity("2.12345")).toBe("2,123");
        expect(formatQuantity(1000)).toBe("1.000");
        expect(formatCount(12500)).toBe("12.500");
        expect(formatCount(null)).toBe("0");
    });
});

describe("getErrorMessage", () => {
    it("uses the error message when present", () => {
        expect(getErrorMessage(new Error("Không có quyền"), "fallback")).toBe("Không có quyền");
        expect(getErrorMessage({ message: "Từ API" }, "fallback")).toBe("Từ API");
    });

    it("falls back for blank or missing messages", () => {
        expect(getErrorMessage(new Error("  "), "Tải báo cáo thất bại")).toBe("Tải báo cáo thất bại");
        expect(getErrorMessage("boom", "Tải báo cáo thất bại")).toBe("Tải báo cáo thất bại");
        expect(getErrorMessage(null, "x")).toBe("x");
    });
});
