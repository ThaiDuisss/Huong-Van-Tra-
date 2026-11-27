import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@/lib/api-client";
import {
  loadRecommendedProducts,
  productRecommendationApi,
} from "../services/productRecommendation.api";
import type {
  ProductRecommendations,
  ProductRecommendationMode,
  RecommendedProductView,
} from "../types/productRecommendation.types";

interface RecommendationState {
  loading: boolean;
  result: ProductRecommendations | null;
  products: RecommendedProductView[];
  error: string;
}
function errorMessage(error: unknown) {
  if (error instanceof ApiError && [401, 403].includes(error.status ?? 0))
    return "Phiên đăng nhập chưa được cấp quyền xem gợi ý sản phẩm.";
  return "Không tải được gợi ý sản phẩm. Bạn vẫn có thể tiếp tục bán hàng và thanh toán.";
}

/** Caches recommendation + POS variant views per customer and mode across tab switches. */
export function useProductRecommendations(customerId: string | null, mode: ProductRecommendationMode): RecommendationState {
  const [key] = useState(() => crypto.randomUUID());
  const query = useQuery({
    queryKey: ["product-recommendations", customerId ?? "guest", mode],
    queryFn: async ({ signal }) => {
      const result = mode === "EXPLORE" || mode === "FAMILIAR"
        ? await productRecommendationApi.getPersonalized(customerId ?? "", mode, signal)
        : await productRecommendationApi.get(customerId, mode, key, signal);
      const products = await loadRecommendedProducts(result, signal);
      return { result, products };
    },
    staleTime: 120_000,
    gcTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) {
    return {
      loading: true,
      result: null,
      products: [],
      error: "",
    };
  }
  if (query.isError) {
    return {
      loading: false,
      result: null,
      products: [],
      error: errorMessage(query.error),
    };
  }
  return {
    loading: false,
    result: query.data?.result ?? null,
    products: query.data?.products ?? [],
    error: "",
  };
}
