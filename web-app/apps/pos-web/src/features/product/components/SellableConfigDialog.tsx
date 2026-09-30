import { X } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";

import { productVariantApi } from "../services/productVariant.api";
import type { ProductVariant, SellableConfigInput } from "../types/product.types";

interface SellableConfigDialogProps {
    variant: ProductVariant;
    onClose(): void;
    onSaved(variant: ProductVariant): void;
}

const inputClass =
    "h-10 w-full rounded-xl border border-pos-border bg-white px-3 font-medium text-gray-700 focus:border-[#07883a] focus:outline-none disabled:bg-neutral-50";
const labelClass = "grid gap-1.5 text-sm font-bold text-gray-500";
const hintClass = "text-xs font-medium text-gray-400";

export function SellableConfigDialog({ variant, onClose, onSaved }: SellableConfigDialogProps) {
    const [sellableQuantity, setSellableQuantity] = useState(String(variant.sellableQuantity ?? 0));
    const [productionThreshold, setProductionThreshold] = useState(String(variant.productionThreshold ?? 0));
    const [productionBatchQuantity, setProductionBatchQuantity] = useState(
        String(variant.productionBatchQuantity ?? 1)
    );
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    // Số lượng có thể bán thay đổi liên tục theo đơn hàng nên tải lại giá trị mới nhất trước khi cho sửa.
    useEffect(() => {
        let active = true;
        productVariantApi
            .detail(String(variant.id))
            .then((latest) => {
                if (!active) return;
                setSellableQuantity(String(latest.sellableQuantity ?? 0));
                setProductionThreshold(String(latest.productionThreshold ?? 0));
                setProductionBatchQuantity(String(latest.productionBatchQuantity ?? 1));
            })
            .catch((err) => console.error("Error loading latest sellable config", err))
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [variant.id]);

    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") onClose();
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [onClose]);

    function buildInput(): SellableConfigInput | null {
        const input: SellableConfigInput = {
            sellableQuantity: Number(sellableQuantity),
            productionThreshold: Number(productionThreshold),
            productionBatchQuantity: Number(productionBatchQuantity)
        };
        const values = [sellableQuantity, productionThreshold, productionBatchQuantity];
        if (values.some((value) => value.trim() === "") || Object.values(input).some(Number.isNaN)) {
            setError("Vui lòng nhập đầy đủ cả ba giá trị.");
            return null;
        }
        if (input.sellableQuantity < 0 || input.productionThreshold < 0) {
            setError("Số lượng có thể bán và ngưỡng sản xuất không được âm.");
            return null;
        }
        if (input.productionBatchQuantity <= 0) {
            setError("Số lượng mỗi mẻ sản xuất phải lớn hơn 0.");
            return null;
        }
        return input;
    }

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        const input = buildInput();
        if (!input) return;

        setError("");
        setSubmitting(true);
        try {
            onSaved(await productVariantApi.updateSellableConfig(String(variant.id), input));
        } catch (err) {
            setError(err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu cấu hình bán hàng.");
        } finally {
            setSubmitting(false);
        }
    }

    const disabled = loading || submitting;

    return (
        <div className="fixed inset-0 z-40 grid place-items-center overflow-y-auto bg-black/40 p-6">
            <form
                aria-labelledby="sellable-config-title"
                aria-modal="true"
                className="w-full max-w-lg rounded-3xl border border-pos-border bg-white p-6 shadow-pos-panel"
                noValidate
                onSubmit={handleSubmit}
                role="dialog"
            >
                <header className="mb-5 flex items-start justify-between gap-3 border-b border-pos-border pb-3">
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-bold text-gray-700" id="sellable-config-title">
                            Cấu hình bán hàng
                        </h2>
                        <p className="text-sm font-semibold text-gray-500">{variant.variantName}</p>
                        <p className="font-mono text-xs text-gray-400">{variant.sku}</p>
                    </div>
                    <button
                        aria-label="Đóng biểu mẫu"
                        className="grid size-9 shrink-0 cursor-pointer place-items-center rounded-lg transition-all hover:bg-neutral-100 focus:outline-none"
                        onClick={onClose}
                        type="button"
                    >
                        <X size={20} />
                    </button>
                </header>

                {error ? (
                    <div
                        className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-600"
                        role="alert"
                    >
                        {error}
                    </div>
                ) : null}

                <div className="grid gap-4">
                    <label className={labelClass}>
                        Số lượng có thể bán *
                        <input
                            className={inputClass}
                            disabled={disabled}
                            min={0}
                            onChange={(event) => setSellableQuantity(event.target.value)}
                            step="0.01"
                            type="number"
                            value={sellableQuantity}
                        />
                        <span className={hintClass}>
                            Ghi đè con số hiện tại. Mỗi đơn bán thành công sẽ tự trừ vào số này.
                        </span>
                    </label>

                    <label className={labelClass}>
                        Ngưỡng yêu cầu sản xuất *
                        <input
                            className={inputClass}
                            disabled={disabled}
                            min={0}
                            onChange={(event) => setProductionThreshold(event.target.value)}
                            step="0.01"
                            type="number"
                            value={productionThreshold}
                        />
                        <span className={hintClass}>
                            Khi số lượng có thể bán giảm xuống bằng hoặc dưới ngưỡng, hệ thống tạo cảnh báo cần sản xuất.
                        </span>
                    </label>

                    <label className={labelClass}>
                        Số lượng mỗi mẻ sản xuất *
                        <input
                            className={inputClass}
                            disabled={disabled}
                            min={0.01}
                            onChange={(event) => setProductionBatchQuantity(event.target.value)}
                            step="0.01"
                            type="number"
                            value={productionBatchQuantity}
                        />
                        <span className={hintClass}>Số lượng đề xuất sản xuất trong cảnh báo.</span>
                    </label>
                </div>

                <footer className="mt-6 flex items-center justify-end gap-3 border-t border-pos-border pt-4">
                    <button
                        className="h-11 cursor-pointer rounded-xl border border-pos-border bg-white px-6 font-bold text-gray-500 transition-all hover:bg-neutral-50 focus:outline-none"
                        onClick={onClose}
                        type="button"
                    >
                        Hủy bỏ
                    </button>
                    <button
                        className="h-11 cursor-pointer rounded-xl bg-[#07883a] px-6 font-bold text-white shadow-sm transition-all hover:bg-[#067331] focus:outline-none disabled:opacity-50"
                        disabled={disabled}
                        type="submit"
                    >
                        {submitting ? "Đang lưu..." : loading ? "Đang tải..." : "Lưu cấu hình"}
                    </button>
                </footer>
            </form>
        </div>
    );
}
