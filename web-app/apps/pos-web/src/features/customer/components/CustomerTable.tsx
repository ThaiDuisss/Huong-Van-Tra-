import { LoaderCircle, MoreHorizontal } from "lucide-react";
import { type ReactNode, useState } from "react";

import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_OUTLINE_BUTTON,
    CUSTOMER_STATUS_CLASS_NAMES,
    CUSTOMER_STATUS_LABELS
} from "../constants/customer-list.constants";
import type {
    CustomerResponse,
    PageResponse
} from "../types/customer.types";
import {
    customerTierBadgeStyle,
    formatCustomerRelativeDate
} from "../utils/customer-list.utils";

interface Props {
    customers: CustomerResponse[];
    result: PageResponse<CustomerResponse> | null;
    loading: boolean;
    loadingMore: boolean;
    error: string;
    onLoadMore: () => void;
}

export function CustomerTable({
    customers,
    result,
    loading,
    loadingMore,
    error,
    onLoadMore
}: Props) {
    const [selectedIds, setSelectedIds] = useState<Set<string>>(
        () => new Set()
    );
    const selectedVisibleCount = customers.filter((customer) =>
        selectedIds.has(customer.id)
    ).length;
    const allVisibleSelected =
        customers.length > 0 && selectedVisibleCount === customers.length;
    const someVisibleSelected =
        selectedVisibleCount > 0 && !allVisibleSelected;

    function toggleCustomer(customerId: string) {
        setSelectedIds((current) => {
            const next = new Set(current);
            if (next.has(customerId)) next.delete(customerId);
            else next.add(customerId);
            return next;
        });
    }

    function toggleAllVisible() {
        setSelectedIds((current) => {
            const next = new Set(current);
            customers.forEach((customer) => {
                if (allVisibleSelected) next.delete(customer.id);
                else next.add(customer.id);
            });
            return next;
        });
    }

    return (
        <section className="grid min-h-[360px] flex-1 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-[8px] border border-[#e1e8e5] bg-white shadow-[0_14px_34px_rgba(31,53,43,0.06)]">
            <div className="min-h-0 overflow-auto">
                <table className="w-full min-w-[1120px] border-separate border-spacing-0 text-[13px] text-[#233025]">
                    <thead>
                        <tr>
                            <th className="sticky top-0 z-[1] h-[42px] w-12 border-b border-[#e8eeeb] bg-[#f8faf9] px-3.5 pr-1 text-center text-[11px] font-black uppercase text-[#556477]">
                                <input
                                    aria-label="Chọn tất cả khách hàng"
                                    checked={allVisibleSelected}
                                    className="size-4 accent-[#159734]"
                                    disabled={!customers.length}
                                    onChange={toggleAllVisible}
                                    ref={(element) => {
                                        if (element) {
                                            element.indeterminate =
                                                someVisibleSelected;
                                        }
                                    }}
                                    type="checkbox"
                                />
                            </th>
                            <TableHead> </TableHead>
                            <TableHead>Mã KH</TableHead>
                            <TableHead>Khách hàng</TableHead>
                            <TableHead>Số điện thoại</TableHead>
                            <TableHead>Hạng thành viên</TableHead>
                            <TableHead>Lần mua gần nhất</TableHead>
                            <TableHead>Trạng thái</TableHead>
                            <TableHead>
                                <span className="sr-only">Thao tác</span>
                            </TableHead>
                        </tr>
                    </thead>
                    <tbody className={loading ? "opacity-50" : ""}>
                        {customers.map((customer, index) => (
                            <CustomerTableRow
                                customer={customer}
                                index={index}
                                isSelected={selectedIds.has(customer.id)}
                                key={customer.id}
                                onToggle={() => toggleCustomer(customer.id)}
                            />
                        ))}
                    </tbody>
                </table>
                {loading && !customers.length && (
                    <TableMessage>
                        <LoaderCircle
                            className="animate-spin text-[#159734]"
                            size={18}
                            aria-hidden="true"
                        />
                        Đang tải danh sách khách hàng...
                    </TableMessage>
                )}
                {!loading && !error && !customers.length && (
                    <TableMessage>Không tìm thấy khách hàng phù hợp</TableMessage>
                )}
            </div>
            <footer className="flex min-h-[54px] items-center justify-between gap-4 border-t border-[#e8eeeb] px-3.5 py-[9px] text-xs font-extrabold text-[#657282]">
                <span>
                    Đã hiển thị {customers.length} / {result?.totalElements ?? 0}{" "}
                    khách hàng
                    {selectedVisibleCount > 0 &&
                        ` · Đã chọn ${selectedVisibleCount}`}
                </span>
                {result && !result.last && (
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_OUTLINE_BUTTON}`}
                        disabled={loading || loadingMore}
                        onClick={onLoadMore}
                        type="button"
                    >
                        {loadingMore && (
                            <LoaderCircle
                                className="animate-spin"
                                size={15}
                                aria-hidden="true"
                            />
                        )}
                        {loadingMore
                            ? "Đang tải..."
                            : `Tải thêm ${Math.min(
                                result.size,
                                result.totalElements - customers.length
                            )} khách hàng`}
                    </button>
                )}
            </footer>
        </section>
    );
}

function CustomerTableRow({
    customer,
    index,
    isSelected,
    onToggle
}: {
    customer: CustomerResponse;
    index: number;
    isSelected: boolean;
    onToggle: () => void;
}) {
    return (
        <tr
            className={`group cursor-pointer ${isSelected ? "[&>td]:bg-[#f0faf2]" : ""
                }`}
        >
            <td className="h-[58px] w-12 whitespace-nowrap border-b border-[#eef2f0] bg-white px-3.5 pr-1 text-center align-middle group-hover:bg-[#f7fbf8]">
                <input
                    aria-label={`Chọn ${customer.fullName}`}
                    checked={isSelected}
                    className="size-4 accent-[#159734]"
                    onChange={onToggle}
                    type="checkbox"
                />
            </td>
            <TableCell className="w-14 text-center font-bold text-[#657282]">
                {index + 1}
            </TableCell>
            <TableCell className="font-black text-[#526071]">
                {customer.customerCode}
            </TableCell>
            <TableCell>
                <button
                    className="bg-transparent p-0 text-left font-black text-[#172317] hover:text-[#159734]"
                    type="button"
                >
                    {customer.fullName}
                </button>
                <span className="mt-0.5 block text-[11px] text-[#7a8794]">
                    {customer.email || "Chưa có email"}
                </span>
            </TableCell>
            <TableCell>{customer.phone}</TableCell>
            <TableCell>
                <span
                    className="inline-flex h-[25px] items-center justify-center rounded-[7px] px-[9px] text-[11px] font-black"
                    style={customerTierBadgeStyle(customer.tierColor)}
                >
                    {customer.tierName || "Chưa xếp hạng"}
                </span>
            </TableCell>
            <TableCell>
                {customer.lastPurchaseDate
                    ? formatCustomerRelativeDate(customer.lastPurchaseDate)
                    : "Chưa từng mua"}
            </TableCell>
            <TableCell>
                <span
                    className={`inline-flex h-[25px] items-center justify-center rounded-[7px] px-[9px] text-[11px] font-black ${CUSTOMER_STATUS_CLASS_NAMES[customer.status]
                        }`}
                >
                    {CUSTOMER_STATUS_LABELS[customer.status]}
                </span>
            </TableCell>
            <TableCell>
                <button
                    className="grid size-8 place-items-center rounded-lg border border-transparent bg-transparent text-[#596879] hover:border-[#dce5e1] hover:bg-white hover:text-[#172317]"
                    type="button"
                    aria-label={`Mở thao tác cho ${customer.fullName}`}
                >
                    <MoreHorizontal size={18} aria-hidden="true" />
                </button>
            </TableCell>
        </tr>
    );
}

function TableHead({ children }: { children: ReactNode }) {
    return (
        <th className="sticky top-0 z-[1] h-[42px] whitespace-nowrap border-b border-[#e8eeeb] bg-[#f8faf9] px-3.5 text-left text-[11px] font-black uppercase text-[#556477]">
            {children}
        </th>
    );
}

function TableCell({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <td
            className={`h-[58px] whitespace-nowrap border-b border-[#eef2f0] bg-white px-3.5 align-middle group-hover:bg-[#f7fbf8] ${className}`}
        >
            {children}
        </td>
    );
}

function TableMessage({ children }: { children: ReactNode }) {
    return (
        <div className="grid min-h-52 place-items-center text-sm font-semibold text-[#697684]">
            <span className="flex items-center gap-2">{children}</span>
        </div>
    );
}
