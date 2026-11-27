import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient } from "@/lib/api-client";
import { IngredientRecommendationPanel } from "../../../product-recommendation/components/IngredientRecommendationPanel";
import { createIngredientRecommendationApi } from "../../../product-recommendation/services/ingredientRecommendation.api";
import type {
  IngredientRecommendations,
  RecommendedIngredient,
} from "../../../product-recommendation/types/ingredientRecommendation.types";
import { defaultBundleQuantity } from "../../services/posCustomBundle.api";
import { CustomBundleBuilder } from "./CustomBundleBuilder";
import {
  addFlexibleWeightStep,
  formatKgPriceMeta,
  parseFlexibleWeightInput,
} from "./flexibleWeight";

const customerId = "11111111-1111-4111-8111-111111111111";
const ingredientA = "22222222-2222-4222-8222-222222222222";
const ingredientB = "33333333-3333-4333-8333-333333333333";
const unitG = "44444444-4444-4444-8444-444444444444";

function item(overrides: Partial<RecommendedIngredient> = {}): RecommendedIngredient {
  return {
    ingredientId: ingredientA,
    ingredientCode: "LOLTQX-G-48",
    ingredientName: "Lá ô long Tứ Quý Xuân",
    unitId: unitG,
    unitSymbol: "g",
    retailPricePerUnit: 1630,
    availableQuantity: 410,
    behavioralScore: 0.85,
    pairScore: 0.4,
    priorScore: 0.5,
    finalScore: 0.72,
    reason: "Thường xuất hiện trong sản phẩm khách đã mua",
    evidenceSources: ["PRODUCT_BOM_PROXY"],
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("unified CustomBundleBuilder & Ingredient Recommendation", () => {
  it("uses safe default quantity by unit and never defaults to 10 kg", () => {
    expect(defaultBundleQuantity("g")).toBe(10);
    expect(defaultBundleQuantity("kg")).toBe(0.1);
    expect(defaultBundleQuantity("KG")).toBe(0.1);
  });

  it("renders AI_RECOMMENDATION mode without manual catalog search or retail grid", () => {
    const submit = vi.fn();
    const html = renderToStaticMarkup(
      createElement(CustomBundleBuilder, {
        branchId: null,
        customerId,
        mode: "AI_RECOMMENDATION",
        submitLoading: false,
        onSubmit: submit,
        onCancel: vi.fn(),
      }),
    );

    expect(html).toContain("AI gợi ý nguyên liệu");
    expect(html).toContain("Gói trà đã chọn");
    expect(html).toContain("Thêm vào đơn");
    expect(html).not.toContain("Tìm nguyên liệu bán lẻ theo tên hoặc mã");
    expect(html).not.toContain("Tải lại");
    expect(html).not.toContain("0 / 10 nguyên liệu");
    expect(submit).not.toHaveBeenCalled();
  });

  it("hides fallback notice banner when status is FALLBACK_SIMILAR_RETAIL and renders kg price", () => {
    const data: IngredientRecommendations = {
      customerId,
      status: "FALLBACK_SIMILAR_RETAIL",
      validOrderCount: 6,
      modelVersion: "hvtra-ingredient-baseline-v1",
      items: [item({ retailPricePerUnit: 1050, unitSymbol: "g" })],
    };
    const html = renderToStaticMarkup(
      createElement(IngredientRecommendationPanel, {
        data,
        loading: false,
        error: "",
        selectedIngredientIds: [],
        onAdd: vi.fn(),
        onAddAll: vi.fn(),
      }),
    );

    expect(html).not.toContain("Gợi ý các nguyên liệu bán lẻ tương đồng có thể thay thế tại kho.");
    expect(html).not.toContain("Tải lại");
    expect(html).toContain("1.050.000");
    expect(html).toContain("/ kg");
  });

  it("parses flexible weight inputs, computes kg-based lineTotal, converts to base unit, and validates bounds", () => {
    expect(formatKgPriceMeta(1050, "g")).toEqual({ price: 1_050_000, unitLabel: "kg", isKgNormalized: true });
    expect(formatKgPriceMeta(950_000, "kg")).toEqual({ price: 950_000, unitLabel: "kg", isKgNormalized: true });
    expect(formatKgPriceMeta(15_000, "túi")).toEqual({ price: 15_000, unitLabel: "túi", isKgNormalized: false });

    // Base unit g: 1050 đ/g -> 1.050.000 đ/kg
    expect(parseFlexibleWeightInput("10g", "g", 1050, 1000)).toMatchObject({
      ok: true,
      baseQuantity: 10,
      quantityKg: 0.01,
      lineTotal: 10_500,
    });
    expect(parseFlexibleWeightInput("20 g", "g", 1050, 1000)).toMatchObject({
      ok: true,
      baseQuantity: 20,
      quantityKg: 0.02,
      lineTotal: 21_000,
    });
    expect(parseFlexibleWeightInput("0.5kg", "g", 1050, 1000)).toMatchObject({
      ok: true,
      baseQuantity: 500,
      quantityKg: 0.5,
      lineTotal: 525_000,
    });
    expect(parseFlexibleWeightInput("0,5 kg", "g", 1050, 1000)).toMatchObject({
      ok: true,
      baseQuantity: 500,
      quantityKg: 0.5,
      lineTotal: 525_000,
    });
    expect(parseFlexibleWeightInput("1kg", "g", 1050, 1000)).toMatchObject({
      ok: true,
      baseQuantity: 1000,
      quantityKg: 1,
      lineTotal: 1_050_000,
    });

    // Base unit kg: 20g -> 0.02 kg
    expect(parseFlexibleWeightInput("20g", "kg", 1_050_000, 2)).toMatchObject({
      ok: true,
      baseQuantity: 0.02,
      quantityKg: 0.02,
      lineTotal: 21_000,
    });
    expect(parseFlexibleWeightInput("0.5kg", "kg", 1_050_000, 2)).toMatchObject({
      ok: true,
      baseQuantity: 0.5,
      quantityKg: 0.5,
      lineTotal: 525_000,
    });

    // Validation rejections
    expect(parseFlexibleWeightInput("-10g", "g", 1050, 100).ok).toBe(false);
    expect(parseFlexibleWeightInput("0g", "g", 1050, 100).ok).toBe(false);
    expect(parseFlexibleWeightInput("abc", "g", 1050, 100).ok).toBe(false);
    expect(parseFlexibleWeightInput("10ml", "g", 1050, 100).ok).toBe(false);
    expect(parseFlexibleWeightInput("10", "g", 1050, 100).ok).toBe(false);
    expect(parseFlexibleWeightInput("0.5g", "kg", 1_050_000, 2).ok).toBe(false);
    expect(parseFlexibleWeightInput("200g", "g", 1050, 100).ok).toBe(false);

    // Increment stepping preserves user unit
    expect(addFlexibleWeightStep("10g", "g")).toBe("20g");
    expect(addFlexibleWeightStep("0.5kg", "g")).toBe("0.6kg");
  });

  it("renders MANUAL mode with catalog search and without AI suggestion panel", () => {
    const html = renderToStaticMarkup(
      createElement(CustomBundleBuilder, {
        branchId: null,
        customerId,
        mode: "MANUAL",
        submitLoading: false,
        onSubmit: vi.fn(),
        onCancel: vi.fn(),
      }),
    );

    expect(html).toContain("Tìm nguyên liệu bán lẻ theo tên hoặc mã");
    expect(html).toContain("Thành phần trong gói");
    expect(html).not.toContain("AI gợi ý nguyên liệu");
  });

  it("renders suggested ingredients with stock, unit, price, reason, without probability %", () => {
    const data: IngredientRecommendations = {
      customerId,
      status: "OK",
      validOrderCount: 6,
      modelVersion: "hvtra-ingredient-baseline-v1",
      items: [item()],
    };
    const html = renderToStaticMarkup(
      createElement(IngredientRecommendationPanel, {
        data,
        loading: false,
        error: "",
        selectedIngredientIds: [ingredientA],
        onRefresh: vi.fn(),
        onAdd: vi.fn(),
        onAddAll: vi.fn(),
      }),
    );

    expect(html).toContain("Lá ô long Tứ Quý Xuân");
    expect(html).toContain("Tồn: 410 g");
    expect(html).toContain("Thường xuất hiện trong sản phẩm khách đã mua");
    expect(html).toContain("Đã chọn");
    expect(html).not.toContain("72%");
    expect(html).not.toContain("0.72");
  });

  it("serializes repeated selectedIngredientIds and validates customer contract", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        customerId,
        status: "OK",
        validOrderCount: 6,
        modelVersion: "hvtra-ingredient-baseline-v1",
        items: [item()],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const api = createIngredientRecommendationApi(
      createApiClient({
        baseUrl: "http://gateway.test/api",
        retry: false,
        tokenProvider: { getAccessToken: async () => "token" },
      }),
    );

    const result = await api.list(customerId, {
      selectedIngredientIds: [ingredientA, ingredientB],
      limit: 5,
    });
    expect(result.items).toHaveLength(1);
    const [url] = fetchMock.mock.calls[0] as unknown as [string];
    const parsed = new URL(url);
    expect(parsed.pathname).toBe(`/api/recommendation/recommendations/${customerId}/ingredients`);
    expect(parsed.searchParams.getAll("selectedIngredientIds")).toEqual([ingredientA, ingredientB]);
  });
});
