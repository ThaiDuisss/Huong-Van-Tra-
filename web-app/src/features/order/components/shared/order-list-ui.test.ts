import { Children, createElement, isValidElement } from "react";
import type {
  ButtonHTMLAttributes,
  HTMLAttributes,
  ReactElement,
  ReactNode,
  KeyboardEvent,
} from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import ts from "typescript";
import { describe, expect, it, vi } from "vitest";
import {
  OrderDataTable,
  OrderPageLayout,
  OrderPageStates,
  OrderPagination,
  OrderStatusFilters,
  OrderToolbar,
} from "./OrderListUI";
import { Button } from "../../../../components/ui/Button";
import { SearchInput } from "../../../../components/ui/SearchInput";
import { getOrderPageNumbers, getOrderRecordRange } from "./order-list-ui.utils";

function elements(node: ReactNode): ReactElement[] {
  return Children.toArray(node).flatMap((child) =>
    isValidElement<{ children?: ReactNode }>(child)
      ? [child, ...elements(child.props.children)]
      : [],
  );
}
/** Như `elements`, nhưng mở cả component hàm (không dùng hook) để tới được thẻ DOM bên trong. */
function renderedElements(node: ReactNode): ReactElement[] {
  return Children.toArray(node).flatMap((child) => {
    if (!isValidElement<{ children?: ReactNode }>(child)) return [];
    if (typeof child.type === "function") {
      const render = child.type as (props: unknown) => ReactNode;
      return renderedElements(render(child.props));
    }
    return [child, ...renderedElements(child.props.children)];
  });
}
function button(element: ReactElement) {
  return element.props as ButtonHTMLAttributes<HTMLButtonElement>;
}
function html(node: ReactNode) {
  return renderToStaticMarkup(node);
}

describe("shared Order list presentation", () => {
  it("accepts screen-specific titles, sidebar state, actions and one spacing override", () => {
    const markup = html(
      createElement(OrderPageLayout, {
        title: "Hoàn hàng",
        description: "Theo dõi phiếu hoàn",
        sidebar: "Thời gian",
        sidebarCollapsed: true,
        style: { "--order-ui-gap": "20px" } as React.CSSProperties,
        actions: createElement("button", null, "Làm mới"),
        children: "Nội dung",
      }),
    );
    expect(markup).toContain("Hoàn hàng</h1>");
    expect(markup).toContain("--order-ui-gap:20px");
    expect(markup).toContain("Làm mới");
    expect(markup).toContain("Nội dung");
    expect(markup).toContain("pageTimeCollapsed");
  });

  it("keeps each screen's search and Escape callbacks controlled by that screen", () => {
    const onChange = vi.fn();
    const onKeyDown = vi.fn();
    const tree = OrderToolbar({
      search: {
        "aria-label": "Tìm hóa đơn",
        placeholder: "Mã hóa đơn",
        value: "HD-01",
        onChange,
        onKeyDown,
      },
    });
    const input = elements(tree).find((node) => node.type === SearchInput)!;
    expect(input.props).toMatchObject({
      value: "HD-01",
      onChange,
      onKeyDown,
      "aria-label": "Tìm hóa đơn",
    });
    expect(html(tree)).toContain('placeholder="Mã hóa đơn"');
  });

  it("renders distinct status options/counts and dispatches only the selected key", () => {
    const onChange = vi.fn();
    const tree = OrderStatusFilters({
      options: [
        { key: "ALL", label: "Tất cả", count: 0 },
        { key: "PAID", label: "Đã thanh toán", count: 12 },
      ],
      value: "PAID",
      onChange,
      label: "Thanh toán:",
    });
    const buttons = elements(tree).filter((node) => node.type === "button");
    expect(button(buttons[0])["aria-pressed"]).toBe(false);
    expect(button(buttons[1])["aria-pressed"]).toBe(true);
    button(buttons[0]).onClick?.({} as React.MouseEvent<HTMLButtonElement>);
    expect(onChange).toHaveBeenCalledExactlyOnceWith("ALL");
    expect(html(tree)).toContain("Tất cả<span>0</span>");
    expect(html(tree)).toContain("Đã thanh toán<span>12</span>");
  });

  it("preserves error filter semantics and disabled native button accessibility", () => {
    const markup = html(
      createElement(OrderStatusFilters, {
        options: [{ key: "FAILED", label: "Có lỗi", isError: true, disabled: true }],
        value: "FAILED",
        onChange: vi.fn(),
      }),
    );
    expect(markup).toContain("errorFilterChip");
    expect(markup).toContain('aria-pressed="true"');
    expect(markup).toContain("disabled");
  });

  const tableProps = () => ({
    columns: [
      { key: "code", header: "Mã", render: (row: { id: string }) => row.id },
      {
        key: "action",
        header: "Thao tác",
        render: () => createElement("button", null, "Chi tiết"),
      },
    ],
    rows: [{ id: "ROW-1" }],
    getRowKey: (row: { id: string }) => row.id,
    loading: false,
    error: null as string | null,
    loadingTitle: "Đang tải",
    errorTitle: "Lỗi tải danh sách",
    emptyTitle: "Không có bản ghi",
    caption: "Phiếu",
    pagination: "Phân trang",
  });
  it("renders caller columns/cells and sends row identity/index to its own action", () => {
    const onRowClick = vi.fn();
    const tree = OrderDataTable({ ...tableProps(), onRowClick, isRowActive: () => true });
    const row = elements(tree).find(
      (node) =>
        node.type === "tr" && (node.props as { "data-active"?: boolean })["data-active"] === true,
    )!;
    const props = row.props as HTMLAttributes<HTMLTableRowElement>;
    props.onClick?.({} as React.MouseEvent<HTMLTableRowElement>);
    expect(onRowClick).toHaveBeenCalledExactlyOnceWith({ id: "ROW-1" }, 0);
    const markup = html(tree);
    expect(markup).toContain('scope="col">Mã');
    expect(markup).toContain("ROW-1");
    expect(markup).toContain("Chi tiết");
    expect(markup).toContain("Phân trang");
  });

  it("handles row keyboard activation without consuming a nested action button event", () => {
    const onRowClick = vi.fn();
    const tree = OrderDataTable({ ...tableProps(), onRowClick });
    const row = elements(tree).find(
      (node) => node.type === "tr" && (node.props as { tabIndex?: number }).tabIndex === 0,
    )!;
    const props = row.props as HTMLAttributes<HTMLTableRowElement>;
    const currentTarget = {} as HTMLTableRowElement;
    const preventDefault = vi.fn();
    props.onKeyDown?.({
      key: "Enter",
      target: currentTarget,
      currentTarget,
      preventDefault,
    } as unknown as KeyboardEvent<HTMLTableRowElement>);
    expect(onRowClick).toHaveBeenCalledTimes(1);
    expect(preventDefault).toHaveBeenCalledTimes(1);
    props.onKeyDown?.({
      key: "Enter",
      target: {},
      currentTarget,
      preventDefault,
    } as unknown as KeyboardEvent<HTMLTableRowElement>);
    expect(onRowClick).toHaveBeenCalledTimes(1);
  });

  it("renders API errors as alerts even when the API page is empty", () => {
    const markup = html(
      createElement(OrderDataTable, {
        ...tableProps(),
        rows: [],
        error: "Không thể kết nối đến máy chủ.",
      }),
    );
    expect(markup).toContain('role="alert"');
    expect(markup).toContain("Không thể kết nối đến máy chủ.");
    expect(markup).not.toContain("Không có bản ghi");
    expect(markup).toContain('colSpan="2"');
  });

  it("hides stale data while loading and separates genuine empty results", () => {
    const loading = html(
      createElement(OrderDataTable, { ...tableProps(), loading: true, error: "Lỗi cũ" }),
    );
    expect(loading).toContain("Đang tải");
    expect(loading).not.toContain("ROW-1");
    expect(loading).not.toContain("Lỗi cũ");
    const empty = html(createElement(OrderDataTable, { ...tableProps(), rows: [] }));
    expect(empty).toContain("Không có bản ghi");
    expect(empty).not.toContain('role="alert"');
  });

  it("retains an optional retry action without inventing network or empty messages", () => {
    const retry = vi.fn();
    const tree = OrderPageStates({
      kind: "error",
      title: "Không tải được",
      description: "Lỗi API cụ thể",
      onRetry: retry,
    });
    const action = elements(tree).find((node) => node.type === Button)!;
    button(action).onClick?.({} as React.MouseEvent<HTMLButtonElement>);
    expect(retry).toHaveBeenCalledTimes(1);
    expect(html(tree)).toContain("Lỗi API cụ thể");
    expect(
      html(createElement(OrderPageStates, { kind: "empty", title: "Chưa có phiếu" })),
    ).not.toContain("Thử lại");
  });

  it("preserves backend record page/size independently from the requested current page", () => {
    const tree = OrderPagination({
      page: 2,
      recordPage: 1,
      pageSize: 25,
      totalItems: 83,
      totalPages: 4,
      itemLabel: "hóa đơn",
      onPageChange: vi.fn(),
    });
    expect(html(tree)).toContain("Hiển thị 26 - 50 trong 83 hóa đơn");
    expect(html(tree)).toMatch(/aria-current="page"[^>]*>3</);
  });

  it("uses the server page count and sends bounded Previous/Next selections", () => {
    const change = vi.fn();
    const tree = OrderPagination({
      page: 1,
      pageSize: 10,
      totalItems: 100,
      totalPages: 3,
      itemLabel: "dòng",
      onPageChange: change,
    });
    const buttons = renderedElements(tree).filter((node) => node.type === "button");
    expect(buttons).toHaveLength(5);
    button(buttons[0]).onClick?.({} as React.MouseEvent<HTMLButtonElement>);
    button(buttons.at(-1)!).onClick?.({} as React.MouseEvent<HTMLButtonElement>);
    expect(change.mock.calls).toEqual([[0], [2]]);
  });

  it("disables boundary/loading pagination and retains the reference's five-page window", () => {
    expect(getOrderPageNumbers(0, 0)).toEqual([0]);
    expect(getOrderPageNumbers(9, 7)).toEqual([4, 5, 6, 7, 8]);
    expect(getOrderRecordRange(0, 10, 0)).toEqual({ from: 0, to: 0 });
    expect(getOrderRecordRange(2, 10, 23)).toEqual({ from: 21, to: 23 });
    const tree = OrderPagination({
      page: 0,
      pageSize: 10,
      totalItems: 0,
      totalPages: 0,
      itemLabel: "phiếu",
      onPageChange: vi.fn(),
    });
    const buttons = renderedElements(tree).filter((node) => node.type === "button");
    expect(button(buttons[0]).disabled).toBe(true);
    expect(button(buttons.at(-1)!).disabled).toBe(true);
    const busy = OrderPagination({
      page: 1,
      pageSize: 10,
      totalItems: 40,
      totalPages: 4,
      itemLabel: "phiếu",
      onPageChange: vi.fn(),
      disabled: true,
    });
    expect(
      renderedElements(busy)
        .filter((node) => node.type === "button")
        .every((node) => button(node).disabled),
    ).toBe(true);
  });
});

describe("shared style propagation across Order list routes", () => {
  const screens = [
    ["OrderListPage", "visibleOrders", "isOrdersLoading", "ordersError", 4],
    ["InvoiceListPage", "visibleInvoices", "isListLoading", "listError", 5],
    ["OrderRefundPage", "visibleRefunds", "isListLoading", "listError", 6],
    ["OrderReturnPage", "visibleRows", "isRowsLoading", "rowsError", 5],
  ] as const;
  it.each(screens)(
    "%s imports and renders the same shared family with its own state",
    (name, rows, loading, error, columnCount) => {
      const source = readFileSync(new URL(`../../pages/${name}.tsx`, import.meta.url), "utf8");
      const file = ts.createSourceFile(
        `${name}.tsx`,
        source,
        ts.ScriptTarget.Latest,
        true,
        ts.ScriptKind.TSX,
      );
      const tags: ts.JsxOpeningLikeElement[] = [];
      function visit(node: ts.Node) {
        if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) tags.push(node);
        ts.forEachChild(node, visit);
      }
      visit(file);
      for (const component of [
        "OrderPageLayout",
        "OrderToolbar",
        "OrderStatusFilters",
        "OrderDataTable",
        "OrderPagination",
      ])
        expect(tags.filter((tag) => tag.tagName.getText(file) === component)).toHaveLength(1);
      expect(source).toContain('from "../components/shared/OrderListUI"');
      expect(source).not.toMatch(
        /styles\.(page|header|tableCard|tableWrap|pagination|filterToolbar)\b/,
      );
      const table = tags.find((tag) => tag.tagName.getText(file) === "OrderDataTable")!;
      function value(key: string) {
        const attribute = table.attributes.properties.find(
          (p) => ts.isJsxAttribute(p) && p.name.getText(file) === key,
        ) as ts.JsxAttribute;
        return (attribute.initializer as ts.JsxExpression).expression!;
      }
      expect(value("rows").getText(file)).toBe(rows);
      expect(value("loading").getText(file)).toBe(loading);
      expect(value("error").getText(file)).toBe(error);
      expect((value("columns") as ts.ArrayLiteralExpression).elements).toHaveLength(columnCount);
    },
  );

  it("centralizes layout/header/toolbar/table/pagination styles in one scoped CSS module", () => {
    const source = readFileSync(new URL("./OrderListUI.tsx", import.meta.url), "utf8");
    const css = readFileSync(new URL("./OrderListUI.module.css", import.meta.url), "utf8");
    expect(source).toContain('from "./OrderListUI.module.css"');
    expect(css).toContain("--order-ui-gap: 12px");
    expect(css.match(/gap: var\(--order-ui-gap\)/g)!.length).toBeGreaterThanOrEqual(2);
    expect(css).toContain("@media (max-width: 640px)");
    expect(css).not.toContain("!important");
  });
});
