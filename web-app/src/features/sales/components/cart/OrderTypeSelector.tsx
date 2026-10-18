import { ShoppingBag, Truck } from "lucide-react";
import type { SaleOrderType } from "../../types/sale.types";

interface OrderTypeSelectorProps {
  value: SaleOrderType;
  onChange(value: SaleOrderType): void;
}

const orderTypes = [
  {
    value: "QUICK_SALE",
    label: "Bán nhanh",
    icon: ShoppingBag
  },
  {
    value: "DELIVERY",
    label: "Bán giao hàng",
    icon: Truck
  }
] as const;

export function OrderTypeSelector({
  value,
  onChange
}: OrderTypeSelectorProps) {
  return (
    <div className="flex items-center gap-5 rounded-2xl bg-pos-surface px-5 shadow-pos-card">
      {orderTypes.map((orderType) => {
        const Icon = orderType.icon;
        const active = orderType.value === value;

        return (
          <button
            key={orderType.value}
            type="button"
            onClick={() => onChange(orderType.value)}
            aria-pressed={active}
            className={`flex h-12 min-w-44 items-center justify-center gap-3 rounded-xl px-6 font-semibold shadow-pos-card transition xl:min-w-48 ${
              active
                ? "bg-hvt-green-900 text-white"
                : "bg-pos-gold-light text-pos-gold hover:bg-[#efdfc3]"
            }`}
          >
            <Icon size={24} aria-hidden="true" />
            {orderType.label}
          </button>
        );
      })}
    </div>
  );
}
