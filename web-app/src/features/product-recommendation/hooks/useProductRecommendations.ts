import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api-client";
import {
  loadRecommendedProducts,
  productRecommendationApi,
} from "../services/productRecommendation.api";
import { RecommendationRequestScope } from "../services/recommendationLifecycle";
import type {
  ProductRecommendations,
  RecommendationMode,
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

/** Mount per invoice/customer/generation/mode/refresh; cleanup invalidates late responses. */
export function useProductRecommendations(customerId: string | null, mode: RecommendationMode) {
  const [key] = useState(() => crypto.randomUUID());
  const [state, setState] = useState<RecommendationState>({
    loading: true,
    result: null,
    products: [],
    error: "",
  });
  useEffect(() => {
    const scope = new RecommendationRequestScope(key);
    void (async () => {
      try {
        const result = await productRecommendationApi.get(
          customerId,
          mode,
          scope.key,
          scope.controller.signal,
        );
        if (!scope.isCurrent()) return;
        const products = await loadRecommendedProducts(result, scope.controller.signal);
        if (scope.isCurrent()) setState({ loading: false, result, products, error: "" });
      } catch (error) {
        if (scope.isCurrent())
          setState({ loading: false, result: null, products: [], error: errorMessage(error) });
      }
    })();
    return () => scope.cancel();
  }, [customerId, mode, key]);
  return state;
}
