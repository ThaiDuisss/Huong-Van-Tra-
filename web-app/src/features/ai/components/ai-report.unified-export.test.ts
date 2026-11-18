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
import { Dialog, DialogContent, DialogFooter } from "../../../components/ui/dialog";

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
  return {exportBusinessReport: vi.fn().mockImplementation(async input => input.reportType === "CUSTOM_BOM" ? bomReceipt : receipt), downloadBusinessReport: vi.fn().mockResolvedValue(blob),
    listAiReports: vi.fn().mockResolvedValue({content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true}),
    downloadAiReport: vi.fn().mockResolvedValue(blob)} as unknown as AiReportApi;
}
function dialog(productApi = api()) {
  const props = {api: productApi, actor: "staff-a",
    onClose: vi.fn(), onExported: vi.fn()};
  const view = mount(() => AiReportExportDialog(props)); view.render();
  return {...view, props,
    bom() {change(view.find(n => n.props.value === "CUSTOM_BOM"), "CUSTOM_BOM"); view.render();},
    submit() {(view.find(n => n.type === "form").props.onSubmit as (e: {preventDefault(): void}) => void)({preventDefault() {}});},
  };
}
beforeEach(() => {vi.clearAllMocks(); fixture.recovery = null; fixture.bomRecovery = null; fixture.actor = "staff-a";});
afterEach(() => {vi.unstubAllGlobals();});

describe("unified export dialog events", () => {
  it("shares the modal and footer while showing dates only for Product across repeated switches", () => {
    const view = dialog();
    const dates = () => ["Từ ngày", "Đến ngày"].map(label => view.find(n => n.props["aria-label"] === label));
    const footer = () => view.find(n => n.type === DialogFooter);
    const modalClass = view.find(n => n.type === DialogContent).props.className;
    expect(view.find(n => n.props.value === "PRODUCT").props.checked).toBe(true);
    expect(dates().map(n => n.props.disabled)).toEqual([false, false]);
    expect(text(view.render())).not.toContain("Synthetic");
    change(dates()[0], "2026-09-03"); change(dates()[1], "2026-09-21"); view.render();
    const footerBefore = footer();
    for (let count = 0; count < 3; count++) {
      view.bom();
      expect(nodes(view.render()).filter(n => n.props.type === "date")).toHaveLength(0);
      expect(text(view.render())).not.toMatch(/Từ ngày|Đến ngày|dữ liệu cố định|chưa hỗ trợ lọc theo ngày|Synthetic|kinh doanh thực tế|CUSTOM_BOM\/REPEAT|Top-5|TEST|receipt|mã xuất|quyền đăng nhập/);
      const body = view.find(n => n.props.className === "grid gap-4 px-6 py-5");
      const sections = Children.toArray(body.props.children as ReactNode).filter(isValidElement);
      expect(sections).toHaveLength(1);
      expect(sections[0].type).toBe("fieldset");
      expect(view.find(n => n.type === DialogContent).props.className).toBe(modalClass);
      expect(footer()).toEqual(footerBefore);
      change(view.find(n => n.props.value === "PRODUCT"), "PRODUCT"); view.render();
      expect(dates().map(n => n.props.value)).toEqual(["2026-09-03", "2026-09-21"]);
      expect(dates().map(n => n.props.disabled)).toEqual([false, false]);
      expect(text(view.render())).not.toContain("Synthetic");
      expect(footer()).toEqual(footerBefore);
    }
  });
  it.each([
    ["", "2026-10-31", "Vui lòng chọn đầy đủ"],
    ["2026-10-01", "", "Vui lòng chọn đầy đủ"],
    ["2026-02-30", "2026-10-31", "Vui lòng chọn đầy đủ"],
    ["2026-10-20", "2026-10-10", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"],
    ["2026-10-01", "2099-10-31", "Ngày kết thúc không được sau hôm nay"],
    ["2025-01-01", "2026-10-31", "không quá 366 ngày"],
  ])("rejects Product dates %s / %s before any export request", async (from, to, message) => {
    const view = dialog();
    change(view.find(n => n.props["aria-label"] === "Từ ngày"), from);
    change(view.find(n => n.props["aria-label"] === "Đến ngày"), to);
    view.render(); view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.props.role === "alert"))).toContain(message);
    expect(view.props.api.exportBusinessReport).not.toHaveBeenCalled();
    expect(fixture.save).not.toHaveBeenCalled();
  });
  it("sends the selected Product period unchanged, including a single-day report", async () => {
    const view = dialog();
    change(view.find(n => n.props["aria-label"] === "Từ ngày"), "2026-10-15");
    change(view.find(n => n.props["aria-label"] === "Đến ngày"), "2026-10-15");
    view.render(); view.submit(); await flush();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledWith({requestId: expect.any(String), fromDate: "2026-10-15", toDate: "2026-10-15"});
  });
  it("closes from the shared footer and dialog dismissal callback", () => {
    const view = dialog();
    click(view.find(n => n.type === "button" && text(n) === "Đóng"));
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).toHaveBeenCalledTimes(2);
    view.bom();
    click(view.find(n => n.type === "button" && text(n) === "Đóng"));
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).toHaveBeenCalledTimes(4);
  });
  it("offers PRODUCT/BOM and exports Product without requesting a demo-token", async () => {
    const view = dialog();
    expect(text(view.render())).toContain("Theo sản phẩm"); expect(text(view.render())).toContain("Theo BOM");
    expect(nodes(view.render()).some(n => n.props.type === "password")).toBe(false);
    view.submit(); await flush();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledWith({requestId: expect.any(String), fromDate: "2026-10-01", toDate: "2026-10-31"});
    expect(view.props.api.downloadBusinessReport).toHaveBeenCalledWith(receipt);
    expect(fixture.save).toHaveBeenCalledWith(blob, "product.xlsx");
    expect(view.props.onExported).toHaveBeenCalledWith("PRODUCT"); expect(fixture.excel).not.toHaveBeenCalled();
  });
  it("preserves same-intent Product retry, download receipt and recovery period", async () => {
    fixture.recovery = {requestId: "existing-intent", fromDate: "2026-08-02", toDate: "2026-10-31"};
    const intent = {...fixture.recovery};
    const product = api(); vi.mocked(product.downloadBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = dialog(product); view.submit(); await flush(); view.render(); view.submit(); await flush();
    expect(product.exportBusinessReport).toHaveBeenCalledTimes(1);
    expect(product.exportBusinessReport).toHaveBeenCalledWith(intent);
    expect(product.downloadBusinessReport).toHaveBeenCalledTimes(2);
    expect(fixture.recover).toHaveBeenLastCalledWith("staff-a", null);
  });
  it("BOM uses official export/current login without a second token or Product date selection", async () => {
    const view = dialog(); change(view.find(n => n.props["aria-label"] === "Từ ngày"), ""); view.render();
    view.bom(); view.submit(); await flush();
    expect(nodes(view.render()).some(n => n.props.type === "password")).toBe(false);
    expect(nodes(view.render()).some(n => n.props.type === "date")).toBe(false);
    expect(text(view.render())).not.toContain("Synthetic"); expect(fixture.excel).not.toHaveBeenCalled();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledWith({requestId: expect.any(String), reportType: "CUSTOM_BOM",
      fromDate: "2026-04-01", toDate: "2026-04-30"}, expect.any(AbortSignal));
    expect(fixture.save).toHaveBeenCalledWith(blob, bomReceipt.fileName);
    expect(view.props.onExported).toHaveBeenCalledWith("CUSTOM_BOM");
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledTimes(1); expect(fixture.recover).not.toHaveBeenCalled();
    expect(fixture.bomRecover).toHaveBeenLastCalledWith("staff-a", null);
  });
  it("BOM retries the same receipt/request ID and keeps Product recovery separate", async () => {
    fixture.bomRecovery = {requestId: "bom-intent", reportType: "CUSTOM_BOM", fromDate: "2026-04-01", toDate: "2026-04-30"};
    const product = api(); vi.mocked(product.downloadBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = dialog(product); view.bom(); view.submit(); await flush(); view.render(); view.submit(); await flush();
    expect(product.exportBusinessReport).toHaveBeenCalledTimes(1);
    expect(product.exportBusinessReport).toHaveBeenCalledWith({requestId: "bom-intent", reportType: "CUSTOM_BOM", fromDate: "2026-04-01", toDate: "2026-04-30"}, expect.any(AbortSignal));
    expect(product.downloadBusinessReport).toHaveBeenCalledTimes(2); expect(fixture.recover).not.toHaveBeenCalled();
  });
  it("BOM loading locks controls and double-submit; unmount aborts and suppresses late download", async () => {
    let finish!: (value: AiBusinessExport) => void;
    const product = api(); vi.mocked(product.exportBusinessReport).mockReturnValue(new Promise(resolve => {finish = resolve;}));
    const view = dialog(product); view.bom(); view.submit(); view.submit();
    expect(product.exportBusinessReport).toHaveBeenCalledTimes(1);
    view.render(); expect(view.find(n => n.type === "form").props["aria-busy"]).toBe(true);
    expect(view.find(n => n.type === "fieldset").props.disabled).toBe(true);
    const footer = view.find(n => n.type === DialogFooter);
    expect(nodes(footer.props.children as ReactNode).filter(n => n.type === "button").map(n => n.props.disabled)).toEqual([true, true]);
    expect(text(footer)).toBe("ĐóngXuất báo cáo");
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).not.toHaveBeenCalled();
    view.unmount(); expect(vi.mocked(product.exportBusinessReport).mock.calls[0][1]!.aborted).toBe(true);
    finish(bomReceipt); await flush();
    expect(fixture.save).not.toHaveBeenCalled(); expect(view.props.onExported).not.toHaveBeenCalled();
    expect(product.downloadBusinessReport).not.toHaveBeenCalled();
  });
  it.each([401, 404, 503, 0])("BOM failure %s is sanitized and never falls back to Product", async (status) => {
    const product = api(); vi.mocked(product.exportBusinessReport).mockRejectedValue(new ApiError({status, message: "fixture-secret-must-not-display"}));
    const view = dialog(product); view.bom(); view.submit(); await flush();
    const html = text(view.render()); expect(html).not.toContain("fixture-secret-must-not-display");
    expect(html).toMatch(/Phiên|Không tìm|Nguồn|Không thể/);
    expect(product.exportBusinessReport).toHaveBeenCalledTimes(1); expect(fixture.save).not.toHaveBeenCalled();
    expect(fixture.excel).not.toHaveBeenCalled(); expect(nodes(view.render()).some(n => n.props.type === "password")).toBe(false);
  });
  it("Product and BOM attempts never share a request ID when switching modes", async () => {
    const product = api(); vi.mocked(product.exportBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = dialog(product); view.bom(); view.submit(); await flush(); view.render();
    change(view.find(n => n.props.value === "PRODUCT"), "PRODUCT"); view.render(); view.submit(); await flush();
    const calls = vi.mocked(product.exportBusinessReport).mock.calls;
    expect(calls[0][0].reportType).toBe("CUSTOM_BOM"); expect(calls[1][0].reportType).toBeUndefined();
    expect(calls[0][0].requestId).not.toBe(calls[1][0].requestId); expect(fixture.excel).not.toHaveBeenCalled();
  });
  it("keeps Product recovery actions outside the common footer", async () => {
    const product = api(); vi.mocked(product.exportBusinessReport).mockRejectedValueOnce(new Error("temporary"));
    const view = dialog(product);
    view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.type === DialogFooter))).toBe("ĐóngXuất báo cáo");
    click(view.find(n => n.type === "button" && text(n) === "Chọn kỳ khác"));
    view.render(); view.submit(); await flush();
    const calls = vi.mocked(product.exportBusinessReport).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[0][0].requestId).not.toBe(calls[1][0].requestId);
  });
  it("keeps a recovered BOM compact without retry helper text or empty date sections", () => {
    fixture.bomRecovery = {requestId: "bom-intent", reportType: "CUSTOM_BOM", fromDate: "2026-04-01", toDate: "2026-04-30"};
    const view = dialog(); view.bom();
    const body = view.find(n => n.props.className === "grid gap-4 px-6 py-5");
    expect(Children.toArray(body.props.children as ReactNode).filter(isValidElement)).toHaveLength(1);
    expect(text(view.render())).not.toMatch(/Thử lại|Tạo lượt xuất mới|Synthetic|Từ ngày|Đến ngày/);
    expect(text(view.find(n => n.type === DialogFooter))).toBe("ĐóngXuất báo cáo");
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
