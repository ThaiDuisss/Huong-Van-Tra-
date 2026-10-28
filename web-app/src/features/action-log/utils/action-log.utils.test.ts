import { describe, expect, it } from "vitest";

import type { ActionLogFilters } from "../types/action-log.types";
import {
  DEFAULT_ACTION_LOG_FILTERS,
  countAdvancedFilters,
  formatDuration,
  formatOccurredAt,
  getServiceLabel,
  humanizeAction,
  isDefaultFilters,
  resolveTimeRange,
  shortId,
  toActionLogQuery,
} from "./action-log.utils";

function filters(overrides: Partial<ActionLogFilters> = {}): ActionLogFilters {
  return { ...DEFAULT_ACTION_LOG_FILTERS, ...overrides };
}

const NOW = new Date(2026, 9, 26, 15, 45, 0);

function localStart(year: number, month: number, day: number) {
  return new Date(year, month, day, 0, 0, 0, 0).toISOString();
}

describe("resolveTimeRange", () => {
  it("starts today at local midnight", () => {
    expect(resolveTimeRange(filters({ timeRange: "TODAY" }), NOW)).toEqual({ from: localStart(2026, 9, 26) });
  });

  it("counts today as one of the 7 and 30 days", () => {
    expect(resolveTimeRange(filters({ timeRange: "SEVEN_DAYS" }), NOW)).toEqual({ from: localStart(2026, 9, 20) });
    expect(resolveTimeRange(filters({ timeRange: "THIRTY_DAYS" }), NOW)).toEqual({ from: localStart(2026, 8, 27) });
  });

  it("sends no bound for ALL", () => {
    expect(resolveTimeRange(filters({ timeRange: "ALL" }), NOW)).toEqual({});
  });

  it("converts a custom local range to ISO and drops invalid ends", () => {
    const range = resolveTimeRange(
      filters({ timeRange: "CUSTOM", customFrom: "2026-10-01T08:00", customTo: "" }),
      NOW,
    );
    expect(range.from).toBe(new Date(2026, 9, 1, 8, 0).toISOString());
    expect(range.to).toBeUndefined();
    expect(resolveTimeRange(filters({ timeRange: "CUSTOM", customFrom: "abc", customTo: "xyz" }), NOW)).toEqual({
      from: undefined,
      to: undefined,
    });
  });
});

describe("toActionLogQuery", () => {
  it("trims text filters and leaves blank ones undefined", () => {
    const query = toActionLogQuery(
      filters({ timeRange: "ALL", service: "Order-Service", username: "  sale01 ", action: "  ", status: "FAIL" }),
      1,
      50,
    );
    expect(query).toMatchObject({ page: 1, size: 50, service: "Order-Service", username: "sale01", status: "FAIL" });
    expect(query.action).toBeUndefined();
    expect(query.entityId).toBeUndefined();
    expect(query.from).toBeUndefined();
  });

  it("includes the time range lower bound by default", () => {
    expect(toActionLogQuery(DEFAULT_ACTION_LOG_FILTERS, 0, 20).from).toBeDefined();
  });
});

describe("filter state", () => {
  it("counts only non-blank advanced filters", () => {
    expect(countAdvancedFilters(filters())).toBe(0);
    expect(countAdvancedFilters(filters({ entityType: "ORDER", entityId: " ", requestId: "r-1" }))).toBe(2);
  });

  it("detects the default filters", () => {
    expect(isDefaultFilters(filters())).toBe(true);
    expect(isDefaultFilters(filters({ timeRange: "TODAY" }))).toBe(false);
    expect(isDefaultFilters(filters({ userId: "u" }))).toBe(false);
  });
});

describe("display helpers", () => {
  it("labels services case-insensitively and keeps unknown names", () => {
    expect(getServiceLabel("order-service")).toBe("Đơn hàng");
    expect(getServiceLabel("IDENTITY-SERVICE")).toBe("Tài khoản");
    expect(getServiceLabel("Recommendation-Service")).toBe("Recommendation-Service");
  });

  it("formats durations in ms below one second and in seconds above", () => {
    expect(formatDuration(null)).toBe("—");
    expect(formatDuration(0)).toBe("0 ms");
    expect(formatDuration(999)).toBe("999 ms");
    expect(formatDuration(1000)).toBe("1 s");
    expect(formatDuration(1550)).toBe("1,6 s");
  });

  it("formats the occurred time as date then clock, and keeps invalid input", () => {
    expect(formatOccurredAt("2026-09-20T11:17:49Z")).toMatch(/^\d{2}\/\d{2}\/2026 \d{2}:\d{2}:\d{2}$/);
    expect(formatOccurredAt("bad")).toBe("bad");
  });

  it("humanizes action codes", () => {
    expect(humanizeAction("ORDER_DRAFT_ITEM_ADD")).toBe("Order draft item add");
    expect(humanizeAction("LOGIN")).toBe("Login");
  });

  it("shortens ids longer than head + 1", () => {
    expect(shortId("123456789")).toBe("123456789");
    expect(shortId("1234567890")).toBe("12345678…");
  });
});
