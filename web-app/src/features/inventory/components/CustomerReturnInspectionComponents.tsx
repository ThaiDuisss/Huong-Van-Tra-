import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import type { CustomerReturnInspectionStatus } from "../types/customer-return-inspection.types";

const inspectionStatusLabels: Record<CustomerReturnInspectionStatus, string> = {
    CANCELLED: "Đã hủy",
    CREATED: "Chờ xử lý",
    PROCESSING: "Chưa hoàn tất xử lý",
    POSTED: "Đã xử lý"
};

export function InspectionStatusBadge({ status }: { status: CustomerReturnInspectionStatus }) {
    return <OrderStatusBadge
        label={inspectionStatusLabels[status]}
        status={status === "CREATED" ? "PENDING" : status === "POSTED" ? "COMPLETED" : status}
    />;
}
