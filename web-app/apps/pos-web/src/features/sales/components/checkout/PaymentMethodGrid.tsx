import {
  Building2,
  CreditCard,
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
    value: "E_WALLET",
    label: "Ví điện tử",
    icon: Building2
  },
  {
    value: "CARD",
    label: "Thẻ",
    icon: CreditCard
  },
  {
    value: "COMBINED",
    label: "Kết hợp",
    icon: WalletCards
  }
] as const;

export function PaymentMethodGrid({
  value,
  onChange
}: PaymentMethodGridProps) {
  return (
    <div className="grid gap-3">
      <p className="text-sm font-semibold">
        Phương thức thanh toán
      </p>
      <div className="grid grid-cols-5 gap-2">
        {paymentMethods.map((method) => {
          const Icon = method.icon;
          const active = method.value === value;

          return (
            <button
              key={method.value}
              type="button"
              onClick={() => onChange(method.value)}
              className={`grid h-16 place-items-center rounded-xl border px-1 text-xs font-semibold ${
                active
                  ? "border-hvt-green-800 bg-hvt-green-50 text-hvt-green-900"
                  : "border-pos-border"
              }`}
            >
              <Icon size={22} aria-hidden="true" />
              {method.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
