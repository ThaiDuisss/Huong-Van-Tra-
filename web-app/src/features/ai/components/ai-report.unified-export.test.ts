import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import type { BusinessExportRecovery } from "../utils/ai-report.export";
import type { AiReportApi } from "../services/aiReport.api";
import type { AiBusinessExport, AiBusinessReportType } from "../types/ai-report.types";

type Slot = {value?: unknown; deps?: readonly unknown[]; cleanup?: () => void};
type Hooks = {slots: Slot[]; cursor: number; effects: (() => void)[]};
const fixture = vi.hoisted(() => ({
  active: null as Hooks | null, excel: vi.fn(), save: vi.fn(), recover: vi.fn(),
  recovery: null as BusinessExportRecovery | null, ingredientRecovery: null as BusinessExportRecovery | null,
  ingredientRecover: vi.fn(), actor: "staff-a",
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
  readIngredientExportRecovery: () => fixture.ingredientRecovery, saveIngredientExportRecovery: fixture.ingredientRecover,
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
const ingredientReceipt: AiBusinessExport = {...receipt, reportType: "INGREDIENT", fromDate: "2026-10-01", toDate: "2026-10-15",
  fileName: "AI_Report_HuongVanTra_20261001_20261015.xlsx"};
function api() {
  return {
    exportBusinessReport: vi.fn().mockImplementation(async input => input.reportType === "INGREDIENT" ? ingredientReceipt : receipt),
    downloadBusinessReport: vi.fn().mockResolvedValue(blob),
    listAiReports: vi.fn().mockResolvedValue({content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true}),
    downloadAiReport: vi.fn().mockResolvedValue(blob),
  } as unknown as AiReportApi;
}
function dialog(productApi = api()) {
  const props = {api: productApi, actor: "staff-a", onClose: vi.fn(), onExported: vi.fn()};
  const view = mount(() => AiReportExportDialog(props)); view.render();
  return {...view, props,
    select(kind: AiBusinessReportType) {
      change(view.find(n => n.props.type === "radio" && n.props.value === kind), kind); view.render();
    },
    submit() {(view.find(n => n.type === "form").props.onSubmit as (e: {preventDefault(): void}) => void)({preventDefault() {}});},
  };
}
beforeEach(() => {vi.clearAllMocks(); fixture.recovery = null; fixture.ingredientRecovery = null; fixture.actor = "staff-a";});
afterEach(() => {vi.unstubAllGlobals();});

describe("shared Product and Ingredient export dialog", () => {
  const dateLabels = ["Từ ngày", "Đến ngày"];
  const dateInputs = (view: ReturnType<typeof dialog>) =>
    dateLabels.map(label => view.find(n => n.props["aria-label"] === label));
  function setDates(view: ReturnType<typeof dialog>, from: string, to: string) {
    change(dateInputs(view)[0], from); change(dateInputs(view)[1], to); view.render();
  }
  async function ready(view = dialog()) {
    view.select("INGREDIENT"); await flush(); view.render();
    setDates(view, "2026-10-01", "2026-10-15");
    return view;
  }

  it("defaults to Product and allows flexible custom dates for both Product and Ingredient", async () => {
    const view = dialog();
    expect(text(view.render())).toContain("Theo sản phẩmTheo nguyên liệu");
    expect(view.find(n => n.props.value === "PRODUCT").props.checked).toBe(true);
    expect(view.find(n => n.props.value === "INGREDIENT").props.checked).toBe(false);
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-10-01", "2026-10-31"]);
    setDates(view, "2026-09-04", "2026-10-17");
    view.select("INGREDIENT"); await flush(); view.render();
    expect(dateInputs(view).map(n => n.props.max)).toEqual(["2026-10-31", "2026-10-31"]);
    expect(dateInputs(view).map(n => n.props.value)).toEqual(["2026-09-04", "2026-10-17"]);
    expect(text(view.find(n => n.type === DialogFooter))).toBe("ĐóngXuất báo cáo");
  });

  it("exports Product report without affecting its legacy payload", async () => {
    const view = dialog();
    setDates(view, "2026-10-04", "2026-10-09");
    view.submit(); await flush();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledExactlyOnceWith({requestId: expect.any(String),
      fromDate: "2026-10-04", toDate: "2026-10-09"}, expect.any(AbortSignal));
    expect(view.props.api.downloadBusinessReport).toHaveBeenCalledExactlyOnceWith(receipt, expect.any(AbortSignal));
    expect(view.props.onExported).toHaveBeenCalledWith("PRODUCT", receipt);
  });

  it("submits custom date period for Ingredient export", async () => {
    const view = await ready();
    setDates(view, "2026-10-15", "2026-10-15");
    view.submit(); await flush();
    expect(view.props.api.exportBusinessReport).toHaveBeenCalledWith({requestId: expect.any(String),
      reportType: "INGREDIENT", fromDate: "2026-10-15", toDate: "2026-10-15"}, expect.any(AbortSignal));
    expect(fixture.save).toHaveBeenCalledWith(blob, ingredientReceipt.fileName);
    expect(view.props.onExported).toHaveBeenCalledExactlyOnceWith("INGREDIENT", ingredientReceipt);
  });

  it("handles empty ingredient data and pending backend contract gracefully without fake files", async () => {
    const adapter = api();
    vi.mocked(adapter.exportBusinessReport)
      .mockRejectedValueOnce(new ApiError({status: 422, code: "INGREDIENT_NO_DATA", message: "empty"}))
      .mockRejectedValueOnce(new ApiError({status: 422, message: "validation_error"}));
    const view = await ready(dialog(adapter));
    view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.props.role === "alert"))).toBe("Chưa có dữ liệu gợi ý nguyên liệu trong kỳ đã chọn.");
    expect(adapter.downloadBusinessReport).not.toHaveBeenCalled();

    view.submit(); await flush(); view.render();
    expect(text(view.find(n => n.props.role === "alert"))).toContain("đang chờ hoàn tất tích hợp từ backend");
  });

  it("closes from footer or dialog dismissal", () => {
    const view = dialog();
    click(view.find(n => n.type === "button" && text(n) === "Đóng"));
    (view.find(n => n.type === Dialog).props.onOpenChange as (open: boolean) => void)(false);
    expect(view.props.onClose).toHaveBeenCalledTimes(2);
  });
});

describe("existing report page wiring", () => {
  it("opens export dialog and reports Ingredient export feedback cleanly", async () => {
    const product = api(); const view = mount(() => AiReportsPage({api: product})); view.render(); await flush(); view.render();
    click(view.find(n => typeof n.props.onClick === "function" && text(n).includes("Xuất báo cáo AI"))); view.render();
    const exported = view.find(n => n.type === AiReportExportDialog);
    expect(exported.props.api).toBe(product);
    (exported.props.onExported as (kind: string) => void)("INGREDIENT"); view.render(); await flush();
    expect(text(view.render())).toContain("báo cáo AI theo nguyên liệu");
    expect(view.find(n => n.type === Pagination).props.totalItems).toBe(0);
  });
});
