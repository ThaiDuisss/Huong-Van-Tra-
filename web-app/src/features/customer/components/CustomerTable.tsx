import {
    Eye,
    Pencil,
    ShieldCheck,
    ShieldOff,
    Trash2
} from "lucide-react";
import { useState } from "react";

import {
    DataTable,
    type DataTableColumn
} from "../../../components/shared/list/DataTable";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { type RowAction, RowActionMenu } from "../../../components/shared/RowActionMenu";
import { StatusBadge } from "../../../components/ui/StatusBadge";

import type {
    CustomerResponse,
    PageResponse
} from "../types/customer.types";
import {
    CUSTOMER_STATUS_LABELS,
    CUSTOMER_STATUS_TONES
} from "../constants/customer-list.constants";
import {
    customerStatusActionLabel,
    customerTierBadgeStyle,
    formatCustomerRelativeDate
} from "../utils/customer-list.utils";

interface Props {
    customers: CustomerResponse[];
    result: PageResponse<CustomerResponse> | null;
    loading: boolean;
    onPageChange: (page: number) => void;
    onCustomerClick: (customer: CustomerResponse) => void;
    onCustomerEdit: (customer: CustomerResponse) => void;
    /** Bỏ trống để ẩn thao tác (vd. màn POS không cho xoá / khoá khách hàng). */
    onCustomerDelete?: (customer: CustomerResponse) => void;
    onCustomerStatusChange?: (customer: CustomerResponse) => void;
}

export function CustomerTable({
    customers,
    result,
    loading,
    onPageChange,
    onCustomerClick,
    onCustomerEdit,
    onCustomerDelete,
    onCustomerStatusChange
}: Props) {
    const totalCustomers = result?.totalElements ?? customers.length;
    const columns: DataTableColumn<CustomerResponse>[] = [
        {
            key: "customer",
            header: "Khách hàng",
            render: (customer) => (
                <div className="min-w-[180px] truncate font-black text-[#172317]">
                    {customer.fullName}
                </div>
            )
        },
        {
            key: "phone",
            header: "Liên hệ",
            className: "font-black text-[#172317]",
            render: (customer) => customer.phone
        },
        {
            key: "tier",
            header: "Hạng khách hàng",
            render: (customer) => <TierBadge customer={customer} />
        },
        {
            key: "lastPurchase",
            header: "Lần mua gần nhất",
            render: (customer) => lastPurchaseLabel(customer)
        },
        {
            key: "status",
            header: "Trạng thái",
            render: (customer) => (
                <StatusBadge tone={CUSTOMER_STATUS_TONES[customer.status]}>
                    {CUSTOMER_STATUS_LABELS[customer.status]}
                </StatusBadge>
            )
        }
    ];

    return (
        <DataTable
            caption="Danh sách khách hàng"
            columns={columns}
            emptyDescription="Thử thay đổi từ khóa tìm kiếm hoặc điều kiện lọc để xem thêm dữ liệu."
            emptyTitle="Chưa có khách hàng phù hợp"
            getRowKey={(customer) => customer.id}
            loading={loading}
            onRowClick={onCustomerClick}
            pagination={
                <TablePagination
                    disabled={loading}
                    itemLabel="khách hàng"
                    onPageChange={onPageChange}
                    page={result?.page ?? 0}
                    pageSize={result?.size ?? customers.length}
                    totalItems={totalCustomers}
                    totalPages={result?.totalPages ?? 1}
                />
            }
            rowActions={(customer) => (
                <CustomerActionMenu
                    customer={customer}
                    onDelete={onCustomerDelete && (() => onCustomerDelete(customer))}
                    onEdit={() => onCustomerEdit(customer)}
                    onOpen={() => onCustomerClick(customer)}
                    onStatusChange={onCustomerStatusChange && (() => onCustomerStatusChange(customer))}
                />
            )}
            rows={customers}
        />
    );
}

function CustomerActionMenu({
    customer,
    onDelete,
    onEdit,
    onOpen,
    onStatusChange
}: {
    customer: CustomerResponse;
    onDelete?: () => void;
    onEdit: () => void;
    onOpen: () => void;
    onStatusChange?: () => void;
}) {
    const [menuOpen, setMenuOpen] = useState(false);
    const StatusIcon = customer.status === "ACTIVE" ? ShieldOff : ShieldCheck;
    const actions: RowAction[] = [
        { label: "Xem chi tiết", icon: <Eye size={15} />, onSelect: onOpen },
        { label: "Sửa thông tin", icon: <Pencil size={15} />, onSelect: onEdit }
    ];
    if (onStatusChange) {
        actions.push({
            label: customerStatusActionLabel(customer.status),
            icon: <StatusIcon size={15} />,
            onSelect: onStatusChange
        });
    }
    if (onDelete) {
        actions.push({ label: "Xóa khách hàng", icon: <Trash2 size={15} />, tone: "danger", onSelect: onDelete });
    }

    return (
        <RowActionMenu
            actions={actions}
            isOpen={menuOpen}
            menuLabel={`Thao tác cho ${customer.fullName}`}
            onOpenChange={setMenuOpen}
            triggerLabel={`Mở thao tác cho ${customer.fullName}`}
        />
    );
}

function TierBadge({ customer }: { customer: CustomerResponse }) {
    return (
        <StatusBadge
            shape="tag"
            style={{ ...customerTierBadgeStyle(customer.tierColor), borderColor: "transparent" }}
        >
            {customer.tierName || "Chưa xếp hạng"}
        </StatusBadge>
    );
}

function lastPurchaseLabel(customer: CustomerResponse) {
    return customer.lastPurchaseDate
        ? formatCustomerRelativeDate(customer.lastPurchaseDate)
        : "Chưa từng mua";
}
