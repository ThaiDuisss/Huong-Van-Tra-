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

const inspectionStatusTabs = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Chờ xử lý" },
    { key: "PROCESSING", label: "Chờ duyệt tiêu hủy" },
    { key: "POSTED", label: "Đã xử lý" },
    { key: "CANCELLED", label: "Đã hủy" }
];

export function InspectionStatusBadge({ status }: { status: CustomerReturnInspectionStatus }) {
    return <OrderStatusBadge
        label={inspectionStatusLabels[status]}
        status={status === "CREATED" ? "PENDING" : status === "POSTED" ? "COMPLETED" : status}
    />;
}

export function InspectionFilterBar({ onChange, onReset, query }: { onChange: (patch: Partial<CustomerReturnInspectionListQuery>) => void; onReset: () => void; query: CustomerReturnInspectionListQuery }) {
    return <ListFilterBar
        activeStatus={query.status ?? "ALL"}
        onReset={onReset}
        onSearchChange={(keyword) => onChange({ returnCode: keyword, page: 0 })}
        onStatusChange={(status) => onChange({ page: 0, status: status === "ALL" ? undefined : (status as CustomerReturnInspectionListQuery["status"]) })}
        searchTerm={query.returnCode ?? ""}
        statusTabs={inspectionStatusTabs}
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
