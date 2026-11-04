import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import type { StocktakeListQuery } from "../types/stocktake.types";

function numberParam(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
  return value && options.includes(value as T) ? (value as T) : fallback;
}

export const defaultStocktakeQuery: StocktakeListQuery = {
  branchId: "ALL",
  keyword: "",
  page: 0,
  scopeType: "ALL",
  size: 10,
  sort: "createdAt,desc",
  status: "ALL",
};

export function useStocktakeQueryState() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = useMemo<StocktakeListQuery>(
    () => ({
      branchId: searchParams.get("branchId") ?? "ALL",
      keyword: searchParams.get("keyword") ?? "",
      page: numberParam(searchParams.get("page"), 1) - 1,
      scopeType: oneOf(
        searchParams.get("scopeType"),
        ["ALL", "FULL", "LOCATION", "PARTIAL"],
        "ALL",
      ),
      size: numberParam(searchParams.get("size"), 10),
      sort: oneOf(
        searchParams.get("sort"),
        ["createdAt,desc", "createdAt,asc", "countCode,asc", "countCode,desc"],
        "createdAt,desc",
      ),
      status: oneOf(
        searchParams.get("status"),
        ["ALL", "DRAFT", "COUNTING", "PENDING_APPROVAL", "COMPLETED", "CANCELLED"],
        "ALL",
      ),
    }),
    [searchParams],
  );

  function setQuery(patch: Partial<StocktakeListQuery>) {
    const merged = { ...query, ...patch };
    const next = new URLSearchParams();
    Object.entries(merged).forEach(([key, value]) => {
      const serialized = key === "page" ? String(Number(value) + 1) : String(value);
      if (serialized && serialized !== "ALL") next.set(key, serialized);
    });
    setSearchParams(next, { replace: true });
  }

  function resetQuery() {
    setSearchParams(new URLSearchParams(), { replace: true });
  }

  return { query, resetQuery, setQuery };
}
