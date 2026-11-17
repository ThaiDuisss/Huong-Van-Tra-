import { useState } from "react";
import { Pencil, RotateCcw } from "lucide-react";

import { Button } from "../../../../components/ui/Button";
import { TextArea, TextInput } from "../../../../components/ui/Field";
import {
    formatCurrency,
    parseCurrencyInput
} from "../../services/money";
import type { SaleManualDiscount, SaleTotals } from "../../types/sale.types";

interface OrderTotalsProps {
    totals: SaleTotals;
    manualOrderDiscount: SaleManualDiscount | null;
    onManualOrderDiscountChange(value: SaleManualDiscount | null): void;
}

export function OrderTotals({
    totals,
    manualOrderDiscount,
    onManualOrderDiscountChange
}: OrderTotalsProps) {
    const [editingDiscount, setEditingDiscount] = useState(false);
    const [draftType, setDraftType] =
        useState<SaleManualDiscount["discountType"]>(
            manualOrderDiscount?.discountType ?? "FIXED_AMOUNT"
        );
    const [draftValue, setDraftValue] = useState(
        manualOrderDiscount
            ? String(manualOrderDiscount.discountValue)
            : ""
    );
    const [draftReason, setDraftReason] = useState(
        manualOrderDiscount?.reasonCode ?? "MANUAL_ORDER_DISCOUNT"
    );
    const [draftNote, setDraftNote] = useState(manualOrderDiscount?.note ?? "");
    const parsedValue =
        draftType === "PERCENTAGE"
            ? Number(draftValue.replace(",", "."))
            : parseCurrencyInput(draftValue);
    const canSave =
        Number.isFinite(parsedValue) &&
        parsedValue > 0 &&
        (draftType !== "PERCENTAGE" || parsedValue <= 100) &&
        draftReason.trim().length > 0;

    function openDiscountEditor() {
        setDraftType(manualOrderDiscount?.discountType ?? "FIXED_AMOUNT");
        setDraftValue(
            manualOrderDiscount
                ? String(manualOrderDiscount.discountValue)
                : ""
        );
        setDraftReason(
            manualOrderDiscount?.reasonCode ?? "MANUAL_ORDER_DISCOUNT"
        );
        setDraftNote(manualOrderDiscount?.note ?? "");
        setEditingDiscount(!editingDiscount);
    }

    function saveDiscount() {
        if (!canSave) {
            return;
        }

        onManualOrderDiscountChange({
            discountType: draftType,
            discountValue: parsedValue,
            reasonCode: draftReason.trim(),
            note: draftNote.trim() || undefined
        });
        setEditingDiscount(false);
    }

    return (
        <div className="grid gap-3">
            <div className="flex items-center justify-between text-sm">
                <span>Tổng tiền hàng</span>
                <div className="flex items-center gap-2">
                    <strong>{formatCurrency(totals.subtotal)}</strong>
                </div>
            </div>

            {totals.itemDiscountAmount > 0 ? (
                <div className="flex items-center justify-between text-sm text-amber-700">
                    <span>Giảm giá sản phẩm</span>
                    <strong>-{formatCurrency(totals.itemDiscountAmount)}</strong>
                </div>
            ) : null}

            <div className="flex items-center justify-between text-sm">
                <div className="grid gap-1">
                    <span>Giảm giá đơn hàng</span>
                    {manualOrderDiscount ? (
                        <span className="max-w-48 truncate text-xs text-amber-700">
                            {manualOrderDiscount.reasonCode}
                        </span>
                    ) : null}
                </div>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={openDiscountEditor}
                        className="grid size-7 place-items-center rounded-full border border-pos-border text-pos-muted hover:border-hvt-green-500 hover:text-hvt-green-900"
                        aria-label="Sửa giảm giá đơn hàng"
                    >
                        <Pencil size={14} aria-hidden="true" />
                    </button>
                    {manualOrderDiscount ? (
                        <button
                            type="button"
                            onClick={() => onManualOrderDiscountChange(null)}
                            className="grid size-7 place-items-center rounded-full border border-pos-border text-pos-muted hover:border-amber-400 hover:text-amber-700"
                            aria-label="Bỏ giảm giá đơn hàng"
                        >
                            <RotateCcw size={14} aria-hidden="true" />
                        </button>
                    ) : null}
                    <strong>-{formatCurrency(totals.orderDiscountAmount)}</strong>
                </div>
            </div>

            {editingDiscount ? (
                <div className="grid gap-2 rounded-xl border border-amber-200 bg-amber-50/60 p-3">
                    <div className="grid grid-cols-[1fr_auto] gap-2">
                        <TextInput
                            className="min-w-0"
                            inputMode={draftType === "PERCENTAGE" ? "decimal" : "numeric"}
                            placeholder={
                                draftType === "PERCENTAGE"
                                    ? "Nhập % giảm"
                                    : "Nhập số tiền giảm"
                            }
                            value={draftValue}
                            onChange={(event) =>
                                setDraftValue(event.currentTarget.value)
                            }
                        />
                        <div className="flex overflow-hidden rounded-lg border border-pos-border bg-white">
                            <button
                                type="button"
                                onClick={() => setDraftType("FIXED_AMOUNT")}
                                className={`h-10 px-3 text-xs font-black ${
                                    draftType === "FIXED_AMOUNT"
                                        ? "bg-hvt-green-900 text-white"
                                        : "text-pos-muted"
                                }`}
                            >
                                VND
                            </button>
                            <button
                                type="button"
                                onClick={() => setDraftType("PERCENTAGE")}
                                className={`h-10 px-3 text-xs font-black ${
                                    draftType === "PERCENTAGE"
                                        ? "bg-hvt-green-900 text-white"
                                        : "text-pos-muted"
                                }`}
                            >
                                %
                            </button>
                        </div>
                    </div>
                    <label className="grid gap-1 text-xs font-semibold text-gray-500">
                        Mã lý do
                        <TextInput
                            value={draftReason}
                            onChange={(event) =>
                                setDraftReason(event.currentTarget.value)
                            }
                        />
                    </label>
                    <label className="grid gap-1 text-xs font-semibold text-gray-500">
                        Ghi chú
                        <TextArea
                            className="resize-none"
                            placeholder="Nhập ghi chú giảm giá"
                            value={draftNote}
                            onChange={(event) =>
                                setDraftNote(event.currentTarget.value)
                            }
                        />
                    </label>
                    <div className="flex justify-end gap-2">
                        <Button onClick={() => setEditingDiscount(false)} size="sm" variant="ghost">
                            Hủy
                        </Button>
                        <Button disabled={!canSave} onClick={saveDiscount} size="sm" variant="primary">
                            Lưu thay đổi
                        </Button>
                    </div>
                </div>
            ) : null}

            {totals.discountAmount > 0 ? (
                <div className="flex items-center justify-between text-sm font-semibold text-amber-700">
                    <span>Tổng giảm giá</span>
                    <strong>-{formatCurrency(totals.discountAmount)}</strong>
                </div>
            ) : null}
            <div className="flex items-center justify-between border-t border-pos-border pt-3">
                <span className="font-semibold">Thành tiền</span>
                <strong className="text-2xl font-bold text-[#159734]">
                    {formatCurrency(totals.total)}
                </strong>
            </div>
        </div>
    );
}
