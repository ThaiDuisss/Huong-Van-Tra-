import {
    ChevronLeft,
    ChevronRight,
    Eye,
    MoreHorizontal,
    Pencil,
    Search,
    Trash2
} from "lucide-react";
import {
    type ReactNode,
    useEffect,
    useRef,
    useState
} from "react";
import { createPortal } from "react-dom";

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
    onPageChange: (page: number) => void;
    onCustomerClick: (customer: CustomerResponse) => void;
    onCustomerEdit: (customer: CustomerResponse) => void;
    onCustomerDelete: (customer: CustomerResponse) => void;
}

export function CustomerTable({
    customers,
    result,
    loading,
    onPageChange,
    onCustomerClick,
    onCustomerEdit,
    onCustomerDelete
}: Props) {
    const totalCustomers = result?.totalElements ?? customers.length;
    const currentPage = (result?.page ?? 0) + 1;
    const totalPages = Math.max(result?.totalPages ?? 1, 1);
    const pageSize = result?.size ?? customers.length;
    const visibleStart = totalCustomers > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const visibleEnd = Math.min(currentPage * pageSize, totalCustomers);
    const pageNumbers = visiblePageNumbers(currentPage, totalPages);
    const showLoadingState = loading && customers.length === 0;
    const showEmptyState = !loading && customers.length === 0;

    return (
        <section className="grid min-h-[330px] flex-1 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <div className="hidden min-h-0 overflow-auto md:block">
                <table className="w-full min-w-[880px] border-separate border-spacing-0 text-[13px] text-[#233025]">
                    <thead>
                        <tr>
                            {tableHeaders().map((header, index) => (
                                <TableHead
                                    className={index === 0 ? "pl-6" : ""}
                                    key={header}
                                >
                                    {header}
                                </TableHead>
                            ))}
                            <TableHead className="w-10">
                                <span className="sr-only">Thao tác</span>
                            </TableHead>
                        </tr>
                    </thead>
                    <tbody className={loading && customers.length > 0 ? "opacity-60" : ""}>
                        {showLoadingState && <CustomerTableLoadingRows />}
                        {showEmptyState && <CustomerTableEmptyRow />}
                        {!showLoadingState &&
                            !showEmptyState &&
                            customers.map((customer, index) => (
                                <CustomerTableRow
                                    customer={customer}
                                    rowIndex={index}
                                    key={customer.id}
                                    onClick={() => onCustomerClick(customer)}
                                    onDelete={() => onCustomerDelete(customer)}
                                    onEdit={() => onCustomerEdit(customer)}
                                />
                            ))}
                    </tbody>
                </table>
            </div>
            <div className="grid gap-2 overflow-auto p-2 md:hidden">
                {showLoadingState && <CustomerMobileLoadingState />}
                {showEmptyState && <CustomerTableEmptyState />}
                {!showLoadingState &&
                    !showEmptyState &&
                    customers.map((customer) => (
                        <MobileCustomerCard
                            customer={customer}
                            key={customer.id}
                            onClick={() => onCustomerClick(customer)}
                            onDelete={() => onCustomerDelete(customer)}
                            onEdit={() => onCustomerEdit(customer)}
                        />
                    ))}
            </div>
            <footer className="flex min-h-[54px] items-center justify-between gap-4 border-t border-[#e4ece8] bg-white px-4 py-2 text-xs font-extrabold text-[#657282] max-md:flex-col max-md:items-start">
                <span>
                    Hiển thị {visibleStart.toLocaleString("vi-VN")}-{visibleEnd.toLocaleString("vi-VN")} trên tổng {totalCustomers.toLocaleString("vi-VN")} khách hàng
                </span>
                <div className="flex items-center gap-1">
                    <PageButton
                        ariaLabel="Trang trước"
                        disabled={loading || currentPage <= 1}
                        onClick={() => onPageChange(currentPage - 2)}
                    >
                        <ChevronLeft size={15} aria-hidden="true" />
                    </PageButton>
                    {pageNumbers.map((page, index) =>
                        page === "gap" ? (
                            <span className="px-1 text-[#8a96a3]" key={`gap-${index}`}>...</span>
                        ) : (
                            <PageButton
                                active={page === currentPage}
                                disabled={loading}
                                key={page}
                                onClick={page === currentPage ? undefined : () => onPageChange(page - 1)}
                            >
                                {page}
                            </PageButton>
                        )
                    )}
                    <PageButton
                        ariaLabel="Trang sau"
                        disabled={loading || currentPage >= totalPages}
                        onClick={() => onPageChange(currentPage)}
                    >
                        <ChevronRight size={15} aria-hidden="true" />
                    </PageButton>
                </div>
            </footer>
        </section>
    );
}

function visiblePageNumbers(currentPage: number, totalPages: number) {
    if (totalPages <= 5) {
        return Array.from({ length: totalPages }, (_item, index) => index + 1);
    }

    const pages: Array<number | "gap"> = [1];
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);

    if (start > 2) pages.push("gap");
    for (let page = start; page <= end; page += 1) pages.push(page);
    if (end < totalPages - 1) pages.push("gap");
    pages.push(totalPages);

    return pages;
}

function CustomerTableLoadingRows() {
    return (
        <>
            {Array.from({ length: 8 }, (_, index) => (
                <tr key={index}>
                    {Array.from({ length: 6 }, (_cell, cellIndex) => (
                        <TableCell
                            className={cellIndex === 0 ? "pl-6" : ""}
                            key={cellIndex}
                        >
                            <span
                                className={`block h-4 animate-pulse rounded bg-[#edf3f0] ${
                                    cellIndex === 0
                                        ? "w-36"
                                        : cellIndex === 1
                                            ? "w-28"
                                            : "w-24"
                                }`}
                            />
                        </TableCell>
                    ))}
                </tr>
            ))}
        </>
    );
}

function CustomerMobileLoadingState() {
    return (
        <>
            {Array.from({ length: 4 }, (_, index) => (
                <div
                    className="h-[128px] animate-pulse rounded-lg border border-[#e1e8e5] bg-[#f4f7f5]"
                    key={index}
                />
            ))}
        </>
    );
}

function CustomerTableEmptyRow() {
    return (
        <tr>
            <td colSpan={6}>
                <CustomerTableEmptyState />
            </td>
        </tr>
    );
}

function CustomerTableEmptyState() {
    return (
        <div className="grid min-h-[320px] place-items-center bg-white p-8 text-center">
            <div className="max-w-[360px]">
                <div className="mx-auto grid size-12 place-items-center rounded-full bg-[#eef6f1] text-[#159734]">
                    <Search size={20} aria-hidden="true" />
                </div>
                <h2 className="mt-4 text-sm font-black text-[#172317]">
                    Chưa có khách hàng phù hợp
                </h2>
                <p className="mt-1.5 text-sm font-semibold text-[#697684]">
                    Thử thay đổi từ khóa tìm kiếm hoặc điều kiện lọc để xem thêm dữ liệu.
                </p>
            </div>
        </div>
    );
}

function CustomerTableRow({
    customer,
    rowIndex,
    onClick,
    onDelete,
    onEdit
}: {
    customer: CustomerResponse;
    rowIndex: number;
    onClick: () => void;
    onDelete: () => void;
    onEdit: () => void;
}) {
    return (
        <tr
            className={`group cursor-pointer ${rowIndex % 2 === 1 ? "[&>td]:bg-[#f3f7f4]" : ""}`}
            onClick={onClick}
            onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onClick();
                }
            }}
            role="button"
            tabIndex={0}
        >
            <TableCell className="pl-6">
                <CustomerIdentity customer={customer} />
            </TableCell>
            {rowCells(customer).map((cell, index) => (
                <TableCell
                    className={cell.strong ? "font-black text-[#172317]" : ""}
                    key={`${customer.id}-${index}`}
                >
                    {cell.content}
                </TableCell>
            ))}
            <TableCell>
                <CustomerActionMenu
                    customer={customer}
                    onDelete={onDelete}
                    onEdit={onEdit}
                    onOpen={onClick}
                />
            </TableCell>
        </tr>
    );
}

function CustomerActionMenu({
    customer,
    onDelete,
    onEdit,
    onOpen
}: {
    customer: CustomerResponse;
    onDelete: () => void;
    onEdit: () => void;
    onOpen: () => void;
}) {
    const [menuOpen, setMenuOpen] = useState(false);
    const [menuPosition, setMenuPosition] = useState({ left: 0, top: 0 });
    const menuButtonRef = useRef<HTMLButtonElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!menuOpen) return;

        function closeMenuOnOutsideClick(event: PointerEvent) {
            const target = event.target as Node;
            if (
                !menuButtonRef.current?.contains(target) &&
                !menuRef.current?.contains(target)
            ) {
                setMenuOpen(false);
            }
        }

        function closeMenu() {
            setMenuOpen(false);
        }

        document.addEventListener("pointerdown", closeMenuOnOutsideClick);
        window.addEventListener("resize", closeMenu);
        window.addEventListener("scroll", closeMenu, true);
        return () => {
            document.removeEventListener("pointerdown", closeMenuOnOutsideClick);
            window.removeEventListener("resize", closeMenu);
            window.removeEventListener("scroll", closeMenu, true);
        };
    }, [menuOpen]);

    function runAction(action: () => void) {
        setMenuOpen(false);
        action();
    }

    function toggleMenu() {
        if (menuOpen) {
            setMenuOpen(false);
            return;
        }

        const rect = menuButtonRef.current?.getBoundingClientRect();
        if (!rect) return;
        const menuWidth = 192;
        const menuHeight = 132;
        const gap = 4;
        const spaceBelow = window.innerHeight - rect.bottom;
        const top =
            spaceBelow >= menuHeight + gap
                ? rect.bottom + gap
                : rect.top - menuHeight - gap;

        setMenuPosition({
            left: Math.max(
                8,
                Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - 8)
            ),
            top: Math.max(8, top)
        });
        setMenuOpen(true);
    }

    return (
        <div
            onClick={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
        >
            <button
                aria-expanded={menuOpen}
                aria-haspopup="menu"
                aria-label={`Mở thao tác cho ${customer.fullName}`}
                className="grid size-8 place-items-center rounded-lg border border-transparent bg-transparent text-[#596879] hover:border-[#dce5e1] hover:bg-white hover:text-[#172317]"
                onClick={toggleMenu}
                ref={menuButtonRef}
                type="button"
            >
                <MoreHorizontal size={18} aria-hidden="true" />
            </button>
            {menuOpen &&
                createPortal(
                    <div
                        className="fixed z-[60] w-48 overflow-hidden rounded-lg border border-[#dce5e1] bg-white py-1 text-xs font-bold shadow-[0_12px_30px_rgba(31,53,43,0.18)]"
                        onClick={(event) => event.stopPropagation()}
                        ref={menuRef}
                        role="menu"
                        style={menuPosition}
                    >
                        <ActionItem icon={Eye} label="Xem chi tiết" onClick={() => runAction(onOpen)} />
                        <ActionItem icon={Pencil} label="Sửa thông tin" onClick={() => runAction(onEdit)} />
                        <ActionItem danger icon={Trash2} label="Xóa khách hàng" onClick={() => runAction(onDelete)} />
                    </div>,
                    document.body
                )}
        </div>
    );
}

function MobileCustomerCard({
    customer,
    onClick,
    onDelete,
    onEdit
}: {
    customer: CustomerResponse;
    onClick: () => void;
    onDelete: () => void;
    onEdit: () => void;
}) {
    return (
        <article className="rounded-lg border border-[#e1e8e5] bg-white p-3">
            <div className="flex items-start gap-3">
                <button
                    className="min-w-0 flex-1 bg-transparent p-0 text-left"
                    onClick={onClick}
                    type="button"
                >
                    <span className="min-w-0">
                        <strong className="block truncate text-sm font-black text-[#172317]">
                            {customer.fullName}
                        </strong>
                        <span className="mt-0.5 block text-xs font-semibold text-[#697684]">
                            {customer.phone}
                        </span>
                    </span>
                </button>
                <CustomerActionMenu
                    customer={customer}
                    onDelete={onDelete}
                    onEdit={onEdit}
                    onOpen={onClick}
                />
            </div>
            <div className="mt-3 flex flex-wrap gap-1.5">
                <TierBadge customer={customer} />
                <StatusBadge status={customer.status} />
            </div>
            <div className="mt-3 grid grid-cols-1 gap-2 text-xs">
                <MiniStat label="Mua gần nhất" value={lastPurchaseLabel(customer)} />
            </div>
        </article>
    );
}

function CustomerIdentity({ customer }: { customer: CustomerResponse }) {
    return (
        <div className="min-w-[180px] truncate font-black text-[#172317]">
            {customer.fullName}
        </div>
    );
}

function MiniStat({ label, value }: { label: string; value: string }) {
    return (
        <span className="rounded-md bg-[#f4f7f5] p-2">
            <span className="block font-semibold text-[#697684]">{label}</span>
            <strong className="mt-0.5 block truncate font-black text-[#172317]">
                {value}
            </strong>
        </span>
    );
}

function tableHeaders() {
    return [
        "Khách hàng",
        "Liên hệ",
        "Hạng khách hàng",
        "Lần mua gần nhất",
        "Trạng thái"
    ];
}

function rowCells(customer: CustomerResponse) {
    return [
        { content: customer.phone, strong: true },
        { content: <TierBadge customer={customer} /> },
        { content: lastPurchaseLabel(customer) },
        { content: <StatusBadge status={customer.status} /> }
    ];
}

function TierBadge({ customer }: { customer: CustomerResponse }) {
    return (
        <span
            className="inline-flex h-[24px] items-center rounded-md px-2 text-[11px] font-black"
            style={customerTierBadgeStyle(customer.tierColor)}
        >
            {customer.tierName || "Chưa xếp hạng"}
        </span>
    );
}

function StatusBadge({ status }: { status: CustomerResponse["status"] }) {
    const locked = status === "SUSPENDED" || status === "INACTIVE";
    return (
        <span
            className={`inline-flex h-[24px] items-center rounded-full px-2 text-[11px] font-black ${locked
                ? "bg-[#fff0ed] text-[#c4422e]"
                : "bg-[#e9f8ed] text-[#159734]"
                }`}
        >
            {locked ? "Đã khóa" : "Hoạt động"}
        </span>
    );
}

function PageButton({
    active = false,
    ariaLabel,
    children,
    disabled = false,
    onClick
}: {
    active?: boolean;
    ariaLabel?: string;
    children: ReactNode;
    disabled?: boolean;
    onClick?: () => void;
}) {
    return (
        <button
            aria-label={ariaLabel}
            className={`grid size-8 place-items-center rounded-lg border text-xs font-black transition-colors ${active
                ? "border-[#159734] bg-[#159734] text-white"
                : "border-[#dce5e1] bg-white text-[#536171] hover:bg-[#f7fbf8]"
                } disabled:cursor-not-allowed disabled:opacity-45`}
            disabled={disabled}
            onClick={onClick}
            type="button"
        >
            {children}
        </button>
    );
}

function ActionItem({
    danger = false,
    icon: Icon,
    label,
    onClick
}: {
    danger?: boolean;
    icon: typeof Eye;
    label: string;
    onClick: () => void;
}) {
    return (
        <button
            className={`flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-[#f3f7f4] ${danger ? "text-[#c4422e]" : "text-[#334336]"
                }`}
            onClick={onClick}
            role="menuitem"
            type="button"
        >
            <Icon size={15} aria-hidden="true" />
            {label}
        </button>
    );
}

function TableHead({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <th className={`sticky top-0 z-[1] h-10 whitespace-nowrap border-b border-[#e4ece8] bg-[#f8faf9] px-3 text-left text-[11px] font-black uppercase text-[#556477] ${className}`}>
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
        <td className={`h-[52px] whitespace-nowrap border-b border-[#eef2f0] bg-white px-3 align-middle group-hover:bg-[#f7fbf8] ${className}`}>
            {children}
        </td>
    );
}

function lastPurchaseLabel(customer: CustomerResponse) {
    return customer.lastPurchaseDate
        ? formatCustomerRelativeDate(customer.lastPurchaseDate)
        : "Chưa từng mua";
}
