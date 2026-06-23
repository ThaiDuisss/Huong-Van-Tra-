import type { SaleProductPreview } from "../../types/sale.types";
import { QuantityControl } from "./QuantityControl";

interface ProductCardProps {
  product: SaleProductPreview;
  quantity: number;
  isInCart: boolean;
  formattedPrice: string;
  onAdd(product: SaleProductPreview): void;
  onIncrement(productId: string): void;
  onDecrement(productId: string): void;
  onRemove(productId: string): void;
}

export function ProductCard({
  product,
  quantity,
  isInCart,
  formattedPrice,
  onAdd,
  onIncrement,
  onDecrement,
  onRemove
}: ProductCardProps) {
  return (
    <article className="grid min-h-[172px] grid-cols-[clamp(120px,12vw,150px)_minmax(0,1fr)] gap-4 overflow-hidden rounded-2xl bg-pos-surface p-4 shadow-pos-card xl:grid-cols-[150px_minmax(0,1fr)]">
      <div className="h-[140px] overflow-hidden rounded-xl bg-neutral-100 xl:h-[148px]">
        <img
          className="h-full w-full object-cover"
          src={product.imageUrl}
          alt={product.name}
          loading="lazy"
        />
      </div>

      <div className="flex min-w-0 flex-col py-1">
        <div className="min-w-0">
          <h2 className="line-clamp-2 text-lg font-bold leading-snug text-gray-500 xl:text-xl">
            {product.name}
          </h2>
          <p className="mt-2 text-sm leading-snug text-neutral-600 xl:text-base">
            Loại: {product.category}
          </p>
          <p className="mt-1 text-sm leading-snug text-neutral-600 xl:text-base">
            Size: {product.variant}
          </p>
          <p className="mt-1 text-xs text-neutral-400">
            SKU: {product.sku}
          </p>
        </div>

        <div className="mt-auto flex min-w-0 items-center justify-between gap-3 pt-3">
          <strong className="min-w-0 truncate text-lg font-bold text-hvt-green-900 xl:text-xl">
            {formattedPrice}
          </strong>

          {isInCart ? (
            <div className="flex items-center gap-2">
              <QuantityControl
                value={quantity}
                disabledIncrement={
                  quantity >= product.availableQuantity
                }
                onIncrement={() => onIncrement(product.id)}
                onDecrement={() => onDecrement(product.id)}
              />
              <button
                type="button"
                onClick={() => onRemove(product.id)}
                className="grid size-9 place-items-center rounded-full border border-pos-border text-neutral-500 hover:border-red-200 hover:bg-red-50 hover:text-red-600"
                aria-label={`Xóa ${product.name} khỏi giỏ`}
              >
                ×
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => onAdd(product)}
              className="grid size-10 place-items-center rounded-full border border-pos-border text-2xl text-gray-500 shadow-[0_2px_8px_rgba(0,0,0,0.04)] hover:border-hvt-green-700 hover:bg-hvt-green-50 hover:text-hvt-green-900"
              aria-label={`Thêm ${product.name} vào giỏ`}
            >
              +
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
