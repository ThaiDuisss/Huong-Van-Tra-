import { ChevronDown, User, X } from "lucide-react";
import { useRef, useState } from "react";

import { CustomerSearchDialog } from "../dialogs/CustomerSearchDialog";
import type { SaleCustomer } from "../../types/sale.types";

interface CustomerSelectorProps {
  customers: SaleCustomer[];
  selectedCustomer: SaleCustomer | null;
  loading: boolean;
  error: string;
  onOpen(): void;
  onSelect(customerId: string): void;
  onClear(): void;
}

export function CustomerSelector({
  customers,
  selectedCustomer,
  loading,
  error,
  onOpen,
  onSelect,
  onClear
}: CustomerSelectorProps) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  function closeDialog() {
    setOpen(false);
    window.requestAnimationFrame(() => {
      triggerRef.current?.focus();
    });
  }

  return (
    <section className="relative grid gap-3 border-b border-pos-border pb-3">
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
        open={open}
        customers={customers}
        selectedCustomerId={selectedCustomer?.id ?? ""}
        loading={loading}
        error={error}
        onSelect={onSelect}
        onClose={closeDialog}
      />
    </section>
  );
}
