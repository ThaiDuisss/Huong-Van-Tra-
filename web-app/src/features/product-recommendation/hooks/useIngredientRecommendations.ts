import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@/lib/api-client";
import { ingredientRecommendationApi } from "../services/ingredientRecommendation.api";
import type { IngredientRecommendations } from "../types/ingredientRecommendation.types";

interface UseIngredientRecommendationsOptions {
  enabled: boolean;
  customerId?: string | null;
  branchId: string | null;
  selectedIngredientIds: string[];
  limit?: number;
}

export interface IngredientRecommendationsState {
  data: IngredientRecommendations | null;
  loading: boolean;
  error: string;
  refresh(): void;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404 || error.status === 502 || error.status === 503) {
      return "Dịch vụ AI gợi ý nguyên liệu tạm thời chưa sẵn sàng.";
    }
    if (error.status === 401 || error.status === 403) {
      return "Bạn không có quyền truy cập gợi ý nguyên liệu.";
    }
  }
  return error instanceof Error
    ? error.message
    : "Không thể tải gợi ý nguyên liệu. Vui lòng thử lại.";
}

/** Gọi lại gợi ý nguyên liệu mỗi khi danh sách nguyên liệu đã chọn thay đổi (debounce + chống response trễ). */
export function useIngredientRecommendations({
  enabled,
  customerId,
  branchId,
  selectedIngredientIds,
  limit = 5,
}: UseIngredientRecommendationsOptions): IngredientRecommendationsState {
  const uniqueSortedIds = useMemo(
    () => Array.from(new Set(selectedIngredientIds)).sort(),
    [selectedIngredientIds],
  );
  const selectedIdsKey = uniqueSortedIds.join(",");

  const [data, setData] = useState<IngredientRecommendations | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [refreshTick, setRefreshTick] = useState(0);

  const requestSeqRef = useRef(0);
  const loadedContextRef = useRef<string | null>(null);
  const cacheRef = useRef<Map<string, { expiresAt: number; data: IngredientRecommendations }>>(new Map());

  const refresh = useCallback(() => {
    cacheRef.current.clear();
    setRefreshTick((tick) => tick + 1);
  }, []);

  useEffect(() => {
    if (!enabled || !customerId) {
      requestSeqRef.current += 1;
      setLoading(false);
      setError("");
      setData(null);
      loadedContextRef.current = null;
      return;
    }

    const baseContext = `${customerId}:${branchId ?? ""}:${limit}:${refreshTick}`;
    const cacheKey = `${baseContext}:${selectedIdsKey}`;
    const cached = cacheRef.current.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) {
      requestSeqRef.current += 1;
      loadedContextRef.current = baseContext;
      setLoading(false);
      setError("");
      setData(cached.data);
      return;
    }

    const isInitialForContext = loadedContextRef.current !== baseContext;
    const delayMs = isInitialForContext ? 0 : 150;

    const controller = new AbortController();
    const currentSeq = ++requestSeqRef.current;
    const idsToSend = selectedIdsKey ? selectedIdsKey.split(",") : [];

    function runFetch() {
      setLoading(true);
      setError("");
      ingredientRecommendationApi
        .list(customerId ?? "", {
          branchId,
          selectedIngredientIds: idsToSend,
          limit,
          signal: controller.signal,
        })
        .then((result) => {
          if (controller.signal.aborted || requestSeqRef.current !== currentSeq) {
            return;
          }
          loadedContextRef.current = baseContext;
          if (result.status !== "UPSTREAM_UNAVAILABLE") {
            if (cacheRef.current.size >= 16) {
              const oldestKey = cacheRef.current.keys().next().value;
              if (oldestKey) cacheRef.current.delete(oldestKey);
            }
            cacheRef.current.set(cacheKey, { expiresAt: Date.now() + 15_000, data: result });
          }
          setData(result);
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted || requestSeqRef.current !== currentSeq) {
            return;
          }
          setError(errorMessage(err));
        })
        .finally(() => {
          if (!controller.signal.aborted && requestSeqRef.current === currentSeq) {
            setLoading(false);
          }
        });
    }

    if (delayMs === 0) {
      runFetch();
      return () => {
        controller.abort();
      };
    }

    const timer = window.setTimeout(runFetch, delayMs);

    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [enabled, customerId, branchId, limit, selectedIdsKey, refreshTick]);

  return { data, loading, error, refresh };
}
