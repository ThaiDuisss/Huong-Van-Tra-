import { useState, useMemo, useEffect } from "react";
import { X, Printer, Plus, Minus, Sliders, Eye } from "lucide-react";
import type { ProductVariant } from "../types/product.types";
import { BarcodeSvg } from "./BarcodeSvg";
import { formatCurrency } from "../utils/product-formatters";

export interface BulkBarcodePrintModalProps {
    isOpen: boolean;
    onClose: () => void;
    variants: ProductVariant[];
}

export function BulkBarcodePrintModal({
    isOpen,
    onClose,
    variants
}: BulkBarcodePrintModalProps) {
    // Map variant.id -> số lượng tem cần in (mặc định 1 tem)
    const [quantities, setQuantities] = useState<Record<string, number>>({});
    const [batchQty, setBatchQty] = useState<number>(1);
    const [activeTab, setActiveTab] = useState<"config" | "preview">("config");

    // Khởi tạo số lượng tem khi variants thay đổi
    useEffect(() => {
        if (isOpen && variants.length > 0) {
            const initialQty: Record<string, number> = {};
            variants.forEach((v) => {
                initialQty[String(v.id)] = quantities[String(v.id)] || 1;
            });
            setQuantities(initialQty);
        }
    }, [isOpen, variants]);

    const handleQuantityChange = (id: string, qty: number) => {
        const validQty = Math.max(0, Math.min(999, Math.floor(qty || 0)));
        setQuantities((prev) => ({ ...prev, [id]: validQty }));
    };

    const handleApplyAll = () => {
        const validQty = Math.max(1, Math.min(999, Math.floor(batchQty || 1)));
        const updated: Record<string, number> = {};
        variants.forEach((v) => {
            updated[String(v.id)] = validQty;
        });
        setQuantities(updated);
    };

    // Danh sách các tem đơn lẻ cần in ra
    const labelItems = useMemo(() => {
        const items: Array<{ variant: ProductVariant; index: number }> = [];
        variants.forEach((v) => {
            const count = quantities[String(v.id)] ?? 1;
            for (let i = 0; i < count; i++) {
                items.push({ variant: v, index: i });
            }
        });
        return items;
    }, [variants, quantities]);

    const totalLabels = labelItems.length;

    // In bằng cách mở một cửa sổ in riêng biệt với CSS chuẩn tem in nhãn
    const handlePrint = () => {
        if (totalLabels === 0) return;

        const printWindow = window.open("", "_blank");
        if (!printWindow) {
            alert("Trình duyệt đã chặn cửa sổ bật lên. Vui lòng cho phép popup để in tem.");
            return;
        }

        // Lấy toàn bộ SVG nội dung từ preview container
        const previewElement = document.getElementById("barcode-print-content");
        const printContent = previewElement ? previewElement.innerHTML : "";

        printWindow.document.write(`
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>In mã vạch - Hương Vân Trà</title>
                <style>
                    @page {
                        size: auto;
                        margin: 2mm;
                    }
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: #fff;
                        color: #000;
                    }
                    .labels-grid {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 4mm;
                        justify-content: flex-start;
                        padding: 4mm;
                    }
                    .label-card {
                        width: 50mm;
                        height: 32mm;
                        padding: 2mm;
                        border: 1px dashed #bbb;
                        border-radius: 2mm;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: space-between;
                        text-align: center;
                        page-break-inside: avoid;
                        background: #fff;
                    }
                    .brand-name {
                        font-size: 8pt;
                        font-weight: 800;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        color: #000;
                    }
                    .variant-name {
                        font-size: 8pt;
                        font-weight: 700;
                        line-height: 1.1;
                        max-height: 2.2em;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        display: -webkit-box;
                        -webkit-line-clamp: 2;
                        -webkit-box-orient: vertical;
                        margin: 1mm 0;
                    }
                    .barcode-container {
                        width: 100%;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        flex: 1;
                    }
                    .barcode-container svg {
                        max-width: 100%;
                        max-height: 14mm;
                    }
                    .footer-info {
                        width: 100%;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        font-size: 8pt;
                        margin-top: 1mm;
                        border-top: 0.5px solid #ddd;
                        padding-top: 1mm;
                    }
                    .price {
                        font-weight: 800;
                    }
                    @media print {
                        .label-card {
                            border: 0.5px solid #eee;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="labels-grid">
                    ${printContent}
                </div>
                <script>
                    window.onload = function() {
                        window.focus();
                        window.print();
                        setTimeout(function() {
                            window.close();
                        }, 500);
                    };
                </script>
            </body>
            </html>
        `);
        printWindow.document.close();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
            <div className="flex flex-col w-full max-w-5xl h-[88vh] bg-white rounded-2xl shadow-2xl border border-neutral-100 overflow-hidden animate-in fade-in-50 zoom-in-95 duration-200">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-200 bg-linear-to-r from-emerald-50/50 to-white">
                    <div className="flex items-center gap-3">
                        <div className="grid w-10 h-10 rounded-xl bg-pos-primary/10 text-pos-primary place-items-center">
                            <Printer size={22} />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-gray-900">
                                In mã
                            </h2>
                            <p className="text-xs font-medium text-gray-500">
                                Đã chọn <span className="font-bold text-gray-700">{variants.length}</span> mặt hàng | Tổng số tem sẽ in: <span className="font-bold text-pos-primary">{totalLabels}</span> tem
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        {/* Tab toggle on smaller screens */}
                        <div className="flex lg:hidden bg-neutral-100 p-1 rounded-xl text-xs font-bold">
                            <button
                                type="button"
                                onClick={() => setActiveTab("config")}
                                className={`px-3 py-1.5 rounded-lg transition-all ${
                                    activeTab === "config"
                                        ? "bg-white text-gray-900 shadow-xs"
                                        : "text-gray-500 hover:text-gray-900"
                                }`}
                            >
                                <Sliders size={14} className="inline mr-1" />
                                Cấu hình
                            </button>
                            <button
                                type="button"
                                onClick={() => setActiveTab("preview")}
                                className={`px-3 py-1.5 rounded-lg transition-all ${
                                    activeTab === "preview"
                                        ? "bg-white text-gray-900 shadow-xs"
                                        : "text-gray-500 hover:text-gray-900"
                                }`}
                            >
                                <Eye size={14} className="inline mr-1" />
                                Xem trước ({totalLabels})
                            </button>
                        </div>
                        <button
                            type="button"
                            onClick={onClose}
                            className="grid w-9 h-9 rounded-xl text-gray-400 hover:text-gray-700 hover:bg-neutral-100 place-items-center transition-all cursor-pointer"
                        >
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Body - Split Layout */}
                <div className="flex-1 flex overflow-hidden">
                    {/* Left Column: Bảng danh sách chọn số lượng tem */}
                    <div
                        className={`flex-1 lg:flex flex-col border-r border-neutral-200 min-w-0 ${
                            activeTab === "config" ? "flex" : "hidden lg:flex"
                        }`}
                    >
                        {/* Quick Batch Actions Toolbar */}
                        <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-neutral-50/80 border-b border-neutral-200">
                            <div className="flex items-center gap-2">
                                <span className="text-xs font-bold text-gray-600">Số lượng tem mỗi loại:</span>
                                <div className="flex items-center">
                                    <input
                                        type="number"
                                        min={1}
                                        max={999}
                                        value={batchQty}
                                        onChange={(e) => setBatchQty(parseInt(e.target.value) || 1)}
                                        className="w-18 h-8 px-2 text-center text-sm font-bold border border-gray-300 rounded-lg bg-white focus:outline-hidden focus:ring-2 focus:ring-pos-primary"
                                    />
                                    <button
                                        type="button"
                                        onClick={handleApplyAll}
                                        className="ml-2 px-3 h-8 text-xs font-bold text-white bg-pos-primary rounded-lg hover:bg-hvt-green-700 transition-all cursor-pointer shadow-xs active:scale-95"
                                    >
                                        Áp dụng tất cả
                                    </button>
                                </div>
                            </div>
                            <div className="flex items-center gap-1.5">
                                {[1, 5, 10, 20].map((num) => (
                                    <button
                                        key={num}
                                        type="button"
                                        onClick={() => {
                                            setBatchQty(num);
                                            const updated: Record<string, number> = {};
                                            variants.forEach((v) => {
                                                updated[String(v.id)] = num;
                                            });
                                            setQuantities(updated);
                                        }}
                                        className="px-2 py-1 text-xs font-semibold rounded-md border border-gray-200 bg-white text-gray-700 hover:bg-neutral-100 hover:border-gray-300 cursor-pointer active:scale-95 transition-all"
                                    >
                                        {num} tem
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* List items table */}
                        <div className="flex-1 overflow-y-auto">
                            <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                                <thead className="sticky top-0 z-10 bg-white shadow-xs text-xs font-bold text-gray-500 uppercase tracking-wider">
                                    <tr>
                                        <th className="px-4 py-3 border-b border-neutral-200">Mặt hàng</th>
                                        <th className="px-4 py-3 border-b border-neutral-200">Mã vạch (Barcode)</th>
                                        <th className="px-4 py-3 border-b border-neutral-200 text-right">Giá bán</th>
                                        <th className="px-4 py-3 border-b border-neutral-200 text-center w-36">Số tem</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-neutral-100">
                                    {variants.map((v) => {
                                        const qty = quantities[String(v.id)] ?? 1;
                                        const hasBarcode = Boolean(v.barcode && v.barcode.trim());
                                        return (
                                            <tr key={v.id} className="hover:bg-neutral-50/70 transition-colors">
                                                <td className="px-4 py-3 align-middle">
                                                    <div className="font-bold text-gray-900 leading-snug">
                                                        {v.variantName}
                                                    </div>
                                                    <div className="text-xs text-gray-500">
                                                        {v.productName}
                                                    </div>
                                                </td>
                                                <td className="px-4 py-3 align-middle">
                                                    {hasBarcode ? (
                                                        <span className="text-xs font-mono text-emerald-700 font-bold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 inline-block">
                                                            {v.barcode}
                                                        </span>
                                                    ) : (
                                                        <span className="text-[11px] font-medium text-amber-600 bg-amber-50 px-2 py-0.5 rounded border border-amber-200 inline-block">
                                                            Chưa có mã vạch
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="px-4 py-3 align-middle text-right font-bold text-gray-800">
                                                    {formatCurrency(v.sellingPrice)}
                                                </td>
                                                <td className="px-4 py-3 align-middle">
                                                    <div className="flex items-center justify-center gap-1">
                                                        <button
                                                            type="button"
                                                            onClick={() => handleQuantityChange(String(v.id), qty - 1)}
                                                            className="w-7 h-7 rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-neutral-100 flex items-center justify-center cursor-pointer active:scale-95"
                                                        >
                                                            <Minus size={13} />
                                                        </button>
                                                        <input
                                                            type="number"
                                                            min={0}
                                                            max={999}
                                                            value={qty}
                                                            onChange={(e) =>
                                                                handleQuantityChange(String(v.id), parseInt(e.target.value) || 0)
                                                            }
                                                            className="w-12 h-7 text-center font-bold text-sm border border-gray-300 rounded-md focus:ring-1 focus:ring-pos-primary focus:outline-hidden"
                                                        />
                                                        <button
                                                            type="button"
                                                            onClick={() => handleQuantityChange(String(v.id), qty + 1)}
                                                            className="w-7 h-7 rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-neutral-100 flex items-center justify-center cursor-pointer active:scale-95"
                                                        >
                                                            <Plus size={13} />
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    {/* Right Column: Xem trước bản in trực tiếp (Live Preview) */}
                    <div
                        className={`w-full lg:w-[420px] xl:w-[480px] bg-neutral-100 flex flex-col ${
                            activeTab === "preview" ? "flex" : "hidden lg:flex"
                        }`}
                    >
                        <div className="px-5 py-3.5 bg-white border-b border-neutral-200 flex items-center justify-between shadow-xs">
                            <div className="flex items-center gap-2">
                                <Eye size={16} className="text-pos-primary" />
                                <h3 className="text-sm font-bold text-gray-800">
                                    Xem trước tem in ({totalLabels})
                                </h3>
                            </div>
                            <span className="text-xs font-semibold text-gray-500 bg-neutral-100 px-2 py-1 rounded-md">
                                Khổ 50x32 mm
                            </span>
                        </div>

                        {/* Preview Scroll Area */}
                        <div className="flex-1 p-5 overflow-y-auto">
                            {totalLabels === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 p-8">
                                    <Printer size={36} className="mb-2 text-gray-300" />
                                    <p className="text-sm font-medium">Chưa có tem nào được chọn để in.</p>
                                    <p className="text-xs mt-1">Vui lòng tăng số lượng tem ở cột bên trái.</p>
                                </div>
                            ) : (
                                <div
                                    id="barcode-print-content"
                                    className="flex flex-wrap gap-4 justify-center"
                                >
                                    {labelItems.map(({ variant, index }) => {
                                        const barcodeValue = variant.barcode;
                                        return (
                                            <div
                                                key={`${variant.id}-${index}`}
                                                className="label-card w-[210px] h-[135px] p-2.5 bg-white rounded-xl border border-gray-300 shadow-sm flex flex-col justify-between items-center text-center"
                                            >
                                                <div className="brand-name text-[10px] font-extrabold uppercase tracking-wider text-pos-primary">
                                                    Hương Vân Trà
                                                </div>
                                                <div className="variant-name text-[11px] font-bold text-gray-900 leading-tight line-clamp-2 px-1">
                                                    {variant.variantName}
                                                </div>
                                                <div className="barcode-container w-full flex items-center justify-center py-0.5">
                                                    <BarcodeSvg
                                                        value={barcodeValue}
                                                        width={1.3}
                                                        height={34}
                                                        fontSize={10}
                                                        displayValue={true}
                                                    />
                                                </div>
                                                <div className="footer-info w-full flex items-center justify-center text-[11px] border-t border-gray-100 pt-1 px-1">
                                                    <span className="price font-extrabold text-emerald-700">
                                                        {formatCurrency(variant.sellingPrice)}
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* Footer Controls */}
                <div className="flex items-center justify-between px-6 py-4 border-t border-neutral-200 bg-white">
                    <div className="text-sm font-medium text-gray-600">
                        Tổng số tem cần in: <span className="font-bold text-pos-primary text-base">{totalLabels}</span> tem
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-4 py-2.5 text-sm font-bold text-gray-700 bg-white border border-gray-300 rounded-xl hover:bg-neutral-50 active:scale-95 transition-all cursor-pointer"
                        >
                            Đóng
                        </button>
                        <button
                            type="button"
                            onClick={handlePrint}
                            disabled={totalLabels === 0}
                            className={`flex items-center gap-2 px-5 py-2.5 text-sm font-bold text-white rounded-xl shadow-md transition-all active:scale-95 cursor-pointer ${
                                totalLabels === 0
                                    ? "bg-gray-300 cursor-not-allowed shadow-none"
                                    : "bg-pos-primary hover:bg-hvt-green-700"
                            }`}
                        >
                            <Printer size={18} />
                            In {totalLabels > 0 ? `(${totalLabels} tem)` : ""}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
