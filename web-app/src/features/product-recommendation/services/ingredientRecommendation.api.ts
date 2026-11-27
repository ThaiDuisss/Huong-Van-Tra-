import { apiClient } from "@/api/api-client";
import type { ApiClient } from "@/lib/api-client";
import type {
  IngredientRecommendations,
  IngredientRecommendationStatus,
  RecommendedIngredient,
} from "../types/ingredientRecommendation.types";
import { recommendationRequestOptions } from "./productRecommendation.api";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const VALID_STATUSES: ReadonlySet<IngredientRecommendationStatus> = new Set([
  "OK",
  "INSUFFICIENT_VALID_ORDERS",
  "NO_INGREDIENT_EVIDENCE",
  "NO_AVAILABLE_INGREDIENTS",
  "FALLBACK_SIMILAR_RETAIL",
  "UPSTREAM_UNAVAILABLE",
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isValidIngredientItem(value: unknown): value is RecommendedIngredient {
  if (!isRecord(value)) return false;
  return (
    typeof value.ingredientId === "string" &&
    UUID_PATTERN.test(value.ingredientId) &&
    typeof value.ingredientCode === "string" &&
    value.ingredientCode.trim().length > 0 &&
    typeof value.ingredientName === "string" &&
    value.ingredientName.trim().length > 0 &&
    typeof value.unitId === "string" &&
    UUID_PATTERN.test(value.unitId) &&
    typeof value.unitSymbol === "string" &&
    value.unitSymbol.trim().length > 0 &&
    typeof value.retailPricePerUnit === "number" &&
    Number.isFinite(value.retailPricePerUnit) &&
    value.retailPricePerUnit > 0 &&
    typeof value.availableQuantity === "number" &&
    Number.isFinite(value.availableQuantity) &&
    value.availableQuantity > 0 &&
    typeof value.reason === "string" &&
    Array.isArray(value.evidenceSources)
  );
}

function validateIngredientRecommendations(
  value: unknown,
  customerId: string,
): IngredientRecommendations {
  if (
    !isRecord(value) ||
    value.customerId !== customerId ||
    !VALID_STATUSES.has(value.status as IngredientRecommendationStatus) ||
    typeof value.validOrderCount !== "number" ||
    !Number.isInteger(value.validOrderCount) ||
    value.validOrderCount < 0 ||
    typeof value.modelVersion !== "string" ||
    !Array.isArray(value.items) ||
    value.items.length > 10 ||
    !value.items.every(isValidIngredientItem)
  ) {
    throw new Error("Phản hồi gợi ý nguyên liệu không hợp lệ.");
  }

  return value as unknown as IngredientRecommendations;
}

export interface IngredientRecommendationQuery {
  branchId?: string | null;
  selectedIngredientIds?: string[];
  limit?: number;
  signal?: AbortSignal;
}

export function createIngredientRecommendationApi(client: ApiClient = apiClient) {
  return {
    async list(
      customerId: string,
      options: IngredientRecommendationQuery = {},
    ): Promise<IngredientRecommendations> {
      if (!UUID_PATTERN.test(customerId)) {
        throw new Error("Không xác định được khách hàng.");
      }

      const limit = Math.min(Math.max(Math.trunc(options.limit ?? 5), 1), 10);
      const branchId =
        options.branchId && UUID_PATTERN.test(options.branchId) ? options.branchId : undefined;
      const selectedIngredientIds = Array.from(
        new Set(
          (options.selectedIngredientIds ?? []).filter((id) => UUID_PATTERN.test(id)),
        ),
      );

      const response = await client.get<unknown>(
        `/recommendation/recommendations/${encodeURIComponent(customerId)}/ingredients`,
        {
          ...recommendationRequestOptions,
          timeoutMs: 20_000,
          signal: options.signal,
          query: {
            limit,
            branchId,
            selectedIngredientIds:
              selectedIngredientIds.length > 0 ? selectedIngredientIds : undefined,
          },
        },
      );

      return validateIngredientRecommendations(response, customerId);
    },
  };
}

export const ingredientRecommendationApi = createIngredientRecommendationApi();
