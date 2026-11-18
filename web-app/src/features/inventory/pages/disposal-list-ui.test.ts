import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DisposalRequest } from "../types/quality.types";

type Slot = { value?: unknown; deps?: readonly unknown[]; cleanup?: () => void };
type Hooks = { slots: Slot[]; cursor: number; effects: (() => void)[] };
const fixture = vi.hoisted(() => ({
  active: null as Hooks | null, canApprove: true, pathname: "/management/inventory/disposals",
  navigate: vi.fn(), list: vi.fn(), counts: vi.fn(), detail: vi.fn(), approve: vi.fn(),
}));
// Node event fixture: exercise the real page handlers with isolated API boundaries.
vi.mock("react", async original => {
  const actual = await original<typeof import("react")>();
  const slot = () => {
    const hooks = fixture.active!;
    return hooks.slots[hooks.cursor++] ?? (hooks.slots[hooks.cursor - 1] = {});
  };
  const changed = (a?: readonly unknown[], b?: readonly unknown[]) =>
    !a || !b || a.length !== b.length || a.some((v, i) => !Object.is(v, b[i]));
  function memo<T>(compute: () => T, deps: readonly unknown[]) {
    const s = slot(); if (changed(s.deps, deps)) { s.value = compute(); s.deps = deps; } return s.value;
  }
  return { ...actual,
    useState<T>(initial: T | (() => T)) {
      const s = slot();
      if (!("value" in s)) s.value = typeof initial === "function" ? (initial as () => T)() : initial;
      return [s.value, (value: T | ((before: T) => T)) => {
        s.value = typeof value === "function" ? (value as (v: T) => T)(s.value as T) : value;
      }];
    },
    useEffect(effect: () => void | (() => void), deps?: readonly unknown[]) {
      const s = slot();
      if (changed(s.deps, deps)) {
        s.deps = deps;
        fixture.active!.effects.push(() => { s.cleanup?.(); s.cleanup = effect() || undefined; });
      }
    },
    useRef<T>(initial: T) { const s = slot(); return s.value ?? (s.value = { current: initial }); },
    useMemo: memo,
    useCallback<T>(callback: T, deps: readonly unknown[]) { return memo(() => callback, deps); },
  };
});
vi.mock("react-router-dom", async original => ({
  ...await original<typeof import("react-router-dom")>(),
  useNavigate: () => fixture.navigate,
  useParams: () => ({ disposalId: "disposal-001" }),
  useLocation: () => ({ pathname: fixture.pathname }),
}));
vi.mock("@/auth/useAuthorization", () => ({ useAuthorization: () => ({ hasAnyRole: () => fixture.canApprove }) }));
vi.mock("@/auth/auth-context", () => ({ useAuth: () => ({ roles: ["ROLE_MANAGER"] }) }));
vi.mock("@/components/shared/useTimeFilter", () => ({ useTimeFilter: () => ({
  selectedTimeNode: "ALL", selectTimeNode: vi.fn(), clearTimeRange: vi.fn(),
  isTimeCollapsed: false, setIsTimeCollapsed: vi.fn(), timeRange: undefined,
}) }));
vi.mock("../services/quality.api", () => ({ qualityApi: {
  listDisposalRequests: fixture.list, getDisposalStatusCounts: fixture.counts,
  getDisposalRequest: fixture.detail, approveDisposalRequest: fixture.approve,
} }));

import { OrderPageLayout, OrderToolbar, OrderStatusFilters, OrderDataTable, OrderPagination,
  type OrderTableColumn } from "@/features/order/components/shared/OrderListUI";
import { Button } from "@/components/ui/Button";
import { SystemTopbar } from "@/components/layout/System/SystemTopbar";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { DisposalListPage } from "./DisposalListPage";
import { DisposalDetailPage } from "./DisposalDetailPage";

type Node = ReactElement<Record<string, unknown>>;
function nodes(tree: ReactNode): Node[] {
  const result: Node[] = [];
  Children.forEach(tree, n => {
    if (!isValidElement<Record<string, unknown>>(n)) return;
    result.push(n, ...nodes(n.props.children as ReactNode), ...nodes(n.props.actions as ReactNode),
      ...nodes(n.props.pagination as ReactNode));
  });
  return result;
}
function text(tree: ReactNode): string {
  let value = "";
  Children.forEach(tree, n => { value += isValidElement<{ children?: ReactNode }>(n) ? text(n.props.children) : typeof n === "string" ? n : ""; });
  return value;
}
function mount(render: () => ReactElement) {
  const hooks: Hooks = { slots: [], cursor: 0, effects: [] };
  let tree: ReactElement;
  return {
    render() {
      hooks.cursor = 0; fixture.active = hooks; tree = render(); fixture.active = null;
      hooks.effects.splice(0).forEach(run => run()); return tree;
    },
    find(predicate: (n: Node) => boolean) {
      const found = nodes(tree).find(predicate); if (!found) throw new Error("Missing fixture element"); return found;
    },
    unmount() { hooks.slots.forEach(s => s.cleanup?.()); },
  };
}
const flush = async () => { for (let n = 0; n < 20; n++) await Promise.resolve(); };
const request = {
  id: "disposal-001", code: "DSP-20261117-001", status: "PENDING_APPROVAL", sourceType: "DAMAGED",
  returnCode: "RET-001", inspectionCode: "QC-001", requestedAt: "2026-11-17T08:00:00Z",
  requestedBy: { id: "staff-1", name: "Nhân viên kho", role: "INVENTORY_STAFF" },
  location: { id: "warehouse-1", code: "KHO-01", name: "Kho tổng", isCentral: true },
  reason: "Hàng hỏng", method: "DESTROY", scheduledAt: "2026-11-17T08:00:00Z", witnesses: [], auditEvents: [],
  lines: [{ id: "line-1", skuName: "Trà Ô Long Hương Vân", skuCode: "TRA-01", quantity: 2 },
    { id: "line-2", skuName: "Trà Sen", skuCode: "TRA-02", quantity: 3 }],
} as DisposalRequest;
function listView() { const view = mount(DisposalListPage); view.render(); return view; }
function table(view: ReturnType<typeof mount>) { return view.find(n => n.type === OrderDataTable); }
function columns(view: ReturnType<typeof mount>) { return table(view).props.columns as OrderTableColumn<DisposalRequest>[]; }
beforeEach(() => {
  vi.clearAllMocks(); vi.useFakeTimers();
  vi.stubGlobal("window", { setTimeout, clearTimeout });
  fixture.canApprove = true; fixture.pathname = "/management/inventory/disposals";
  fixture.list.mockResolvedValue({ content: [request], page: 0, size: 10, totalElements: 23, totalPages: 3 });
  fixture.counts.mockResolvedValue({ ALL: 23, PENDING_APPROVAL: 20, APPROVED: 3 });
  fixture.detail.mockResolvedValue(request);
  fixture.approve.mockResolvedValue({ ...request, status: "APPROVED" });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe("disposal list uses the customer return list presentation", () => {
  it("uses the shared header, toolbar, statuses and pagination with exactly six ordered columns", async () => {
    const view = listView(); await flush(); view.render();
    expect(view.find(n => n.type === OrderPageLayout).props).toMatchObject({
      title: "Tiêu hủy", description: "Theo dõi và xử lý yêu cầu tiêu hủy hàng hóa.",
    });
    expect(view.find(n => n.type === Button).props.size).toBeUndefined();
    expect(columns(view).map(c => c.header)).toEqual(["Mã yêu cầu", "Trạng thái", "Nguồn", "Hàng tiêu hủy", "Số lượng", "Thời gian"]);
    expect(columns(view).map(c => c.key)).not.toContain("actions");
    expect(columns(view).reduce((sum, c) => sum + parseInt(c.width!), 0)).toBe(100);
    expect(view.find(n => n.type === OrderStatusFilters).props.options).toEqual([
      { key: "ALL", label: "Tất cả", count: 23 }, { key: "PENDING_APPROVAL", label: "Chờ xử lý", count: 20 },
      { key: "APPROVED", label: "Đã xử lý", count: 3 },
    ]);
    expect(view.find(n => n.type === OrderPagination).props).toMatchObject({ pageSize: 10, totalItems: 23, totalPages: 3 });
    expect(fixture.list).toHaveBeenCalledWith({ keyword: "", page: 0, size: 10, status: "ALL", fromDate: undefined, toDate: undefined });
    view.unmount();
  });
  it("opens the exact detail route on row click and keyboard activation without approving from the list", async () => {
    const view = listView(); await flush(); view.render();
    const tree = OrderDataTable(table(view).props as Parameters<typeof OrderDataTable>[0]);
    const row = nodes(tree).find(n => n.type === "tr" && n.props.tabIndex === 0)!;
    (row.props.onClick as () => void)();
    const target = {}; const preventDefault = vi.fn();
    (row.props.onKeyDown as (e: unknown) => void)({ key: "Enter", currentTarget: target, target, preventDefault });
    expect(fixture.navigate.mock.calls).toEqual([["/management/inventory/disposals/disposal-001"], ["/management/inventory/disposals/disposal-001"]]);
    expect(preventDefault).toHaveBeenCalledOnce(); expect(fixture.approve).not.toHaveBeenCalled();
    expect(view.find(n => n.type === OrderDataTable).props.minWidth).toBe(800);
    view.unmount();
  });
  it("keeps server pagination and resets the page for statuses and debounced search", async () => {
    const view = listView(); await flush(); view.render();
    (view.find(n => n.type === OrderPagination).props.onPageChange as (page: number) => void)(2);
    view.render(); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 10 }));
    (view.find(n => n.type === OrderStatusFilters).props.onChange as (value: string) => void)("APPROVED");
    view.render(); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, status: "APPROVED" }));
    const search = view.find(n => n.type === OrderToolbar).props.search as { onChange(v: string): void; onKeyDown(e: { key: string }): void };
    search.onChange("  RET-001  "); view.render();
    await vi.advanceTimersByTimeAsync(350); view.render(); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: "RET-001", page: 0, status: "APPROVED" }));
    expect(fixture.counts).toHaveBeenLastCalledWith("RET-001", undefined, undefined);
    search.onKeyDown({ key: "Escape" }); view.render(); await vi.advanceTimersByTimeAsync(350);
    view.render(); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: "" }));
    view.unmount();
  });
  it("retains loading, error/retry and empty states without showing stale rows", async () => {
    fixture.list.mockRejectedValueOnce(new Error("Không thể tải dữ liệu"));
    const view = listView(); expect(table(view).props.loading).toBe(true);
    await flush(); view.render(); expect(table(view).props.error).toBe("Không thể tải dữ liệu");
    fixture.list.mockResolvedValueOnce({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    (table(view).props.onRetry as () => void)(); view.render(); await flush(); view.render();
    expect(table(view).props).toMatchObject({ loading: false, error: null, rows: [] });
    expect(view.find(n => n.type === OrderPagination).props.totalItems).toBe(0);
    const count = fixture.list.mock.calls.length;
    (view.find(n => n.type === Button).props.onClick as () => void)(); view.render(); await flush();
    expect(fixture.list).toHaveBeenCalledTimes(count + 1); view.unmount();
  });
});

describe("disposal detail retains business actions", () => {
  it("confirms and approves damaged stock from detail with the original API input", async () => {
    const view = mount(DisposalDetailPage); view.render(); await flush(); view.render();
    (view.find(n => n.type === Button && text(n).includes("Phê duyệt")).props.onClick as () => void)();
    view.render(); (view.find(n => n.type === ConfirmDialog).props.onConfirm as () => void)();
    await flush(); view.render();
    expect(fixture.detail).toHaveBeenCalledWith("disposal-001");
    expect(fixture.approve).toHaveBeenCalledExactlyOnceWith({ disposalId: "disposal-001" });
    expect(text(view.render())).toContain("Đã phê duyệt yêu cầu tiêu hủy."); view.unmount();
  });
  it("keeps customer return confirmation on the inspection route and forbids direct approval", async () => {
    fixture.detail.mockResolvedValue({ ...request, sourceType: "CUSTOMER_RETURN_INSPECTION", sourceInspectionId: "inspection-001" });
    const view = mount(DisposalDetailPage); view.render(); await flush(); view.render();
    const link = view.find(n => n.props.to === "/management/inventory/customer-returns/inspection-001");
    expect(text(link)).toContain("Mở phiếu kiểm định để xác nhận");
    expect(nodes(view.render()).some(n => n.type === Button && text(n).includes("Phê duyệt"))).toBe(false);
    expect(fixture.approve).not.toHaveBeenCalled(); view.unmount();
  });
  it("keeps the approval action hidden for staff without the existing role", async () => {
    fixture.canApprove = false;
    const view = mount(DisposalDetailPage); view.render(); await flush(); view.render();
    expect(nodes(view.render()).some(n => n.type === Button && text(n).includes("Phê duyệt"))).toBe(false);
    expect(fixture.approve).not.toHaveBeenCalled(); view.unmount();
  });
  it.each(["/management/inventory/disposals", "/management/inventory/disposals/disposal-001"])("uses the renamed breadcrumb for %s", pathname => {
    fixture.pathname = pathname; const view = mount(SystemTopbar); view.render();
    expect(text(view.render())).toContain("Kho›Tiêu hủy");
    expect(text(view.render())).not.toContain("Hỏng / tiêu hủy"); view.unmount();
  });
});
