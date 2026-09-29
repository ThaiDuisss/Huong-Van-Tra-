import { X } from "lucide-react";
import { useEffect, useState } from "react";
import { warehouseBatchApi, type WarehouseBatchResponse } from "../services/warehouseBatch.api";
import { WarehouseBatchTable } from "./WarehouseBatchTable";

interface BatchDetailDrawerProps {
    open: boolean;
    onClose: () => void;
    itemId: string;
    locationId: string;
    locationName: string;
    productName?: string;
}

export function BatchDetailDrawer({
    open,
    onClose,
    itemId,
    locationId,
    locationName,
    productName
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

    if (!open) return null;

    return (
        <>
            {/* Overlay */}
            <div className="fixed inset-0 bg-black bg-opacity-50 z-40" onClick={onClose} />

            {/* Drawer */}
            <div className="fixed right-0 top-0 bottom-0 w-full max-w-2xl bg-white shadow-lg z-50 flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between p-4 border-b border-gray-200">
                    <div>
                        <h2 className="text-lg font-bold text-gray-900">Chi tiết lô hàng</h2>
                        <p className="text-sm text-gray-600">
                            {productName} • {locationName}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-1 hover:bg-gray-100 rounded transition"
                    >
                        <X size={20} />
                    </button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto p-4">
                    {error && (
                        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                            {error}
                        </div>
                    )}
                    <WarehouseBatchTable batches={batches} loading={loading} />
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
        </>
    );
}
