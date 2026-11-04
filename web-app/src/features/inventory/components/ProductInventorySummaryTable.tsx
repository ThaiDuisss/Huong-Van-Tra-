import { ChevronDown, ChevronUp } from "lucide-react";
import { useCallback, useState } from "react";
import type { WarehouseInventorySummary, LocationInventoryDetail } from "../services/warehouseInventory.api";
import { warehouseInventoryApi } from "../services/warehouseInventory.api";
import { getLocationTypeLabel } from "../utils/inventory-labels";

interface ProductInventorySummaryTableProps {
    products: WarehouseInventorySummary[];
    loading?: boolean;
    onLocationSelect?: (itemId: string, locationId: string, locationName: string, locationType: string) => void;
    isDetailPanelOpen?: boolean;
}

export function ProductInventorySummaryTable({
    products,
    loading,
    onLocationSelect,
    isDetailPanelOpen
}: ProductInventorySummaryTableProps) {
    const [expandedItemId, setExpandedItemId] = useState<string | null>(null);
    const [locationDetails, setLocationDetails] = useState<Record<string, LocationInventoryDetail[]>>({});
    const [loadingLocations, setLoadingLocations] = useState<Record<string, boolean>>({});

    const handleExpandItem = useCallback(
        async (itemId: string) => {
            const isExpanding = expandedItemId !== itemId;
            setExpandedItemId(isExpanding ? itemId : null);

            if (isExpanding && !locationDetails[itemId]) {
                setLoadingLocations((prev) => ({ ...prev, [itemId]: true }));
                try {
                    const details = await warehouseInventoryApi.getProductLocationInventory(itemId);
                    setLocationDetails((prev) => ({ ...prev, [itemId]: details }));
                } catch (err) {
                    console.error("Error loading locations:", err);
                } finally {
                    setLoadingLocations((prev) => ({ ...prev, [itemId]: false }));
                }
            }
        },
        [expandedItemId, locationDetails]
    );

    if (loading) {
        return <div className="p-4 text-center text-gray-600">Đang tải...</div>;
    }

    if (!products.length) {
        return <div className="p-4 text-center text-gray-500">Không có mặt hàng</div>;
    }

    return (
        <div className="overflow-x-auto min-h-[280px]">
            <table className="w-full text-left text-[13px] border-separate border-spacing-0">
                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-[#172317] border-b border-[#edf0f4]">
                    <tr>
                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] first:pl-[18px] w-8"></th>
                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">SKU</th>
                        {!isDetailPanelOpen && (
                            <>
                                <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Tên mặt hàng</th>
                                <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">Kệ POS</th>
                                <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">Kho tổng</th>
                                <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">Tổng tồn</th>
                                <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-center">Đơn vị</th>
                            </>
                        )}
                    </tr>
                </thead>
                <tbody>
                    {products.map((product, idx) => {
                        const isEven = idx % 2 === 1;
                        const rowBgClass = isEven ? "bg-[#f2faf1]/50" : "bg-white";

                        return (
                            <div key={product.inventoryItemId} className="contents">
                                {/* Level 1: Item Row */}
                                <tr
                                    className={`cursor-pointer hover:bg-[#f8faf8] transition-all border-b border-[#f2f4f2] ${rowBgClass}`}
                                    onClick={() => handleExpandItem(product.inventoryItemId)}
                                >
                                    <td className="px-4 py-3 text-center">
                                        {expandedItemId === product.inventoryItemId ? (
                                            <ChevronUp size={16} className="inline" />
                                        ) : (
                                            <ChevronDown size={16} className="inline" />
                                        )}
                                    </td>
                                    <td className="px-4 py-3 font-mono text-[#172317]">{product.sku}</td>
                                    {!isDetailPanelOpen && (
                                        <>
                                            <td className="px-4 py-3 font-semibold text-[#172317]">{product.itemName}</td>
                                            <td className="px-4 py-3 text-right text-blue-600 font-semibold">
                                                {product.shelfQuantity.toFixed(2)}
                                            </td>
                                            <td className="px-4 py-3 text-right text-purple-600 font-semibold">
                                                {product.warehouseQuantity.toFixed(2)}
                                            </td>
                                            <td className="px-4 py-3 text-right text-gray-900 font-bold">
                                                {product.totalQuantity.toFixed(2)}
                                            </td>
                                            <td className="px-4 py-3 text-center text-gray-700 font-medium">{product.unitSymbol}</td>
                                        </>
                                    )}
                                </tr>

                                {/* Level 2: Locations (Expanded) */}
                                {expandedItemId === product.inventoryItemId && (
                                    <tr className="bg-gray-50 border-b border-[#f2f4f2]">
                                        <td colSpan={isDetailPanelOpen ? 2 : 8} className="p-4">
                                            <div className="space-y-2">
                                                <div className="text-sm font-semibold text-gray-700 mb-3">Tồn kho theo vị trí:</div>

                                                {loadingLocations[product.inventoryItemId] && (
                                                    <div className="text-sm text-gray-600">Đang tải vị trí...</div>
                                                )}

                                                {!loadingLocations[product.inventoryItemId] &&
                                                    locationDetails[product.inventoryItemId] &&
                                                    locationDetails[product.inventoryItemId].length > 0 ? (
                                                    <div className="space-y-2">
                                                        {locationDetails[product.inventoryItemId].map((location) => (
                                                            <div
                                                                key={location.locationId}
                                                                className="bg-white border border-pos-border rounded-xl p-3 hover:shadow-md transition cursor-pointer hover:border-hvt-green-700"
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    onLocationSelect?.(
                                                                        product.inventoryItemId,
                                                                        location.locationId,
                                                                        location.locationName,
                                                                        location.locationType
                                                                    );
                                                                }}
                                                            >
                                                                <div className="flex items-center justify-between">
                                                                    <div>
                                                                        <div className="font-semibold text-gray-900">
                                                                            📍 {getLocationTypeLabel(location.locationType)} - {location.locationName}
                                                                        </div>
                                                                        {location.batchCount > 0 && (
                                                                            <div className="text-xs text-gray-600 mt-1">
                                                                                {location.batchCount} lô hàng
                                                                            </div>
                                                                        )}
                                                                        {location.batchCount === 0 && (
                                                                            <div className="text-xs text-gray-500 italic mt-1">
                                                                                Không có lô hàng
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                    <div className="text-right">
                                                                        <div className="text-sm font-semibold text-gray-900">
                                                                            {location.quantityOnHand.toFixed(2)}
                                                                        </div>
                                                                        <div className="text-xs text-gray-500">
                                                                            Tồn: {location.availableQuantity.toFixed(2)} khả dụng
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        ))}
                                                    </div>
                                                ) : (
                                                    <div className="text-sm text-gray-500 italic">Không có thông tin vị trí tồn kho</div>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </div>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
