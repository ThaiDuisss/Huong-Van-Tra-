import { readFileSync } from "node:fs";
import ts from "typescript";
import { describe, expect, it, vi } from "vitest";
import {
  initialPosVariant,
  posVariantSelectionError,
  PosProductSelectionError,
} from "../../sales/services/posProductSelection";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import type { RecommendedProductView } from "../types/productRecommendation.types";

// Exercise the actual component handlers in the existing Node test environment.
const source = readFileSync(
  new URL("../components/ProductRecommendationResults.tsx", import.meta.url),
  "utf8",
);
const file = ts.createSourceFile(
  "ProductRecommendationResults.tsx",
  source,
  ts.ScriptTarget.Latest,
  true,
  ts.ScriptKind.TSX,
);
let handlers: string[] = [];
function visit(node: ts.Node) {
  if (ts.isFunctionDeclaration(node) && node.name?.text === "RecommendedProductCard") {
    handlers = (node.body?.statements ?? [])
      .filter(
        (statement) =>
          ts.isFunctionDeclaration(statement) &&
          ["add", "reloadMetadata"].includes(statement.name?.text ?? ""),
      )
      .map((statement) => statement.getText(file));
  }
  ts.forEachChild(node, visit);
}
visit(file);
if (handlers.length !== 2) throw new Error("Card handlers changed; update the regression harness.");
const executable = ts.transpileModule(handlers.join("\n"), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
}).outputText;
const parent = "22222222-2222-4222-8222-222222222222";
const variantId = "44444444-4444-4444-8444-444444444444";
const variant: SaleProductPreview = {
  id: variantId,
  productId: parent,
  productVariantId: variantId,
  sku: "TEA",
  name: "Trà",
  variant: "100g",
  barcode: "",
  category: "Trà",
  price: 10000,
  priceAvailable: true,
  imageUrl: "",
  availableQuantity: 3,
  sellableQuantity: 3,
  shelfQuantity: 3,
  warehouseQuantity: 0,
};
const recommendation = { rank: 1, productId: parent, productCode: "TEA", name: "Trà", score: 0.6 };
const view: RecommendedProductView = { recommendation, variants: [variant] };
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function fixture() {
  const onAdd = vi.fn(async () => true);
  const state = {
    disabled: false,
    selected: variant,
    variantId,
    recommendation,
    onAdd,
    addRef: { current: onAdd },
    live: { current: true },
    busy: { current: false },
    controller: { current: null as AbortController | null },
    initialPosVariant,
    posVariantSelectionError,
    PosProductSelectionError,
    loadRecommendedProduct: vi.fn<() => Promise<RecommendedProductView>>().mockResolvedValue(view),
    resolveRecommendedVariant: vi
      .fn<() => Promise<SaleProductPreview>>()
      .mockResolvedValue(variant),
    onPending: vi.fn(),
    setRefreshing: vi.fn(),
    setError: vi.fn(),
    setAdded: vi.fn(),
    setDetails: vi.fn(),
    setVariantId: vi.fn(),
  };
  const run = new Function(...Object.keys(state), `${executable}\nreturn {add,reloadMetadata};`);
  return {
    state,
    card: run(...Object.values(state)) as { add(): Promise<void>; reloadMetadata(): Promise<void> },
  };
}

describe("actual recommendation card handlers", () => {
  it("retries metadata and restores the single variant without adding automatically", async () => {
    const { state, card } = fixture();
    await card.reloadMetadata();
    expect(state.loadRecommendedProduct).toHaveBeenCalledExactlyOnceWith(
      recommendation,
      expect.any(AbortSignal),
    );
    expect(state.setDetails).toHaveBeenCalledExactlyOnceWith(view);
    expect(state.setVariantId).toHaveBeenCalledWith(variantId);
    expect(state.onAdd).not.toHaveBeenCalled();
    expect(state.busy.current).toBe(false);
  });
  it("clears an old selection and requires staff choice when retry returns multiple variants", async () => {
    const { state, card } = fixture();
    state.loadRecommendedProduct.mockResolvedValue({
      ...view,
      variants: [variant, { ...variant, id: parent, productVariantId: parent }],
    });
    await card.reloadMetadata();
    expect(state.setVariantId).toHaveBeenCalledWith("");
    expect(state.onAdd).not.toHaveBeenCalled();
  });
  it("keeps an empty/error retry unavailable without retaining a stale selected variant", async () => {
    const { state, card } = fixture();
    state.loadRecommendedProduct.mockResolvedValue({
      recommendation,
      variants: [],
      error: "POS không tìm thấy biến thể đang bán",
    });
    await card.reloadMetadata();
    expect(state.setDetails).toHaveBeenCalledWith(
      expect.objectContaining({ variants: [], error: expect.any(String) }),
    );
    expect(state.setVariantId).toHaveBeenCalledWith("");
    expect(state.onAdd).not.toHaveBeenCalled();
  });
  it("ignores a retry result after customer/tab context unmounts and aborts", async () => {
    const { state, card } = fixture();
    const pending = deferred<RecommendedProductView>();
    state.loadRecommendedProduct.mockReturnValue(pending.promise);
    const work = card.reloadMetadata();
    state.live.current = false;
    state.controller.current?.abort();
    pending.resolve(view);
    await work;
    expect(state.setDetails).not.toHaveBeenCalled();
    expect(state.setVariantId).not.toHaveBeenCalled();
    expect(state.onAdd).not.toHaveBeenCalled();
  });
  it("serializes retry and Add before a React render", async () => {
    const { state, card } = fixture();
    const pending = deferred<RecommendedProductView>();
    state.loadRecommendedProduct.mockReturnValue(pending.promise);
    const work = card.reloadMetadata();
    await card.reloadMetadata();
    await card.add();
    expect(state.loadRecommendedProduct).toHaveBeenCalledTimes(1);
    expect(state.resolveRecommendedVariant).not.toHaveBeenCalled();
    expect(state.onAdd).not.toHaveBeenCalled();
    pending.resolve(view);
    await work;
  });
  it("does not start duplicate Add/retry while the variant recheck is pending", async () => {
    const { state, card } = fixture();
    const pending = deferred<SaleProductPreview>();
    state.resolveRecommendedVariant.mockReturnValue(pending.promise);
    const work = card.add();
    await card.add();
    await card.reloadMetadata();
    expect(state.resolveRecommendedVariant).toHaveBeenCalledTimes(1);
    expect(state.loadRecommendedProduct).not.toHaveBeenCalled();
    pending.resolve(variant);
    await work;
    expect(state.onAdd).toHaveBeenCalledExactlyOnceWith(variant);
  });
  it("never hands a late verified variant to Add after the popup/context closes", async () => {
    const { state, card } = fixture();
    const pending = deferred<SaleProductPreview>();
    state.resolveRecommendedVariant.mockReturnValue(pending.promise);
    const work = card.add();
    state.live.current = false;
    state.controller.current?.abort();
    pending.resolve(variant);
    await work;
    expect(state.onAdd).not.toHaveBeenCalled();
  });
  it("shows the exact shared price/identity validation error without calling Add", async () => {
    const { state, card } = fixture();
    state.resolveRecommendedVariant.mockRejectedValue(
      new PosProductSelectionError("Biến thể chưa có giá bán hợp lệ."),
    );
    await card.add();
    expect(state.setError).toHaveBeenCalledWith("Biến thể chưa có giá bán hợp lệ.");
    expect(state.onAdd).not.toHaveBeenCalled();
    expect(state.busy.current).toBe(false);
  });
});
