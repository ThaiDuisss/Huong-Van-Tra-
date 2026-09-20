import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { actionLogApi, describeActionLogError } from "../services/action-log.api";
import type { ActionLogFilters, ActionLogPageResponse } from "../types/action-log.types";
import { toActionLogQuery } from "../utils/action-log.utils";

const FILTER_DEBOUNCE_MS = 400;

/**
 * Tải nhật ký theo bộ lọc + phân trang.
 * - Bộ lọc được debounce để gõ chữ không gọi API liên tục; khi bộ lọc mới có hiệu lực thì về trang đầu
 *   trong cùng một lần render (không phát sinh request thừa với bộ lọc cũ).
 * - Giữ dữ liệu cũ trong lúc tải lại để bảng không nháy, bỏ qua response của request đã lỗi thời.
 */
export function useActionLogs(filters: ActionLogFilters, initialPageSize = 20) {
  const [data, setData] = useState<ActionLogPageResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [applied, setApplied] = useState({ filters, page: 0, pageSize: initialPageSize });
  const requestIdRef = useRef(0);
  // Mỗi lần bộ lọc/trang/refresh đổi là một "request" mới; đang tải = request hiện tại chưa hoàn tất.
  const request = useMemo(() => ({ applied, refreshToken }), [applied, refreshToken]);
  const [settledRequest, setSettledRequest] = useState<typeof request | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setApplied((current) => (current.filters === filters ? current : { ...current, filters, page: 0 }));
    }, FILTER_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [filters]);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    const { applied: current } = request;

    actionLogApi
      .search(toActionLogQuery(current.filters, current.page, current.pageSize))
      .then((response) => {
        if (requestId !== requestIdRef.current) return;
        setData(response);
        setError(null);
        setLastUpdated(new Date());
      })
      .catch((cause: unknown) => {
        if (requestId !== requestIdRef.current) return;
        setError(describeActionLogError(cause));
      })
      .finally(() => {
        if (requestId === requestIdRef.current) setSettledRequest(request);
      });
  }, [request]);

  const refresh = useCallback(() => setRefreshToken((value) => value + 1), []);
  const setPage = useCallback((page: number) => setApplied((current) => ({ ...current, page })), []);

  const setPageSize = useCallback(
    (pageSize: number) => setApplied((current) => ({ ...current, page: 0, pageSize })),
    [],
  );

  return {
    data,
    error,
    isLoading: settledRequest !== request,
    lastUpdated,
    page: applied.page,
    pageSize: applied.pageSize,
    refresh,
    setPage,
    setPageSize,
  };
}
