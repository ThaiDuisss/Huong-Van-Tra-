import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient } from "@hvt/api-client";
import { createElement as h, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
vi.mock("../../../api/api-client", () => ({apiClient: {}}));
vi.mock("../../../components/ui/dialog", () => ({
  Dialog: ({children}: {children: ReactNode}) => children,
  DialogContent: ({children}: {children: ReactNode}) => h("div", {role: "dialog"}, children),
  DialogDescription: ({children}: {children: ReactNode}) => h("p", {}, children),
  DialogFooter: ({children}: {children: ReactNode}) => h("footer", {}, children),
  DialogHeader: ({children}: {children: ReactNode}) => h("header", {}, children),
  DialogTitle: ({children}: {children: ReactNode}) => h("h2", {}, children),
}));
import { createAiReportApi } from "./aiReport.api";
import { AiReportExportDialog } from "../components/AiReportExportDialog";
import { readBusinessExportRecovery, saveBusinessExportRecovery, validateBusinessPeriod, vietnamToday } from "../utils/ai-report.export";
import type { AiBusinessExport } from "../types/ai-report.types";

const input = {fromDate: "2026-10-01", toDate: "2026-10-11", requestId: "11111111-1111-4111-8111-111111111111"};
const id = "22222222-2222-4222-8222-222222222222";
const mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const bytes = new TextEncoder().encode("PK synthetic workbook bytes");
async function receipt(): Promise<AiBusinessExport> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return {id, fileId: id, fileName: "AI_Report_HuongVanTra_20261001_20261011.xlsx", fromDate: input.fromDate,
    toDate: input.toDate, generatedAt: "2026-10-12T01:00:00Z",
    sha256: Array.from(new Uint8Array(digest), (n) => n.toString(16).padStart(2, "0")).join("")};
}
function adapter() {
  return createAiReportApi(createApiClient({baseUrl: "http://gateway.test/api", retry: false,
    tokenProvider: {getAccessToken: async () => "fixture-token", refreshAccessToken: async () => null}}));
}
afterEach(() => vi.unstubAllGlobals());

describe("generated business AI export", () => {
  it("validates missing, impossible, reversed, future and oversized periods", () => {
    for (const [from, to] of [["", ""], ["2026-02-30", "2026-03-01"], ["2026-10-12", "2026-10-01"],
      ["2026-10-12", "2099-01-01"], ["2024-01-01", "2026-10-11"]]) {
      expect(validateBusinessPeriod(from, to, "2026-10-12")).not.toBeNull();
    }
    expect(validateBusinessPeriod("2026-10-11", "2026-10-11", "2026-10-12")).toBeNull();
  });
  it("uses Vietnam date at UTC day boundary", () => {
    expect(vietnamToday(new Date("2026-10-11T17:00:00Z"))).toBe("2026-10-12");
  });
  it("POSTs authenticated explicit export then downloads separate endpoint and verifies checksum", async () => {
    const report = await receipt();
    const calls: {url: string; init: RequestInit}[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
      calls.push({url, init});
      return init.method === "POST" ? new Response(JSON.stringify(report), {headers: {"Content-Type": "application/json"}})
        : new Response(bytes, {headers: {"Content-Type": mime}});
    }));
    const api = adapter();
    const result = await api.exportBusinessReport(input);
    const blob = await api.downloadBusinessReport(result);
    expect(new Uint8Array(await blob.arrayBuffer())).toEqual(bytes);
    expect(calls[0].url).toBe("http://gateway.test/api/recommendation/ai-reports/exports");
    expect(JSON.parse(calls[0].init.body as string)).toEqual(input);
    expect(new Headers(calls[0].init.headers).get("Authorization")).toBe("Bearer fixture-token");
    expect(calls[1].url).toContain(`/exports/${id}/download`);
    expect(calls.every((call) => !call.url.includes("internal/") && !call.url.includes("minio"))).toBe(true);
  });
  it("rejects checksum mismatch, empty data and wrong MIME before saving", async () => {
    const report = await receipt();
    for (const response of [new Response("changed", {headers: {"Content-Type": mime}}),
      new Response("", {headers: {"Content-Type": mime}}), new Response("error", {headers: {"Content-Type": "application/pdf"}})]) {
      vi.stubGlobal("fetch", vi.fn(async () => response));
      await expect(adapter().downloadBusinessReport(report)).rejects.toThrow();
    }
  });
  it("rejects unauthorized generation and inconsistent receipt", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("Forbidden", {status: 403})));
    await expect(adapter().exportBusinessReport(input)).rejects.toThrow();
    const report = await receipt();
    for (const change of [{fileId: "other"}, {fromDate: "2026-09-01"}, {fileName: "original.xlsx"}, {sha256: "bad"}]) {
      vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({...report, ...change}), {headers: {"Content-Type": "application/json"}})));
      await expect(adapter().exportBusinessReport(input)).rejects.toThrow();
    }
  });
  it("preserves actor-scoped retry IDs across reload without tokens or bytes", () => {
    const values = new Map<string, string>();
    vi.stubGlobal("sessionStorage", {getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key)});
    saveBusinessExportRecovery("actor-a", input);
    expect(readBusinessExportRecovery("actor-a")).toEqual(input);
    expect(readBusinessExportRecovery("actor-b")).toBeNull();
    expect([...values.values()].join("")).not.toContain("token");
    saveBusinessExportRecovery("actor-a", null);
    expect(readBusinessExportRecovery("actor-a")).toBeNull();
  });
  it("renders actual date inputs and separate business action", () => {
    const html = renderToStaticMarkup(h(AiReportExportDialog, {api: adapter(), onClose: () => {}, onExported: () => {}}));
    expect(html.match(/type="date"/g)).toHaveLength(2);
    expect(html).toContain("Từ ngày"); expect(html).toContain("Đến ngày"); expect(html).toContain("Xuất báo cáo AI");
    expect(html).not.toContain("TF-IDF"); expect(html).not.toContain("60%");
  });
});
