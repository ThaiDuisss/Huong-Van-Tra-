import { Copy, Pencil } from "lucide-react";

import { DetailPanel } from "../shared/DetailPanel";
import { StatusBadge } from "../shared/StatusBadge";
import type { BomListItem } from "../../types/bom.types";

interface BomDetailPanelProps {
    bom: BomListItem | null;
    onClose(): void;
}

export function BomDetailPanel({
    bom,
    onClose
}: BomDetailPanelProps) {
    if (!bom) {
        return null;
    }

    const components =
        bom.components.length > 0
            ? bom.components
            : [
                  {
                      id: "empty",
                      name: "Chưa có dữ liệu thành phần chi tiết",
                      group: "-",
                      quantity: 0,
                      unit: "-",
                      cost: 0
                  }
              ];

    return (
        <DetailPanel title="Chi tiết BOM" onClose={onClose}>
            <div className="grid gap-6">
                <section className="grid gap-4">
                    <h3 className="font-bold text-gray-500">
                        Thông tin thành phẩm
                    </h3>
                    <div className="flex gap-4">
                        <img
                            src={bom.productImageUrl}
                            alt={bom.productName}
                            className="size-24 rounded-xl object-cover"
                        />
                        <div className="grid gap-1 text-sm">
                            <strong className="text-base text-gray-500">
                                {bom.productName}
                            </strong>
                            <span>SKU: {bom.productSku}</span>
                            <span>Phiên bản: {bom.version}</span>
                            <span>
                                Ngày áp dụng:{" "}
                                {formatDate(bom.effectiveDate)}
                            </span>
                            <StatusBadge status={bom.status} />
                        </div>
                    </div>
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-gray-500">
                        Danh sách thành phần BOM
                    </h3>
                    <div className="overflow-hidden rounded-xl border border-pos-border">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-neutral-50 text-xs text-gray-500">
                                <tr>
                                    <th className="p-3">Tên thành phần</th>
                                    <th className="p-3">Nhóm</th>
                                    <th className="p-3">Định lượng</th>
                                    <th className="p-3">Đơn vị</th>
                                    <th className="p-3 text-right">
                                        Chi phí
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {components.map((component) => (
                                    <tr key={component.id}>
                                        <td className="border-t border-pos-border p-3">
                                            {component.name}
                                        </td>
                                        <td className="border-t border-pos-border p-3">
                                            {component.group}
                                        </td>
                                        <td className="border-t border-pos-border p-3">
                                            {component.quantity}
                                        </td>
                                        <td className="border-t border-pos-border p-3">
                                            {component.unit}
                                        </td>
                                        <td className="border-t border-pos-border p-3 text-right">
                                            {formatVnd(
                                                component.cost
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex justify-between text-lg font-bold">
                        <span>Tổng cộng</span>
                        <span className="text-hvt-green-900">
                            {formatVnd(bom.totalCost)}
                        </span>
                    </div>
                </section>

                <section className="grid grid-cols-2 gap-3">
                    <StatCard
                        label="Tổng thành phần"
                        value={`${bom.componentCount}`}
                    />
                    <StatCard
                        label="Chi phí nguyên liệu"
                        value={formatVnd(bom.materialCost)}
                    />
                    <StatCard
                        label="Chi phí bao bì"
                        value={formatVnd(bom.packagingCost)}
                    />
                    <StatCard
                        label="Tổng chi phí BOM"
                        value={formatVnd(bom.totalCost)}
                    />
                </section>

                <section className="grid gap-3">
                    <h3 className="font-bold text-gray-500">
                        Lịch sử thay đổi
                    </h3>
                    {bom.history.length > 0 ? (
                        bom.history.map((entry) => (
                            <div
                                key={entry.id}
                                className="grid grid-cols-[1fr_auto] gap-2 border-b border-pos-border pb-2 text-sm last:border-b-0"
                            >
                                <div>
                                    <p className="font-semibold">
                                        {formatDate(entry.date)}
                                    </p>
                                    <p className="text-gray-500">
                                        {entry.description}
                                    </p>
                                </div>
                                <span className="text-gray-500">
                                    {entry.actor}
                                </span>
                            </div>
                        ))
                    ) : (
                        <p className="text-sm text-gray-500">
                            Chưa có lịch sử thay đổi.
                        </p>
                    )}
                </section>

                <div className="grid grid-cols-2 gap-3 border-t border-pos-border pt-4">
                    <button
                        type="button"
                        className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-pos-border bg-white text-sm font-bold text-hvt-green-900"
                    >
                        <Pencil size={16} aria-hidden="true" />
                        Chỉnh sửa BOM
                    </button>
                    <button
                        type="button"
                        className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-pos-border bg-white text-sm font-bold text-gray-500"
                    >
                        <Copy size={16} aria-hidden="true" />
                        Nhân bản BOM
                    </button>
                </div>
            </div>
        </DetailPanel>
    );
}

function StatCard({
    label,
    value
}: {
    label: string;
    value: string;
}) {
    return (
        <div className="rounded-xl border border-pos-border p-3">
            <p className="text-xs text-gray-500">{label}</p>
            <strong className="mt-1 block text-lg text-gray-500">
                {value}
            </strong>
        </div>
    );
}

function formatVnd(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN").format(
        new Date(`${value}T00:00:00`)
    );
}
