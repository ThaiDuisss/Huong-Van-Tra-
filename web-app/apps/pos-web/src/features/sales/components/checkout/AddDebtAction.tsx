import { ClipboardPlus } from "lucide-react";

interface AddDebtActionProps {
  onClick(): void;
}

export function AddDebtAction({ onClick }: AddDebtActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-14 items-center justify-center gap-2 rounded-xl border border-pos-gold text-sm font-semibold text-pos-gold hover:bg-pos-gold-light"
    >
      <ClipboardPlus size={22} aria-hidden="true" />
      Tính vào công nợ
    </button>
  );
}
