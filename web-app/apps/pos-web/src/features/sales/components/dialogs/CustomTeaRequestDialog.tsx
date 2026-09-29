import { Search, X } from "lucide-react";
import { useMemo, useState } from "react";

import { formatCurrency } from "../../services/money";
import type { SaleProductPreview } from "../../types/sale.types";

interface CustomTeaRequestDialogProps {
    open: boolean;
    products: SaleProductPreview[];
    searchQuery: string;
    searchLoading: boolean;
    searchError: string;
    submitLoading: boolean;
    onClose(): void;
    onSearchChange(value: string): void;
    onSubmit(input: {
        product: SaleProductPreview;
        quantity: number;
        note: string;
    }): void;
}

export function CustomTeaRequestDialog({
    open,
    products,
    searchQuery,
    searchLoading,
    searchError,
    submitLoading,
    onClose,
    onSearchChange,
    onSubmit
}: CustomTeaRequestDialogProps) {
    const [selectedProductId, setSelectedProductId] = useState("");
    const [quantity, setQuantity] = useState("1");
    const [note, setNote] = useState("");
    const [validationError, setValidationError] = useState("");
    const selectedProduct = useMemo(
        () => products.find((product) => product.id === selectedProductId) ?? null,
        [products, selectedProductId]
    );

    if (!open) {
        return null;
    }

    function submit() {
        const parsedQuantity = Number(quantity);
        const trimmedNote = note.trim();

        if (!selectedProduct) {
            setValidationError("Vui lòng chọn SKU để gắn yêu cầu.");
            return;
        }

        if (!Number.isFinite(parsedQuantity) || parsedQuantity <= 0) {
            setValidationError("Số lượng phải lớn hơn 0.");
            return;
        }

        if (!trimmedNote) {
            setValidationError("Vui lòng nhập yêu cầu của khách hàng.");
            return;
        }

        onSubmit({
            product: selectedProduct,
            quantity: parsedQuantity,
            note: trimmedNote
        });
    }

    return (
        <div
            className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
            role="presentation"
        >
            <section
                aria-modal="true"
                className="grid max-h-[88vh] w-full max-w-[780px] grid-rows-[auto_minmax(0,1fr)_auto] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start justify-between gap-4 border-b border-[#e8eef0] px-5 py-4">
                    <div className="grid gap-1">
                        <h2 className="text-xl font-black text-[#172317]">
                            Trà theo yêu cầu
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            Chọn SKU, nhập số lượng và ghi rõ yêu cầu để Kho chuẩn bị.
                        </p>
                    </div>
                    <button
                        aria-label="Đóng"
                        className="grid h-9 w-9 place-items-center rounded-lg border border-[#dce5e1] text-[#536078]"
                        disabled={submitLoading}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </header>

                <div className="grid min-h-0 gap-4 overflow-y-auto px-5 py-4">
                    <label className="grid gap-2 text-sm font-black text-[#536078]">
                        Sản phẩm / SKU
                        <div className="relative">
                            <Search
                                aria-hidden="true"
                                className="absolute left-3 top-3 text-[#697684]"
                                size={16}
                            />
                            <input
                                className="h-11 w-full rounded-lg border border-pos-border pl-9 pr-3 text-sm font-bold outline-none focus:border-[#159734]"
                                onChange={(event) => {
                                    setSelectedProductId("");
                                    onSearchChange(event.currentTarget.value);
                                }}
                                placeholder="Tìm theo tên, SKU hoặc barcode"
                                value={searchQuery}
                            />
                        </div>
                    </label>

                    <div className="grid max-h-[220px] gap-2 overflow-y-auto rounded-xl border border-[#e8eef0] bg-[#f8fbf8] p-2">
                        {searchLoading ? (
                            <p className="px-3 py-4 text-sm font-bold text-[#697684]">
                                Đang tìm sản phẩm...
                            </p>
                        ) : null}
                        {searchError ? (
                            <p className="px-3 py-4 text-sm font-bold text-red-700">
                                {searchError}
                            </p>
                        ) : null}
                        {!searchLoading && !searchError && products.length === 0 ? (
                            <p className="px-3 py-4 text-sm font-bold text-[#697684]">
                                Nhập từ khóa để chọn SKU.
                            </p>
                        ) : null}
                        {products.map((product) => {
                            const selected = product.id === selectedProductId;
                            return (
                                <button
                                    className={`grid gap-1 rounded-lg border px-3 py-2 text-left ${
                                        selected
                                            ? "border-[#159734] bg-white shadow-pos-card"
                                            : "border-transparent bg-white"
                                    }`}
                                    key={product.id}
                                    onClick={() => setSelectedProductId(product.id)}
                                    type="button"
                                >
                                    <span className="font-black text-[#172317]">
                                        {product.name}
                                    </span>
                                    <span className="text-xs font-bold text-[#697684]">
                                        {product.sku} · {product.variant || "SKU mặc định"} ·{" "}
                                        {formatCurrency(product.price)}
                                    </span>
                                    <span className="text-xs font-black text-[#159734]">
                                        Tồn khả dụng:{" "}
                                        {product.sellableQuantity === null
                                            ? "Chưa đồng bộ"
                                            : product.sellableQuantity}
                                    </span>
                                </button>
                            );
                        })}
                    </div>

                    <label className="grid gap-2 text-sm font-black text-[#536078]">
                        Số lượng
                        <input
                            className="h-11 rounded-lg border border-pos-border px-3 text-sm font-bold outline-none focus:border-[#159734]"
                            min={1}
                            onChange={(event) => setQuantity(event.currentTarget.value)}
                            type="number"
                            value={quantity}
                        />
                    </label>

                    <label className="grid gap-2 text-sm font-black text-[#536078]">
                        Yêu cầu của khách hàng
                        <textarea
                            className="min-h-[130px] resize-y rounded-lg border border-pos-border px-3 py-2 text-sm font-semibold outline-none focus:border-[#159734]"
                            onChange={(event) => setNote(event.currentTarget.value)}
                            placeholder="Ví dụ: Trà hoa nhài nhẹ mùi. Đóng hộp 200g. Không dùng túi lọc. Dùng làm quà tặng."
                            value={note}
                        />
                    </label>

                    {validationError ? (
                        <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
                            {validationError}
                        </p>
                    ) : null}
                </div>

                <footer className="flex justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                        disabled={submitLoading}
                        onClick={onClose}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={submitLoading}
                        onClick={submit}
                        type="button"
                    >
                        {submitLoading ? "Đang thêm..." : "Thêm vào đơn"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
