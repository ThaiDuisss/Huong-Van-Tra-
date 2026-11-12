import { ChevronDown, ChevronUp } from "lucide-react";
import { Fragment, useCallback, useState } from "react";

import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";

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
            <Table>
                <TableHead>
                    <TableRow>
                        <TableHeaderCell className="w-8" />
                        <TableHeaderCell>SKU</TableHeaderCell>
                        {!isDetailPanelOpen && (
                            <>
                                <TableHeaderCell>Tên mặt hàng</TableHeaderCell>
                                <TableHeaderCell className="text-right">Kệ POS</TableHeaderCell>
                                <TableHeaderCell className="text-right">Kho tổng</TableHeaderCell>
                                <TableHeaderCell className="text-right">Tổng tồn</TableHeaderCell>
                                <TableHeaderCell className="text-center">Đơn vị</TableHeaderCell>
                            </>
                        )}
                    </TableRow>
                </TableHead>
                <tbody>
                    {products.map((product, idx) => {
                        const isEven = idx % 2 === 1;
                        const rowBgClass = isEven ? "bg-[#f2faf1]/50" : "bg-white";

                        return (
                            <Fragment key={product.inventoryItemId}>
                                {/* Level 1: Item Row */}
                                <TableRow
                                    className={`cursor-pointer transition-all hover:bg-[#f8faf8] ${rowBgClass}`}
                                    onClick={() => handleExpandItem(product.inventoryItemId)}
                                >
                                    <TableCell className="text-center">
                                        {expandedItemId === product.inventoryItemId ? (
                                            <ChevronUp size={16} className="inline" />
                                        ) : (
                                            <ChevronDown size={16} className="inline" />
                                        )}
                                    </TableCell>
                                    <TableCell className="font-mono text-[#172317]">{product.sku}</TableCell>
                                    {!isDetailPanelOpen && (
                                        <>
                                            <TableCell className="font-semibold text-[#172317]">{product.itemName}</TableCell>
                                            <TableCell className="text-right text-blue-600 font-semibold">
                                                {product.shelfQuantity.toFixed(2)}
                                            </TableCell>
                                            <TableCell className="text-right text-purple-600 font-semibold">
                                                {product.warehouseQuantity.toFixed(2)}
                                            </TableCell>
                                            <TableCell className="text-right text-gray-900 font-bold">
                                                {product.totalQuantity.toFixed(2)}
                                            </TableCell>
                                            <TableCell className="text-center text-gray-700 font-medium">{product.unitSymbol}</TableCell>
                                        </>
                                    )}
                                </TableRow>

                                {/* Level 2: Locations (Expanded) */}
                                {expandedItemId === product.inventoryItemId && (
                                    <TableRow className="bg-gray-50">
                                        <TableCell className="p-4" colSpan={isDetailPanelOpen ? 2 : 8}>
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
                                        </TableCell>
                                    </TableRow>
                                )}
                            </Fragment>
                        );
                    })}
                </tbody>
            </Table>
        </div>
    );
}
