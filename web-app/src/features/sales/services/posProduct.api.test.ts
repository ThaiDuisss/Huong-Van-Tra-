import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/api-client";
import { posProductApi } from "./posProduct.api";

vi.mock("@/api/api-client", () => ({ apiClient: { get: vi.fn() } }));
const parent = "22222222-2222-4222-8222-222222222222";
const variantId = "44444444-4444-4444-8444-444444444444";
const variant = {
  productId: parent,
  variantId,
  productName: "Trà",
  variantName: "100g",
  sku: "TEA-100",
  sellingPrice: "10000",
  shelfQuantity: 1,
  warehouseQuantity: 2,
};

afterEach(() => vi.clearAllMocks());

describe("shared POS response mapping", () => {
  it.each([
    null,
    undefined,
    {},
    [],
    { content: null },
    { content: {} },
    { content: [null] },
    { content: [{}] },
  ])("rejects malformed HTTP 200 data instead of reporting no variants: %j", async (body) => {
    vi.mocked(apiClient.get).mockResolvedValue(body);
    await expect(posProductApi.listProductVariants(parent)).rejects.toThrow(
      "Phản hồi thông tin bán hàng không hợp lệ",
    );
  });
  it.each(["search", "searchPage", "listProductVariants"] as const)(
    "%s preserves a legitimate empty page",
    async (method) => {
      vi.mocked(apiClient.get).mockResolvedValue({
        content: [],
        page: 0,
        size: 50,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      });
      const result = await posProductApi[method](method === "listProductVariants" ? parent : "TEA");
      expect(Array.isArray(result) ? result : result.content).toEqual([]);
    },
  );
  it.each(["search", "searchPage"] as const)(
    "%s uses the same response guard as recommendation lookup",
    async (method) => {
      vi.mocked(apiClient.get).mockResolvedValue({});
      await expect(posProductApi[method]("TEA")).rejects.toThrow(
        "Phản hồi thông tin bán hàng không hợp lệ",
      );
    },
  );
  it("maps the same real variant for manual search and parent lookup", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ content: [variant] });
    const [manual] = await posProductApi.search("TEA");
    const [suggested] = await posProductApi.listProductVariants(parent, { cache: "no-store" });
    expect(suggested).toEqual(manual);
    expect(suggested).toMatchObject({
      productId: parent,
      productVariantId: variantId,
      price: 10000,
      sellableQuantity: 3,
    });
    expect(apiClient.get).toHaveBeenLastCalledWith(
      "/pos/products",
      expect.objectContaining({ query: expect.objectContaining({ productId: parent, _fresh: 1 }) }),
    );
  });
  it.each([null, {}, { ...variant, variantId: "" }, { ...variant, productId: null }])(
    "rejects invalid detail identity safely: %j",
    async (body) => {
      vi.mocked(apiClient.get).mockResolvedValue(body);
      await expect(posProductApi.getPosView(variantId)).rejects.toThrow(
        "Phản hồi thông tin bán hàng không hợp lệ",
      );
    },
  );
  it("does not invent variant identity or price from the parent", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ ...variant, sellingPrice: null });
    const current = await posProductApi.getPosView(variantId);
    expect(current.productVariantId).toBe(variantId);
    expect(current.productId).toBe(parent);
    expect(current.priceAvailable).toBe(false);
  });
});
