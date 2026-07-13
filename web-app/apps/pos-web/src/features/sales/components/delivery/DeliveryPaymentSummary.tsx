import { formatCurrency } from "../../services/money";
import type { DeliveryTotals } from "../../types/delivery.types";

interface DeliveryPaymentSummaryProps {
  totals: DeliveryTotals;
  depositAmount: number;
  paymentMethod: string;
  onDepositAmountChange(value: number): void;
  onPaymentMethodChange(value: string): void;
}

const paymentMethods = [
  { value: "COD", label: "Thanh toán khi nhận hàng" },
  { value: "CASH", label: "Tiền mặt" },
  { value: "BANK_TRANSFER", label: "Chuyển khoản" },
  { value: "COMBINED", label: "Thanh toán tổng hợp" }
];

export function DeliveryPaymentSummary({
  totals,
  depositAmount,
  paymentMethod,
  onDepositAmountChange,
  onPaymentMethodChange
}: DeliveryPaymentSummaryProps) {
  return (
    <>
      <section className="grid gap-3 border-b border-pos-border pb-3">
        <h2 className="text-base font-bold text-gray-500">Thanh toán</h2>
        <label className="grid gap-1 text-xs font-bold text-[#53678f]">
          Phương thức thanh toán
          <select
            value={paymentMethod}
            onChange={(event) => onPaymentMethodChange(event.currentTarget.value)}
            className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-semibold text-[#172317]"
          >
            {paymentMethods.map((method) => (
              <option key={method.value} value={method.value}>
                {method.label}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-bold text-[#53678f]">
          Tiền cọc
          <input
            type="number"
            min={0}
            value={depositAmount || ""}
            onChange={(event) =>
              onDepositAmountChange(Number(event.currentTarget.value || 0))
            }
            className="h-10 rounded-lg border border-[#d8eadc] bg-white px-3 text-sm font-semibold text-[#172317]"
          />
        </label>
      </section>

      <section className="grid gap-2 border-b border-pos-border pb-3">
        <h2 className="text-base font-bold text-gray-500">Tổng tiền</h2>
        <SummaryRow label="Tiền hàng" value={totals.itemTotal} />
        <SummaryRow label="Giảm giá" value={-totals.discountAmount} />
        <SummaryRow label="Phí giao hàng" value={totals.shippingFee} />
        <SummaryRow label="Thành tiền" value={totals.grandTotal} strong />
        <SummaryRow label="Đã thanh toán" value={totals.paidAmount} />
        <SummaryRow label="Tiền cọc" value={totals.depositPaidAmount} />
        <SummaryRow label="Số tiền còn phải thu" value={totals.codAmount} strong />
        <SummaryRow label="COD amount" value={totals.codAmount} />
      </section>
    </>
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
    <div className="flex items-center justify-between gap-3 text-sm">
      <span className="font-semibold text-[#697684]">{label}</span>
      <strong className={strong ? "text-base text-[#159734]" : "text-[#172317]"}>
        {formatCurrency(value)}
      </strong>
    </div>
  );
}
