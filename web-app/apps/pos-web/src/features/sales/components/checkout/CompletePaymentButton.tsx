import { Leaf } from "lucide-react";
interface CompletePaymentButtonProps {
  total: number;
  disabled?: boolean;
  label?: string;
  loadingLabel?: string;
  loading?: boolean;
  onClick(): void;
}

export function CompletePaymentButton({
  disabled = false,
  label = "Hoàn tất thanh toán",
  loadingLabel = "Đang thanh toán...",
  loading = false,
  onClick
}: CompletePaymentButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="mt-auto flex h-[50px] items-center justify-center gap-3 rounded-lg bg-[#159734] px-4 text-[13px] font-bold uppercase text-white shadow-pos-panel disabled:cursor-not-allowed disabled:bg-neutral-300"
    >
      <Leaf size={22} aria-hidden="true" />
      <span>{loading ? loadingLabel : label}</span>
      <span className="rounded-md bg-white/18 px-2 py-1 text-[11px] normal-case">
        F12
      </span>
    </button>
  );
}
