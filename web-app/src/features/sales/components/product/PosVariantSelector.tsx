import type { SaleProductPreview } from "../../types/sale.types";
import { posVariantSelectionError } from "../../services/posProductSelection";

/** The same unit picker is used to switch a cart line or select a recommendation's variant. */
export function PosVariantSelector({
  variants,
  selectedId,
  disabled,
  onSelect,
  formatCurrency,
}: {
  variants: SaleProductPreview[];
  selectedId: string | null;
  disabled?: boolean;
  onSelect(id: string): void;
  formatCurrency(value: number): string;
}) {
  return (
    <ul className="grid gap-2" aria-label="Chọn biến thể / đơn vị bán">
      {variants.map((variant) => {
        const id = variant.productVariantId ?? variant.id;
        const error = posVariantSelectionError(variant);
        return (
          <li key={id}>
            <button
              type="button"
              aria-pressed={id === selectedId}
              disabled={disabled || Boolean(error)}
              className={`grid w-full grid-cols-[1fr_auto] items-center gap-3 rounded-xl border px-4 py-3 text-left disabled:opacity-60 ${
                id === selectedId
                  ? "border-[#159734] bg-[#eaf7ec]"
                  : "border-[#e8eef0] hover:border-[#b9dfc8]"
              }`}
              onClick={() => onSelect(id)}
            >
              <span className="grid gap-0.5">
                <strong className="text-sm font-black text-[#172317]">
                  {variant.variant || variant.name}
                </strong>
                <small className="text-xs font-semibold text-[#697684]">
                  {variant.sku} · Trên kệ {variant.shelfQuantity} · Trong kho{" "}
                  {variant.warehouseQuantity}
                </small>
                {error && <small className="text-xs text-amber-800">{error}</small>}
              </span>
              <strong className="text-sm font-black tabular-nums text-[#172317]">
                {error ? "Chưa thể chọn" : formatCurrency(variant.price)}
              </strong>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
