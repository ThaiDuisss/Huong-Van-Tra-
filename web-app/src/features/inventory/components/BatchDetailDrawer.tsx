import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { IconButton } from "@/components/ui/Button";
import { Drawer } from "@/components/ui/Overlay";
import { warehouseBatchApi, type WarehouseBatchResponse } from "../services/warehouseBatch.api";
import { WarehouseBatchTable } from "./WarehouseBatchTable";

interface BatchDetailDrawerProps {
    open: boolean;
    onClose: () => void;
    itemId: string;
    locationId: string;
    locationName: string;
    itemName?: string;
}

export function BatchDetailDrawer({
    open,
    onClose,
    itemId,
    locationId,
    locationName,
    itemName
}: BatchDetailDrawerProps) {
    const [batches, setBatches] = useState<WarehouseBatchResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) return;

        const loadBatches = async () => {
            setLoading(true);
            setError(null);
            try {
                const data = await warehouseBatchApi.getBatchesByItemAndLocation(itemId, locationId);
                setBatches(data);
            } catch (err) {
                setError("Lỗi tải danh sách lô hàng");
                console.error(err);
            } finally {
                setLoading(false);
            }
        };

        loadBatches();
    }, [open, itemId, locationId]);

    return (
        <Drawer className="max-w-[65vw]" labelledBy="batch-detail-drawer-title" onClose={onClose} open={open}>
            <div className="flex min-h-0 flex-1 flex-col overflow-y-auto border-l border-gray-200 bg-white">
                {/* Header */}
                <div className="flex items-center justify-between p-4 border-b border-gray-200">
                    <div>
                        <h2 className="text-xl font-bold text-gray-900" id="batch-detail-drawer-title">
                            Chi tiết lô hàng
                        </h2>

                        <p className="text-base font-semibold text-gray-700 mt-1">
                            {locationName}
                        </p>

                        <p className="text-sm text-gray-500 mt-0.5">
                            {itemName}
                        </p>
                    </div>
                    <IconButton aria-label="Đóng chi tiết lô hàng" onClick={onClose}>
                        <X size={20} />
                    </IconButton>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-4">
                    {error && (
                        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                            {error}
                        </div>
                    )}
                    <WarehouseBatchTable
                        batches={batches}
                        loading={loading}
                        showExpiry={batches[0]?.itemType !== "INGREDIENT"}
                    />
                </div>

                {/* Summary Footer */}
                {batches.length > 0 && (
                    <div className="border-t border-gray-200 bg-gray-50 p-4">
                        <div className="grid grid-cols-4 gap-4 text-sm">
                            <div>
                                <div className="text-xs text-gray-600">Tổng lô</div>
                                <div className="font-bold text-gray-900">{batches.length}</div>
                            </div>
                            <div>
                                <div className="text-xs text-gray-600">Tổng tồn</div>
                                <div className="font-bold text-gray-900">
                                    {batches.reduce((sum, b) => sum + b.quantityOnHand, 0).toFixed(2)}
                                </div>
                            </div>
                            <div>
                                <div className="text-xs text-gray-600">Tồn khả dụng</div>
                                <div className="font-bold text-green-600">
                                    {batches.reduce((sum, b) => sum + b.availableQuantity, 0).toFixed(2)}
                                </div>
                            </div>
                            <div>
                                <div className="text-xs text-gray-600">Đã đặt</div>
                                <div className="font-bold text-orange-600">
                                    {batches.reduce((sum, b) => sum + b.quantityReserved, 0).toFixed(2)}
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Drawer>
    );
}
