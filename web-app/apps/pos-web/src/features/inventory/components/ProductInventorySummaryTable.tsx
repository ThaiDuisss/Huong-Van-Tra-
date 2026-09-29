import { ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";
import type { WarehouseInventorySummary, LocationInventoryDetail } from "../services/warehouseInventory.api";

interface ProductInventorySummaryTableProps {
    products: WarehouseInventorySummary[];
    loading?: boolean;
    onLocationSelect?: (itemId: string, locationId: string, locationName: string) => void;
    locationDetails?: Record<string, LocationInventoryDetail[]>;
    batchesLoading?: Record<string, boolean>;
}

export function ProductInventorySummaryTable({
    products,
    loading,
    onLocationSelect,
    locationDetails = {},
    batchesLoading = {}
}: ProductInventorySummaryTableProps) {
    const [expandedItemId, setExpandedItemId] = useState<string | null>(null);

    if (loading) {
        return <div className="p-4">Đang tải...</div>;
    }

    if (!products.length) {
        return <div className="p-4 text-center text-gray-500">Không có sản phẩm</div>;
    }

    const getStockStatusBadge = (status: string) => {
        switch (status) {
            case "OUT_OF_STOCK":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-red-50 text-red-700 rounded">Hết hàng</span>;
            case "LOW_STOCK":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-orange-50 text-orange-700 rounded">Sắp hết</span>;
            case "SUFFICIENT":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-green-50 text-green-700 rounded">Đủ hàng</span>;
            default:
                return null;
        }
    };

    const getLocationTypeLabel = (type: string) => {
        switch (type) {
            case "SHELF":
                return "🛍️ Kệ POS";
            case "WAREHOUSE":
                return "📦 Kho tổng";
            default:
                return type;
        }
    };

    return (
        <div className="space-y-4">
            {products.map((product) => (
                <div key={product.inventoryItemId} className="border border-gray-200 rounded-lg overflow-hidden">
                    {/* Product Header */}
                    <div
                        className="flex items-center justify-between gap-4 p-4 hover:bg-gray-50 cursor-pointer bg-white"
                        onClick={() => setExpandedItemId(expandedItemId === product.inventoryItemId ? null : product.inventoryItemId)}
                    >
                        <div className="flex items-center gap-3 flex-1 min-w-0">
                            {expandedItemId === product.inventoryItemId ? (
                                <ChevronUp size={16} className="shrink-0" />
                            ) : (
                                <ChevronDown size={16} className="shrink-0" />
                            )}
                            <div className="min-w-0">
                                <div className="text-sm font-bold text-gray-900">{product.productCode}</div>
                                <div className="text-xs text-gray-600 truncate">{product.productName}</div>
                            </div>
                        </div>

                        <div className="flex items-center gap-4 shrink-0">
                            <div className="text-right">
                                <div className="text-xs text-gray-500">Danh mục</div>
                                <div className="text-sm font-bold text-gray-900">{product.categoryName}</div>
                            </div>
                            <div className="text-right">
                                <div className="text-xs text-gray-500">Kệ POS</div>
                                <div className="text-sm font-bold text-blue-600">{product.shelfQuantity.toFixed(2)}</div>
                            </div>
                            <div className="text-right">
                                <div className="text-xs text-gray-500">Kho tổng</div>
                                <div className="text-sm font-bold text-purple-600">{product.warehouseQuantity.toFixed(2)}</div>
                            </div>
                            <div className="text-right">
                                <div className="text-xs text-gray-500">Tổng tồn</div>
                                <div className="text-sm font-bold text-gray-900">{product.totalQuantity.toFixed(2)}</div>
                            </div>
                            {getStockStatusBadge(product.stockStatus)}
                        </div>
                    </div>

                    {/* Expanded Location Details */}
                    {expandedItemId === product.inventoryItemId && (
                        <div className="border-t border-gray-200 bg-gray-50 p-4 space-y-4">
                            <div className="text-sm font-bold text-gray-700">Chi tiết tồn kho theo vị trí:</div>

                            <div className="space-y-3">
                                {locationDetails[product.inventoryItemId]?.map((location) => (
                                    <div
                                        key={location.locationId}
                                        className="bg-white border border-gray-200 rounded-lg p-3 hover:shadow-md transition cursor-pointer"
                                        onClick={() =>
                                            onLocationSelect?.(product.inventoryItemId, location.locationId, location.locationName)
                                        }
                                    >
                                        <div className="flex items-center justify-between mb-2">
                                            <div className="font-bold text-gray-900">
                                                {getLocationTypeLabel(location.locationType)} - {location.locationName}
                                            </div>
                                            <div className="text-sm font-bold text-gray-700">
                                                Còn: {location.availableQuantity.toFixed(2)} / {location.quantityOnHand.toFixed(2)}
                                            </div>
                                        </div>

                                        {location.batchCount > 0 && (
                                            <div className="text-xs text-gray-600">
                                                {location.batchCount} lô hàng {batchesLoading[location.locationId] && "đang tải..."}
                                            </div>
                                        )}
                                        {location.batchCount === 0 && (
                                            <div className="text-xs text-gray-500 italic">Không có lô hàng</div>
                                        )}

                                        <div className="text-xs text-gray-500 mt-1">
                                            Đã đặt: {location.quantityReserved.toFixed(2)}, Khoá: {location.quantityBlocked.toFixed(2)}
                                        </div>
                                    </div>
                                ))}
                            </div>

                            {!locationDetails[product.inventoryItemId]?.length && (
                                <div className="text-sm text-gray-500 italic">Không có thông tin vị trí tồn kho</div>
                            )}
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}
