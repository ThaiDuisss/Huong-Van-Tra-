import { Leaf } from "lucide-react";

interface CartNoteProps {
  value: string;
  maxLength?: number;
  onChange(value: string): void;
}

export function CartNote({
  value,
  maxLength = 200,
  onChange
}: CartNoteProps) {
  return (
    <label className="flex min-w-0 items-center gap-4 rounded-2xl bg-pos-surface px-6 shadow-pos-card">
      <Leaf
        className="size-6 shrink-0 text-neutral-400"
        strokeWidth={1.7}
        aria-hidden="true"
      />
      <input
        className="min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-neutral-400"
        aria-label="Ghi chú đơn hàng"
        placeholder="Ghi chú pha chế hoặc yêu cầu của khách"
        maxLength={maxLength}
        value={value}
        onChange={(event) => onChange(event.currentTarget.value)}
      />
      <span className="text-sm text-neutral-400">
        {value.length}/{maxLength}
      </span>
    </label>
  );
}
