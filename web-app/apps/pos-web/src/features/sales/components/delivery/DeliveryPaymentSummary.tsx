import { formatCurrency } from "../../services/money";
import type { DeliveryTotals } from "../../types/delivery.types";
import type { SaleDiscountOption } from "../../types/sale.types";
import { DiscountSelector } from "../checkout/DiscountSelector";

interface DeliveryPaymentSummaryProps {
  discounts: SaleDiscountOption[];
  selectedDiscountId: string;
  totals: DeliveryTotals;
  depositAmount: number;
  onDiscountChange(discountId: string): void;
  onDepositAmountChange(value: number): void;
}

export function DeliveryPaymentSummary({
  discounts,
  selectedDiscountId,
  totals,
  depositAmount,
  onDiscountChange,
  onDepositAmountChange
}: DeliveryPaymentSummaryProps) {
  return (
    <section className="grid gap-2">
      <div className="grid grid-cols-[minmax(0,1fr)_160px] gap-3">
        <DiscountSelector
          compact
          discounts={discounts}
          selectedDiscountId={selectedDiscountId}
          subtotal={totals.itemTotal}
          onChange={onDiscountChange}
          onRemove={() => onDiscountChange("none")}
        />
        <label className="grid gap-1 text-xs font-bold text-[#53678f]">
          Tiền cọc
          <input
            type="number"
            min={0}
            max={totals.grandTotal}
            value={depositAmount || ""}
            onChange={(event) =>
              onDepositAmountChange(
                Math.min(
                  totals.grandTotal,
                  Math.max(0, Number(event.currentTarget.value || 0))
                )
              )
            }
            className="h-9 rounded-lg border border-[#d8eadc] bg-white px-2 text-right text-sm font-semibold text-[#172317]"
          />
        </label>
      </div>

      <div className="grid grid-cols-2 gap-x-5 gap-y-1 border-y border-[#edf0f4] py-2">
        <SummaryRow label="Tiền hàng" value={totals.itemTotal} />
        <SummaryRow label="Giảm giá" value={-totals.discountAmount} />
        <SummaryRow label="Phí giao hàng" value={totals.shippingFee} />
        <SummaryRow label="Thành tiền" value={totals.grandTotal} strong />
        <SummaryRow label="Đã thanh toán" value={totals.paidAmount} />
        <SummaryRow label="Tiền cọc" value={totals.depositPaidAmount} />
        <SummaryRow label="Còn phải thu" value={totals.codAmount} strong />
      </div>
    </section>
  );
}

function SummaryRow({
  label,
  value,
  strong = false
}: {
  label: string;
  value: number;
  strong?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3 text-xs">
      <span className="font-semibold text-[#697684]">{label}</span>
      <strong className={strong ? "text-sm text-[#159734]" : "text-[#172317]"}>
        {formatCurrency(value)}
      </strong>
    </div>
  );
}
