import { useState } from "react";
import { Pencil, RotateCcw } from "lucide-react";

import {
    formatCurrency,
    parseCurrencyInput
} from "../../services/money";
import type { SaleTotals } from "../../types/sale.types";

interface OrderTotalsProps {
    totals: SaleTotals;
    subtotalOverrideNote: string;
    onSubtotalOverride(value: number, note: string): void;
    onSubtotalReset(): void;
}

export function OrderTotals({
    totals,
    subtotalOverrideNote,
    onSubtotalOverride,
    onSubtotalReset
}: OrderTotalsProps) {
    const [editingSubtotal, setEditingSubtotal] = useState(false);
    const [draftSubtotal, setDraftSubtotal] = useState(
        formatCurrency(totals.subtotal)
    );
    const [draftNote, setDraftNote] = useState(subtotalOverrideNote);
    const canSave = draftNote.trim().length > 0;

    function openSubtotalEditor() {
        setDraftSubtotal(formatCurrency(totals.subtotal));
        setDraftNote(subtotalOverrideNote);
        setEditingSubtotal(!editingSubtotal);
    }

    function saveSubtotalOverride() {
        if (!canSave) {
            return;
        }

        onSubtotalOverride(
            parseCurrencyInput(draftSubtotal),
            draftNote.trim()
        );
        setEditingSubtotal(false);
    }

    return (
        <div className="grid gap-3">
            <div className="flex items-center justify-between text-sm">
                <div className="grid gap-1">
                    <span>Tổng tiền hàng</span>
                    {subtotalOverrideNote ? (
                        <span className="max-w-48 truncate text-xs text-amber-700">
                            Đã sửa: {subtotalOverrideNote}
                        </span>
                    ) : null}
                </div>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={openSubtotalEditor}
                        className="grid size-7 place-items-center rounded-full border border-pos-border text-pos-muted hover:border-hvt-green-500 hover:text-hvt-green-900"
                        aria-label="Sửa tổng tiền hàng"
                    >
                        <Pencil size={14} aria-hidden="true" />
                    </button>
                    {subtotalOverrideNote ? (
                        <button
                            type="button"
                            onClick={onSubtotalReset}
                            className="grid size-7 place-items-center rounded-full border border-pos-border text-pos-muted hover:border-amber-400 hover:text-amber-700"
                            aria-label="Hoàn tác sửa tổng tiền hàng"
                        >
                            <RotateCcw size={14} aria-hidden="true" />
                        </button>
                    ) : null}
                    <strong>{formatCurrency(totals.subtotal)}</strong>
                </div>
            </div>

            {editingSubtotal ? (
                <div className="grid gap-2 rounded-xl border border-amber-200 bg-amber-50/60 p-3">
                    <label className="grid gap-1 text-xs font-semibold text-pos-ink">
                        Tổng tiền hàng mới
                        <input
                            className="h-10 rounded-lg border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700"
                            inputMode="numeric"
                            value={draftSubtotal}
                            onChange={(event) =>
                                setDraftSubtotal(event.currentTarget.value)
                            }
                        />
                    </label>
                    <label className="grid gap-1 text-xs font-semibold text-pos-ink">
                        Ghi chú bắt buộc
                        <textarea
                            className="min-h-16 resize-none rounded-lg border border-pos-border bg-white px-3 py-2 text-sm outline-none focus:border-hvt-green-700"
                            placeholder="Nhập lý do thay đổi tổng tiền hàng"
                            value={draftNote}
                            onChange={(event) =>
                                setDraftNote(event.currentTarget.value)
                            }
                        />
                    </label>
                    <div className="flex justify-end gap-2">
                        <button
                            type="button"
                            onClick={() => setEditingSubtotal(false)}
                            className="h-9 rounded-lg px-3 text-xs font-semibold text-pos-muted hover:bg-white"
                        >
                            Hủy
                        </button>
                        <button
                            type="button"
                            onClick={saveSubtotalOverride}
                            disabled={!canSave}
                            className="h-9 rounded-lg bg-hvt-green-900 px-3 text-xs font-bold text-white disabled:bg-neutral-300"
                        >
                            Lưu thay đổi
                        </button>
                    </div>
                </div>
            ) : null}

            <div className="flex items-center justify-between text-sm">
                <span>Giảm giá</span>
                <strong>{formatCurrency(totals.discountAmount)}</strong>
            </div>
            <div className="flex items-center justify-between border-t border-pos-border pt-3">
                <span className="font-semibold">Thành tiền</span>
                <strong className="text-3xl font-bold text-hvt-green-900">
                    {formatCurrency(totals.total)}
                </strong>
            </div>
        </div>
    );
}
