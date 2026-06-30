import { MoreHorizontal } from "lucide-react";

import { StatusBadge } from "../shared/StatusBadge";
import type { ProductionOrderListItem } from "../../types/production-order.types";

interface ProductionOrderTableProps {
    items: ProductionOrderListItem[];
    selectedOrderId: string;
    checkedIds: string[];
    onRowClick(item: ProductionOrderListItem): void;
    onToggleCheck(id: string): void;
}

export function ProductionOrderTable({
    items,
    selectedOrderId,
    checkedIds,
    onRowClick,
    onToggleCheck
}: ProductionOrderTableProps) {
    return (
        <div className="overflow-x-auto min-h-[280px]">
            <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900">
                    <tr>
                        <th className="w-12 px-4 py-3 first:pl-[18px]"> </th>
                        <th className="px-4 py-3">Mã LSX</th>
                        <th className="px-4 py-3">Thành phẩm</th>
                        <th className="px-4 py-3">BOM</th>
                        <th className="px-4 py-3">SL kế hoạch</th>
                        <th className="px-4 py-3">SL hoàn thành</th>
                        <th className="px-4 py-3">Ngày bắt đầu</th>
                        <th className="px-4 py-3">Ngày dự kiến</th>
                        <th className="px-4 py-3">Trạng thái</th>
                        <th className="px-4 py-3">Người phụ trách</th>
                        <th className="px-4 py-3 text-right pr-[18px]">Thao tác</th>
                    </tr>
                </thead>
                <tbody>
                    {items.map((item, idx) => {
                        const active = item.id === selectedOrderId;
                        const isEven = idx % 2 === 1;
                        const rowBgClass = active
                            ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                            : isEven
                                ? "bg-[#f2faf1]/50"
                                : "bg-white";

                        return (
                            <tr
                                key={item.id}
                                onClick={() => onRowClick(item)}
                                className={`cursor-pointer border-b border-pos-border hover:bg-neutral-50 transition-all ${rowBgClass}`}
                            >
                                <td className="border-b border-pos-border px-4 py-4 first:pl-[18px]">
                                    <input
                                        type="checkbox"
                                        checked={checkedIds.includes(item.id)}
                                        onClick={(event) => event.stopPropagation()}
                                        onChange={() => onToggleCheck(item.id)}
                                        aria-label={`Chọn ${item.code}`}
                                    />
                                </td>
                                <td className="border-b border-pos-border px-4 py-4 font-semibold text-gray-500">
                                    {item.code}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    <div className="flex items-center gap-3">
                                        <img
                                            src={item.productImageUrl}
                                            alt={item.productName}
                                            className="size-9 rounded-lg object-cover"
                                        />
                                        <span className="font-semibold text-gray-500">
                                            {item.productName}
                                        </span>
                                    </div>
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.bomCode}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.plannedQuantity} {item.unit}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.completedQuantity} {item.unit}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {formatDate(item.startDate)}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {formatDate(item.dueDate)}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    <StatusBadge status={item.status} />
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.owner}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4 text-right pr-[18px]">
                                    <button
                                        type="button"
                                        className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 ml-auto"
                                        onClick={(event) => event.stopPropagation()}
                                        aria-label={`Mở thao tác ${item.code}`}
                                    >
                                        <MoreHorizontal size={18} aria-hidden="true" />
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN").format(
        new Date(`${value}T00:00:00`)
    );
}
