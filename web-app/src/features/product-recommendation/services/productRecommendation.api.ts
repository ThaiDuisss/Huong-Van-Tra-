import { apiClient } from "@/api/api-client";
import { ApiError, type ApiClient } from "@/lib/api-client";
import { posProductApi } from "../../sales/services/posProduct.api";
import { resolvePosVariant } from "../../sales/services/posProductSelection";
import type {
  ProductRecommendations,
  ProductRecommendation,
  PersonalizedMode,
  RecommendationMode,
  RecommendedProductView,
} from "../types/productRecommendation.types";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const recommendationRequestOptions = {
  retry: false as const,
  retryOnUnauthorized: false,
  timeoutMs: 8_000,
  cache: "no-store" as const,
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
function validateResponse(value: unknown, customerId: string | null): ProductRecommendations {
  if (
    !isRecord(value) ||
    value.customerId !== customerId ||
    !["MODEL", "HYBRID", "POPULARITY", "FALLBACK"].includes(String(value.source)) ||
    (customerId === null && (value.source !== "POPULARITY" || value.modelType !== "popularity")) ||
    !Array.isArray(value.items) ||
    value.items.length > 5 ||
    !value.items.every(
      (item, index) =>
        isRecord(item) &&
        item.rank === index + 1 &&
        typeof item.productId === "string" &&
        uuid.test(item.productId) &&
        typeof item.productCode === "string" &&
        typeof item.name === "string" &&
        typeof item.score === "number" &&
        Number.isFinite(item.score),
    ) ||
    new Set(value.items.map((item) => item.productId)).size !== value.items.length
  ) {
    throw new Error("Phản hồi gợi ý sản phẩm không hợp lệ.");
  }
  return value as unknown as ProductRecommendations;
}

export function createProductRecommendationApi(client: ApiClient = apiClient) {
  return {
    async eligibility(customerId: string, signal: AbortSignal) {
      if (!uuid.test(customerId)) throw new Error("Không xác định được khách hàng.");
      let value: unknown;
      try {
        value = await client.get<unknown>(
          `/recommendation/recommendations/${customerId}/eligibility`,
          { ...recommendationRequestOptions, signal },
        );
      } catch (error) {
        // During a staggered deployment the old Gateway may lack the capability route.
        if (error instanceof ApiError && error.status === 404) return false;
        throw error;
      }
      if (!isRecord(value) || value.customerId !== customerId || typeof value.enabled !== "boolean") {
        throw new Error("Phản hồi điều kiện gợi ý không hợp lệ.");
      }
      if (!value.enabled) return false;
      if (typeof value.validOrderCount !== "number" || !Number.isInteger(value.validOrderCount) ||
          value.validOrderCount < 0) {
        throw new Error("Không xác định được số đơn hợp lệ.");
      }
      return value.validOrderCount >= 5;
    },
    async getPersonalized(customerId: string, mode: PersonalizedMode, signal: AbortSignal) {
      if (!uuid.test(customerId)) throw new Error("Không xác định được khách hàng.");
      const value = await client.get<unknown>(
        `/recommendation/recommendations/${customerId}/${mode.toLowerCase()}`,
        { ...recommendationRequestOptions, signal, query: { limit: 5 } },
      );
      if (!isRecord(value) || value.customerId !== customerId || value.mode !== mode ||
          value.status !== "ELIGIBLE" || !isRecord(value.product)) {
        throw new Error("Điều kiện gợi ý đã thay đổi. Vui lòng mở lại gợi ý.");
      }
      const product = value.product;
      if (!["PERSONALIZED", "NO_CANDIDATES"].includes(String(product.status))) {
        throw new Error("Không có dữ liệu để xếp hạng sản phẩm lúc này.");
      }
      const serving = isRecord(product.serving) ? product.serving : undefined;
      return validateResponse({
        customerId,
        source: serving?.fallbackApplied === true ? "FALLBACK" : "MODEL",
        items: product.items,
      }, customerId);
    },
    async get(
      customerId: string | null,
      mode: RecommendationMode,
      key: string,
      signal?: AbortSignal,
    ) {
      if ((customerId !== null && !uuid.test(customerId)) || !uuid.test(key)) {
        throw new Error("Không xác định được khách hàng hoặc yêu cầu gợi ý.");
      }
      const value = await client.get<unknown>(
        `/recommendation/recommendations/${customerId ?? "guest"}`,
        {
          ...recommendationRequestOptions,
          signal,
          query: customerId === null ? { limit: 5 } : { mode, limit: 5 },
          headers: customerId === null ? undefined : { "Idempotency-Key": key },
        },
      );
      return validateResponse(value, customerId);
    },
  };
}
export const productRecommendationApi = createProductRecommendationApi();

export async function loadRecommendedProducts(
  result: ProductRecommendations,
  signal: AbortSignal,
): Promise<RecommendedProductView[]> {
  return Promise.all(
    result.items.map((recommendation) => loadRecommendedProduct(recommendation, signal)),
  );
}

/** Retry POS metadata only; never reissues the recommendation/snapshot request. */
export async function loadRecommendedProduct(
  recommendation: ProductRecommendation,
  signal: AbortSignal,
): Promise<RecommendedProductView> {
  try {
    const variants = await posProductApi.listProductVariants(recommendation.productId, {
      ...recommendationRequestOptions,
      signal,
    });
    const matching = variants.filter((variant) => variant.productId === recommendation.productId);
    return {
      recommendation,
      variants: matching,
      error: !variants.length
        ? "POS không tìm thấy biến thể đang bán của sản phẩm này. Cần kiểm tra sản phẩm và đồng bộ danh mục gợi ý."
        : !matching.length
          ? "Thông tin sản phẩm và biến thể POS không khớp. Vui lòng tải lại."
          : undefined,
    };
  } catch {
    return {
      recommendation,
      variants: [],
      error: "Không tải được thông tin bán hàng hiện tại.",
    };
  }
}

export async function resolveRecommendedVariant(
  productId: string,
  variantId: string,
  signal: AbortSignal,
) {
  return resolvePosVariant(productId, variantId, { ...recommendationRequestOptions, signal });
}
