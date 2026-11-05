import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient } from "@/lib/api-client";
import { apiClient } from "@/api/api-client";
import { posProductApi } from "../../sales/services/posProduct.api";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import {
  createProductRecommendationApi,
  loadRecommendedProducts,
  loadRecommendedProduct,
  productRecommendationApi,
  resolveRecommendedVariant,
} from "./productRecommendation.api";
import { RecommendationRequestScope } from "./recommendationLifecycle";
import {
  initialPosVariant,
  posVariantSelectionError,
} from "../../sales/services/posProductSelection";

vi.mock("@/api/api-client", () => ({ apiClient: { get: vi.fn() } }));
vi.mock("../../sales/services/posProduct.api", () => ({
  posProductApi: { listProductVariants: vi.fn(), getPosView: vi.fn() },
}));
const customer = "11111111-1111-4111-8111-111111111111";
const parent = "22222222-2222-4222-8222-222222222222";
const key = "33333333-3333-4333-8333-333333333333";
const variantId = "44444444-4444-4444-8444-444444444444";
const item = { rank: 1, productId: parent, productCode: "TEA", name: "Trà", score: 0.6 };
function variant(overrides: Partial<SaleProductPreview> = {}): SaleProductPreview {
  return {
    id: variantId,
    productId: parent,
    productVariantId: variantId,
    name: "Trà",
    sku: "TEA-100",
    barcode: "",
    category: "Trà",
    variant: "100g",
    price: 10000,
    priceAvailable: true,
    imageUrl: "/tea.png",
    availableQuantity: 3,
    sellableQuantity: 3,
    shelfQuantity: 3,
    warehouseQuantity: 0,
    ...overrides,
  };
}
function client() {
  const unauthorized = vi.fn();
  const api = createProductRecommendationApi(
    createApiClient({
      baseUrl: "http://gateway.test/api",
      retry: false,
      tokenProvider: { getAccessToken: async () => "fixture-access-token" },
      onUnauthorized: unauthorized,
    }),
  );
  return { api, unauthorized };
}
function response(customerId: string | null = customer) {
  return { customerId, source: "POPULARITY" as const, modelType: "popularity", items: [item] };
}
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("Product V1 shared client", () => {
  it.each(["DISCOVERY", "REPEAT"] as const)(
    "uses shared JWT, member UUID, %s and UUID key",
    async (mode) => {
      const fetch = vi.fn(
        async () =>
          new Response(JSON.stringify(response()), {
            headers: { "Content-Type": "application/json" },
          }),
      );
      vi.stubGlobal("fetch", fetch);
      await client().api.get(customer, mode, key);
      const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
      expect(url).toContain(`/recommendations/${customer}?`);
      expect(new URL(url).searchParams.get("mode")).toBe(mode);
      expect(new URL(url).searchParams.get("limit")).toBe("5");
      expect(new Headers(init.headers).get("Authorization")).toBe("Bearer fixture-access-token");
      expect(new Headers(init.headers).get("Idempotency-Key")).toBe(key);
    },
  );
  it("guest keeps null identity and never substitutes a customer UUID", async () => {
    const fetch = vi.fn(
      async () =>
        new Response(JSON.stringify(response(null)), {
          headers: { "Content-Type": "application/json" },
        }),
    );
    vi.stubGlobal("fetch", fetch);
    expect((await client().api.get(null, "DISCOVERY", key)).customerId).toBeNull();
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(new URL(url).pathname).toBe("/api/recommendation/recommendations/guest");
    expect(new URL(url).searchParams.has("mode")).toBe(false);
    expect(new Headers(init.headers).has("Idempotency-Key")).toBe(false);
  });
  it.each([401, 403, 503])(
    "does not redirect POS or automatically retry HTTP %s",
    async (status) => {
      const fetch = vi.fn(async () => new Response("Unavailable", { status }));
      vi.stubGlobal("fetch", fetch);
      const { api, unauthorized } = client();
      await expect(api.get(customer, "DISCOVERY", key)).rejects.toThrow();
      expect(fetch).toHaveBeenCalledTimes(1);
      expect(unauthorized).not.toHaveBeenCalled();
    },
  );
  it("keeps the same key when the caller retries one logical request", async () => {
    const fetch = vi.fn(
      async () =>
        new Response(JSON.stringify(response()), {
          headers: { "Content-Type": "application/json" },
        }),
    );
    vi.stubGlobal("fetch", fetch);
    const { api } = client();
    await api.get(customer, "DISCOVERY", key);
    await api.get(customer, "DISCOVERY", key);
    expect(
      fetch.mock.calls.map((call) =>
        new Headers((call as unknown as [string, RequestInit])[1].headers).get("Idempotency-Key"),
      ),
    ).toEqual([key, key]);
  });
  it("rejects a response for another customer", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(JSON.stringify(response(null)), {
            headers: { "Content-Type": "application/json" },
          }),
      ),
    );
    await expect(client().api.get(customer, "DISCOVERY", key)).rejects.toThrow("không hợp lệ");
  });
});

describe("exact product/variant mapping and current availability", () => {
  it("uses the real POS adapter with cache bypass and does not invent a missing price", async () => {
    const actual = await vi.importActual<typeof import("../../sales/services/posProduct.api")>(
      "../../sales/services/posProduct.api",
    );
    vi.mocked(apiClient.get).mockResolvedValue({
      content: [
        {
          productId: parent,
          variantId,
          productName: "Trà",
          variantName: "100g",
          sku: "TEA",
          shelfQuantity: 3,
        },
      ],
    });
    const rows = await actual.posProductApi.listProductVariants(parent, { cache: "no-store" });
    expect(rows[0].productVariantId).toBe(variantId);
    expect(rows[0].priceAvailable).toBe(false);
    expect(posVariantSelectionError(rows[0])).toBe("Biến thể chưa có giá bán hợp lệ.");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/pos/products",
      expect.objectContaining({ query: expect.objectContaining({ productId: parent, _fresh: 1 }) }),
    );
  });
  it("reuses bounded Customer API q search beyond the initial page", async () => {
    const actual = await vi.importActual<typeof import("../../sales/services/posCustomer.api")>(
      "../../sales/services/posCustomer.api",
    );
    vi.mocked(apiClient.get).mockResolvedValue({
      content: [{ id: customer, fullName: "Khách kiểm thử", phone: "0900000000" }],
      totalPages: 4,
    });
    const result = await actual.posCustomerApi.searchCustomerInfo("  Khách  ", 2);
    expect(result.content[0].id).toBe(customer);
    expect(result.totalPages).toBe(4);
    expect(apiClient.get).toHaveBeenCalledWith(
      "/customer",
      expect.objectContaining({ query: { q: "Khách", status: "ACTIVE", page: 2, size: 20 } }),
    );
  });
  it("requires explicit choice for multiple saleable variants", () => {
    expect(initialPosVariant([variant()])).toBe(variantId);
    expect(initialPosVariant([variant(), variant({ id: key, productVariantId: key })])).toBe("");
    expect(initialPosVariant([variant({ sellableQuantity: 0 })])).toBe(variantId);
    expect(posVariantSelectionError(variant({ priceAvailable: false }))).toContain("giá bán");
    expect(posVariantSelectionError(variant({ price: Number.NaN }))).toContain("giá bán");
  });
  it("does not match another parent even if names match", async () => {
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue([variant({ productId: key })]);
    const rows = await loadRecommendedProducts(response(), new AbortController().signal);
    expect(rows[0].variants).toEqual([]);
    expect(rows[0].error).toContain("không khớp");
  });
  it("resolves the staff-selected second unit without substituting the first variant", async () => {
    const second = variant({ id: key, productVariantId: key, variant: "1kg", price: 90000 });
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue([variant(), second]);
    vi.mocked(posProductApi.getPosView).mockResolvedValue(second);
    const resolved = await resolveRecommendedVariant(parent, key, new AbortController().signal);
    expect(posProductApi.getPosView).toHaveBeenCalledWith(key, expect.any(Object));
    expect(resolved).toBe(second);
  });
  it("does not silently choose the one priced unit when a parent has multiple units", () => {
    expect(
      initialPosVariant([
        variant(),
        variant({ id: key, productVariantId: key, priceAvailable: false }),
      ]),
    ).toBe("");
  });
  it("retains an explicit zero price and blocks malformed variant identity", () => {
    expect(posVariantSelectionError(variant({ price: 0 }))).toBe("");
    expect(
      posVariantSelectionError(variant({ id: "undefined", productVariantId: "undefined" })),
    ).toContain("biến thể");
    expect(initialPosVariant([])).toBe("");
  });
  it("keeps a metadata failure visible instead of fabricating variants", async () => {
    vi.mocked(posProductApi.listProductVariants).mockRejectedValue(new Error("Unavailable"));
    const rows = await loadRecommendedProducts(response(), new AbortController().signal);
    expect(rows[0].variants).toEqual([]);
    expect(rows[0].error).toBeTruthy();
  });
  it("rechecks membership and exact variant while leaving zero-stock policy to POS", async () => {
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue([variant()]);
    vi.mocked(posProductApi.getPosView).mockResolvedValue(variant());
    expect(
      (await resolveRecommendedVariant(parent, variantId, new AbortController().signal))
        .productVariantId,
    ).toBe(variantId);
    vi.mocked(posProductApi.getPosView).mockResolvedValue(variant({ sellableQuantity: 0 }));
    await expect(
      resolveRecommendedVariant(parent, variantId, new AbortController().signal),
    ).resolves.toMatchObject({ productVariantId: variantId, sellableQuantity: 0 });
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue([]);
    await expect(
      resolveRecommendedVariant(parent, variantId, new AbortController().signal),
    ).rejects.toThrow();
  });
  it("reads the supplied empty HTTP 200 page safely and reports missing variants, not stock or price", async () => {
    const actual = await vi.importActual<typeof import("../../sales/services/posProduct.api")>(
      "../../sales/services/posProduct.api",
    );
    vi.mocked(apiClient.get).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue(
      await actual.posProductApi.listProductVariants(parent),
    );
    const rows = await loadRecommendedProducts(response(), new AbortController().signal);
    expect(rows[0].variants).toEqual([]);
    expect(rows[0].error).toContain("POS không tìm thấy biến thể đang bán");
    expect(posProductApi.getPosView).not.toHaveBeenCalled();
  });
  it("allows an explicit retry after a metadata API failure", async () => {
    vi.mocked(posProductApi.listProductVariants).mockRejectedValueOnce(new Error("HTTP 503"));
    const failed = await loadRecommendedProducts(response(), new AbortController().signal);
    expect(failed[0].error).toBe("Không tải được thông tin bán hàng hiện tại.");
    vi.mocked(posProductApi.listProductVariants).mockResolvedValueOnce([variant()]);
    const retried = await loadRecommendedProducts(response(), new AbortController().signal);
    expect(retried[0].error).toBeUndefined();
    expect(retried[0].variants[0].productVariantId).toBe(variantId);
  });
  it("retrying one product only reads POS metadata and never reissues recommendation inference", async () => {
    const inference = vi.spyOn(productRecommendationApi, "get");
    vi.mocked(posProductApi.listProductVariants).mockResolvedValue([variant()]);
    const signal = new AbortController().signal;
    const refreshed = await loadRecommendedProduct(item, signal);
    expect(refreshed.variants).toEqual([variant()]);
    expect(posProductApi.listProductVariants).toHaveBeenCalledExactlyOnceWith(
      parent,
      expect.objectContaining({ signal, cache: "no-store", retry: false }),
    );
    expect(inference).not.toHaveBeenCalled();
    inference.mockRestore();
  });
  it.each([null, "", "invalid", -1])(
    "rejects raw sellingPrice=%s without converting it into a sale at zero",
    async (sellingPrice) => {
      const actual = await vi.importActual<typeof import("../../sales/services/posProduct.api")>(
        "../../sales/services/posProduct.api",
      );
      vi.mocked(apiClient.get).mockResolvedValue({
        content: [
          {
            productId: parent,
            variantId,
            productName: "Trà",
            variantName: "100g",
            sku: "TEA",
            sellingPrice,
            shelfQuantity: 3,
            warehouseQuantity: 0,
          },
        ],
      });
      const [mapped] = await actual.posProductApi.listProductVariants(parent);
      expect(posVariantSelectionError(mapped)).toBe("Biến thể chưa có giá bán hợp lệ.");
    },
  );
  it("maps manual search and parent lookup to the same variant, price and inventory fields", async () => {
    const actual = await vi.importActual<typeof import("../../sales/services/posProduct.api")>(
      "../../sales/services/posProduct.api",
    );
    vi.mocked(apiClient.get).mockResolvedValue({
      content: [
        {
          productId: parent,
          variantId,
          productName: "Trà",
          variantName: "100g",
          sku: "TEA",
          sellingPrice: "10000",
          shelfQuantity: "1",
          warehouseQuantity: "2",
          sellableQuantity: 99,
        },
      ],
    });
    const [manual] = await actual.posProductApi.search("TEA");
    const [recommended] = await actual.posProductApi.listProductVariants(parent);
    expect(manual).toEqual(recommended);
    expect(recommended).toMatchObject({
      productId: parent,
      productVariantId: variantId,
      price: 10000,
      sellableQuantity: 3,
    });
  });
  it.each([{ priceAvailable: false }, { productId: key }, { productVariantId: key }])(
    "blocks a changed price or identity during the final read: %j",
    async (changed) => {
      vi.mocked(posProductApi.listProductVariants).mockResolvedValue([variant()]);
      vi.mocked(posProductApi.getPosView).mockResolvedValue(variant(changed));
      await expect(
        resolveRecommendedVariant(parent, variantId, new AbortController().signal),
      ).rejects.toThrow();
    },
  );
});

describe("request lifecycle", () => {
  it("invalidates a late result on customer/tab/reset and rotates a fresh request key", async () => {
    const first = new RecommendationRequestScope();
    let resolve!: (value: string) => void;
    const pending = new Promise<string>((done) => {
      resolve = done;
    });
    const applied: string[] = [];
    const work = pending.then((value) => {
      if (first.isCurrent()) applied.push(value);
    });
    first.cancel();
    const second = new RecommendationRequestScope();
    resolve("old customer");
    await work;
    expect(applied).toEqual([]);
    expect(first.controller.signal.aborted).toBe(true);
    expect(second.isCurrent()).toBe(true);
    expect(second.key).not.toBe(first.key);
    expect(new RecommendationRequestScope(second.key).key).toBe(second.key);
  });
});
