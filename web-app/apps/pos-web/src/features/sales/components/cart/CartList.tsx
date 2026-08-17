import { useState } from "react";
import { BadgePercent, Plus, Minus, ShoppingBag, Trash2, X } from "lucide-react";

import type { SaleCartItem, SaleManualDiscount } from "../../types/sale.types";
import {
  calculateCartItemDiscountAmount,
  calculateCartItemGrossTotal
} from "../../services/totals";
import styles from "../../pages/PosSalePage.module.css";

interface CartListProps {
  items: SaleCartItem[];
  formatCurrency(value: number): string;
  onIncrement(productId: string): void;
  onDecrement(productId: string): void;
  onRemove(productId: string): void;
  onDiscountChange(productId: string, discount: SaleManualDiscount | null): void;
}

export function CartList({
  items,
  formatCurrency,
  onIncrement,
  onDecrement,
  onRemove,
  onDiscountChange
}: CartListProps) {
  const [editingProductId, setEditingProductId] = useState<string | null>(null);

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
        <span>Giảm giá</span>
        <span aria-label="Thao tác" />
      </div>

      <div className={styles.cartRows}>
        {items.map((item, index) => {
          const grossTotal = calculateCartItemGrossTotal(item);
          const discountAmount = calculateCartItemDiscountAmount(item);
          const netTotal = Math.max(0, grossTotal - discountAmount);
          const isEditingDiscount = editingProductId === item.productId;

          return (
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
                  {formatCurrency(netTotal)}
                </small>
              </strong>
              <div className={styles.discountCell}>
                <button
                  type="button"
                  className={styles.discountButton}
                  onClick={() =>
                    setEditingProductId(isEditingDiscount ? null : item.productId)
                  }
                  aria-label={`Sửa giảm giá ${item.name}`}
                >
                  <BadgePercent size={16} aria-hidden="true" />
                  <span>
                    {discountAmount > 0
                      ? `-${formatCurrency(discountAmount)}`
                      : "Giảm"}
                  </span>
                </button>
                {item.manualDiscount ? (
                  <button
                    type="button"
                    className={styles.clearDiscountButton}
                    onClick={() => onDiscountChange(item.productId, null)}
                    aria-label={`Bỏ giảm giá ${item.name}`}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                ) : null}
                {isEditingDiscount ? (
                  <CartItemDiscountEditor
                    item={item}
                    formatCurrency={formatCurrency}
                    onSave={(discount) => {
                      onDiscountChange(item.productId, discount);
                      setEditingProductId(null);
                    }}
                    onCancel={() => setEditingProductId(null)}
                  />
                ) : null}
              </div>
              <div className={styles.rowActions}>
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
          );
        })}
      </div>
    </div>
  );
}

interface CartItemDiscountEditorProps {
  item: SaleCartItem;
  formatCurrency(value: number): string;
  onSave(discount: SaleManualDiscount): void;
  onCancel(): void;
}

function CartItemDiscountEditor({
  item,
  formatCurrency,
  onSave,
  onCancel
}: CartItemDiscountEditorProps) {
  const [discountType, setDiscountType] =
    useState<SaleManualDiscount["discountType"]>(
      item.manualDiscount?.discountType ?? "FIXED_AMOUNT"
    );
  const [discountValue, setDiscountValue] = useState(
    item.manualDiscount ? String(item.manualDiscount.discountValue) : ""
  );
  const [reasonCode, setReasonCode] = useState(
    item.manualDiscount?.reasonCode ?? "MANUAL_ITEM_DISCOUNT"
  );
  const [note, setNote] = useState(item.manualDiscount?.note ?? "");
  const parsedValue =
    discountType === "PERCENTAGE"
      ? Number(discountValue.replace(",", "."))
      : Number(discountValue.replace(/\D/g, ""));
  const grossTotal = calculateCartItemGrossTotal(item);
  const previewDiscount = Number.isFinite(parsedValue)
    ? discountType === "PERCENTAGE"
      ? Math.min(grossTotal, Math.round((grossTotal * parsedValue) / 100))
      : Math.min(grossTotal, Math.round(parsedValue))
    : 0;
  const canSave =
    Number.isFinite(parsedValue) &&
    parsedValue > 0 &&
    (discountType !== "PERCENTAGE" || parsedValue <= 100) &&
    reasonCode.trim().length > 0;

  return (
    <div className={styles.discountEditor}>
      <div className={styles.discountEditorValueRow}>
        <input
          value={discountValue}
          inputMode={discountType === "PERCENTAGE" ? "decimal" : "numeric"}
          placeholder={discountType === "PERCENTAGE" ? "Nhập %" : "Nhập VND"}
          onChange={(event) => setDiscountValue(event.currentTarget.value)}
        />
        <div className={styles.discountTypeToggle}>
          <button
            type="button"
            className={discountType === "FIXED_AMOUNT" ? styles.active : ""}
            onClick={() => setDiscountType("FIXED_AMOUNT")}
          >
            VND
          </button>
          <button
            type="button"
            className={discountType === "PERCENTAGE" ? styles.active : ""}
            onClick={() => setDiscountType("PERCENTAGE")}
          >
            %
          </button>
        </div>
      </div>
      <input
        value={reasonCode}
        placeholder="Mã lý do"
        onChange={(event) => setReasonCode(event.currentTarget.value)}
      />
      <input
        value={note}
        placeholder="Ghi chú"
        onChange={(event) => setNote(event.currentTarget.value)}
      />
      <div className={styles.discountPreview}>
        <span>Giảm {formatCurrency(previewDiscount)}</span>
      </div>
      <div className={styles.discountEditorActions}>
        <button type="button" onClick={onCancel}>
          Hủy
        </button>
        <button
          type="button"
          disabled={!canSave}
          onClick={() =>
            onSave({
              discountType,
              discountValue: parsedValue,
              reasonCode: reasonCode.trim(),
              note: note.trim() || undefined
            })
          }
        >
          Lưu
        </button>
      </div>
    </div>
  );
}
