import { ChevronDown, Sparkles, User, X } from "lucide-react";
import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "../../../../components/ui/Button";
import { Dialog } from "../../../../components/ui/dialog";
import { CustomerCreateDialog } from "../../../customer/components/CustomerCreateDialog";
import { CustomerSearchDialog } from "../dialogs/CustomerSearchDialog";
import { CustomerTasteSurveyDialog } from "../../../customer/components/CustomerTasteSurveyDialog";
import { tasteSurveyApi } from "../../../customer/services/tasteSurvey.api";
import { useGuestTasteSurvey } from "../../../customer/services/guestTasteSurvey";
import { toSaleCustomer } from "../../services/posCustomer.api";
import type { SaleCustomer, SaleProductPreview } from "../../types/sale.types";

interface CustomerSelectorProps {
  customers: SaleCustomer[];
  selectedCustomer: SaleCustomer | null;
  loading: boolean;
  error: string;
  disabled?: boolean;
  invoiceId?: string;
  tasteSurveyScope?: string;
  onSearch?(query: string): void;
  onLoadMore?(): void;
  hasMore?: boolean;
  onOpen(): void;
  onSelect(customerId: string): void;
  onCreated(customer: SaleCustomer): void;
  onClear(): void;
  onAddToCart?(product: SaleProductPreview): Promise<boolean>;
}

export function CustomerSelector({
  customers,
  selectedCustomer,
  loading,
  error,
  disabled,
  invoiceId,
  tasteSurveyScope,
  onSearch,
  onLoadMore,
  hasMore,
  onOpen,
  onSelect,
  onCreated,
  onClear,
  onAddToCart
}: CustomerSelectorProps) {
  const [open, setOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [openSurveyContext, setOpenSurveyContext] = useState<string | null>(null);
  const surveyContext = `${invoiceId ?? ""}:${selectedCustomer?.id ?? "guest"}`;
  const triggerRef = useRef<HTMLButtonElement>(null);

  // Check customer taste survey history
  const { data: surveyHistory, refetch: refetchSurveyHistory } = useQuery({
    queryKey: ["customer-taste-surveys", selectedCustomer?.id],
    queryFn: () => (selectedCustomer?.id ? tasteSurveyApi.history(selectedCustomer.id, 0) : null),
    enabled: !!selectedCustomer?.id
  });

  const guestSurvey = useGuestTasteSurvey(selectedCustomer ? undefined : tasteSurveyScope);
  const hasSurvey = selectedCustomer ? (surveyHistory?.totalElements || 0) > 0 : guestSurvey !== null;
  const isNewOrNoSurvey = !hasSurvey;

  function openCreateDialog() {
    setOpen(false);
    setCreateOpen(true);
  }

  function closeDialog() {
    setOpen(false);
    window.requestAnimationFrame(() => {
      triggerRef.current?.focus();
    });
  }

  return (
    <section className="relative grid min-w-0 shrink-0 grid-cols-1 gap-2.5 border-b border-pos-border pb-3">
      <h2 className="text-base font-bold text-gray-500">
        Khách hàng
      </h2>
      <div
        className={`flex min-h-14 min-w-0 items-center gap-2 rounded-xl border px-3 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-hvt-green-700 ${
          open
            ? "border-hvt-green-800"
            : "border-pos-border hover:border-hvt-green-400"
        }`}
      >
        <button
          ref={triggerRef}
          disabled={disabled}
          type="button"
          className="flex min-w-0 flex-1 items-center gap-3 text-left"
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={() => {
            onOpen();
            setOpen(true);
          }}
        >
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-neutral-100 text-neutral-500">
            <User size={22} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <strong className="block truncate text-sm text-gray-500">
              {selectedCustomer?.name ?? "Chọn khách hàng"}
            </strong>
            <span className="block truncate text-xs text-[#53678f]">
              {selectedCustomer
                ? selectedCustomer.phone || selectedCustomer.customerCode
                : "Tìm theo tên, SĐT hoặc mã KH"}
            </span>
          </div>
        </button>

        <Button
          className="shrink-0 whitespace-nowrap"
          disabled={disabled}
          onClick={() => setOpenSurveyContext(surveyContext)}
          size="sm"
          title={isNewOrNoSurvey ? "Khảo sát khẩu vị khách hàng" : "Cập nhật khẩu vị khách hàng"}
          variant={isNewOrNoSurvey ? "primary" : "secondary"}
        >
          <Sparkles size={14} aria-hidden="true" />
          {isNewOrNoSurvey ? "Khảo sát" : "Cập nhật"}
        </Button>

        {selectedCustomer ? (
          <button
            type="button"
            onClick={onClear}
            disabled={disabled}
            className="grid size-9 shrink-0 place-items-center rounded-full text-gray-500 hover:bg-neutral-100"
            aria-label="Bỏ chọn khách hàng"
          >
            <X size={22} aria-hidden="true" />
          </button>
        ) : (
          <ChevronDown
            className="shrink-0 text-gray-500"
            size={22}
            aria-hidden="true"
          />
        )}
      </div>

      <CustomerSearchDialog
        open={open && !disabled}
        customers={customers}
        selectedCustomerId={selectedCustomer?.id ?? ""}
        loading={loading}
        error={error}
        onSearch={onSearch}
        onLoadMore={onLoadMore}
        hasMore={hasMore}
        onSelect={onSelect}
        onCreate={openCreateDialog}
        onClose={closeDialog}
      />

      <Dialog onOpenChange={setCreateOpen} open={createOpen}>
        {createOpen && (
          <CustomerCreateDialog
            onCancel={() => setCreateOpen(false)}
            onSaved={(customer) => {
              setCreateOpen(false);
              onCreated(toSaleCustomer(customer));
            }}
          />
        )}
      </Dialog>

      {/* Customer Taste Survey Modal */}
      {openSurveyContext === surveyContext && (
        <CustomerTasteSurveyDialog
          key={surveyContext}
          open
          customer={selectedCustomer}
          invoiceId={invoiceId}
          guestScope={tasteSurveyScope}
          onClose={() => setOpenSurveyContext(null)}
          onAddToCart={onAddToCart}
          onSurveySubmitted={() => {
            void refetchSurveyHistory();
          }}
        />
      )}
    </section>
  );
}
