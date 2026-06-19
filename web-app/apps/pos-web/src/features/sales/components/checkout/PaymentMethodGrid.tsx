import {
  PanelsTopLeft,
  Landmark,
  WalletCards
} from "lucide-react";
import type { SalePaymentMethod } from "../../types/sale.types";

interface PaymentMethodGridProps {
  value: SalePaymentMethod;
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
  onChange
}: PaymentMethodGridProps) {
  return (
    <div className="grid gap-3">
      <div className="grid grid-cols-2 gap-3">
        {paymentMethods.map((method) => {
          const Icon = method.icon;
          const active = method.value === value;

          return (
            <button
              key={method.value}
              type="button"
              onClick={() => onChange(method.value)}
              className={`grid h-[58px] place-items-center rounded-lg border px-1 text-[12px] font-semibold ${
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
