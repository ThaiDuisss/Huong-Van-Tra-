import { apiClient } from "@/api/api-client";

export interface RecommendedBomIngredient {
  ingredientId: string;
  ingredientCode: string;
  ingredientName: string;
  quantity: string;
  unitId: string;
  unitSymbol: string;
  retailPricePerUnit: string;
}

export interface RecommendedBom {
  rank: number;
  variantId: string;
  sku: string;
  compositionRevision: number;
  compositionSha256: string;
  ingredientSimilarity: number;
  tasteStatus: string;
  tasteScore?: number;
  finalScore: number;
  matchingIngredients: string[];
  reason: string;
  quantityBasis: { kind: "CATALOG_BOM_ORIGINAL_INPUT"; ingredientInputGrams: string };
  availability: {
    enough: true;
    branchId?: string;
    lines: Array<{ ingredientId: string; requiredQuantity: string; availableQuantity: string; missingQuantity: string }>;
  };
  bundlePrefill: { bundleName: string; lines: RecommendedBomIngredient[] };
}

export interface BomRecommendations {
  customerId: string;
  validOrderCount: number;
  status: "OK" | "INSUFFICIENT_VALID_ORDERS" | "INSUFFICIENT_INGREDIENT_SIGNAL"
    | "CANDIDATE_POOL_UNAVAILABLE" | "NO_AVAILABLE_CANDIDATES"
    | "CATALOG_UNAVAILABLE" | "AVAILABILITY_UNAVAILABLE" | "SELECTION_STALE";
  sourceQuality: "CURRENT_BOM_PROXY" | "NONE";
  items: RecommendedBom[];
}

async function request(customerId: string, branchId: string | null, signal?: AbortSignal, selected?: RecommendedBom) {
  const response = await apiClient.get<BomRecommendations>(
    `/recommendation/recommendations/${encodeURIComponent(customerId)}/boms`,
    {
      cache: "no-store",
      timeoutMs: 20_000,
      retry: false,
      signal,
      query: {
        branchId: branchId ?? undefined,
        limit: selected ? 1 : 5,
        variantId: selected?.variantId,
        compositionRevision: selected?.compositionRevision,
        compositionSha256: selected?.compositionSha256,
      },
    },
  );
  if (response.customerId !== customerId || !Array.isArray(response.items) || response.items.length > 5) {
    throw new Error("Kết quả công thức không khớp với khách hàng đang chọn.");
  }
  return response;
}

export const bomRecommendationApi = {
  list: request,
  async revalidate(customerId: string, branchId: string | null, selected: RecommendedBom, signal?: AbortSignal) {
    const response = await request(customerId, branchId, signal, selected);
    const current = response.items[0];
    if (response.status !== "OK" || response.items.length !== 1 || !current?.availability.enough
      || (current.availability.branchId ?? null) !== branchId
      || current.variantId !== selected.variantId || current.compositionRevision !== selected.compositionRevision
      || current.compositionSha256 !== selected.compositionSha256) {
      throw new Error("Công thức hoặc tồn nguyên liệu đã thay đổi. Vui lòng mở lại danh sách gợi ý.");
    }
    return current;
  },
};

export function sameBundleQuote(previous: RecommendedBom, current: RecommendedBom): boolean {
  const a = previous.bundlePrefill.lines;
  const b = current.bundlePrefill.lines;
  return a.length === b.length && a.every((line, index) => (
    line.ingredientId === b[index].ingredientId && line.unitId === b[index].unitId
    && Number(line.quantity) === Number(b[index].quantity)
    && Number(line.retailPricePerUnit) === Number(b[index].retailPricePerUnit)
  ));
}
