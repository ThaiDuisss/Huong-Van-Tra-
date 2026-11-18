import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import type { BusinessExportRecovery } from "../utils/ai-report.export";
import type { AiReportApi } from "../services/aiReport.api";
import type { AiBusinessExport } from "../types/ai-report.types";

// Node-only event fixture: exercise handlers/state without a browser, DOM renderer or server.
type Slot = {value?: unknown; deps?: readonly unknown[]; cleanup?: () => void};
type Hooks = {slots: Slot[]; cursor: number; effects: (() => void)[]};
const fixture = vi.hoisted(() => ({
  active: null as Hooks | null, excel: vi.fn(), save: vi.fn(), recover: vi.fn(),
  recovery: null as BusinessExportRecovery | null, bomRecovery: null as BusinessExportRecovery | null,
  bomRecover: vi.fn(), actor: "staff-a",
}));
vi.mock("react", async (original) => {
  const actual = await original<typeof import("react")>();
  function slot() {
    const hooks = fixture.active!;
    return hooks.slots[hooks.cursor++] ?? (hooks.slots[hooks.cursor - 1] = {});
  }
  const changed = (a: readonly unknown[] | undefined, b: readonly unknown[] | undefined) =>
    !a || !b || a.length !== b.length || a.some((v, i) => !Object.is(v, b[i]));
  return {...actual,
    useState<T>(initial: T | (() => T)) {
      const s = slot();
      if (!("value" in s)) s.value = typeof initial === "function" ? (initial as () => T)() : initial;
      return [s.value, (value: T | ((before: T) => T)) => {
        s.value = typeof value === "function" ? (value as (v: T) => T)(s.value as T) : value;
      }];
    },
    useRef<T>(initial: T) { const s = slot(); return s.value ?? (s.value = {current: initial}); },
    useMemo<T>(compute: () => T, deps: readonly unknown[]) {
      const s = slot(); if (changed(s.deps, deps)) {s.value = compute(); s.deps = deps;} return s.value;
    },
    useEffect(effect: () => void | (() => void), deps?: readonly unknown[]) {
      const s = slot();
      if (changed(s.deps, deps)) {
        s.deps = deps;
        fixture.active!.effects.push(() => {s.cleanup?.(); s.cleanup = effect() || undefined;});
      }
    },
  };
});
vi.mock("../../../api/api-client", () => ({apiClient: {}}));
vi.mock("../../../auth/auth-context", () => ({useAuth: () => ({keycloakSubject: fixture.actor})}));
vi.mock("../../ingredient-demo/services/ingredientDemo.api", async (original) => ({
  ...await original<typeof import("../../ingredient-demo/services/ingredientDemo.api")>(),
  ingredientDemoApi: {excel: fixture.excel},
}));
vi.mock("../utils/ai-report.export", async (original) => ({
  ...await original<typeof import("../utils/ai-report.export")>(),
  vietnamToday: () => "2026-10-31", readBusinessExportRecovery: () => fixture.recovery,
  saveBusinessExportRecovery: fixture.recover, saveBusinessDownload: fixture.save,
  readBomExportRecovery: () => fixture.bomRecovery, saveBomExportRecovery: fixture.bomRecover,
}));
import { ApiError } from "@/lib/api-client";
import { AiReportExportDialog } from "./AiReportExportDialog";
import { AiReportsPage } from "../pages/AiReportsPage";
import { AiReportTable } from "./AiReportTable";
import { AiReportImportDialog } from "./AiReportImportDialog";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { Dialog, DialogFooter } from "../../../components/ui/dialog";

type Node = ReactElement<Record<string, unknown>>;
function nodes(tree: ReactNode): Node[] {
  const all: Node[] = [];
  Children.forEach(tree, (node) => {
    if (!isValidElement<Record<string, unknown>>(node)) return;
    all.push(node); all.push(...nodes(node.props.children as ReactNode), ...nodes(node.props.actions as ReactNode));
  });
  return all;
}
function text(tree: ReactNode): string {
  let result = "";
  Children.forEach(tree, n => {result += isValidElement<{children?: ReactNode}>(n) ? text(n.props.children) : typeof n === "string" ? n : "";});
  return result;
}
function mount(render: () => ReactElement) {
  const hooks: Hooks = {slots: [], cursor: 0, effects: []};
  let tree: ReactElement;
  return {
    render() {hooks.cursor = 0; fixture.active = hooks; tree = render(); fixture.active = null; hooks.effects.splice(0).forEach(f => f()); return tree;},
    find(predicate: (node: Node) => boolean) {const n = nodes(tree).find(predicate); if (!n) throw new Error("Missing fixture element"); return n;},
    unmount() {hooks.slots.forEach(s => s.cleanup?.());},
  };
}
const flush = async () => {for (let n = 0; n < 16; n++) await Promise.resolve();};
function click(node: Node) {(node.props.onClick as () => void)();}
function change(node: Node, value: string) {(node.props.onChange as (e: {target: {value: string}}) => void)({target: {value}});}
const mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const blob = new Blob([new Uint8Array([80, 75, 3, 4])], {type: mime});
const receipt: AiBusinessExport = {id: "report", fileId: "report", fileName: "product.xlsx", fromDate: "2026-10-01",
  toDate: "2026-10-31", generatedAt: "2026-10-31T00:00:00Z", sha256: "a".repeat(64)};
const bomReceipt: AiBusinessExport = {...receipt, reportType: "CUSTOM_BOM", fromDate: "2026-04-01", toDate: "2026-04-30",
  fileName: "AI_Report_HuongVanTra_20260401_20260430.xlsx"};
function api() {
  return {getBomExportPeriod: vi.fn().mockResolvedValue({fromDate: "2026-04-01", toDate: "2026-04-30"}),
    exportBusinessReport: vi.fn().mockImplementation(async input => input.reportType === "CUSTOM_BOM" ? bomReceipt : receipt),
    downloadBusinessReport: vi.fn().mockResolvedValue(blob),
    listAiReports: vi.fn().mockResolvedValue({content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true}),
    downloadAiReport: vi.fn().mockResolvedValue(blob)} as unknown as AiReportApi;
}
function dialog(productApi = api()) {
  const props = {api: productApi, actor: "staff-a",
    onClose: vi.fn(), onExported: vi.fn()};
  const view = mount(() => AiReportExportDialog(props)); view.render();
  return {...view, props,
    select(kind: "PRODUCT" | "CUSTOM_BOM") {
      change(view.find(n => n.props.type === "radio" && n.props.value === kind), kind); view.render();
    },
    submit() {(view.find(n => n.type === "form").props.onSubmit as (e: {preventDefault(): void}) => void)({preventDefault() {}});},
  };
}
beforeEach(() => {vi.clearAllMocks(); fixture.recovery = null; fixture.bomRecovery = null; fixture.actor = "staff-a";});
afterEach(() => {vi.unstubAllGlobals();});

describe("shared Product and BOM export dialog", () => {
  const dateLabels = ["Từ ngày", "Đến ngày"];
  const dateInputs = (view: ReturnType<typeof dialog>) =>
    dateLabels.map(label => view.find(n => n.props["aria-label"] === label));
  function setDates(view: ReturnType<typeof dialog>, from: string, to: string) {
    change(dateInputs(view)[0], from); change(dateInputs(view)[1], to); view.render();
  }
  async function ready(view = dialog()) {
    view.select("CUSTOM_BOM"); await flush(); view.render();
    setDates(view, "2026-04-01", "2026-04-30");
    return view;
  }

  it("defaults to Product and preserves the shared dates while switching modes", async () => {
    const view = dialog();
    expect(text(view.render())).toContain("Theo sản phẩmTheo BOM");
    expect(view.find(n => n.props.value === "PRODUCT").props.checked).toBe(true);
    expect(view.find(n => n.props.value === "CUSTOM_BOM").props.checked).toBe(false);
    expect(view.props.api.getBomExportPeriod).not.toHaveBeenCalled();
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-10-01", "2026-10-31"]);
    setDates(view, "2026-04-04", "2026-04-17");
    view.select("CUSTOM_BOM"); await flush(); view.render();
    expect(dateInputs(view).map(n => n.props.max)).toEqual(["2026-04-30", "2026-04-30"]);
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-04-04", "2026-04-17"]);
    setDates(view, "2026-04-05", "2026-04-12");
    view.select("PRODUCT");
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-04-05", "2026-04-12"]);
    expect(dateInputs(view).map(n => n.props.max)).toEqual(["2026-10-31", "2026-10-31"]);
    view.select("CUSTOM_BOM");
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-04-05", "2026-04-12"]);
    expect(view.props.api.getBomExportPeriod).toHaveBeenCalledOnce();
    expect(text(view.find(n => n.type === DialogFooter))).toBe("ĐóngXuất báo cáo");
  });

  it("exports only Product with its legacy payload even without BOM limits", async () => {
    const view = dialog();
    setDates(view, "2026-10-04", "2026-10-09");
    view.submit(); await flush();
    expect(view.props.api.getBomExportPeriod).not.toHaveBeenCalled();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledExactlyOnceWith({requestId: expect.any(String),
      fromDate: "2026-10-04", toDate: "2026-10-09"}, expect.any(AbortSignal));
    expect(view.props.api.downloadBusinessReport).toHaveBeenCalledExactlyOnceWith(receipt, expect.any(AbortSignal));
    expect(view.props.onExported).toHaveBeenCalledWith("PRODUCT");
    expect(fixture.recover).toHaveBeenLastCalledWith("staff-a", null);
    expect(fixture.bomRecover).not.toHaveBeenCalled();
  });

  it("rejects reversed Product dates", async () => {
    const view = dialog(); setDates(view, "2026-10-09", "2026-10-04");
    view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.props.role === "alert"))).toContain("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
    expect(view.props.api.exportBusinessReport).not.toHaveBeenCalled();
  });

  it("keeps the saved Product request ID through a BOM switch and a download retry", async () => {
    const intent = {requestId: "product-intent", fromDate: "2026-04-04", toDate: "2026-04-17"};
    fixture.recovery = {...intent};
    const adapter = api();
    vi.mocked(adapter.downloadBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = dialog(adapter);
    expect(dateInputs(view).map(n => n.props.value)).toEqual([intent.fromDate, intent.toDate]);
    view.submit(); await flush(); view.render();
    view.select("CUSTOM_BOM"); await flush(); view.render();
    expect(dateInputs(view).every(n => !n.props.disabled)).toBe(true);
    view.select("PRODUCT"); view.submit(); await flush();
    expect(adapter.exportBusinessReport).toHaveBeenCalledExactlyOnceWith(intent, expect.any(AbortSignal));
    expect(adapter.downloadBusinessReport).toHaveBeenCalledTimes(2);
    expect(fixture.bomRecover).not.toHaveBeenCalled();
  });

  it.each([
    ["", "2026-04-30", "Vui lòng chọn đầy đủ"],
    ["2026-04-01", "", "Vui lòng chọn đầy đủ"],
    ["2026-02-30", "2026-04-30", "Vui lòng chọn đầy đủ"],
    ["2026-04-20", "2026-04-10", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"],
    ["2026-03-31", "2026-04-30", "ngoài phạm vi dữ liệu BOM"],
    ["2026-04-01", "2026-05-01", "ngoài phạm vi dữ liệu BOM"],
  ])("rejects BOM dates %s / %s before exporting", async (from, to, message) => {
    const view = await ready();
    change(dateInputs(view)[0], from); change(dateInputs(view)[1], to);
    view.render(); view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.props.role === "alert"))).toContain(message);
    expect(view.props.api.exportBusinessReport).not.toHaveBeenCalled();
  });

  it("sends the selected inclusive single-day period to the official BOM API", async () => {
    const view = await ready();
    change(dateInputs(view)[0], "2026-04-15"); change(dateInputs(view)[1], "2026-04-15");
    view.render(); view.submit(); await flush();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledWith({requestId: expect.any(String),
      reportType: "CUSTOM_BOM", fromDate: "2026-04-15", toDate: "2026-04-15"}, expect.any(AbortSignal));
    expect(fixture.save).toHaveBeenCalledWith(blob, bomReceipt.fileName);
    expect(view.props.onExported).toHaveBeenCalledExactlyOnceWith("CUSTOM_BOM");
    expect(fixture.excel).not.toHaveBeenCalled();
    expect(fixture.bomRecover).toHaveBeenLastCalledWith("staff-a", null);
  });

  it("closes from the footer or dialog dismissal", () => {
    const view = dialog();
    click(view.find(n => n.type === "button" && text(n) === "Đóng"));
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).toHaveBeenCalledTimes(2);
  });

  it("retries the same receipt and preserves the selected period after a download error", async () => {
    const intent = {requestId: "bom-intent", reportType: "CUSTOM_BOM", fromDate: "2026-04-02", toDate: "2026-04-18"} as const;
    fixture.bomRecovery = {...intent};
    const adapter = api();
    vi.mocked(adapter.downloadBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = await ready(dialog(adapter));
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-04-01", "2026-04-30"]);
    click(view.find(n => n.type === "button" && text(n) === "Dùng kỳ đã lưu")); view.render();
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-04-02", "2026-04-18"]);
    view.submit(); await flush(); view.render(); view.submit(); await flush();
    expect(adapter.exportBusinessReport).toHaveBeenCalledExactlyOnceWith(intent, expect.any(AbortSignal));
    expect(adapter.downloadBusinessReport).toHaveBeenCalledTimes(2);
  });

  it("locks controls on submit and aborts on unmount without downloading late results", async () => {
    let finish!: (value: AiBusinessExport) => void;
    const adapter = api();
    vi.mocked(adapter.exportBusinessReport).mockReturnValue(new Promise(resolve => {finish = resolve;}));
    const view = await ready(dialog(adapter)); view.submit(); view.submit();
    expect(adapter.exportBusinessReport).toHaveBeenCalledOnce();
    view.render(); expect(view.find(n => n.type === "form").props["aria-busy"]).toBe(true);
    expect(dateInputs(view).every(n => n.props.disabled)).toBe(true);
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).not.toHaveBeenCalled();
    view.unmount(); expect(vi.mocked(adapter.exportBusinessReport).mock.calls[0][1]!.aborted).toBe(true);
    finish(bomReceipt); await flush();
    expect(fixture.save).not.toHaveBeenCalled();
    expect(adapter.downloadBusinessReport).not.toHaveBeenCalled();
  });

  it("lets the user retry loading the valid period and prevents export while unavailable", async () => {
    const adapter = api();
    vi.mocked(adapter.getBomExportPeriod).mockRejectedValueOnce(new Error("unavailable"));
    const view = await ready(dialog(adapter));
    view.submit();
    expect(adapter.exportBusinessReport).not.toHaveBeenCalled();
    click(view.find(n => n.type === "button" && text(n) === "Tải lại phạm vi ngày"));
    view.render(); await ready(view);
    expect(dateInputs(view).map(n => n.props.max)).toEqual(["2026-04-30", "2026-04-30"]);
  });

  it("does not block Product when BOM's limit lookup fails", async () => {
    const adapter = api();
    vi.mocked(adapter.getBomExportPeriod).mockRejectedValue(new Error("unavailable"));
    const view = await ready(dialog(adapter));
    view.submit(); expect(adapter.exportBusinessReport).not.toHaveBeenCalled();
    view.select("PRODUCT"); view.submit(); await flush();
    expect(adapter.exportBusinessReport).toHaveBeenCalledExactlyOnceWith({requestId: expect.any(String),
      fromDate: "2026-04-01", toDate: "2026-04-30"}, expect.any(AbortSignal));
  });

  it.each([401, 404, 503, 0])("sanitizes export error %s without calling another export flow", async (status) => {
    const adapter = api();
    vi.mocked(adapter.exportBusinessReport).mockRejectedValue(new ApiError({status, message: "fixture-secret-must-not-display"}));
    const view = await ready(dialog(adapter)); view.submit(); await flush();
    const html = text(view.render());
    expect(html).not.toContain("fixture-secret-must-not-display");
    expect(html).toMatch(/Phiên|Không tìm|Nguồn|Không thể/);
    expect(adapter.exportBusinessReport).toHaveBeenCalledOnce();
    expect(fixture.save).not.toHaveBeenCalled();
  });

  it("creates a new request ID only when choosing another period after an error", async () => {
    const adapter = api();
    vi.mocked(adapter.exportBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = await ready(dialog(adapter)); view.submit(); await flush(); view.render();
    click(view.find(n => n.type === "button" && text(n) === "Chọn kỳ khác"));
    view.render(); change(dateInputs(view)[0], "2026-04-02"); view.render(); view.submit(); await flush();
    const calls = vi.mocked(adapter.exportBusinessReport).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[1][0]).toMatchObject({reportType: "CUSTOM_BOM", fromDate: "2026-04-02"});
    expect(calls[1][0].requestId).not.toBe(calls[0][0].requestId);
  });
});

describe("existing report page wiring", () => {
  it("opens this dialog from the existing button and keeps BOM out of persistent list/pagination", async () => {
    const product = api(); const view = mount(() => AiReportsPage({api: product})); view.render(); await flush(); view.render();
    click(view.find(n => typeof n.props.onClick === "function" && text(n).includes("Xuất báo cáo AI"))); view.render();
    const exported = view.find(n => n.type === AiReportExportDialog);
    expect(exported.props.api).toBe(product);
    (exported.props.onExported as (kind: string) => void)("CUSTOM_BOM"); view.render(); await flush();
    expect(product.listAiReports).toHaveBeenCalledTimes(1);
    expect(text(view.render())).toContain("chưa tự thêm vào danh sách");
    click(view.find(n => typeof n.props.onClick === "function" && text(n).includes("Nhập báo cáo"))); view.render();
    expect(view.find(n => n.type === AiReportImportDialog).props.open).toBe(true);
    click(view.find(n => typeof n.props.onClick === "function" && text(n).includes("Làm mới"))); view.render(); await flush(); view.render();
    expect(product.listAiReports).toHaveBeenCalledTimes(2);
    expect(view.find(n => n.type === Pagination).props.totalItems).toBe(0);
  });
  it("has no BOM credential callbacks or password UI on account change", async () => {
    const product = api(); const view = mount(() => AiReportsPage({api: product})); view.render(); await flush(); view.render();
    const open = () => {click(view.find(n => typeof n.props.onClick === "function" && text(n).includes("Xuất báo cáo AI"))); view.render(); return view.find(n => n.type === AiReportExportDialog);};
    const first = open(); expect(first.props.rememberBomToken).toBeUndefined(); expect(first.props.getBomToken).toBeUndefined();
    (first.props.onClose as () => void)(); view.render(); const again = open();
    expect(again.props.getBomToken).toBeUndefined();
    fixture.actor = "staff-b"; view.render();
    const next = view.find(n => n.type === AiReportExportDialog); expect(next.key).toBe("export:staff-b");
    expect(next.props.getBomToken).toBeUndefined(); view.unmount();
    const reload = mount(() => AiReportsPage({api: product})); reload.render();
    click(reload.find(n => typeof n.props.onClick === "function" && text(n).includes("Xuất báo cáo AI"))); reload.render();
    expect(reload.find(n => n.type === AiReportExportDialog).props.getBomToken).toBeUndefined();
  });
  it("keeps original-file download and server paging callbacks on the existing table", async () => {
    const product = api();
    const record = {id: "original", name: "Original", fileName: "original.xlsx", fileType: "XLSX"};
    vi.mocked(product.listAiReports).mockResolvedValue({content: [record], totalElements: 45, totalPages: 3, page: 0, size: 20, first: true, last: false} as Awaited<ReturnType<AiReportApi["listAiReports"]>>);
    const link = {href: "", download: "", click: vi.fn(), remove: vi.fn()};
    vi.stubGlobal("document", {body: {appendChild: vi.fn()}, createElement: () => link}); vi.stubGlobal("window", {setTimeout: () => 0});
    const view = mount(() => AiReportsPage({api: product})); view.render(); await flush(); view.render();
    (view.find(n => n.type === AiReportTable).props.onExport as (r: unknown) => void)(record); await flush();
    expect(product.downloadAiReport).toHaveBeenCalledWith(record); expect(link.download).toBe("original.xlsx"); expect(link.click).toHaveBeenCalledTimes(1);
    view.render(); (view.find(n => n.type === Pagination).props.onPageChange as (p: number) => void)(2); view.render(); await flush();
    expect(product.listAiReports).toHaveBeenLastCalledWith(1, 20, expect.any(AbortSignal));
  });
});
