import { formatCurrency, parseCurrencyInput } from "../../services/money";

interface CashReceivedInputProps {
  value: number;
  changeAmount: number;
  onChange(value: number): void;
}

export function CashReceivedInput({
  value,
  changeAmount,
  onChange
}: CashReceivedInputProps) {
  return (
    <div className="grid grid-cols-2 gap-3">
      <label className="grid min-h-20 gap-2 rounded-xl border border-pos-border p-3">
        <span className="text-sm font-semibold">Tiền khách đưa</span>
        <input
          className="min-w-0 bg-transparent text-lg font-semibold outline-none placeholder:text-neutral-400"
          inputMode="numeric"
          value={value > 0 ? formatCurrency(value) : ""}
          placeholder="Nhập số tiền"
          onChange={(event) =>
            onChange(parseCurrencyInput(event.currentTarget.value))
          }
        />
      </label>

      <div className="grid min-h-20 gap-2 rounded-xl border border-pos-border p-3">
        <span className="text-sm font-semibold">Tiền thừa</span>
        <strong className="self-end text-right text-lg text-hvt-green-900">
          {formatCurrency(changeAmount)}
        </strong>
      </div>
    </div>
  );
}
