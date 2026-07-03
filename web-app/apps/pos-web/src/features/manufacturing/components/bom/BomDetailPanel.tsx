import { Pencil } from "lucide-react";

import { DetailPanel } from "../shared/DetailPanel";
import { StatusBadge } from "../shared/StatusBadge";
import type { BomResponse } from "../../types/bom.types";

interface BomDetailPanelProps {
    bom: BomResponse | null;
    onClose(): void;
    onEdit(bom: BomResponse): void;
}

export function BomDetailPanel({
    bom,
    onClose,
    onEdit
}: BomDetailPanelProps) {
    if (!bom) {
        return null;
    }

    const items = bom.items ?? [];

    return (
        <DetailPanel title="Chi tiết BOM" onClose={onClose}>
            <div className="grid gap-6">
                <section className="grid gap-4">
                    <h3 className="font-bold text-gray-700">
                        Thông tin chung
                    </h3>
                    <div className="grid gap-2 text-sm text-gray-600">
                        <div>
                            <span className="text-gray-400 font-medium">Mã BOM: </span>
                            <span className="font-bold">{bom.bomCode || "-"}</span>
                        </div>
                        <div>
                            <span className="text-gray-400 font-medium">Tên BOM: </span>
                            <span className="font-semibold">{bom.name || "-"}</span>
                        </div>
                        <div>
                            <span className="text-gray-400 font-medium">Sản phẩm: </span>
                            <span className="font-semibold">{bom.productName || "-"}</span>
                        </div>
                        <div>
                            <span className="text-gray-400 font-medium">Mặt hàng: </span>
                            <span className="font-semibold">{bom.variantName || "-"}</span>
                        </div>
                        <div>
                            <span className="text-gray-400 font-medium">SKU: </span>
                            <span className="font-mono bg-neutral-100 px-1.5 py-0.5 rounded text-xs font-semibold">{bom.variantSku || "-"}</span>
                        </div>
                        <div>
                            <span className="text-gray-400 font-medium">Phiên bản: </span>
                            <span className="font-bold bg-gray-100 text-gray-800 px-2 py-0.5 rounded text-xs">
                                {bom.version}
                            </span>
                        </div>
                        <div className="flex items-center gap-2 mt-1">
                            <span className="text-gray-400 font-medium">Mặc định: </span>
                            {bom.isDefault ? (
                                <span className="bg-green-100 text-green-800 font-bold px-2 py-0.5 rounded text-xs">
                                    Mặc định
                                </span>
                            ) : (
                                <span className="bg-gray-100 text-gray-500 px-2 py-0.5 rounded text-xs">
                                    Không
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="text-gray-400 font-medium">Trạng thái: </span>
                            <StatusBadge status={bom.status} />
                        </div>
                        <div className="mt-2 border-t border-pos-border pt-2">
                            <p className="text-gray-400 font-medium mb-1">Mô tả / Ghi chú:</p>
                            <p className="text-gray-700 bg-neutral-50 p-2.5 rounded-lg border border-pos-border text-xs min-h-[50px] whitespace-pre-wrap">
                                {bom.description || "Không có mô tả."}
                            </p>
                        </div>
                    </div>
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-gray-700">
                        Danh sách thành phần nguyên liệu ({items.length})
                    </h3>
                    {items.length > 0 ? (
                        <div className="overflow-hidden rounded-xl border border-pos-border">
                            <table className="w-full text-left text-xs">
                                <thead className="bg-neutral-50 text-[11px] font-bold text-gray-500 uppercase tracking-wider">
                                    <tr>
                                        <th className="p-3">Mã nguyên liệu</th>
                                        <th className="p-3">Tên nguyên liệu</th>
                                        <th className="p-3 text-right">Định lượng</th>
                                        <th className="p-3">Đơn vị</th>
                                        <th className="p-3 text-right">Hao hụt (%)</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-pos-border">
                                    {items.map((item) => (
                                        <tr key={item.id} className="hover:bg-neutral-50">
                                            <td className="p-3 font-mono text-gray-500 font-semibold">
                                                {item.ingredient.ingredientCode}
                                            </td>
                                            <td className="p-3 font-medium text-gray-700">
                                                {item.ingredient.name}
                                            </td>
                                            <td className="p-3 text-right font-semibold text-gray-900">
                                                {item.quantity}
                                            </td>
                                            <td className="p-3 text-gray-500 font-medium">
                                                {item.unit.symbol || item.unit.name}
                                            </td>
                                            <td className="p-3 text-right font-medium text-amber-600">
                                                {item.wasteRate}%
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <p className="text-sm text-gray-500 italic">
                            Chưa cấu hình nguyên liệu cho BOM này.
                        </p>
                    )}
                </section>

                <div className="border-t border-pos-border pt-4">
                    <button
                        type="button"
                        onClick={() => onEdit(bom)}
                        className="inline-flex w-full h-11 items-center justify-center gap-2 rounded-xl bg-[#07883a] text-sm font-bold text-white hover:bg-[#067331] transition-all cursor-pointer shadow-sm"
                    >
                        <Pencil size={16} aria-hidden="true" />
                        Chỉnh sửa BOM
                    </button>
                </div>
            </div>
        </DetailPanel>
    );
}
