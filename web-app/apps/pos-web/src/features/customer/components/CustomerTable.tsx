import {
    Eye,
    History,
    LoaderCircle,
    MoreHorizontal,
    Pencil,
    Send,
    ShoppingCart,
    StickyNote,
    Trash2
} from "lucide-react";
import {
    type ReactNode,
    useEffect,
    useRef,
    useState
} from "react";
import { createPortal } from "react-dom";

import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_OUTLINE_BUTTON
} from "../constants/customer-list.constants";
import type { CustomerPrimaryTab } from "../types/customer-list.types";
import type {
    CustomerResponse,
    PageResponse
} from "../types/customer.types";
import {
    customerTierBadgeStyle,
    formatCustomerRelativeDate
} from "../utils/customer-list.utils";

interface Props {
    activeTab: CustomerPrimaryTab;
    bulkSelection: boolean;
    customers: CustomerResponse[];
    result: PageResponse<CustomerResponse> | null;
    loading: boolean;
    loadingMore: boolean;
    selectedIds: Set<string>;
    onSelectedIdsChange: (selectedIds: Set<string>) => void;
    onLoadMore: () => void;
    onCustomerClick: (customer: CustomerResponse) => void;
    onCustomerEdit: (customer: CustomerResponse) => void;
    onCustomerDelete: (customer: CustomerResponse) => void;
}

export function CustomerTable({
    activeTab,
    bulkSelection,
    customers,
    result,
    loading,
    loadingMore,
    selectedIds,
    onSelectedIdsChange,
    onLoadMore,
    onCustomerClick,
    onCustomerEdit,
    onCustomerDelete
}: Props) {
    const selectedVisibleCount = customers.filter((customer) =>
        selectedIds.has(customer.id)
    ).length;
    const allVisibleSelected =
        customers.length > 0 && selectedVisibleCount === customers.length;
    const someVisibleSelected =
        selectedVisibleCount > 0 && !allVisibleSelected;

    function toggleCustomer(customerId: string) {
        const next = new Set(selectedIds);
        if (next.has(customerId)) next.delete(customerId);
        else next.add(customerId);
        onSelectedIdsChange(next);
    }

    function toggleAllVisible() {
        const next = new Set(selectedIds);
        customers.forEach((customer) => {
            if (allVisibleSelected) next.delete(customer.id);
            else next.add(customer.id);
        });
        onSelectedIdsChange(next);
    }

    return (
        <section className="grid min-h-[330px] flex-1 grid-rows-[minmax(0,1fr)_auto] overflow-hidden bg-white">
            <div className="hidden min-h-0 overflow-auto md:block">
                <table className="w-full min-w-[860px] border-separate border-spacing-0 text-[13px] text-[#233025]">
                    <thead>
                        <tr>
                            {bulkSelection && (
                                <TableHead className="w-10 text-center">
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
                                </TableHead>
                            )}
                            {tableHeaders(activeTab).map((header) => (
                                <TableHead key={header}>{header}</TableHead>
                            ))}
                            <TableHead className="w-10">
                                <span className="sr-only">Thao tác</span>
                            </TableHead>
                        </tr>
                    </thead>
                    <tbody className={loading ? "opacity-60" : ""}>
                        {customers.map((customer) => (
                            <CustomerTableRow
                                activeTab={activeTab}
                                bulkSelection={bulkSelection}
                                customer={customer}
                                isSelected={selectedIds.has(customer.id)}
                                key={customer.id}
                                onClick={() => onCustomerClick(customer)}
                                onDelete={() => onCustomerDelete(customer)}
                                onEdit={() => onCustomerEdit(customer)}
                                onToggle={() => toggleCustomer(customer.id)}
                            />
                        ))}
                    </tbody>
                </table>
            </div>
            <div className="grid gap-2 overflow-auto p-2 md:hidden">
                {customers.map((customer) => (
                    <MobileCustomerCard
                        bulkSelection={bulkSelection}
                        customer={customer}
                        isSelected={selectedIds.has(customer.id)}
                        key={customer.id}
                        onClick={() => onCustomerClick(customer)}
                        onDelete={() => onCustomerDelete(customer)}
                        onEdit={() => onCustomerEdit(customer)}
                        onToggle={() => toggleCustomer(customer.id)}
                    />
                ))}
            </div>
            <footer className="flex min-h-[48px] items-center justify-between gap-4 border-t border-[#e4ece8] px-3 py-2 text-xs font-extrabold text-[#657282]">
                <span>
                    {result?.last
                        ? `Đã hiển thị tất cả ${result?.totalElements ?? 0} khách hàng`
                        : `Đã hiển thị ${customers.length} / ${result?.totalElements ?? 0} khách hàng`}
                    {selectedVisibleCount > 0 &&
                        ` · Đã chọn ${selectedVisibleCount}`}
                </span>
                {result && !result.last && (
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_OUTLINE_BUTTON} h-8 px-2.5 text-xs`}
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
    activeTab,
    bulkSelection,
    customer,
    isSelected,
    onClick,
    onDelete,
    onEdit,
    onToggle
}: {
    activeTab: CustomerPrimaryTab;
    bulkSelection: boolean;
    customer: CustomerResponse;
    isSelected: boolean;
    onClick: () => void;
    onDelete: () => void;
    onEdit: () => void;
    onToggle: () => void;
}) {
    return (
        <tr
            className={`group cursor-pointer ${isSelected ? "[&>td]:bg-[#f0faf2]" : ""}`}
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
            {bulkSelection && (
                <TableCell className="w-10 text-center">
                    <input
                        aria-label={`Chọn ${customer.fullName}`}
                        checked={isSelected}
                        className="size-4 accent-[#159734]"
                        onChange={(event) => {
                            event.stopPropagation();
                            onToggle();
                        }}
                        onClick={(event) => event.stopPropagation()}
                        type="checkbox"
                    />
                </TableCell>
            )}
            <TableCell>
                <CustomerIdentity customer={customer} />
            </TableCell>
            {rowCells(activeTab, customer).map((cell, index) => (
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
        const menuHeight = 250;
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
                        <ActionItem icon={ShoppingCart} label="Tạo đơn nhanh" onClick={() => runAction(onOpen)} />
                        <ActionItem icon={History} label="Xem lịch sử mua" onClick={() => runAction(onOpen)} />
                        <ActionItem icon={Pencil} label="Sửa thông tin" onClick={() => runAction(onEdit)} />
                        <ActionItem icon={StickyNote} label="Thêm ghi chú" onClick={() => runAction(onOpen)} />
                        <ActionItem icon={Send} label="Gửi ưu đãi" onClick={() => runAction(onOpen)} />
                        <ActionItem danger icon={Trash2} label="Xóa khách hàng" onClick={() => runAction(onDelete)} />
                    </div>,
                    document.body
                )}
        </div>
    );
}

function MobileCustomerCard({
    bulkSelection,
    customer,
    isSelected,
    onClick,
    onDelete,
    onEdit,
    onToggle
}: {
    bulkSelection: boolean;
    customer: CustomerResponse;
    isSelected: boolean;
    onClick: () => void;
    onDelete: () => void;
    onEdit: () => void;
    onToggle: () => void;
}) {
    return (
        <article className="rounded-lg border border-[#e1e8e5] bg-white p-3">
            <div className="flex items-start gap-3">
                {bulkSelection && (
                    <input
                        aria-label={`Chọn ${customer.fullName}`}
                        checked={isSelected}
                        className="mt-2 size-4 accent-[#159734]"
                        onChange={onToggle}
                        type="checkbox"
                    />
                )}
                <button
                    className="flex min-w-0 flex-1 items-start gap-3 bg-transparent p-0 text-left"
                    onClick={onClick}
                    type="button"
                >
                    <CustomerAvatar customer={customer} />
                    <span className="min-w-0">
                        <strong className="block truncate text-sm font-black text-[#172317]">
                            {customer.fullName}
                        </strong>
                        <span className="mt-0.5 block text-xs font-semibold text-[#697684]">
                            {customer.customerCode} · {customer.phone}
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
                <InsightBadge>{getPrimaryInsight(customer)}</InsightBadge>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                <MiniStat label="Chi tiêu" value={formatCurrency(customer.totalSpent)} />
                <MiniStat label="Mua gần nhất" value={lastPurchaseLabel(customer)} />
            </div>
        </article>
    );
}

function CustomerIdentity({ customer }: { customer: CustomerResponse }) {
    return (
        <div className="flex min-w-[220px] items-center gap-3">
            <CustomerAvatar customer={customer} />
            <div className="min-w-0">
                <div className="truncate font-black text-[#172317]">
                    {customer.fullName}
                </div>
                <div className="text-xs font-semibold text-[#697684]">
                    {customer.customerCode}
                </div>
            </div>
        </div>
    );
}

function CustomerAvatar({ customer }: { customer: CustomerResponse }) {
    return (
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-[#eef6f1] text-sm font-black text-[#159734]">
            {customer.fullName.trim().charAt(0).toUpperCase()}
        </span>
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

function tableHeaders(activeTab: CustomerPrimaryTab) {
    if (activeTab === "care") {
        return [
            "Khách hàng",
            "Lý do cần chăm sóc",
            "Hạng TV",
            "Tổng chi tiêu",
            "Lần mua gần nhất"
        ];
    }
    if (activeTab === "loyal") {
        return [
            "Khách hàng",
            "Hạng TV",
            "Tổng chi tiêu",
            "Số đơn",
            "Tần suất mua"
        ];
    }
    if (activeTab === "new") {
        return [
            "Khách hàng",
            "Ngày tạo",
            "Đơn đầu tiên",
            "Trạng thái quay lại",
            "Tổng chi tiêu"
        ];
    }
    if (activeTab === "inactive") {
        return [
            "Khách hàng",
            "Hạng trước đó",
            "Tổng chi tiêu",
            "Không mua",
            "Lần mua gần nhất"
        ];
    }
    return [
        "Khách hàng",
        "Liên hệ",
        "Hạng TV",
        "Tổng chi tiêu",
        "Số đơn",
        "Lần mua gần nhất"
    ];
}

function rowCells(activeTab: CustomerPrimaryTab, customer: CustomerResponse) {
    if (activeTab === "care") {
        return [
            { content: <InsightBadge>{getPrimaryInsight(customer)}</InsightBadge> },
            { content: <TierBadge customer={customer} /> },
            { content: formatCurrency(customer.totalSpent), strong: true },
            { content: lastPurchaseLabel(customer) }
        ];
    }
    if (activeTab === "loyal") {
        return [
            { content: <TierBadge customer={customer} /> },
            { content: formatCurrency(customer.totalSpent), strong: true },
            { content: estimateOrderCount(customer), strong: true },
            { content: purchaseFrequency(customer) }
        ];
    }
    if (activeTab === "new") {
        return [
            { content: formatDate(customer.createdAt) },
            { content: customer.lastPurchaseDate ? "Đã có" : "Chưa có" },
            { content: estimateOrderCount(customer) > 1 ? "Đã quay lại" : "Chưa quay lại" },
            { content: formatCurrency(customer.totalSpent), strong: true }
        ];
    }
    if (activeTab === "inactive") {
        return [
            { content: customer.tierName || "Chưa có hạng" },
            { content: formatCurrency(customer.totalSpent), strong: true },
            { content: `${daysSince(customer.lastPurchaseDate) ?? 0} ngày` },
            { content: lastPurchaseLabel(customer) }
        ];
    }
    return [
        { content: customer.phone, strong: true },
        { content: <TierBadge customer={customer} /> },
        { content: formatCurrency(customer.totalSpent), strong: true },
        { content: estimateOrderCount(customer), strong: true },
        { content: lastPurchaseLabel(customer) }
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

function InsightBadge({ children }: { children: ReactNode }) {
    return (
        <span className="inline-flex h-[24px] items-center rounded-md bg-[#f1f5f3] px-2 text-[11px] font-black text-[#596879]">
            {children}
        </span>
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
            className={`flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-[#f3f7f4] ${
                danger ? "text-[#c4422e]" : "text-[#334336]"
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

function formatCurrency(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}

function estimateOrderCount(customer: CustomerResponse) {
    if (!customer.totalSpent) return 0;
    return Math.max(1, Math.round(customer.totalSpent / 85_000));
}

function getPrimaryInsight(customer: CustomerResponse) {
    return getCustomerInsights(customer)[0];
}

function getCustomerInsights(customer: CustomerResponse) {
    const insights: string[] = [];
    const days = daysSince(customer.lastPurchaseDate);
    if (["DIAMOND", "PLATINUM", "GOLD"].includes(customer.tierCode ?? "")) {
        insights.push("VIP");
    }
    if (customer.totalSpent >= 1_000_000) insights.push("Chi tiêu cao");
    if (days !== null && days >= 90) insights.push("90 ngày+");
    else if (days !== null && days >= 60) insights.push("60 ngày chưa mua");
    else if (days !== null && days >= 30) insights.push("30 ngày chưa mua");
    if (!customer.lastPurchaseDate) insights.push("Chưa từng mua");
    if (isBirthdayThisWeek(customer.dateOfBirth)) insights.push("Sinh nhật tuần này");
    if (estimateOrderCount(customer) === 1) insights.push("Mới mua 1 lần");
    return insights.length ? insights : ["Đang hoạt động"];
}

function purchaseFrequency(customer: CustomerResponse) {
    const orders = estimateOrderCount(customer);
    const inactiveDays = daysSince(customer.lastPurchaseDate);
    if (orders >= 12 && inactiveDays !== null && inactiveDays <= 30) return "Rất thường xuyên";
    if (orders >= 6 && inactiveDays !== null && inactiveDays <= 45) return "Thường xuyên";
    if (orders >= 2) return "Thỉnh thoảng";
    return "Ít mua";
}

function lastPurchaseLabel(customer: CustomerResponse) {
    return customer.lastPurchaseDate
        ? formatCustomerRelativeDate(customer.lastPurchaseDate)
        : "Chưa từng mua";
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN").format(new Date(value));
}

function daysSince(value: string | null) {
    if (!value) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const date = new Date(`${value}T00:00:00`);
    return Math.round((today.getTime() - date.getTime()) / 86_400_000);
}

function isBirthdayThisWeek(value: string | null) {
    if (!value) return false;
    const today = new Date();
    const birthday = new Date(value);
    birthday.setFullYear(today.getFullYear());
    const diff = Math.round(
        (birthday.getTime() - today.getTime()) / 86_400_000
    );
    return diff >= 0 && diff <= 7;
}
