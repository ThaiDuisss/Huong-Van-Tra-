import { ClipboardPlus } from "lucide-react";

interface AddDebtActionProps {
  onClick(): void;
}

export function AddDebtAction({ onClick }: AddDebtActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-10 items-center justify-center gap-2 rounded-lg border border-[#ff9c45] text-[12px] font-semibold text-[#f27c13] hover:bg-[#fff4eb]"
    >
      <ClipboardPlus size={22} aria-hidden="true" />
      Tính vào công nợ
    </button>
  );
}
