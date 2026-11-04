import { ChevronDown, User, X } from "lucide-react";
import { useRef, useState } from "react";

import { Dialog } from "../../../../components/ui/dialog";
import { CustomerCreateDialog } from "../../../customer/components/CustomerCreateDialog";
import { customerApi } from "../../../customer/services/customer.api";
import type { CustomerTierResponse } from "../../../customer/types/customer.types";
import { CustomerSearchDialog } from "../dialogs/CustomerSearchDialog";
import { toSaleCustomer } from "../../services/posCustomer.api";
import type { SaleCustomer } from "../../types/sale.types";

interface CustomerSelectorProps {
  customers: SaleCustomer[];
  selectedCustomer: SaleCustomer | null;
  loading: boolean;
  error: string;
  disabled?: boolean;
  onSearch?(query: string): void;
  onLoadMore?(): void;
  hasMore?: boolean;
  onOpen(): void;
  onSelect(customerId: string): void;
  onCreated(customer: SaleCustomer): void;
  onClear(): void;
}

export function CustomerSelector({
  customers,
  selectedCustomer,
  loading,
  error,
  disabled,
  onSearch,
  onLoadMore,
  hasMore,
  onOpen,
  onSelect,
  onCreated,
  onClear
}: CustomerSelectorProps) {
  const [open, setOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [tiers, setTiers] = useState<CustomerTierResponse[] | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  function openCreateDialog() {
    setOpen(false);
    setCreateOpen(true);

    if (tiers === null) {
      void customerApi
        .tiers()
        .then(setTiers)
        // Không tải được hạng thì vẫn cho tạo khách, chỉ thiếu lựa chọn hạng.
        .catch(() => setTiers([]));
    }
  }

  function closeDialog() {
    setOpen(false);
    window.requestAnimationFrame(() => {
      triggerRef.current?.focus();
    });
  }

  return (
    <section className="relative grid shrink-0 gap-3 border-b border-pos-border pb-3">
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
            tiers={tiers ?? []}
            onCancel={() => setCreateOpen(false)}
            onSaved={(customer) => {
              setCreateOpen(false);
              onCreated(toSaleCustomer(customer));
            }}
          />
        )}
      </Dialog>
    </section>
  );
}
