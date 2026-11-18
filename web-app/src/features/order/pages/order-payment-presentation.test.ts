import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderSummaryResponse } from "../types/order.types";

type Slot = { value?: unknown; deps?: readonly unknown[]; cleanup?: () => void };
type Hooks = { slots: Slot[]; cursor: number; effects: (() => void)[] };
const fixture = vi.hoisted(() => ({
  active: null as Hooks | null, list: vi.fn(), stats: vi.fn(), detail: vi.fn(),
  ordersExport: vi.fn(), invoicesExport: vi.fn(), returns: vi.fn(), refund: vi.fn(),
}));
vi.mock("react", async original => {
  const actual = await original<typeof import("react")>();
  const slot = () => {
    const hooks = fixture.active!;
    return hooks.slots[hooks.cursor++] ?? (hooks.slots[hooks.cursor - 1] = {});
  };
  return { ...actual,
    useState<T>(initial: T | (() => T)) {
      if (!fixture.active) return actual.useState(initial);
      const s = slot(); if (!("value" in s)) s.value = typeof initial === "function" ? (initial as () => T)() : initial;
      return [s.value, (value: T | ((before: T) => T)) => {
        s.value = typeof value === "function" ? (value as (v: T) => T)(s.value as T) : value;
      }];
    },
    useEffect(effect: () => void | (() => void), deps?: readonly unknown[]) {
      if (!fixture.active) return actual.useEffect(effect, deps);
      const s = slot();
      if (!s.deps || !deps || s.deps.length !== deps.length || s.deps.some((v, i) => !Object.is(v, deps[i]))) {
        s.deps = deps; fixture.active.effects.push(() => { s.cleanup?.(); s.cleanup = effect() || undefined; });
      }
    },
  };
});
vi.mock("react-router-dom", async original => ({ ...await original<typeof import("react-router-dom")>(), useNavigate: () => vi.fn() }));
vi.mock("../services/order.api", () => ({
  getOrders: fixture.list, getOrderStats: fixture.stats, getOrderById: fixture.detail,
  exportOrders: fixture.ordersExport, exportInvoices: fixture.invoicesExport,
}));
vi.mock("../services/order-return.api", () => ({ getOrderReturns: fixture.returns, getOrderRefundDetail: fixture.refund }));

import { getInvoicePaymentStatus, getInvoicePaymentStatusText, getPaymentStatusText } from "../utils/order-formatters";
import { getInvoiceRefundedAmount } from "../services/invoice-refund-summary";
import { InvoiceDetailPanel } from "../components/InvoiceDetailPanel";
import { PaymentStatusBadge } from "../components/PaymentStatusBadge";
import { ExportDropdown } from "../components/ExportDropdown";
import { OrderDataTable, OrderStatusFilters, OrderPagination, type OrderTableColumn } from "../components/shared/OrderListUI";
import { OrderListPage } from "./OrderListPage";
import { InvoiceListPage } from "./InvoiceListPage";

type Node = ReactElement<Record<string, unknown>>;
function nodes(tree: ReactNode): Node[] {
  const result: Node[] = [];
  Children.forEach(tree, n => {
    if (!isValidElement<Record<string, unknown>>(n)) return;
    result.push(n, ...nodes(n.props.children as ReactNode), ...nodes(n.props.actions as ReactNode),
      ...nodes(n.props.pagination as ReactNode), ...nodes(n.props.value as ReactNode));
  }); return result;
}
function mount(render: () => ReactElement) {
  const hooks: Hooks = { slots: [], cursor: 0, effects: [] }; let tree: ReactElement;
  return {
    render() { hooks.cursor = 0; fixture.active = hooks; tree = render(); fixture.active = null; hooks.effects.splice(0).forEach(f => f()); return tree; },
    find(predicate: (n: Node) => boolean) { const n = nodes(tree).find(predicate); if (!n) throw new Error("Missing fixture element"); return n; },
    unmount() { hooks.slots.forEach(s => s.cleanup?.()); },
  };
}
const rows: OrderSummaryResponse[] = [
  { id: "unpaid", status: "PENDING_PAYMENT", paymentStatus: "UNPAID" },
  { id: "paid", status: "PAYMENT_CONFIRMED", paymentStatus: "PAID" },
  { id: "completed", status: "COMPLETED", paymentStatus: "PAID" },
  { id: "partial-refund", status: "COMPLETED", paymentStatus: "PARTIALLY_REFUNDED", paymentStatusLabel: "Hoàn tiền một phần" },
  { id: "full-refund", status: "COMPLETED", paymentStatus: "REFUNDED", paymentStatusLabel: "Đã hoàn tiền" },
  { id: "expired", status: "EXPIRED", paymentStatus: "UNPAID" },
];
const flush = async () => { for (let n = 0; n < 20; n++) await Promise.resolve(); };
beforeEach(() => {
  vi.clearAllMocks(); vi.useFakeTimers(); vi.stubGlobal("window", { setTimeout, clearTimeout });
  fixture.list.mockResolvedValue({ content: rows, totalElements: 6, totalPages: 1, number: 0, size: 10 });
  fixture.stats.mockResolvedValue({ total: 6, pending: 1, processing: 1, completed: 3, paidInvoices: 2, returnedOrRefunded: 2 });
  fixture.returns.mockResolvedValue([{ id: "return-1", status: "COMPLETED", refundRequired: true }]);
  fixture.refund.mockResolvedValue({ orderId: "order-1", totalAmountRefunded: "20000", refundStatus: "REFUNDED" });
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe("invoice payment presentation keeps raw settlement/refund state", () => {
  it.each([
    ["UNPAID", "UNPAID", "Chưa thanh toán"], ["WAITING_PAYMENT", "WAITING_PAYMENT", "Chưa thanh toán"],
    ["PAID", "PAID", "Đã thanh toán"], ["PARTIALLY_REFUNDED", "PAID", "Đã thanh toán"],
    ["REFUNDED", "PAID", "Đã thanh toán"], ["FAILED", "FAILED", "Thanh toán thất bại"],
    ["PARTIALLY_PAID", "PARTIALLY_PAID", "Đã đặt cọc"], ["NEED_REVIEW", "NEED_REVIEW", "Chờ kiểm tra thanh toán"],
  ])("presents %s as %s without mutating API data", (paymentStatus, status, label) => {
    const order = Object.freeze({ id: "order-1", paymentStatus });
    expect(getInvoicePaymentStatus(order)).toBe(status);
    expect(getInvoicePaymentStatusText(order)).toBe(label);
    expect(order.paymentStatus).toBe(paymentStatus);
  });
  it("retains explicit refund labels for reconciliation outside the invoice payment column", () => {
    expect(getPaymentStatusText(rows[3])).toBe("Hoàn tiền một phần");
    expect(getPaymentStatusText(rows[4])).toBe("Đã hoàn tiền");
    const tree = InvoiceDetailPanel({ order: { ...rows[3], paidAmount: "100000" } });
    expect(nodes(tree).find(n => n.type === PaymentStatusBadge)?.props).toMatchObject({ status: "PAID", label: "Đã thanh toán" });
    expect(nodes(tree).some(n => n.props.label === "Hoàn tiền" && n.props.value === "Hoàn tiền một phần")).toBe(true);
  });
  it("removes refund/shipping lifecycle tabs without dropping refunded or expired records", async () => {
    const view = mount(OrderListPage); view.render(); await vi.advanceTimersByTimeAsync(0); await flush(); view.render();
    const options = view.find(n => n.type === OrderStatusFilters).props.options as { key: string }[];
    expect(options.map(o => o.key)).toEqual(["ALL", "PENDING_PAYMENT", "PROCESSING", "COMPLETED"]);
    expect(view.find(n => n.type === OrderDataTable).props.rows).toEqual(rows);
    expect(view.find(n => n.type === OrderPagination).props.totalItems).toBe(6);
    expect(fixture.list).toHaveBeenLastCalledWith(expect.not.objectContaining({ paymentStatus: expect.anything() }));
    (view.find(n => n.type === OrderStatusFilters).props.onChange as (s: string) => void)("COMPLETED");
    view.render(); await vi.advanceTimersByTimeAsync(0); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: "COMPLETED", page: 0 }));
    (view.find(n => n.type === ExportDropdown).props.onExport as (mode: string) => void)("filtered");
    expect(fixture.ordersExport).toHaveBeenCalledWith(expect.objectContaining({ status: "COMPLETED", paymentStatus: undefined }));
    view.unmount();
  });
  it("uses payment presentation for invoice cells and the same paid bucket in filters/export", async () => {
    const view = mount(InvoiceListPage); view.render(); await vi.advanceTimersByTimeAsync(0); await flush(); view.render();
    const table = view.find(n => n.type === OrderDataTable);
    expect(table.props.rows).toEqual(rows);
    const column = (table.props.columns as OrderTableColumn<OrderSummaryResponse>[]).find(c => c.header === "Thanh toán")!;
    for (const row of rows.slice(1, 5)) {
      expect(nodes(column.render(row, 0)).find(n => n.type === PaymentStatusBadge)?.props).toMatchObject({ status: "PAID", label: "Đã thanh toán" });
    }
    const filters = view.find(n => n.type === OrderStatusFilters);
    expect(filters.props.options).toEqual(expect.arrayContaining([expect.objectContaining({ key: "PAID", count: 4 })]));
    (filters.props.onChange as (s: string) => void)("PAID"); view.render();
    await vi.advanceTimersByTimeAsync(0); await flush(); view.render();
    expect(fixture.list).toHaveBeenLastCalledWith(expect.objectContaining({ paymentStatus: "PAID", sort: "invoicePayment" }));
    (view.find(n => n.type === ExportDropdown).props.onExport as (mode: string) => void)("filtered");
    expect(fixture.invoicesExport).toHaveBeenCalledWith(expect.objectContaining({ paymentStatus: "PAID", sort: "invoicePayment" }));
    view.unmount();
  });
});

describe("invoice refund amounts come from authoritative order-level evidence", () => {
  it("does not sum the cumulative amount once per return", async () => {
    fixture.returns.mockResolvedValue([{ id: "pending", status: "RECEIVED" }, { id: "return-1", status: "COMPLETED" }, { id: "return-2", status: "COMPLETED" }]);
    expect(await getInvoiceRefundedAmount("order-1")).toBe(20000);
    expect(fixture.refund).toHaveBeenCalledExactlyOnceWith("return-1");
  });
  it.each([null, "", "invalid", -1])("does not invent refunded money for missing/invalid evidence %s", async totalAmountRefunded => {
    fixture.refund.mockResolvedValue({ orderId: "order-1", totalAmountRefunded });
    await expect(getInvoiceRefundedAmount("order-1")).rejects.toThrow();
  });
  it("rejects evidence belonging to another order or a missing return", async () => {
    fixture.refund.mockResolvedValue({ orderId: "other", totalAmountRefunded: 20000 });
    await expect(getInvoiceRefundedAmount("order-1")).rejects.toThrow();
    fixture.returns.mockResolvedValue([]);
    await expect(getInvoiceRefundedAmount("order-1")).rejects.toThrow();
  });
  it.each([[20000, "80.000đ"], [100000, "0đ"]])("shows original paid, actual refunded %s and net retained", async (amount, net) => {
    fixture.refund.mockResolvedValue({ orderId: "order-1", totalAmountRefunded: amount });
    const panel = InvoiceDetailPanel({ order: { id: "order-1", paymentStatus: "PARTIALLY_REFUNDED", paidAmount: "100000" } });
    const child = nodes(panel).find(n => typeof n.type === "function" && n.type.name === "InvoiceRefundDetails")!;
    const view = mount(() => (child.type as (props: unknown) => ReactElement)(child.props));
    view.render(); await flush(); view.render();
    expect(view.find(n => n.props.label === "Đã hoàn").props.value).toContain(amount.toLocaleString("vi-VN"));
    expect(view.find(n => n.props.label === "Thực nhận sau hoàn").props.value).toBe(net);
    view.unmount();
    expect(renderToStaticMarkup(InvoiceDetailPanel({ order: { id: "order-1", paymentStatus: "REFUNDED", paidAmount: "100000" } }))).toContain("Khách trả");
  });
});
