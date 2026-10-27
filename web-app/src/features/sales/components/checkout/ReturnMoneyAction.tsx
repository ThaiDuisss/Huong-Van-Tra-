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
      className="flex h-10 items-center justify-center gap-2 rounded-lg border border-[#ff9c45] text-[12px] font-semibold text-[#f27c13] hover:bg-[#fff4eb]"
    >
      <HandCoins size={22} aria-hidden="true" />
      Tách thừa trả khách
    </button>
  );
}
