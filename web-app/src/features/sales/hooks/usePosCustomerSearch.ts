import { useEffect, useRef, useState } from "react";
import { posCustomerApi } from "../services/posCustomer.api";
import type { SaleCustomer } from "../types/sale.types";

/** Existing customer picker, bounded server search; no full customer DB download. */
export function usePosCustomerSearch() {
  const [customers, setCustomers] = useState<SaleCustomer[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [hasMore, setHasMore] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const query = useRef("");
  const page = useRef(0);
  const sequence = useRef(0);
  useEffect(
    () => () => {
      pending.current?.abort();
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );
  async function load(value: string, nextPage: number, version: number) {
    const controller = new AbortController();
    pending.current = controller;
    try {
      const response = await posCustomerApi.searchCustomerInfo(value, nextPage, controller.signal);
      if (controller.signal.aborted || version !== sequence.current) return;
      page.current = nextPage;
      setCustomers((current) =>
        nextPage === 0
          ? response.content
          : [
              ...current,
              ...response.content.filter(
                (customer) => !current.some((old) => old.id === customer.id),
              ),
            ],
      );
      setHasMore(nextPage + 1 < response.totalPages);
    } catch {
      if (!controller.signal.aborted && version === sequence.current)
        setError("Không thể tải danh sách khách hàng.");
    } finally {
      if (!controller.signal.aborted && version === sequence.current) setLoading(false);
    }
  }
  function search(value = "") {
    pending.current?.abort();
    if (timer.current) clearTimeout(timer.current);
    query.current = value;
    const version = ++sequence.current;
    setLoading(true);
    setError("");
    setHasMore(false);
    setCustomers([]);
    timer.current = setTimeout(
      () => {
        void load(value, 0, version);
      },
      value ? 250 : 0,
    );
  }
  function loadMore() {
    if (loading || !hasMore) return;
    setLoading(true);
    setError("");
    void load(query.current, page.current + 1, ++sequence.current);
  }
  return { customers, setCustomers, loading, error, hasMore, search, loadMore };
}
