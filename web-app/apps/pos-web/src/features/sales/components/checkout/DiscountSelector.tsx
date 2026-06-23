import { ChevronDown, X } from "lucide-react";
import { useRef, useState } from "react";

import { DiscountSearchDialog } from "../dialogs/DiscountSearchDialog";
import type { SaleDiscountOption } from "../../types/sale.types";

interface DiscountSelectorProps {
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  subtotal: number;
  onChange(discountId: string): void;
  onRemove(): void;
}

export function DiscountSelector({
  discounts,
  selectedDiscountId,
  subtotal,
  onChange,
  onRemove
}: DiscountSelectorProps) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const selectedDiscount =
    discounts.find((discount) => discount.id === selectedDiscountId) ??
    discounts[0];

  function closeDialog() {
    setOpen(false);
    window.requestAnimationFrame(() => {
      triggerRef.current?.focus();
    });
  }

  return (
    <div className="relative grid gap-2">
      <span className="text-sm font-semibold">Mã khuyến mãi</span>
      <div className="flex gap-2">
        <button
          ref={triggerRef}
          type="button"
          className={`flex h-10 min-w-0 flex-1 items-center rounded-lg border bg-white px-3 text-left text-sm outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-700 ${
            open ? "border-hvt-green-800" : "border-pos-border"
          }`}
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={() => setOpen(true)}
        >
          <span
            className={
              selectedDiscount?.id === "none"
                ? "truncate text-neutral-400"
                : "truncate text-gray-500"
            }
          >
            {selectedDiscount?.id === "none"
              ? "Tìm hoặc nhập mã khuyến mãi"
              : `${selectedDiscount?.code} - ${selectedDiscount?.label}`}
          </span>
          <ChevronDown
            className="ml-auto size-4 shrink-0 text-gray-500"
            aria-hidden="true"
          />
        </button>

        {selectedDiscount?.id !== "none" ? (
          <button
            type="button"
            onClick={onRemove}
            className="grid size-10 place-items-center rounded-lg border border-pos-border text-neutral-500 hover:bg-red-50 hover:text-red-600"
            aria-label="Bỏ khuyến mãi đang áp dụng"
          >
            <X size={18} aria-hidden="true" />
          </button>
        ) : null}
      </div>

      <DiscountSearchDialog
        open={open}
        discounts={discounts}
        selectedDiscountId={selectedDiscountId}
        subtotal={subtotal}
        onSelect={onChange}
        onRemove={onRemove}
        onClose={closeDialog}
      />
    </div>
  );
}
