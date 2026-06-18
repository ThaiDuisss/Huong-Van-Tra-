import { formatCurrency } from "../../services/money";

interface QuickCashButtonsProps {
  total: number;
  onSelect(amount: number): void;
}

function getQuickAmounts(total: number) {
  if (total <= 0) {
    return [20000, 50000, 100000, 200000, 500000];
  }

  const roundedToTen = Math.ceil(total / 10000) * 10000;
  const roundedToFifty = Math.ceil(total / 50000) * 50000;

  return Array.from(
    new Set([
      roundedToTen,
      roundedToFifty,
      roundedToFifty + 50000,
      500000,
      1000000
    ])
  ).slice(0, 5);
}

export function QuickCashButtons({
  total,
  onSelect
}: QuickCashButtonsProps) {
  return (
    <div className="rounded-lg border border-pos-border p-3">
      <p className="mb-2 text-xs font-semibold">Tiền nhận nhanh</p>
      <div className="grid grid-cols-5 gap-2">
        {getQuickAmounts(total).map((amount) => (
          <button
            key={amount}
            type="button"
            onClick={() => onSelect(amount)}
            className="h-8 rounded-full border border-pos-border text-xs font-medium hover:border-hvt-green-700 hover:bg-hvt-green-50"
          >
            {amount >= 1000000
              ? `${amount / 1000000} triệu`
              : formatCurrency(amount).replace(/\s?₫/, "")}
          </button>
        ))}
      </div>
    </div>
  );
}
