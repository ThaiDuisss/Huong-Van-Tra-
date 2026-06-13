import { MoreHorizontal } from "lucide-react";

import { StatusBadge } from "../shared/StatusBadge";
import type { BomListItem } from "../../types/bom.types";

interface BomTableProps {
    items: BomListItem[];
    selectedBomId: string;
    checkedIds: string[];
    onRowClick(item: BomListItem): void;
    onToggleCheck(id: string): void;
}

export function BomTable({
    items,
    selectedBomId,
    checkedIds,
    onRowClick,
    onToggleCheck
}: BomTableProps) {
    return (
        <div className="overflow-x-auto">
            <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-neutral-50 text-xs font-bold text-pos-muted">
                    <tr>
                        <th className="w-12 px-4 py-3"> </th>
                        <th className="px-4 py-3">Mã BOM</th>
                        <th className="px-4 py-3">Tên BOM</th>
                        <th className="px-4 py-3">Thành phẩm</th>
                        <th className="px-4 py-3">Phiên bản</th>
                        <th className="px-4 py-3">Tổng thành phần</th>
                        <th className="px-4 py-3">Chi phí BOM</th>
                        <th className="px-4 py-3">Ngày cập nhật</th>
                        <th className="px-4 py-3">Trạng thái</th>
                        <th className="px-4 py-3 text-right">
                            Thao tác
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {items.map((item) => {
                        const active = item.id === selectedBomId;

                        return (
                            <tr
                                key={item.id}
                                onClick={() => onRowClick(item)}
                                className={`cursor-pointer border-b border-pos-border ${
                                    active
                                        ? "bg-hvt-green-50/70"
                                        : "hover:bg-neutral-50"
                                }`}
                            >
                                <td className="border-b border-pos-border px-4 py-4">
                                    <input
                                        type="checkbox"
                                        checked={checkedIds.includes(
                                            item.id
                                        )}
                                        onClick={(event) =>
                                            event.stopPropagation()
                                        }
                                        onChange={() =>
                                            onToggleCheck(item.id)
                                        }
                                        aria-label={`Chọn ${item.name}`}
                                    />
                                </td>
                                <td className="border-b border-pos-border px-4 py-4 font-semibold text-pos-ink">
                                    {item.code}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.name}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    <div className="flex items-center gap-3">
                                        <img
                                            src={item.productImageUrl}
                                            alt={item.productName}
                                            className="size-9 rounded-lg object-cover"
                                        />
                                        <div>
                                            <p className="font-semibold text-pos-ink">
                                                {item.productName}
                                            </p>
                                            <p className="text-xs text-pos-muted">
                                                SKU:{" "}
                                                {item.productSku}
                                            </p>
                                        </div>
                                    </div>
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    <span className="rounded-md border border-pos-border px-2 py-1 text-xs font-bold">
                                        {item.version}
                                    </span>
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {item.componentCount} thành phần
                                </td>
                                <td className="border-b border-pos-border px-4 py-4 font-semibold">
                                    {formatVnd(item.totalCost)}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    {formatDate(item.updatedAt)}
                                </td>
                                <td className="border-b border-pos-border px-4 py-4">
                                    <StatusBadge
                                        status={item.status}
                                    />
                                </td>
                                <td className="border-b border-pos-border px-4 py-4 text-right">
                                    <button
                                        type="button"
                                        className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"
                                        aria-label={`Mở thao tác ${item.name}`}
                                        onClick={(event) =>
                                            event.stopPropagation()
                                        }
                                    >
                                        <MoreHorizontal
                                            size={18}
                                            aria-hidden="true"
                                        />
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

function formatVnd(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 0
    }).format(value);
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN").format(
        new Date(`${value}T00:00:00`)
    );
}
