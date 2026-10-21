import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { accountApi, describeAccountError } from "../services/account.api";
import type { AccountFilters, AccountPageResponse } from "../types/account.types";
import { toAccountQuery } from "../utils/account.utils";

const FILTER_DEBOUNCE_MS = 400;

/**
 * Tải danh sách tài khoản theo bộ lọc + phân trang (cùng cách làm với `useActionLogs`).
 * - Bộ lọc được debounce để gõ chữ không gọi API liên tục; bộ lọc mới có hiệu lực thì về trang đầu.
 * - Giữ dữ liệu cũ trong lúc tải lại để bảng không nháy, bỏ qua response của request đã lỗi thời.
 */
export function useAccounts(filters: AccountFilters, initialPageSize = 20) {
  const [data, setData] = useState<AccountPageResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [applied, setApplied] = useState({ filters, page: 0, pageSize: initialPageSize });
  const requestIdRef = useRef(0);
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

    accountApi
      .search(toAccountQuery(current.filters, current.page, current.pageSize))
      .then((response) => {
        if (requestId !== requestIdRef.current) return;
        setData(response);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (requestId !== requestIdRef.current) return;
        setError(describeAccountError(cause, "Không thể tải danh sách tài khoản."));
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
    page: applied.page,
    pageSize: applied.pageSize,
    refresh,
    setPage,
    setPageSize,
  };
}
