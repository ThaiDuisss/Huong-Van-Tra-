import { X } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";

import { Button, IconButton } from "../../../components/ui/Button";
import { Field, TextInput } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { productVariantApi } from "../services/productVariant.api";
import type { ProductVariant, SellableConfigInput } from "../types/product.types";

interface SellableConfigDialogProps {
    variant: ProductVariant;
    onClose(): void;
    onSaved(variant: ProductVariant): void;
}

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
        <ModalShell
            className="w-[min(512px,calc(100vw-32px))] p-6"
            closeOnBackdrop={false}
            labelledBy="sellable-config-title"
            onClose={onClose}
        >
            <form noValidate onSubmit={handleSubmit}>
                <header className="mb-5 flex items-start justify-between gap-3 border-b border-pos-border pb-3">
                    <div className="grid gap-0.5">
                        <h2 className="text-xl font-bold text-gray-700" id="sellable-config-title">
                            Cấu hình bán hàng
                        </h2>
                        <p className="text-sm font-semibold text-gray-500">{variant.variantName}</p>
                        <p className="font-mono text-xs text-gray-400">{variant.sku}</p>
                    </div>
                    <IconButton aria-label="Đóng biểu mẫu" onClick={onClose} size="md">
                        <X size={20} />
                    </IconButton>
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
                    <Field hint="Ghi đè con số hiện tại. Mỗi đơn bán thành công sẽ tự trừ vào số này." label="Số lượng có thể bán" required>
                        <TextInput
                            disabled={disabled}
                            min={0}
                            onChange={(event) => setSellableQuantity(event.target.value)}
                            step="0.01"
                            type="number"
                            value={sellableQuantity}
                        />
                    </Field>

                    <Field hint="Khi số lượng có thể bán giảm xuống bằng hoặc dưới ngưỡng, hệ thống tạo cảnh báo cần sản xuất." label="Ngưỡng yêu cầu sản xuất" required>
                        <TextInput
                            disabled={disabled}
                            min={0}
                            onChange={(event) => setProductionThreshold(event.target.value)}
                            step="0.01"
                            type="number"
                            value={productionThreshold}
                        />
                    </Field>

                    <Field hint="Số lượng đề xuất sản xuất trong cảnh báo." label="Số lượng mỗi mẻ sản xuất" required>
                        <TextInput
                            disabled={disabled}
                            min={0.01}
                            onChange={(event) => setProductionBatchQuantity(event.target.value)}
                            step="0.01"
                            type="number"
                            value={productionBatchQuantity}
                        />
                    </Field>
                </div>

                <footer className="mt-6 flex items-center justify-end gap-3 border-t border-pos-border pt-4">
                    <Button onClick={onClose}>
                        Hủy bỏ
                    </Button>
                    <Button disabled={disabled} type="submit" variant="primary">
                        {submitting ? "Đang lưu..." : loading ? "Đang tải..." : "Lưu cấu hình"}
                    </Button>
                </footer>
            </form>
        </ModalShell>
    );
}
