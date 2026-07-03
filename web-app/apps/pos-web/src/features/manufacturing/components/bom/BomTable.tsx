import { MoreHorizontal, Eye, Pencil, ToggleLeft } from "lucide-react";
import { useState, useEffect, useRef } from "react";

import { StatusBadge } from "../shared/StatusBadge";
import type { BomResponse } from "../../types/bom.types";

interface BomTableProps {
    items: BomResponse[];
    selectedBomId: string;
    onRowClick(item: BomResponse): void;
    onEdit(item: BomResponse): void;
    onToggleStatus(item: BomResponse): void;
}

export function BomTable({
    items,
    selectedBomId,
    onRowClick,
    onEdit,
    onToggleStatus
}: BomTableProps) {
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);
    const menuRef = useRef<HTMLDivElement | null>(null);

    // Close menu when clicking outside
    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                setActiveActionMenuId(null);
            }
        }
        document.addEventListener("mousedown", handleClickOutside);
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    return (
        <div className="overflow-x-auto min-h-[350px]">
            <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900">
                    <tr>
                        <th className="px-4 py-3">Tên BOM</th>
                        <th className="px-4 py-3">Mặt Hàng</th>
                        <th className="px-4 py-3">Mặc định</th>
                        <th className="px-4 py-3">Trạng thái</th>
                        <th className="px-4 py-3 text-right pr-[18px]">
                            Thao tác
                        </th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-pos-border">
                    {items.length > 0 ? (
                        items.map((item, idx) => {
                            const active = item.id === selectedBomId;
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
                                    className={`cursor-pointer hover:bg-neutral-50 transition-all ${rowBgClass}`}
                                >
                    
                                    <td className="px-4 py-4 text-gray-900 font-medium">
                                        {item.name || "-"}
                                    </td>
                                    <td className="px-4 py-4">
                                        <div>
                                            <p className="font-semibold text-gray-800">
                                                {item.variantName}
                                            </p>
                                            <p className="text-xs text-gray-400">
                                                {item.productName}
                                            </p>
                                        </div>
                                    </td>
                            
                                    <td className="px-4 py-4">
                                        {item.isDefault ? (
                                            <span className="inline-block rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-bold text-green-800">
                                                ✓ Mặc định
                                            </span>
                                        ) : (
                                            <span className="inline-block rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-400">
                                                Không
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-4 py-4">
                                        <StatusBadge status={item.status} />
                                    </td>
                                    <td className="px-4 py-4 text-right pr-[18px] relative">
                                        <button
                                            type="button"
                                            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 ml-auto focus:outline-none"
                                            aria-label={`Mở thao tác ${item.name}`}
                                            onClick={(event) => {
                                                event.stopPropagation();
                                                setActiveActionMenuId(
                                                    activeActionMenuId === item.id ? null : item.id
                                                );
                                            }}
                                        >
                                            <MoreHorizontal size={18} aria-hidden="true" />
                                        </button>

                                        {activeActionMenuId === item.id && (
                                            <div
                                                ref={menuRef}
                                                className="absolute right-4 top-12 z-20 w-44 rounded-xl border border-pos-border bg-white py-1.5 shadow-pos-dropdown text-left"
                                                onClick={(e) => e.stopPropagation()}
                                            >
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        setActiveActionMenuId(null);
                                                        onRowClick(item);
                                                    }}
                                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs font-semibold text-gray-700 hover:bg-neutral-50"
                                                >
                                                    <Eye size={14} className="text-gray-400" />
                                                    Xem chi tiết
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        setActiveActionMenuId(null);
                                                        onEdit(item);
                                                    }}
                                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs font-semibold text-gray-700 hover:bg-neutral-50"
                                                >
                                                    <Pencil size={14} className="text-gray-400" />
                                                    Chỉnh sửa BOM
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        setActiveActionMenuId(null);
                                                        onToggleStatus(item);
                                                    }}
                                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs font-semibold text-gray-700 hover:bg-neutral-50 border-t border-pos-border mt-1 pt-2"
                                                >
                                                    <ToggleLeft size={14} className="text-amber-500" />
                                                    {item.status === "ACTIVE"
                                                        ? "Vô hiệu hóa"
                                                        : "Kích hoạt"}
                                                </button>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            );
                        })
                    ) : (
                        <tr>
                            <td colSpan={8} className="px-4 py-8 text-center text-gray-400 italic">
                                Không tìm thấy dữ liệu BOM nào.
                            </td>
                        </tr>
                    )}
                </tbody>
            </table>
        </div>
    );
}
