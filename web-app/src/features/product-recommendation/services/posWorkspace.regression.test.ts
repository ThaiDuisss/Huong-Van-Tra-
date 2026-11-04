import { readFileSync } from "node:fs";
import ts from "typescript";
import { describe, expect, it, vi } from "vitest";
import { WorkspaceMutationLock } from "./recommendationLifecycle";
import { posVariantSelectionError } from "../../sales/services/posProductSelection";

// Execute the actual page handlers with their closure dependencies isolated.
// This avoids exporting private handlers or installing a DOM test framework.
const source = readFileSync(new URL("../../sales/pages/PosSalePage.tsx", import.meta.url), "utf8");
const file = ts.createSourceFile(
  "PosSalePage.tsx",
  source,
  ts.ScriptTarget.Latest,
  true,
  ts.ScriptKind.TSX,
);
const names = [
  "addProduct",
  "canLeaveCurrentInvoice",
  "selectInvoice",
  "createInvoice",
  "applySelectedCustomer",
  "closeCurrentInvoice",
  "closeInvoice",
  "completePayment",
  "updateCartItemQuantity",
  "clearCustomer",
  "resetCurrentSale",
];
const handlers: string[] = [];
let recommendationAdd = "";
function visit(node: ts.Node) {
  if (
    ts.isJsxSelfClosingElement(node) &&
    node.tagName.getText(file) === "ProductRecommendationLauncher"
  ) {
    const attribute = node.attributes.properties.find(
      (property) => ts.isJsxAttribute(property) && property.name.getText(file) === "onAdd",
    );
    if (
      attribute &&
      ts.isJsxAttribute(attribute) &&
      attribute.initializer &&
      ts.isJsxExpression(attribute.initializer) &&
      attribute.initializer.expression
    ) {
      recommendationAdd = attribute.initializer.expression.getText(file);
    }
  }
  if (ts.isFunctionDeclaration(node) && node.name && names.includes(node.name.text))
    handlers.push(node.getText(file));
  if (
    ts.isVariableDeclaration(node) &&
    ts.isIdentifier(node.name) &&
    names.includes(node.name.text) &&
    node.initializer &&
    ts.isCallExpression(node.initializer)
  ) {
    const callback = node.initializer.arguments[0];
    if (callback && ts.isArrowFunction(callback))
      handlers.push(`function ${node.name.text}() ${callback.body.getText(file)}`);
  }
  ts.forEachChild(node, visit);
}
visit(file);
if (handlers.length !== names.length)
  throw new Error("POS handlers changed; update the regression harness.");
if (!recommendationAdd)
  throw new Error("Recommendation callback changed; update the regression harness.");
const executable = ts.transpileModule(
  handlers.join("\n") + `\nconst addRecommendedProduct = ${recommendationAdd};`,
  {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
  },
).outputText;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function fixture(
  options: { online?: boolean; orderType?: string; customerId?: string | null } = {},
) {
  const noop = () => vi.fn();
  const state = {
    addingProductId: null,
    checkoutLoading: false,
    bankTransferPayment: null,
    deliveryState: { submitPending: false },
    online: options.online ?? true,
    draftOrder: { id: "order-a", status: "DRAFT" },
    currentBranchId: null,
    selectedCustomerId: options.customerId === undefined ? "customer-a" : options.customerId,
    orderType: options.orderType ?? "QUICK_SALE",
    posVariantSelectionError,
    currentInvoiceId: "invoice-1",
    workspaceMutation: { current: new WorkspaceMutationLock() },
    recommendationVersion: { current: 0 },
    recommendationGeneration: 0,
    invoiceTabs: {
      activeId: "tab-a",
      tabs: [
        { id: "tab-a", label: 1, sale: null },
        { id: "tab-b", label: 2, sale: null },
      ],
    },
    activeInvoiceIdRef: { current: "tab-a" },
    MAX_INVOICE_TABS: 10,
    backorderVariantIdsRef: { current: new Set<string>() },
    bankTransferBackorderRef: { current: null },
    stockByVariantIdRef: { current: {} },
    handledBankNotificationIdRef: { current: null },
    cartItemsById: {
      "variant-a": {
        itemId: "line-a",
        productId: "variant-a",
        productVariantId: "variant-a",
        name: "Trà",
        sku: "TEA",
        quantity: 1,
      },
    },
    reservedCouponUsage: null,
    initialDeliveryState: {},
    posOrderApi: {
      addDraftItem: vi.fn(),
      updateDraftItemQuantity: vi.fn(),
      cancelDraftOrder: vi.fn().mockResolvedValue(undefined),
    },
    releaseCouponUsage: vi.fn().mockResolvedValue(undefined),
    offerBackorder: vi.fn().mockResolvedValue(undefined),
    isOutOfStockError: () => false,
    getCartQuantity: vi.fn(() => 0),
    upsertLocalCartItem: noop(),
    applyDraftOrder: noop(),
    checkShelfShortage: vi.fn().mockResolvedValue(undefined),
    refreshBackorderEstimate: vi.fn().mockResolvedValue(undefined),
    logApiError: noop(),
    getErrorMessage: () => "Unavailable",
    setCheckoutError: noop(),
    setCheckoutSuccess: noop(),
    setAddingProductId: noop(),
    setSelectedCustomersByInvoice: noop(),
    patchCurrentDeliveryState: noop(),
    setRecommendationGeneration: noop(),
    setInvoiceTabs: noop(),
    captureCurrentSale: noop(),
    loadSaleIntoWorkspace: noop(),
    reviveExpiredDraft: noop(),
    newInvoiceTabId: () => "new-tab",
    nextInvoiceLabel: () => 3,
    setClosingInvoice: noop(),
    setDraftOrder: noop(),
    setCartItemsById: noop(),
    setBackorderOffer: noop(),
    setBackorderEstimate: noop(),
    setDepositEnabled: noop(),
    setDepositAmount: noop(),
    setNote: noop(),
    setCashReceived: noop(),
    setSelectedDiscountId: noop(),
    setReservedCouponUsage: noop(),
    setManualOrderDiscount: noop(),
    setBankTransferPayment: noop(),
    setBankTransferStatus: noop(),
    setBankTransferEventMessage: noop(),
    setQuantityUpdating: noop(),
    setPaymentMethod: noop(),
    updateLocalCartItemQuantity: noop(),
  };
  const run = new Function(
    ...Object.keys(state),
    `${executable}\nreturn {${names.join(",")},addRecommendedProduct};`,
  );
  const page = run(...Object.values(state)) as Record<string, (...args: unknown[]) => unknown>;
  return { state, page };
}
const product = {
  id: "variant-a",
  productVariantId: "variant-a",
  name: "Trà",
  sku: "TEA",
  sellableQuantity: 3,
  price: 10000,
  priceAvailable: true,
};

describe("actual POS handlers: async Add and invoice context", () => {
  it.each(["customer/reset generation", "active invoice"])(
    "rejects an old recommendation after changing %s",
    async (changed) => {
      const { state, page } = fixture();
      if (changed === "active invoice") state.activeInvoiceIdRef.current = "tab-b";
      else state.recommendationVersion.current += 1;
      await expect(page.addRecommendedProduct(product)).resolves.toBe(false);
      expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
      expect(state.offerBackorder).not.toHaveBeenCalled();
    },
  );
  it("a current AI selection calls the same manual Add action with the selected customer", async () => {
    const { state, page } = fixture();
    state.posOrderApi.addDraftItem.mockResolvedValue({ id: "order-a" });
    await expect(page.addRecommendedProduct(product)).resolves.toBe(true);
    expect(state.posOrderApi.addDraftItem).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        orderId: "order-a",
        customerId: "customer-a",
        productVariantId: "variant-a",
      }),
    );
  });
  it.each(["QUICK_SALE", "DELIVERY"])(
    "%s zero stock opens the existing offer without silently accepting backorder",
    async (orderType) => {
      const { state, page } = fixture({ orderType });
      const empty = { ...product, sellableQuantity: 0 };
      state.getCartQuantity.mockReturnValue(2);
      await expect(page.addProduct(empty)).resolves.toBe(false);
      expect(state.offerBackorder).toHaveBeenCalledExactlyOnceWith(
        { kind: "add", product: empty },
        "variant-a",
        "Trà",
        "TEA",
        3,
      );
      expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
    },
  );
  it("only sends allowBackorder after staff acceptance through the existing POS action", async () => {
    const { state, page } = fixture({ orderType: "DELIVERY" });
    state.posOrderApi.addDraftItem.mockResolvedValue({ id: "order-a" });
    await expect(page.addProduct({ ...product, sellableQuantity: 0 }, true)).resolves.toBe(true);
    expect(state.posOrderApi.addDraftItem).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        orderId: "order-a",
        customerId: "customer-a",
        productVariantId: "variant-a",
        orderType: "DELIVERY",
        allowBackorder: true,
      }),
    );
  });
  it("offline zero stock stays blocked without a backorder or order mutation", async () => {
    const { state, page } = fixture({ online: false });
    await expect(page.addProduct({ ...product, sellableQuantity: 0 })).resolves.toBe(false);
    expect(state.setCheckoutError).toHaveBeenCalledWith(expect.stringContaining("mất mạng"));
    expect(state.offerBackorder).not.toHaveBeenCalled();
    expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
    expect(state.upsertLocalCartItem).not.toHaveBeenCalled();
  });
  it.each([{ priceAvailable: false }, { price: -1 }, { price: Number.NaN }])(
    "manual and AI Add share the invalid price guard: %j",
    async (invalid) => {
      const { state, page } = fixture();
      await expect(page.addProduct({ ...product, ...invalid })).resolves.toBe(false);
      expect(state.setCheckoutError).toHaveBeenCalledWith("Biến thể chưa có giá bán hợp lệ.");
      expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
      expect(state.offerBackorder).not.toHaveBeenCalled();
    },
  );
  it("existing cart products reuse server Add and guest identity stays null", async () => {
    const { state, page } = fixture({ customerId: null });
    const order = { id: "order-a", items: [{ productVariantId: "variant-a", quantity: 2 }] };
    state.posOrderApi.addDraftItem.mockResolvedValue(order);
    await expect(page.addProduct(product)).resolves.toBe(true);
    expect(state.posOrderApi.addDraftItem).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        orderId: "order-a",
        customerId: null,
        productVariantId: "variant-a",
        allowBackorder: false,
      }),
    );
    expect(state.applyDraftOrder).toHaveBeenCalledExactlyOnceWith(order);
    expect(state.upsertLocalCartItem).not.toHaveBeenCalled();
  });
  it("manual offline Add with valid price and stock still upserts the existing local cart", async () => {
    const { state, page } = fixture({ online: false });
    await expect(page.addProduct(product)).resolves.toBe(true);
    expect(state.upsertLocalCartItem).toHaveBeenCalledExactlyOnceWith(product, 1);
    expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
  });
  it("invalidates AI and clears the selected customer on completed-sale reset", () => {
    const { state, page } = fixture();
    page.resetCurrentSale();
    const update = state.setSelectedCustomersByInvoice.mock.calls[0][0] as (
      value: object,
    ) => object;
    expect(update({ "invoice-1": { id: "old-customer" } })).toEqual({ "invoice-1": null });
    expect(state.recommendationVersion.current).toBe(1);
    expect(state.setDraftOrder).toHaveBeenCalledWith(null);
  });

  it("serializes quantity edits with Add and tab navigation", async () => {
    const { state, page } = fixture();
    const request = deferred<object>();
    state.posOrderApi.updateDraftItemQuantity.mockReturnValue(request.promise);
    const pending = page.updateCartItemQuantity("variant-a", 2);
    expect(page.canLeaveCurrentInvoice()).toBe(false);
    await expect(page.addProduct(product)).resolves.toBe(false);
    expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
    const order = { id: "order-a", items: [] };
    request.resolve(order);
    await expect(pending).resolves.toBe(true);
    expect(state.applyDraftOrder).toHaveBeenCalledExactlyOnceWith(order);
    expect(state.workspaceMutation.current.isLocked()).toBe(false);
  });
  it("locks customer/tab/new/close/checkout while Add is pending, then applies only that order", async () => {
    const { state, page } = fixture();
    const request = deferred<object>();
    state.posOrderApi.addDraftItem.mockReturnValue(request.promise);
    const pending = page.addProduct(product);
    expect(state.workspaceMutation.current.isLocked()).toBe(true);
    expect(page.canLeaveCurrentInvoice()).toBe(false);
    page.selectInvoice("tab-b");
    page.createInvoice();
    page.applySelectedCustomer({ id: "another" });
    await page.closeInvoice("tab-a");
    await page.completePayment();
    expect(state.setInvoiceTabs).not.toHaveBeenCalled();
    expect(state.setSelectedCustomersByInvoice).not.toHaveBeenCalled();
    expect(state.posOrderApi.cancelDraftOrder).not.toHaveBeenCalled();
    await expect(page.addProduct(product)).resolves.toBe(false);
    expect(state.posOrderApi.addDraftItem).toHaveBeenCalledTimes(1);
    expect(state.posOrderApi.addDraftItem.mock.calls[0][0]).toMatchObject({
      orderId: "order-a",
      customerId: "customer-a",
      productVariantId: "variant-a",
    });
    const order = { id: "order-a", items: [product] };
    request.resolve(order);
    await expect(pending).resolves.toBe(true);
    expect(state.applyDraftOrder).toHaveBeenCalledExactlyOnceWith(order);
    expect(state.activeInvoiceIdRef.current).toBe("tab-a");
    expect(state.workspaceMutation.current.isLocked()).toBe(false);
    expect(page.canLeaveCurrentInvoice()).toBe(true);
  });

  it("never retries an ambiguous Add failure and releases the navigation lock", async () => {
    const { state, page } = fixture();
    state.posOrderApi.addDraftItem.mockRejectedValue(new Error("Timeout after possible commit"));
    await expect(page.addProduct(product)).resolves.toBe(false);
    expect(state.posOrderApi.addDraftItem).toHaveBeenCalledTimes(1);
    expect(state.applyDraftOrder).not.toHaveBeenCalled();
    expect(state.workspaceMutation.current.isLocked()).toBe(false);
  });

  it("closing the last invoice clears the customer and invalidates recommendation generation", async () => {
    const { state, page } = fixture();
    state.invoiceTabs.tabs.splice(1);
    await page.closeInvoice("tab-a");
    expect(state.setSelectedCustomersByInvoice).toHaveBeenCalledWith({ "invoice-1": null });
    expect(state.recommendationVersion.current).toBe(1);
    expect(state.setRecommendationGeneration).toHaveBeenCalledTimes(1);
    expect(state.setInvoiceTabs).toHaveBeenCalledWith({
      activeId: "tab-a",
      tabs: [{ id: "tab-a", label: 1, sale: null }],
    });
    expect(state.workspaceMutation.current.isLocked()).toBe(false);
  });

  it("does not start Add while active invoice cancellation is pending", async () => {
    const { state, page } = fixture();
    const cancellation = deferred<void>();
    state.posOrderApi.cancelDraftOrder.mockReturnValue(cancellation.promise);
    const close = page.closeInvoice("tab-a");
    await Promise.resolve();
    expect(state.workspaceMutation.current.isLocked()).toBe(true);
    await expect(page.addProduct(product)).resolves.toBe(false);
    expect(state.posOrderApi.addDraftItem).not.toHaveBeenCalled();
    cancellation.resolve();
    await close;
    expect(state.workspaceMutation.current.isLocked()).toBe(false);
  });
});
