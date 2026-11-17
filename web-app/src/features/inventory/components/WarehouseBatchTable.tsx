import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";

import { formatVietnamDateTime } from "../utils/inventory-format";
import type { WarehouseBatchResponse } from "../services/warehouseBatch.api";

interface WarehouseBatchTableProps {
    batches: WarehouseBatchResponse[];
    loading?: boolean;
    onBatchSelect?: (batch: WarehouseBatchResponse) => void;
    showExpiry?: boolean;
}

export function WarehouseBatchTable({ batches, loading, onBatchSelect, showExpiry = true }: WarehouseBatchTableProps) {
    if (loading) {
        return <div className="p-4">Đang tải...</div>;
    }

    if (!batches.length) {
        return <div className="p-4 text-center text-gray-500">Không có lô hàng</div>;
    }

    const getStatusBadge = (status: string) => {
        switch (status) {
            case "AWAITING_QC":
                return <StatusBadge shape="tag" tone="warning">Chờ kiểm chất lượng</StatusBadge>;
            case "AVAILABLE":
                return <StatusBadge shape="tag" tone="success">Sẵn sàng</StatusBadge>;
            case "DEPLETED":
                return <StatusBadge shape="tag">Hết hàng</StatusBadge>;
            default:
                return <StatusBadge shape="tag">{status}</StatusBadge>;
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
            <Table>
                <TableHead>
                    <TableRow>
                        <TableHeaderCell>Mã lô</TableHeaderCell>
                        <TableHeaderCell>Mã lô NCC</TableHeaderCell>
                        <TableHeaderCell>Ngày SX</TableHeaderCell>
                        {showExpiry && <TableHeaderCell>Hạn sử dụng</TableHeaderCell>}
                        <TableHeaderCell className="text-right">SL ban đầu</TableHeaderCell>
                        <TableHeaderCell className="text-right">SL còn lại</TableHeaderCell>
                        <TableHeaderCell className="text-right">Tồn khả dụng</TableHeaderCell>
                        <TableHeaderCell className="text-center">Đơn vị</TableHeaderCell>
                        <TableHeaderCell className="text-center">Trạng thái</TableHeaderCell>
                        {showExpiry && <TableHeaderCell className="text-center">Còn lại</TableHeaderCell>}
                    </TableRow>
                </TableHead>
                <tbody>
                    {batches.map((batch) => (
                        <TableRow
                            key={batch.batchId}
                            className="cursor-pointer hover:bg-gray-50"
                            onClick={() => onBatchSelect?.(batch)}
                        >
                            <TableCell className="text-sm font-bold text-gray-900">{batch.batchCode}</TableCell>
                            <TableCell className="text-sm text-gray-600">{batch.supplierBatchCode || "-"}</TableCell>
                            <TableCell className="text-sm text-gray-600">
                                {batch.manufactureDate ? formatVietnamDateTime(batch.manufactureDate) : "-"}
                            </TableCell>
                            {showExpiry && (
                                <TableCell className="text-sm text-gray-600">
                                    {batch.expiryDate ? formatVietnamDateTime(batch.expiryDate) : "-"}
                                </TableCell>
                            )}
                            <TableCell className="text-sm text-right text-gray-600">
                                {(batch.quantityOnHand + batch.quantityReserved + batch.quantityBlocked).toFixed(2)}
                            </TableCell>
                            <TableCell className="text-sm text-right font-bold text-gray-900">
                                {batch.quantityOnHand.toFixed(2)}
                            </TableCell>
                            <TableCell className="text-sm text-right font-bold text-green-600">
                                {batch.availableQuantity.toFixed(2)}
                            </TableCell>
                              <TableCell className="text-sm text-center text-gray-700 font-medium">
                                {batch.unitSymbol}
                            </TableCell>
                            <TableCell className="text-center">{getStatusBadge(batch.status)}</TableCell>
                            {showExpiry && <TableCell className="text-center">{getExpiryBadge(batch.daysUntilExpiry)}</TableCell>}
                        </TableRow>
                    ))}
                </tbody>
            </Table>
        </div>
    );
}
