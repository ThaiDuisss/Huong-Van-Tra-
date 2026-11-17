import { useState } from "react";
import { Button } from "../../../components/ui/Button";
import { Field, NativeSelect } from "../../../components/ui/Field";
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
          <Field label="Phạm vi gợi ý">
            <NativeSelect
              value={mode}
              onChange={(event) => setMode(event.target.value as RecommendationMode)}
            >
              <option value="DISCOVERY">Khám phá sản phẩm chưa mua</option>
              <option value="REPEAT">Sản phẩm đã mua</option>
            </NativeSelect>
          </Field>
        )}
        <Button
          className="justify-self-start"
          variant="primary"
          onClick={() => {
            const next = generation + 1;
            setGeneration(next);
            setRequest(`${actor}:${customer?.id ?? "guest"}:${mode}:${next}`);
          }}
        >
          Xem sản phẩm gợi ý
        </Button>
      </section>
      {request === context && (
        <ProductRecommendationResults key={context} customerId={customer?.id ?? null} mode={mode} />
      )}
    </div>
  );
}
