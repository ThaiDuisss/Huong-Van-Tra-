import { formatVietnamDateTime } from "../utils/inventory-format";
import type { WarehouseBatchResponse } from "../services/warehouseBatch.api";

interface WarehouseBatchTableProps {
    batches: WarehouseBatchResponse[];
    loading?: boolean;
    onBatchSelect?: (batch: WarehouseBatchResponse) => void;
}

export function WarehouseBatchTable({ batches, loading, onBatchSelect }: WarehouseBatchTableProps) {
    if (loading) {
        return <div className="p-4">Đang tải...</div>;
    }

    if (!batches.length) {
        return <div className="p-4 text-center text-gray-500">Không có lô hàng</div>;
    }

    const getStatusBadge = (status: string) => {
        switch (status) {
            case "AWAITING_QC":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-yellow-50 text-yellow-700 rounded">Chờ kiểm chất lượng</span>;
            case "AVAILABLE":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-green-50 text-green-700 rounded">Sẵn sàng</span>;
            case "DEPLETED":
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-gray-50 text-gray-700 rounded">Hết hàng</span>;
            default:
                return <span className="inline-flex px-2 py-1 text-xs font-bold bg-gray-50 text-gray-700 rounded">{status}</span>;
        }
    };

    const getExpiryBadge = (daysUntilExpiry?: number) => {
        if (daysUntilExpiry === undefined || daysUntilExpiry === null) {
            return <span className="text-gray-500">-</span>;
        }
        if (daysUntilExpiry < 0) {
            return <span className="text-red-600 font-bold">Hết hạn ({Math.abs(daysUntilExpiry)} ngày)</span>;
        }
        if (daysUntilExpiry <= 30) {
            return <span className="text-orange-600 font-bold">Sắp hết ({daysUntilExpiry} ngày)</span>;
        }
        return <span className="text-green-600">{daysUntilExpiry} ngày</span>;
    };

    return (
        <div className="overflow-x-auto">
            <table className="w-full border-collapse">
                <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700">Mã lô</th>
                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700">Mã lô NCC</th>
                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700">Ngày SX</th>
                        <th className="px-4 py-2 text-left text-xs font-bold text-gray-700">Hạn sử dụng</th>
                        <th className="px-4 py-2 text-right text-xs font-bold text-gray-700">SL ban đầu</th>
                        <th className="px-4 py-2 text-right text-xs font-bold text-gray-700">SL còn lại</th>
                        <th className="px-4 py-2 text-right text-xs font-bold text-gray-700">Tồn khả dụng</th>
                        <th className="px-4 py-2 text-center text-xs font-bold text-gray-700">Trạng thái</th>
                        <th className="px-4 py-2 text-center text-xs font-bold text-gray-700">Còn lại</th>
                    </tr>
                </thead>
                <tbody>
                    {batches.map((batch) => (
                        <tr
                            key={batch.batchId}
                            className="border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
                            onClick={() => onBatchSelect?.(batch)}
                        >
                            <td className="px-4 py-2 text-sm font-bold text-gray-900">{batch.batchCode}</td>
                            <td className="px-4 py-2 text-sm text-gray-600">{batch.supplierBatchCode || "-"}</td>
                            <td className="px-4 py-2 text-sm text-gray-600">
                                {batch.manufactureDate ? formatVietnamDateTime(batch.manufactureDate) : "-"}
                            </td>
                            <td className="px-4 py-2 text-sm text-gray-600">
                                {batch.expiryDate ? formatVietnamDateTime(batch.expiryDate) : "-"}
                            </td>
                            <td className="px-4 py-2 text-sm text-right text-gray-600">
                                {(batch.quantityOnHand + batch.quantityReserved + batch.quantityBlocked).toFixed(2)}
                            </td>
                            <td className="px-4 py-2 text-sm text-right font-bold text-gray-900">
                                {batch.quantityOnHand.toFixed(2)}
                            </td>
                            <td className="px-4 py-2 text-sm text-right font-bold text-green-600">
                                {batch.availableQuantity.toFixed(2)}
                            </td>
                            <td className="px-4 py-2 text-center">{getStatusBadge(batch.status)}</td>
                            <td className="px-4 py-2 text-center">{getExpiryBadge(batch.daysUntilExpiry)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
