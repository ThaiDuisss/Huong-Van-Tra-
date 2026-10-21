import {
  PanelsTopLeft,
  Landmark,
  WalletCards
} from "lucide-react";
import type { SalePaymentMethod } from "../../types/sale.types";

interface PaymentMethodGridProps {
  value: SalePaymentMethod;
  /** Phương thức tạm không dùng được (vd. chuyển khoản khi mất mạng). */
  disabledMethods?: readonly SalePaymentMethod[];
  /** Lý do hiện trên tooltip của nút bị khoá. */
  disabledReason?: string;
  onChange(value: SalePaymentMethod): void;
}

const paymentMethods = [
  {
    value: "CASH",
    label: "Tiền mặt",
    icon: WalletCards
  },
  {
    value: "BANK_TRANSFER",
    label: "Chuyển khoản",
    icon: Landmark
  },
  {
    value: "COMBINED",
    label: "Thanh toán tổng hợp",
    icon: PanelsTopLeft
  }
] as const;

export function PaymentMethodGrid({
  value,
  disabledMethods = [],
  disabledReason,
  onChange
}: PaymentMethodGridProps) {
  return (
    <div className="grid gap-3">
      <div className="grid grid-cols-2 gap-3">
        {paymentMethods.map((method) => {
          const Icon = method.icon;
          const active = method.value === value;
          const disabled = disabledMethods.includes(method.value);

          return (
            <button
              key={method.value}
              type="button"
              disabled={disabled}
              title={disabled ? disabledReason : undefined}
              aria-disabled={disabled || undefined}
              onClick={() => onChange(method.value)}
              className={`grid h-[58px] place-items-center rounded-lg border px-1 text-[12px] font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${
                method.value === "COMBINED" ? "col-span-2" : ""
              } ${
                active
                  ? "border-[#8fd6a5] bg-[#f7fff9] text-[#12231a]"
                  : "border-pos-border bg-white text-[#12231a]"
              }`}
            >
              <Icon
                className={active ? "text-[#159734]" : "text-[#12231a]"}
                size={24}
                aria-hidden="true"
              />
              {method.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
