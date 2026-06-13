import type { SaleProductPreview } from "../../types/sale.types";
import { ProductCard } from "./ProductCard";

interface ProductGridProps {
  products: SaleProductPreview[];
  quantities: Record<string, number>;
  productIdsInCart: ReadonlySet<string>;
  formatCurrency(value: number): string;
  onAdd(product: SaleProductPreview): void;
  onIncrement(productId: string): void;
  onDecrement(productId: string): void;
  onRemove(productId: string): void;
}

export function ProductGrid({
  products,
  quantities,
  productIdsInCart,
  formatCurrency,
  onAdd,
  onIncrement,
  onDecrement,
  onRemove
}: ProductGridProps) {
  if (products.length === 0) {
    return (
      <div className="grid min-h-0 place-items-center rounded-2xl border border-dashed border-hvt-green-200 bg-pos-surface p-10 text-center shadow-pos-card">
        <div>
          <strong className="text-lg text-pos-ink">
            Chưa có sản phẩm trong đơn
          </strong>
          <p className="mt-2 text-neutral-500">
            Tìm theo tên trà, SKU hoặc mã vạch rồi nhấn + để thêm
            sản phẩm.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="grid min-h-0 grid-cols-2 gap-4 overflow-y-auto pr-1 xl:gap-5">
      {products.map((product) => (
        <ProductCard
          key={product.id}
          product={product}
          quantity={quantities[product.id] ?? 0}
          isInCart={productIdsInCart.has(product.id)}
          formattedPrice={formatCurrency(product.price)}
          onAdd={onAdd}
          onIncrement={onIncrement}
          onDecrement={onDecrement}
          onRemove={onRemove}
        />
      ))}
    </div>
  );
}
