import { useState } from "react";
import { PackagePlus } from "lucide-react";

export interface ShelfShortage {
    productVariantId: string;
    name: string;
    sku: string;
    /** Tổng số lượng đang đặt trong giỏ. */
    requestedQuantity: number;
    shelfQuantity: number;
    warehouseQuantity: number;
    /** Số thiếu trên kệ = đặt − trên kệ. */
    missingQuantity: number;
    shelfLocationId?: string | null;
    warehouseLocationId?: string | null;
}

interface ShelfShortageDialogProps {
    shortage: ShelfShortage;
    submitting: boolean;
    error: string;
    /** Chuyển ngay số lượng đã chọn từ kho lên kệ; quản lý duyệt lại phiếu sau. */
    onIssue(quantity: number): void;
    /** Chưa đưa lên kệ: khi thanh toán phần thiếu vẫn được xuất kho tự động. */
    onDismiss(): void;
}

export function ShelfShortageDialog({
    shortage,
    submitting,
    error,
    onIssue,
    onDismiss
}: ShelfShortageDialogProps) {
    const [draft, setDraft] = useState(
        String(Math.min(shortage.missingQuantity, Math.max(shortage.warehouseQuantity, 0)) || shortage.missingQuantity)
    );
    const quantity = Number(draft);
    const quantityValid = Number.isInteger(quantity) && quantity > 0;
    const overWarehouse = quantityValid && quantity > shortage.warehouseQuantity;
    const stillShort = quantityValid && quantity < shortage.missingQuantity;
    const canIssue = quantityValid && !overWarehouse && !submitting;

    return (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4" role="presentation">
            <section
                aria-labelledby="shelf-shortage-title"
                aria-modal="true"
                className="grid w-full max-w-[520px] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start gap-3 border-b border-[#e8eef0] px-5 py-4">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-amber-50 text-amber-700">
                        <PackagePlus aria-hidden="true" size={22} />
                    </span>
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-black text-[#172317]" id="shelf-shortage-title">
                            Trên kệ đang thiếu {shortage.missingQuantity}
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            {shortage.name}
                            {shortage.sku ? ` · ${shortage.sku}` : ""}
                        </p>
                    </div>
                </header>

                <form
                    className="grid gap-4 px-5 py-4"
                    id="shelf-shortage-form"
                    onSubmit={(event) => {
                        event.preventDefault();

                        if (canIssue) {
                            onIssue(quantity);
                        }
                    }}
                >
                    <dl className="grid grid-cols-3 gap-2 text-center">
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Khách đặt</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {shortage.requestedQuantity}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Trên kệ</dt>
                            <dd className="text-lg font-black tabular-nums text-[#172317]">
                                {shortage.shelfQuantity}
                            </dd>
                        </div>
                        <div className="rounded-xl border border-[#e8eef0] px-2 py-3">
                            <dt className="text-xs font-bold text-[#697684]">Trong kho</dt>
                            <dd
                                className={`text-lg font-black tabular-nums ${
                                    shortage.warehouseQuantity < shortage.missingQuantity
                                        ? "text-red-700"
                                        : "text-[#172317]"
                                }`}
                            >
                                {shortage.warehouseQuantity}
                            </dd>
                        </div>
                    </dl>

                    <label className="grid gap-1.5">
                        <span className="text-sm font-black text-[#172317]">Số lượng đưa từ kho lên kệ</span>
                        <input
                            autoFocus
                            className="h-11 rounded-lg border border-[#dce5e1] px-3 text-lg font-black tabular-nums outline-none focus:border-[#159734]"
                            inputMode="numeric"
                            onChange={(event) => setDraft(event.currentTarget.value.replace(/\D/g, ""))}
                            onFocus={(event) => event.currentTarget.select()}
                            value={draft}
                        />
                        <span className="text-xs font-semibold text-[#697684]">
                            Có thể đưa nhiều hơn số thiếu (vd. cả thùng) để không phải làm phiếu lại. Hàng lên kệ
                            ngay; quản lý duyệt lại phiếu sau.
                        </span>
                    </label>

                    {overWarehouse ? (
                        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
                            Kho chỉ còn {shortage.warehouseQuantity}, không đưa lên được {quantity}.
                        </p>
                    ) : stillShort ? (
                        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
                            Vẫn còn thiếu {shortage.missingQuantity - quantity}; phần này sẽ được xuất kho tự động khi
                            thanh toán.
                        </p>
                    ) : null}

                    {error ? (
                        <p
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            role="alert"
                        >
                            {error}
                        </p>
                    ) : null}
                </form>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] disabled:opacity-60"
                        disabled={submitting}
                        onClick={onDismiss}
                        type="button"
                    >
                        Để sau
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={!canIssue}
                        form="shelf-shortage-form"
                        type="submit"
                    >
                        {submitting ? "Đang chuyển..." : "Đưa lên kệ ngay"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
