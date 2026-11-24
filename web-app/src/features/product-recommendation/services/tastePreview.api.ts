import { apiClient } from "@/api/api-client";
import { ApiError } from "@/lib/api-client";

export interface TastePreviewRequest {
  taxonomyVersion: string;
  taxonomySha256: string;
  preparationCode: string;
  requestSequence: number;
  limit: number;
  hardGroups: Array<{ attribute: string; allowedValues: string[] }>;
}

export interface TastePreviewVariant {
  variantId: string;
  variantName: string;
  sku: string;
  matchedHard: Array<{ attribute: string; matchedValues: string[] }>;
  reason: string;
}

export interface TastePreviewProduct {
  productId: string;
  productName: string;
  compatibleVariants: TastePreviewVariant[];
}

interface TastePreviewResponse {
  taxonomyVersion: string;
  taxonomySha256: string;
  preparationCode: string;
  requestSequence: number;
  status: "OK" | "INSUFFICIENT_MATCHES" | "NO_MATCH" | "NO_CRITERIA" |
    "TASTE_PROFILE_NOT_READY" | "TASTE_CATALOG_UNAVAILABLE" | "TAXONOMY_MAPPING_REQUIRED";
  items: TastePreviewProduct[];
}

export async function fetchTastePreview(input: TastePreviewRequest, signal: AbortSignal) {
  const result = await apiClient.post<TastePreviewResponse, TastePreviewRequest>(
    "/recommendation/taste/preview", input,
    { cache: "no-store", retry: false, retryOnUnauthorized: false, timeoutMs: 8000, signal },
  );
  if (result.requestSequence !== input.requestSequence || result.taxonomyVersion !== input.taxonomyVersion ||
      result.taxonomySha256 !== input.taxonomySha256 || result.preparationCode !== input.preparationCode ||
      !Array.isArray(result.items)) {
    throw new Error("Phản hồi tư vấn không khớp yêu cầu hiện tại.");
  }
  return result;
}

export function tastePreviewError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.status === 403) return "Bạn chưa có quyền sử dụng tư vấn chọn trà.";
    if (error.status === 503) return "Dữ liệu tư vấn chưa sẵn sàng. Vui lòng thử lại sau.";
    if (error.isNetworkError) return "Không kết nối được để tải dữ liệu tư vấn. Vui lòng thử lại.";
  }
  return "Không tải được dữ liệu tư vấn. Vui lòng thử lại.";
}

export async function fetchTasteVariantUnit(productId: string, variantId: string, signal: AbortSignal) {
  const variant = await apiClient.get<{ id: string; productId: string; unitName?: string }>(
    `/catalog/product-variants/${encodeURIComponent(variantId)}`,
    { cache: "no-store", retry: false, retryOnUnauthorized: false, timeoutMs: 8000, signal },
  );
  return variant.id === variantId && variant.productId === productId ? variant.unitName ?? null : null;
}
