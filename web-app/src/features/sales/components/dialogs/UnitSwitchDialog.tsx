import { useEffect, useState } from "react";
import { ArrowLeftRight, X } from "lucide-react";

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
    const selected = others.find((variant) => variant.id === selectedId) ?? null;
    const quantity = Number(draft);
    const canSwitch = Boolean(selected) && Number.isInteger(quantity) && quantity > 0 && !submitting;

    return (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4" role="presentation">
            <section
                aria-labelledby="unit-switch-title"
                aria-modal="true"
                className="grid w-full max-w-[560px] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
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
                    <button
                        aria-label="Đóng"
                        className="grid size-9 place-items-center rounded-lg text-[#536078] hover:bg-neutral-50"
                        disabled={submitting}
                        onClick={onClose}
                        type="button"
                    >
                        <X aria-hidden="true" size={18} />
                    </button>
                </header>

                <div className="grid max-h-[60vh] gap-3 overflow-y-auto px-5 py-4">
                    {variants === null ? (
                        <p className="text-sm font-semibold text-[#697684]">Đang tải các đơn vị...</p>
                    ) : others.length === 0 ? (
                        <p className="text-sm font-semibold text-[#697684]">
                            {loadError || "Sản phẩm này chưa có đơn vị đóng gói nào khác trong Catalog."}
                        </p>
                    ) : (
                        <ul className="grid gap-2">
                            {others.map((variant) => {
                                const active = variant.id === selectedId;

                                return (
                                    <li key={variant.id}>
                                        <button
                                            className={`grid w-full grid-cols-[1fr_auto] items-center gap-3 rounded-xl border px-4 py-3 text-left ${
                                                active
                                                    ? "border-[#159734] bg-[#eaf7ec]"
                                                    : "border-[#e8eef0] hover:border-[#b9dfc8]"
                                            }`}
                                            onClick={() => setSelectedId(variant.id)}
                                            type="button"
                                        >
                                            <span className="grid gap-0.5">
                                                <strong className="text-sm font-black text-[#172317]">
                                                    {variant.variant || variant.name}
                                                </strong>
                                                <small className="text-xs font-semibold text-[#697684]">
                                                    {variant.sku} · Trên kệ {variant.shelfQuantity} · Trong kho{" "}
                                                    {variant.warehouseQuantity}
                                                </small>
                                            </span>
                                            <strong className="text-sm font-black tabular-nums text-[#172317]">
                                                {formatCurrency(variant.price)}
                                            </strong>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
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
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                        disabled={submitting}
                        onClick={onClose}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={!canSwitch}
                        onClick={() => {
                            if (selected && canSwitch) {
                                onSwitch(selected, quantity);
                            }
                        }}
                        type="button"
                    >
                        {submitting ? "Đang đổi..." : "Đổi đơn vị"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
