import { HandCoins } from "lucide-react";

interface ReturnMoneyActionProps {
  onClick(): void;
}

export function ReturnMoneyAction({
  onClick
}: ReturnMoneyActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-14 items-center justify-center gap-2 rounded-xl border border-pos-gold text-sm font-semibold text-pos-gold hover:bg-pos-gold-light"
    >
      <HandCoins size={22} aria-hidden="true" />
      Tiền thừa trả khách
    </button>
  );
}
