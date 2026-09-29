import { Link } from "react-router-dom";

import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
import orderStyles from "../../order/pages/OrderListPage.module.css";
import styles from "../pages/CustomerReturnListPage.module.css";
import type {
    CustomerReturnInspection,
    CustomerReturnInspectionListQuery,
    CustomerReturnInspectionStatus
} from "../types/customer-return-inspection.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { ListFilterBar } from "./CustomerReturnListUi";

const inspectionStatusLabels: Record<CustomerReturnInspectionStatus, string> = {
    CANCELLED: "Đã hủy",
    CREATED: "Chờ xử lý",
    PROCESSING: "Chờ duyệt tiêu hủy",
    POSTED: "Đã xử lý"
};

const statusOptions = [
    { label: "Tất cả", value: "ALL" },
    { label: "Chờ xử lý", value: "CREATED" },
    { label: "Chờ duyệt tiêu hủy", value: "PROCESSING" },
    { label: "Đã xử lý", value: "POSTED" },
    { label: "Đã hủy", value: "CANCELLED" }
];

export function InspectionStatusBadge({ status }: { status: CustomerReturnInspectionStatus }) {
    return <OrderStatusBadge
        label={inspectionStatusLabels[status]}
        status={status === "CREATED" ? "PENDING" : status === "POSTED" ? "COMPLETED" : status}
    />;
}

export function InspectionFilterBar({ onChange, onReset, query }: { onChange: (patch: Partial<CustomerReturnInspectionListQuery>) => void; onReset: () => void; query: CustomerReturnInspectionListQuery }) {
    return <ListFilterBar
        onOrderCodeChange={(orderCode) => onChange({ orderCode, page: 0 })}
        onReset={onReset}
        onReturnCodeChange={(returnCode) => onChange({ returnCode, page: 0 })}
        onStatusChange={(status) => onChange({ page: 0, status: status as CustomerReturnInspectionListQuery["status"] })}
        orderCode={query.orderCode}
        returnCode={query.returnCode}
        status={query.status}
        statusOptions={statusOptions}
    />;
}

export function InspectionTable({ inspections }: { inspections: CustomerReturnInspection[] }) {
    return <div className={orderStyles.tableWrap}>
        <table className={`${orderStyles.orderTable} ${styles.dataTable} ${styles.inspectionTable}`}>
            <thead><tr><th>Phiếu kiểm định</th><th>Mã trả hàng</th><th>Đơn hàng</th><th>Số dòng</th><th>Tổng số lượng</th><th>Trạng thái</th><th>Ngày tạo</th></tr></thead>
            <tbody>{inspections.map((inspection, index) => <tr className={index % 2 === 0 ? orderStyles.zebraFirstRow : orderStyles.zebraSecondRow} key={inspection.id}>
                <td><Link className={styles.codeLink} to={`/management/inventory/customer-returns/${inspection.id}`}>{inspection.inspectionCode}</Link></td>
                <td>{inspection.returnCode}</td>
                <td>{inspection.orderCode ?? "–"}</td>
                <td>{formatVietnamNumber(inspection.lines.length)}</td>
                <td>{formatVietnamNumber(inspection.lines.reduce((sum, line) => sum + Number(line.quantity), 0))}</td>
                <td><InspectionStatusBadge status={inspection.status} /></td>
                <td>{formatVietnamDateTime(inspection.createdAt)}</td>
            </tr>)}</tbody>
        </table>
    </div>;
}
