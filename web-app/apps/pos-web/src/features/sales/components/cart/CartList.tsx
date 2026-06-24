import { MoreHorizontal, Plus, Minus, ShoppingBag, Trash2 } from "lucide-react";

import type { SaleCartItem } from "../../types/sale.types";
import styles from "../../pages/PosSalePage.module.css";

interface CartListProps {
  items: SaleCartItem[];
  formatCurrency(value: number): string;
  onIncrement(productId: string): void;
  onDecrement(productId: string): void;
  onRemove(productId: string): void;
}

export function CartList({
  items,
  formatCurrency,
  onIncrement,
  onDecrement,
  onRemove
}: CartListProps) {
  if (items.length === 0) {
    return (
      <div className={styles.cartEmpty}>
        <ShoppingBag size={42} aria-hidden="true" />
        <strong>Chưa có sản phẩm trong đơn</strong>
        <span>Tìm hàng hóa hoặc quét mã vạch để thêm vào hóa đơn.</span>
      </div>
    );
  }

  return (
    <div className={styles.cartTableShell}>
      <div className={styles.cartTableHeader} role="row">
        <span>#</span>
        <span>Mã / SKU</span>
        <span>Tên sản phẩm</span>
        <span>SL</span>
        <span>Đơn giá / Tổng</span>
        <span aria-label="Thao tác" />
      </div>

      <div className={styles.cartRows}>
        {items.map((item, index) => (
          <article className={styles.cartRow} key={item.productId}>
            <span className={styles.rowIndex}>{index + 1}</span>
            <span className={styles.sku}>{item.sku}</span>
            <span className={styles.productName}>
              {item.name}
              {item.variant ? (
                <small className="mt-1 block font-medium text-neutral-500">
                  {item.variant}
                </small>
              ) : null}
            </span>
            <div className={styles.quantityControl}>
              <button
                type="button"
                onClick={() => onDecrement(item.productId)}
                aria-label={`Giảm số lượng ${item.name}`}
              >
                <Minus size={16} aria-hidden="true" />
              </button>
              <strong>{item.quantity}</strong>
              <button
                type="button"
                onClick={() => onIncrement(item.productId)}
                aria-label={`Tăng số lượng ${item.name}`}
              >
                <Plus size={16} aria-hidden="true" />
              </button>
            </div>
            <strong className={styles.price}>
              {formatCurrency(item.unitPrice)}
              <small className="mt-1 block text-xs font-semibold text-neutral-500">
                {formatCurrency(item.unitPrice * item.quantity)}
              </small>
            </strong>
            <div className={styles.rowActions}>
              <button
                type="button"
                className={styles.iconAction}
                aria-label={`Mở thêm thao tác cho ${item.name}`}
              >
                <MoreHorizontal size={21} aria-hidden="true" />
              </button>
              <button
                type="button"
                className={styles.iconAction}
                onClick={() => onRemove(item.productId)}
                aria-label={`Xóa ${item.name} khỏi hóa đơn`}
              >
                <Trash2 size={18} aria-hidden="true" />
              </button>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
