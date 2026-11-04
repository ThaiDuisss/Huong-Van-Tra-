import { Clock3 } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";

import orderStyles from "../../order/pages/OrderListPage.module.css";
import { formatDateTime } from "../../order/utils/order-formatters";
import {
    disposalStatusLabels
} from "../constants/quality.constants";
import { inventoryStatusMeta } from "../constants/inventory-status-meta.constants";
import type {
    DisposalRequest,
    DisposalStatus,
    QualityBatchStatus
} from "../types/quality.types";
import styles from "../pages/CustomerReturnListPage.module.css";
import disposalStyles from "../pages/DisposalListPage.module.css";
import { isCustomerReturnDisposal } from "../utils/inventory-labels";

export function QualityStatusBadge({ status }: { status: QualityBatchStatus }) {
    const meta = inventoryStatusMeta[status];
    const Icon = meta.icon;
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${meta.tone}`} title={meta.description}><Icon size={14} />{meta.label}</span>;
}

export function DisposalStatusBadge({ status, customerReturn = false }: { status: DisposalStatus; customerReturn?: boolean }) {
    const tone = status === "APPROVED" || status === "COMPLETED" ? "border-green-200 bg-green-50 text-green-700" : status === "REJECTED" ? "border-red-200 bg-red-50 text-red-700" : "border-amber-200 bg-amber-50 text-amber-700";
    const label = customerReturn && status === "PENDING_APPROVAL" ? "Chờ xác nhận xử lý"
        : customerReturn && (status === "APPROVED" || status === "COMPLETED") ? "Đã tiêu hủy"
        : disposalStatusLabels[status];
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}><Clock3 size={14} />{label}</span>;
}

export function DisposalTable({
    canApprove,
    disposals,
    onApprove,
    openActionId,
    onOpenActionChange
}: {
    canApprove: boolean;
    disposals: DisposalRequest[];
    onApprove(request: DisposalRequest): void;
    openActionId: string | null;
    onOpenActionChange(requestId: string, open: boolean): void;
}) {
    const navigate = useNavigate();

    function getDisposalActions(request: DisposalRequest): RowAction[] {
        const actions: RowAction[] = [{
            label: "Xem chi tiết",
            onSelect: () => navigate(`/management/inventory/disposals/${request.id}`)
        }];
        if (request.status === "PENDING_APPROVAL" && !isCustomerReturnDisposal(request) && canApprove) {
            actions.push({ label: "Phê duyệt", onSelect: () => onApprove(request) });
        }
        return actions;
    }

    return (
        <table className={`${orderStyles.orderTable} ${styles.dataTable} ${disposalStyles.table}`}>
            <thead>
                <tr>
                    <th>Mã yêu cầu</th>
                    <th>Thời gian</th>
                    <th>Trạng thái</th>
                    <th>Phiếu trả</th>
                    <th>Phiếu kiểm định</th>
                    <th>Thao tác</th>
                </tr>
            </thead>
            <tbody>
                {disposals.map((request, index) => (
                    <tr
                        className={index % 2 === 0 ? orderStyles.zebraFirstRow : orderStyles.zebraSecondRow}
                        key={request.id}
                    >
                        <td>
                            <span className={styles.returnCodeText}>
                                {request.code}
                            </span>
                        </td>
                        <td className={disposalStyles.createdAt}>{formatDateTime(request.requestedAt)}</td>
                        <td><DisposalStatusBadge customerReturn={isCustomerReturnDisposal(request)} status={request.status} /></td>
                        <td className={disposalStyles.reference}>{request.returnCode ?? "–"}</td>
                        <td className={disposalStyles.reference}>{request.inspectionCode ?? "–"}</td>
                        <td>
                            <RowActionMenu
                                actions={getDisposalActions(request)}
                                isOpen={openActionId === request.id}
                                menuLabel={`Thao tác yêu cầu ${request.code}`}
                                onOpenChange={(open) => onOpenActionChange(request.id, open)}
                                triggerLabel={`Mở thao tác cho yêu cầu ${request.code}`}
                            />
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
}
