import { useState } from "react";
import { BadgePercent, ChevronDown, MessageSquareText, Plus, Minus, ShoppingBag, Trash2, X } from "lucide-react";

import type { SaleCartItem, SaleManualDiscount } from "../../types/sale.types";
import {
  calculateCartItemDiscountAmount,
  calculateCartItemGrossTotal
} from "../../services/totals";
import styles from "../../pages/PosSalePage.module.css";

/** Quy cách đóng gói của biến thể: tên biến thể bỏ phần tên sản phẩm phía trước (vd. "1 Hộp", "Thùng 24 Hộp"). */
function unitLabel(item: SaleCartItem) {
  const variant = item.variant?.trim() ?? "";

  if (!variant) {
    return "Mặc định";
  }

  const name = item.name.trim();
  const rest = variant.toLowerCase().startsWith(name.toLowerCase())
    ? variant.slice(name.length).trim()
    : variant;

  return rest || variant;
}

interface CartListProps {
  items: SaleCartItem[];
  formatCurrency(value: number): string;
  onIncrement(productId: string): void;
  onDecrement(productId: string): void;
  /** Nhập thẳng số lượng (vd. 100) thay vì bấm +/- nhiều lần. */
  onQuantityChange(productId: string, quantity: number): void;
  /** Đổi sang biến thể đóng gói khác của cùng sản phẩm (hộp ↔ thùng). */
  onUnitChange(productId: string): void;
  onRemove(productId: string): void;
  onDiscountChange(productId: string, discount: SaleManualDiscount | null): void;
  onNoteChange(productId: string, note: string): void;
}

export function CartList({
  items,
  formatCurrency,
  onIncrement,
  onDecrement,
  onQuantityChange,
  onUnitChange,
  onRemove,
  onDiscountChange,
  onNoteChange
}: CartListProps) {
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [editingNoteItem, setEditingNoteItem] = useState<SaleCartItem | null>(null);
  const [draftNote, setDraftNote] = useState("");

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
        <span>Đơn vị</span>
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
                {item.note ? (
                  <small className={styles.itemNote}>
                    Yêu cầu: {item.note}
                  </small>
                ) : null}
                {item.bundle ? (
                  <small className="mt-1 block text-xs font-semibold text-neutral-500">
                    {item.bundle.components
                      .map((component) => `${component.ingredientName} ${component.quantity}${component.unit ?? ""}`)
                      .join(" · ")}
                  </small>
                ) : null}
              </span>
              {item.bundle ? (
                <span className={styles.unitStatic}>Bundle</span>
              ) : (
                <button
                  type="button"
                  className={styles.unitSelect}
                  onClick={() => onUnitChange(item.productId)}
                  aria-label={`Đổi đơn vị ${item.name}, đang là ${unitLabel(item)}`}
                  title={item.variant || undefined}
                >
                  <span>{unitLabel(item)}</span>
                  <ChevronDown size={14} aria-hidden="true" />
                </button>
              )}
              {item.bundle ? (
                <div className={styles.quantityControl}>
                  <strong>{item.quantity}</strong>
                </div>
              ) : (
              <div className={styles.quantityControl}>
                <button
                  type="button"
                  onClick={() => onDecrement(item.productId)}
                  aria-label={`Giảm số lượng ${item.name}`}
                >
                  <Minus size={16} aria-hidden="true" />
                </button>
                <QuantityInput
                  key={item.quantity}
                  label={`Số lượng ${item.name}`}
                  quantity={item.quantity}
                  onCommit={(quantity) => onQuantityChange(item.productId, quantity)}
                />
                <button
                  type="button"
                  onClick={() => onIncrement(item.productId)}
                  aria-label={`Tăng số lượng ${item.name}`}
                >
                  <Plus size={16} aria-hidden="true" />
                </button>
              </div>
              )}
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
                {item.bundle ? null : (
                <button
                  type="button"
                  className={styles.iconAction}
                  onClick={() => {
                    setEditingNoteItem(item);
                    setDraftNote(item.note ?? "");
                  }}
                  aria-label={`Sửa yêu cầu ${item.name}`}
                >
                  <MessageSquareText size={18} aria-hidden="true" />
                </button>
                )}
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
      {editingNoteItem ? (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
          role="presentation"
        >
          <section
            aria-modal="true"
            className="grid w-full max-w-[560px] gap-4 rounded-2xl bg-white p-5 shadow-pos-panel"
            role="dialog"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-black text-[#172317]">
                  Sửa yêu cầu khách hàng
                </h2>
                <p className="text-sm font-semibold text-[#697684]">
                  {editingNoteItem.name} · {editingNoteItem.sku}
                </p>
              </div>
              <button
                aria-label="Đóng"
                className={styles.iconAction}
                onClick={() => setEditingNoteItem(null)}
                type="button"
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>
            <textarea
              className="min-h-[140px] rounded-lg border border-pos-border px-3 py-2 text-sm font-semibold outline-none focus:border-[#159734]"
              onChange={(event) => setDraftNote(event.currentTarget.value)}
              placeholder="Nhập yêu cầu riêng của khách hàng"
              value={draftNote}
            />
            <div className="flex justify-end gap-3">
              <button
                className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                onClick={() => setEditingNoteItem(null)}
                type="button"
              >
                Hủy
              </button>
              <button
                className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white"
                onClick={() => {
                  onNoteChange(editingNoteItem.productId, draftNote.trim());
                  setEditingNoteItem(null);
                }}
                type="button"
              >
                Lưu
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}

/** Ô số lượng gõ tay: chỉ gửi khi rời ô hoặc Enter; Esc hoặc số không hợp lệ thì trả về số cũ. */
function QuantityInput({
  label,
  quantity,
  onCommit
}: {
  label: string;
  quantity: number;
  onCommit(quantity: number): void;
}) {
  const [draft, setDraft] = useState(String(quantity));

  function commit() {
    const next = Number(draft);

    if (!Number.isInteger(next) || next <= 0) {
      setDraft(String(quantity));
      return;
    }

    if (next !== quantity) {
      onCommit(next);
    }
  }

  return (
    <input
      aria-label={label}
      className={styles.quantityInput}
      inputMode="numeric"
      onBlur={commit}
      onChange={(event) => setDraft(event.currentTarget.value.replace(/\D/g, ""))}
      onFocus={(event) => event.currentTarget.select()}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.currentTarget.blur();
        } else if (event.key === "Escape") {
          setDraft(String(quantity));
          event.currentTarget.blur();
        }
      }}
      value={draft}
    />
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
