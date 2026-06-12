import { Leaf } from "lucide-react";
import { formatCurrency } from "../../services/money";

interface CompletePaymentButtonProps {
  total: number;
  disabled?: boolean;
  onClick(): void;
}

export function CompletePaymentButton({
  total,
  disabled = false,
  onClick
}: CompletePaymentButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="mt-auto flex h-16 items-center justify-center gap-4 rounded-xl bg-hvt-green-900 px-4 text-lg font-bold uppercase tracking-wide text-white shadow-pos-panel disabled:cursor-not-allowed disabled:bg-neutral-300"
    >
      <Leaf size={26} aria-hidden="true" />
      <span>Hoàn tất thanh toán</span>
      <span className="rounded-lg bg-white/14 px-2 py-1 text-sm normal-case">
        {formatCurrency(total)}
      </span>
    </button>
  );
}
