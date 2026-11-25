import { ChevronDown, Sparkles, User, X } from "lucide-react";
import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Dialog } from "../../../../components/ui/dialog";
import { CustomerCreateDialog } from "../../../customer/components/CustomerCreateDialog";
import { CustomerSearchDialog } from "../dialogs/CustomerSearchDialog";
import { CustomerTasteSurveyDialog } from "../../../customer/components/CustomerTasteSurveyDialog";
import { tasteSurveyApi } from "../../../customer/services/tasteSurvey.api";
import { toSaleCustomer } from "../../services/posCustomer.api";
import type { SaleCustomer, SaleProductPreview } from "../../types/sale.types";

interface CustomerSelectorProps {
  customers: SaleCustomer[];
  selectedCustomer: SaleCustomer | null;
  loading: boolean;
  error: string;
  disabled?: boolean;
  invoiceId?: string;
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
  const [surveyOpen, setSurveyOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  // Check customer taste survey history
  const { data: surveyHistory, refetch: refetchSurveyHistory } = useQuery({
    queryKey: ["customer-taste-surveys", selectedCustomer?.id],
    queryFn: () => (selectedCustomer?.id ? tasteSurveyApi.history(selectedCustomer.id, 0) : null),
    enabled: !!selectedCustomer?.id
  });

  const hasSurvey = (surveyHistory?.totalElements || 0) > 0;
  const isNewOrNoSurvey = !selectedCustomer || selectedCustomer.orderCount === 0 || !hasSurvey;

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
    <section className="relative grid shrink-0 gap-2.5 border-b border-pos-border pb-3">
      <h2 className="text-base font-bold text-gray-500">
        Khách hàng
      </h2>
      <div
        className={`flex min-h-14 items-center gap-2 rounded-xl border px-3 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-hvt-green-700 ${
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
            <span className="text-xs text-[#53678f]">
              {selectedCustomer
                ? selectedCustomer.phone || selectedCustomer.customerCode
                : "Tìm theo tên, SĐT hoặc mã KH"}
            </span>
          </div>
        </button>

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

      {/* Taste Survey Trigger Action */}
      {selectedCustomer && (
        <div className="flex items-center justify-between gap-2 rounded-xl border border-emerald-100 bg-emerald-50/60 p-2.5 transition">
          <div className="flex items-center gap-2 min-w-0">
            <div className="grid size-7 shrink-0 place-items-center rounded-lg bg-emerald-100 text-emerald-700">
              <Sparkles size={16} aria-hidden="true" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-emerald-950">
                {isNewOrNoSurvey ? "Khách mới / Chưa có hồ sơ" : "Đã có hồ sơ khẩu vị"}
              </p>
              <p className="truncate text-[11px] text-emerald-700">
                {isNewOrNoSurvey
                  ? `${selectedCustomer.orderCount} đơn hàng · Khảo sát để tư vấn trà`
                  : `${surveyHistory?.totalElements ?? 1} lần khảo sát · Cập nhật sở thích`}
              </p>
            </div>
          </div>

          <button
            type="button"
            disabled={disabled}
            onClick={() => setSurveyOpen(true)}
            className={`shrink-0 rounded-lg px-2.5 py-1.5 text-xs font-bold transition shadow-xs ${
              isNewOrNoSurvey
                ? "bg-emerald-600 text-white hover:bg-emerald-700 active:scale-95"
                : "border border-emerald-300 bg-white text-emerald-700 hover:bg-emerald-50 active:scale-95"
            }`}
          >
            {isNewOrNoSurvey ? "Khảo sát khẩu vị" : "Cập nhật khẩu vị"}
          </button>
        </div>
      )}

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
      {selectedCustomer && surveyOpen && (
        <CustomerTasteSurveyDialog
          open={surveyOpen}
          customer={selectedCustomer}
          invoiceId={invoiceId}
          onClose={() => setSurveyOpen(false)}
          onAddToCart={onAddToCart}
          onSurveySubmitted={() => {
            void refetchSurveyHistory();
          }}
        />
      )}
    </section>
  );
}
