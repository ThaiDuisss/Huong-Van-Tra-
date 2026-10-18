import { ChevronDown, X } from "lucide-react";
import { useRef, useState } from "react";

import { DiscountSearchDialog } from "../dialogs/DiscountSearchDialog";
import type { SaleDiscountOption } from "../../types/sale.types";

interface DiscountSelectorProps {
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  subtotal: number;
  compact?: boolean;
  onChange(discountId: string): void;
  onRemove(): void;
}

export function DiscountSelector({
  discounts,
  selectedDiscountId,
  subtotal,
  compact = false,
  onChange,
  onRemove
}: DiscountSelectorProps) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const selectedDiscount =
    discounts.find((discount) => discount.id === selectedDiscountId) ??
    discounts[0];
  const selectedDiscountLabel =
    selectedDiscount?.couponCode ||
    selectedDiscount?.code ||
    selectedDiscount?.label ||
    "";

  function closeDialog() {
    setOpen(false);
    window.requestAnimationFrame(() => {
      triggerRef.current?.focus();
    });
  }

  return (
    <div className={`relative grid ${compact ? "gap-1" : "gap-2"}`}>
      <span className={compact ? "text-xs font-bold text-[#53678f]" : "text-sm font-semibold"}>
        Mã khuyến mãi
      </span>
      <div className="flex gap-2">
        <button
          ref={triggerRef}
          type="button"
          className={`flex min-w-0 flex-1 items-center rounded-lg border bg-white px-3 text-left outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hvt-green-700 ${
            compact ? "h-9 text-xs" : "h-10 text-sm"
          } ${
            open ? "border-hvt-green-800" : "border-pos-border"
          }`}
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={() => setOpen(true)}
        >
          <span
            className={
              selectedDiscount?.id === "none"
                ? "block min-w-0 truncate text-neutral-400"
                : "block min-w-0 truncate text-gray-500"
            }
          >
            {selectedDiscount?.id === "none"
              ? "Tìm hoặc nhập mã khuyến mãi"
              : selectedDiscountLabel}
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
            className={`grid place-items-center rounded-lg border border-pos-border text-neutral-500 hover:bg-red-50 hover:text-red-600 ${
              compact ? "size-9" : "size-10"
            }`}
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
