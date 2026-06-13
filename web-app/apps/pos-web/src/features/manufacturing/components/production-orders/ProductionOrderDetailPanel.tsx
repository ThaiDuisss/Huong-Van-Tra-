import { Printer, RefreshCw } from "lucide-react";

import { DetailPanel } from "../shared/DetailPanel";
import { StatusBadge } from "../shared/StatusBadge";
import { ProductionLog } from "./ProductionLog";
import { ProductionProgress } from "./ProductionProgress";
import type { ProductionOrderListItem } from "../../types/production-order.types";

interface ProductionOrderDetailPanelProps {
    order: ProductionOrderListItem | null;
    onClose(): void;
}

export function ProductionOrderDetailPanel({
    order,
    onClose
}: ProductionOrderDetailPanelProps) {
    if (!order) {
        return null;
    }

    return (
        <DetailPanel
            title="Chi tiết lệnh sản xuất"
            onClose={onClose}
        >
            <div className="grid gap-6">
                <section className="grid gap-4">
                    <h3 className="font-bold text-pos-ink">
                        Thông tin chung
                    </h3>
                    <div className="flex gap-4">
                        <img
                            src={order.productImageUrl}
                            alt={order.productName}
                            className="size-24 rounded-xl object-cover"
                        />
                        <div className="grid gap-1 text-sm">
                            <strong className="text-base text-pos-ink">
                                {order.code}
                            </strong>
                            <span>Thành phẩm: {order.productName}</span>
                            <span>BOM: {order.bomCode}</span>
                            <StatusBadge status={order.status} />
                            <span>Xưởng: {formatWorkshop(order.workshop)}</span>
                            <span>Người phụ trách: {order.owner}</span>
                        </div>
                    </div>
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-pos-ink">
                        Tiến độ sản xuất
                    </h3>
                    <ProductionProgress
                        plannedQuantity={order.plannedQuantity}
                        completedQuantity={order.completedQuantity}
                        unit={order.unit}
                    />
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-pos-ink">
                        Nguyên liệu sử dụng
                    </h3>
                    <div className="overflow-hidden rounded-xl border border-pos-border">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-neutral-50 text-xs text-pos-muted">
                                <tr>
                                    <th className="p-3">Thành phần</th>
                                    <th className="p-3">Cần dùng</th>
                                    <th className="p-3">Đã xuất</th>
                                </tr>
                            </thead>
                            <tbody>
                                {order.materialUsage.length > 0 ? (
                                    order.materialUsage.map((material) => (
                                        <tr key={material.id}>
                                            <td className="border-t border-pos-border p-3">
                                                {material.name}
                                            </td>
                                            <td className="border-t border-pos-border p-3">
                                                {material.required}
                                            </td>
                                            <td className="border-t border-pos-border p-3">
                                                {material.issued}
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td
                                            colSpan={3}
                                            className="border-t border-pos-border p-4 text-center text-pos-muted"
                                        >
                                            Chưa có dữ liệu nguyên liệu.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-pos-ink">
                        Nhật ký sản xuất
                    </h3>
                    <ProductionLog logs={order.logs} />
                </section>

                <div className="grid grid-cols-2 gap-3 border-t border-pos-border pt-4">
                    <button
                        type="button"
                        className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-pos-border bg-white text-sm font-bold text-hvt-green-900"
                    >
                        <RefreshCw size={16} aria-hidden="true" />
                        Cập nhật
                    </button>
                    <button
                        type="button"
                        className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-pos-border bg-white text-sm font-bold text-pos-ink"
                    >
                        <Printer size={16} aria-hidden="true" />
                        In lệnh sản xuất
                    </button>
                </div>
            </div>
        </DetailPanel>
    );
}

function formatWorkshop(workshop: ProductionOrderListItem["workshop"]) {
    if (workshop === "PACKAGING") {
        return "Xưởng đóng gói";
    }

    if (workshop === "MIXING") {
        return "Xưởng pha trộn";
    }

    return "Xưởng quà tặng";
}
