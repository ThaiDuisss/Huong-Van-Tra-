import {
    Eye,
    LoaderCircle,
    MoreHorizontal,
    Pencil,
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
    onCustomerClick: (customer: CustomerResponse) => void;
    onCustomerEdit: (customer: CustomerResponse) => void;
    onCustomerDelete: (customer: CustomerResponse) => void;
}

export function CustomerTable({
    customers,
    result,
    loading,
    loadingMore,
    error,
    onLoadMore,
    onCustomerClick,
    onCustomerEdit,
    onCustomerDelete
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
                                onClick={() => onCustomerClick(customer)}
                                onDelete={() => onCustomerDelete(customer)}
                                onEdit={() => onCustomerEdit(customer)}
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
    onClick,
    onDelete,
    onEdit,
    onToggle
}: {
    customer: CustomerResponse;
    index: number;
    isSelected: boolean;
    onClick: () => void;
    onDelete: () => void;
    onEdit: () => void;
    onToggle: () => void;
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

        const menuWidth = 176;
        const menuHeight = 106;
        const gap = 4;
        const spaceBelow = window.innerHeight - rect.bottom;
        const top =
            spaceBelow >= menuHeight + gap
                ? rect.bottom + gap
                : rect.top - menuHeight - gap;

        setMenuPosition({
            left: Math.max(8, Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - 8)),
            top: Math.max(8, top)
        });
        setMenuOpen(true);
    }

    return (
        <tr
            className={`group cursor-pointer ${isSelected ? "[&>td]:bg-[#f0faf2]" : ""
                }`}
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
            <td className="h-[58px] w-12 whitespace-nowrap border-b border-[#eef2f0] bg-white px-3.5 pr-1 text-center align-middle group-hover:bg-[#f7fbf8]">
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
                    onClick={(event) => {
                        event.stopPropagation();
                        onClick();
                    }}
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
                            className="fixed z-[60] w-44 overflow-hidden rounded-lg border border-[#dce5e1] bg-white py-1 text-xs font-bold shadow-[0_12px_30px_rgba(31,53,43,0.18)]"
                            onClick={(event) => event.stopPropagation()}
                            ref={menuRef}
                            role="menu"
                            style={menuPosition}
                        >
                            <ActionItem
                                icon={Eye}
                                label="Xem chi tiết"
                                onClick={() => runAction(onClick)}
                            />
                            <ActionItem
                                icon={Pencil}
                                label="Chỉnh sửa"
                                onClick={() => runAction(onEdit)}
                            />
                            <ActionItem
                                danger
                                icon={Trash2}
                                label="Xóa khách hàng"
                                onClick={() => runAction(onDelete)}
                            />
                        </div>,
                        document.body
                    )}
                </div>
            </TableCell>
        </tr>
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
