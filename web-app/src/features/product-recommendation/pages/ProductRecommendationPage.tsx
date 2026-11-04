import { useState } from "react";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { useAuth } from "@/auth/auth-context";
import { CustomerSelector } from "../../sales/components/checkout/CustomerCard";
import { usePosCustomerSearch } from "../../sales/hooks/usePosCustomerSearch";
import type { SaleCustomer } from "../../sales/types/sale.types";
import type { RecommendationMode } from "../types/productRecommendation.types";
import { ProductRecommendationResults } from "../components/ProductRecommendationResults";

export function ProductRecommendationPage() {
  const { keycloakSubject, userId } = useAuth();
  const actor = keycloakSubject ?? userId ?? "session";
  const search = usePosCustomerSearch();
  const [customer, setCustomer] = useState<SaleCustomer | null>(null);
  const [mode, setMode] = useState<RecommendationMode>("DISCOVERY");
  const [generation, setGeneration] = useState(0);
  const [request, setRequest] = useState<string | null>(null);
  const context = `${actor}:${customer?.id ?? "guest"}:${mode}:${generation}`;
  return (
    <div className="mx-auto grid w-full max-w-4xl gap-5 p-5">
      <PageHeader
        title="AI gợi ý sản phẩm"
        description="Chọn khách hàng hoặc xem sản phẩm phổ biến cho khách lẻ."
      />
      <section className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-5">
        <CustomerSelector
          customers={search.customers}
          selectedCustomer={customer}
          loading={search.loading}
          error={search.error}
          onOpen={() => search.search("")}
          onSearch={search.search}
          hasMore={search.hasMore}
          onLoadMore={search.loadMore}
          onSelect={(id) => {
            setCustomer(search.customers.find((value) => value.id === id) ?? null);
            setGeneration((value) => value + 1);
          }}
          onCreated={(value) => {
            setCustomer(value);
            setGeneration((version) => version + 1);
          }}
          onClear={() => {
            setCustomer(null);
            setGeneration((value) => value + 1);
          }}
        />
        <p className="text-sm text-slate-600">
          {customer ? `Khách hàng: ${customer.name}` : "Khách lẻ — sử dụng sản phẩm phổ biến."}
        </p>
        {customer && (
          <label className="grid gap-1 text-sm">
            Phạm vi gợi ý
            <select
              className="rounded-lg border border-slate-200 p-2"
              value={mode}
              onChange={(event) => setMode(event.target.value as RecommendationMode)}
            >
              <option value="DISCOVERY">Khám phá sản phẩm chưa mua</option>
              <option value="REPEAT">Sản phẩm đã mua</option>
            </select>
          </label>
        )}
        <button
          type="button"
          className="justify-self-start rounded-lg bg-[#159734] px-4 py-2 text-sm font-semibold text-white"
          onClick={() => {
            const next = generation + 1;
            setGeneration(next);
            setRequest(`${actor}:${customer?.id ?? "guest"}:${mode}:${next}`);
          }}
        >
          Xem sản phẩm gợi ý
        </button>
      </section>
      {request === context && (
        <ProductRecommendationResults key={context} customerId={customer?.id ?? null} mode={mode} />
      )}
    </div>
  );
}
