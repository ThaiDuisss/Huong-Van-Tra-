interface QuantityControlProps {
  value: number;
  onIncrement(): void;
  onDecrement(): void;
  disabledIncrement?: boolean;
  disabledDecrement?: boolean;
}

export function QuantityControl({
  value,
  onIncrement,
  onDecrement,
  disabledIncrement = false,
  disabledDecrement = false
}: QuantityControlProps) {
  return (
    <div
      className="flex h-10 min-w-24 items-center justify-between rounded-full border border-pos-border bg-white px-3 text-lg font-semibold shadow-[0_2px_8px_rgba(0,0,0,0.04)]"
      aria-label="Điều chỉnh số lượng"
    >
      <button
        type="button"
        aria-label="Giảm số lượng"
        disabled={disabledDecrement}
        onClick={onDecrement}
        className="grid size-6 place-items-center rounded-full text-pos-ink disabled:cursor-not-allowed disabled:text-neutral-300"
      >
        -
      </button>
      <span className="min-w-6 text-center">{value}</span>
      <button
        type="button"
        aria-label="Tăng số lượng"
        disabled={disabledIncrement}
        onClick={onIncrement}
        className="grid size-6 place-items-center rounded-full text-pos-ink disabled:cursor-not-allowed disabled:text-neutral-300"
      >
        +
      </button>
    </div>
  );
}
