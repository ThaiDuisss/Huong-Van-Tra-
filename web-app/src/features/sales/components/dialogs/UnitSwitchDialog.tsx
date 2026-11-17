import { useEffect, useState } from "react";
import { ArrowLeftRight, X } from "lucide-react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { ModalShell } from "../../../../components/ui/Overlay";
import { PosVariantSelector } from "../product/PosVariantSelector";
import { posVariantSelectionError } from "../../services/posProductSelection";
import { posProductApi } from "../../services/posProduct.api";
import type { SaleCartItem, SaleProductPreview } from "../../types/sale.types";

interface UnitSwitchDialogProps {
    item: SaleCartItem;
    submitting: boolean;
    error: string;
    formatCurrency(value: number): string;
    /** Thay dòng hiện tại bằng biến thể đã chọn với số lượng mới. */
    onSwitch(target: SaleProductPreview, quantity: number): void;
    onClose(): void;
}

/** Chọn đơn vị đóng gói khác của cùng sản phẩm (các biến thể cùng productId trong Catalog). */
export function UnitSwitchDialog({
    item,
    submitting,
    error,
    formatCurrency,
    onSwitch,
    onClose
}: UnitSwitchDialogProps) {
    const currentVariantId = item.productVariantId ?? item.productId;
    const [variants, setVariants] = useState<SaleProductPreview[] | null>(null);
    const [loadError, setLoadError] = useState("");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [draft, setDraft] = useState("1");

    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const current = await posProductApi.getPosView(currentVariantId);
                const siblings = current.productId
                    ? await posProductApi.listProductVariants(current.productId)
                    : [current];

                if (!cancelled) {
                    setVariants(siblings);
                }
            } catch (loadFailure) {
                if (!cancelled) {
                    setLoadError(loadFailure instanceof Error ? loadFailure.message : "Không tải được các đơn vị");
                    setVariants([]);
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [currentVariantId]);

    const others = (variants ?? []).filter(
        (variant) => (variant.productVariantId ?? variant.id) !== currentVariantId
    );
    const selected = others.find((variant) => (variant.productVariantId ?? variant.id) === selectedId) ?? null;
    const quantity = Number(draft);
    const canSwitch = Boolean(selected && !posVariantSelectionError(selected)) && Number.isInteger(quantity) && quantity > 0 && !submitting;

    return (
        <ModalShell
            className="w-full max-w-[560px]"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="unit-switch-title"
            onClose={onClose}
            scroll={false}
        >
            <header className="flex items-start justify-between gap-3 border-b border-[#e8eef0] px-5 py-4">
                <div className="flex items-start gap-3">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-[#eaf7ec] text-[#159734]">
                        <ArrowLeftRight aria-hidden="true" size={20} />
                    </span>
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-black text-[#172317]" id="unit-switch-title">
                            Đổi đơn vị
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            Đang bán: {item.quantity} × {item.variant || item.name}
                        </p>
                    </div>
                </div>
                <IconButton aria-label="Đóng" disabled={submitting} onClick={onClose}>
                    <X aria-hidden="true" size={18} />
                </IconButton>
            </header>

            <div className="grid min-h-0 flex-1 gap-3 overflow-y-auto px-5 py-4">
                {variants === null ? (
                    <p className="text-sm font-semibold text-[#697684]">Đang tải các đơn vị...</p>
                ) : others.length === 0 ? (
                    <p className="text-sm font-semibold text-[#697684]">
                        {loadError || "Sản phẩm này chưa có đơn vị đóng gói nào khác trong Catalog."}
                    </p>
                ) : (
                    <PosVariantSelector
                        variants={others}
                        selectedId={selectedId}
                        disabled={submitting}
                        onSelect={setSelectedId}
                        formatCurrency={formatCurrency}
                    />
                )}

                {selected ? (
                    <label className="grid gap-1.5">
                        <span className="text-sm font-black text-[#172317]">
                            Số lượng {selected.variant || selected.name}
                        </span>
                        <input
                            className="h-11 rounded-lg border border-[#dce5e1] px-3 text-lg font-black tabular-nums outline-none focus:border-[#159734]"
                            inputMode="numeric"
                            onChange={(event) => setDraft(event.currentTarget.value.replace(/\D/g, ""))}
                            onFocus={(event) => event.currentTarget.select()}
                            value={draft}
                        />
                        <span className="text-xs font-semibold text-[#697684]">
                            Dòng {item.variant || item.name} sẽ được thay bằng đơn vị mới; giảm giá riêng của dòng
                            cũ không được giữ.
                        </span>
                    </label>
                ) : null}

                {error ? (
                    <p
                        className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                        role="alert"
                    >
                        {error}
                    </p>
                ) : null}
            </div>

            <footer className="flex justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                <Button disabled={submitting} onClick={onClose}>
                    Hủy
                </Button>
                <Button
                    disabled={!canSwitch}
                    onClick={() => {
                        if (selected && canSwitch) {
                            onSwitch(selected, quantity);
                        }
                    }}
                    variant="primary"
                >
                    {submitting ? "Đang đổi..." : "Đổi đơn vị"}
                </Button>
            </footer>
        </ModalShell>
    );
}
