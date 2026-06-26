import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    FilePenLine,
    FileSpreadsheet,
    ListFilter,
    MoreHorizontal,
    Plus,
    Search,
    Settings,
    X
} from "lucide-react";
import { useMemo, useState } from "react";

import { DateQuickFilter } from "../../../components/shared/DateQuickFilter";
import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    matchDateFilter,
    type DateFilterKey
} from "../../../components/shared/dateFilters";
import styles from "./OrderListPage.module.css";

type OrderStatus =
    | "Đang giao hàng"
    | "Chờ duyệt"
    | "Đã hoàn thành"
    | "Đã hoàn"
    | "Đã hoàn tiền"
    | "Đang xử lý"
    | "Đã hủy";

type FilterStatus = "Tất cả" | OrderStatus | "Hoàn/Hoàn tiền";

interface OrderRow {
    code: string;
    customerName: string;
    customerPhone?: string;
    date: string;
    type: "Đơn hàng" | "Hoàn hàng" | "Hoàn tiền";
    total: string;
    status: OrderStatus;
    staff: string;
    payment: "Đã thanh toán" | "Chờ thanh toán" | "Hoàn tiền";
    products: string[];
    history: string[];
}

const orders: OrderRow[] = [
    ["DH-2026001", "Nguyễn Thị Hương", "0987 654 321", "17/06/2026", "Đơn hàng", "12.500.000đ", "Đang giao hàng", "Phạm Thu Hương", "Đã thanh toán", ["Trà sen Tây Hồ x2", "Ấm tử sa Bát Tràng x1"], ["Tạo đơn", "Xác nhận", "Đang giao hàng"]],
    ["DH-2026002", "Trần Minh Tuấn", "0912 345 678", "17/06/2026", "Đơn hàng", "6.800.000đ", "Chờ duyệt", "Lê Hoàng Anh", "Chờ thanh toán", ["Trà ô long đặc biệt x3"], ["Tạo đơn", "Chờ duyệt"]],
    ["DH-2026003", "Lê Thị Mai", "0904 112 233", "17/06/2026", "Đơn hàng", "9.600.000đ", "Đã hoàn thành", "Nguyễn Thu Hà", "Đã thanh toán", ["Set trà biếu cao cấp x2"], ["Tạo đơn", "Thanh toán", "Hoàn thành"]],
    ["DH-2026004", "Khách lẻ", undefined, "17/06/2026", "Hoàn hàng", "2.150.000đ", "Đã hoàn", "Trần Quang Huy", "Hoàn tiền", ["Trà shan tuyết x1"], ["Tạo yêu cầu", "Nhận hàng hoàn", "Đã hoàn"]],
    ["DH-2026005", "Hoàng Thị Lan", "0933 456 789", "16/06/2026", "Đơn hàng", "7.800.000đ", "Chờ duyệt", "Phạm Thu Hương", "Chờ thanh toán", ["Trà hoa nhài x5"], ["Tạo đơn", "Chờ duyệt"]],
    ["DH-2026006", "Đặng Văn Cường", "0977 888 999", "16/06/2026", "Hoàn tiền", "5.650.000đ", "Đã hoàn tiền", "Lê Hoàng Anh", "Hoàn tiền", ["Set phụ kiện pha trà x1"], ["Tạo yêu cầu", "Duyệt hoàn tiền", "Đã hoàn tiền"]],
    ["DH-2026007", "Bùi Thị Thảo", "0966 121 212", "15/06/2026", "Đơn hàng", "15.400.000đ", "Đang xử lý", "Nguyễn Thu Hà", "Đã thanh toán", ["Trà cổ thụ x4", "Bộ chén men lam x2"], ["Tạo đơn", "Thanh toán", "Đang xử lý"]],
    ["DH-2026008", "Nguyễn Đức Mạnh", "0945 667 788", "15/06/2026", "Đơn hàng", "4.250.000đ", "Đã hoàn thành", "Trần Quang Huy", "Đã thanh toán", ["Trà lài túi lọc x10"], ["Tạo đơn", "Đóng gói", "Hoàn thành"]],
    ["DH-2026009", "Võ Thị Hằng", "0981 222 333", "15/06/2026", "Hoàn hàng", "550.000đ", "Đã hoàn", "Phạm Thu Hương", "Hoàn tiền", ["Hộp trà xanh x1"], ["Tạo yêu cầu", "Nhận hàng hoàn", "Đã hoàn"]],
    ["DH-2026010", "Trịnh Văn Lâm", "0922 444 555", "15/06/2026", "Hoàn tiền", "1.200.000đ", "Đã hoàn tiền", "Lê Hoàng Anh", "Hoàn tiền", ["Trà gừng mật ong x2"], ["Tạo yêu cầu", "Đối soát", "Đã hoàn tiền"]]
].map(([code, customerName, customerPhone, date, type, total, status, staff, payment, products, history]) => ({
    code,
    customerName,
    customerPhone,
    date,
    type,
    total,
    status,
    staff,
    payment,
    products,
    history
})) as OrderRow[];

const statusFilters: FilterStatus[] = [
    "Tất cả",
    "Chờ duyệt",
    "Đang xử lý",
    "Đang giao hàng",
    "Đã hoàn thành",
    "Đã hoàn",
    "Đã hoàn tiền"
];

const quickFilters: { label: string; value: FilterStatus }[] = [
    { label: "Tất cả", value: "Tất cả" },
    { label: "Chờ duyệt", value: "Chờ duyệt" },
    { label: "Đang xử lý", value: "Đang xử lý" },
    { label: "Đang giao", value: "Đang giao hàng" },
    { label: "Hoàn thành", value: "Đã hoàn thành" },
    { label: "Hoàn/Hoàn tiền", value: "Hoàn/Hoàn tiền" }
];

function normalizeSearch(value: string) {
    return value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLocaleLowerCase("vi-VN");
}

function matchesStatus(order: OrderRow, filterStatus: FilterStatus) {
    if (filterStatus === "Tất cả") {
        return true;
    }

    if (filterStatus === "Hoàn/Hoàn tiền") {
        return order.status === "Đã hoàn" || order.status === "Đã hoàn tiền";
    }

    return order.status === filterStatus;
}

function getStatusCount(filterStatus: FilterStatus) {
    return orders.filter((order) => matchesStatus(order, filterStatus)).length;
}

function getStatusClassName(status: OrderRow["status"]) {
    const statusClass = {
        "Đang giao hàng": styles.blue,
        "Chờ duyệt": styles.orange,
        "Đã hoàn thành": styles.green,
        "Đã hoàn": styles.lightGreen,
        "Đã hoàn tiền": styles.purple,
        "Đang xử lý": styles.blue,
        "Đã hủy": styles.red
    }[status];

    return `${styles.status} ${statusClass}`;
}

export function OrderListPage() {
    const [searchTerm, setSearchTerm] = useState("");
    const [filterStatus, setFilterStatus] = useState<FilterStatus>("Tất cả");
    const [dateFilter, setDateFilter] = useState<DateFilterKey>("ALL");
    const [currentPage, setCurrentPage] = useState(1);
    const [isFilterOpen, setIsFilterOpen] = useState(false);
    const [selectedOrderIds, setSelectedOrderIds] = useState<string[]>([]);
    const [activeOrder, setActiveOrder] = useState<OrderRow | null>(null);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(false);
    const [openActionCode, setOpenActionCode] = useState<string | null>(null);
    const today = useMemo(() => new Date(), []);

    const filteredOrders = useMemo(() => {
        const normalizedTerm = normalizeSearch(searchTerm);

        return orders.filter((order) => {
            const searchableText = normalizeSearch(
                [
                    order.code,
                    order.customerName,
                    order.customerPhone ?? ""
                ].join(" ")
            );

            return (
                matchesStatus(order, filterStatus) &&
                matchDateFilter(order.date, dateFilter, today) &&
                searchableText.includes(normalizedTerm)
            );
        });
    }, [dateFilter, filterStatus, searchTerm, today]);

    const pageSize = 10;
    const pageCount = Math.max(1, Math.ceil(filteredOrders.length / pageSize));
    const safeCurrentPage = Math.min(currentPage, pageCount);
    const paginatedOrders = filteredOrders.slice(
        (safeCurrentPage - 1) * pageSize,
        safeCurrentPage * pageSize
    );

    const selectedVisibleOrders = paginatedOrders.filter((order) =>
        selectedOrderIds.includes(order.code)
    );
    const isAllVisibleSelected =
        paginatedOrders.length > 0 &&
        selectedVisibleOrders.length === paginatedOrders.length;
    const canEditOrder = selectedOrderIds.length === 1;

    const toggleOrderSelection = (orderCode: string) => {
        setSelectedOrderIds((current) =>
            current.includes(orderCode)
                ? current.filter((code) => code !== orderCode)
                : [...current, orderCode]
        );
    };

    const toggleVisibleOrders = () => {
        setSelectedOrderIds((current) => {
            const visibleCodes = paginatedOrders.map((order) => order.code);

            if (isAllVisibleSelected) {
                return current.filter((code) => !visibleCodes.includes(code));
            }

            return Array.from(new Set([...current, ...visibleCodes]));
        });
    };

    const selectFilterStatus = (status: FilterStatus) => {
        setFilterStatus(status);
        setIsFilterOpen(false);
        setSelectedOrderIds([]);
        setCurrentPage(1);
    };

    const selectDateFilter = (filter: DateFilterKey) => {
        setDateFilter(filter);
        setSelectedOrderIds([]);
        setCurrentPage(1);
    };

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setSelectedOrderIds([]);
        setCurrentPage(1);
    };

    const handleAction = (action: string, order: OrderRow) => {
        if (action === "Xem chi tiết" || action === "Sửa đơn") {
            setActiveOrder(order);
        }

        setOpenActionCode(null);
    };

    return (
        <section
            className={`${styles.page} ${
                isTimeCollapsed ? styles.pageTimeCollapsed : ""
            }`}
        >
            <TimeSidebar
                collapsed={isTimeCollapsed}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
            />

            <div className={styles.content}>
                <div className={styles.header}>
                    <div className={styles.title}>
                        <div className={styles.breadcrumbs}>
                            <span>Đơn hàng</span>
                            <ChevronRight size={16} aria-hidden="true" />
                            <span>Tất cả đơn hàng</span>
                        </div>
                        <h1>Tất cả đơn hàng</h1>
                        <p>
                            Quản lý và theo dõi tất cả đơn hàng trong hệ thống
                        </p>
                    </div>

                    <div className={styles.headerActions}>
                        <label className={styles.searchBox}>
                            <Search size={20} aria-hidden="true" />
                            <input
                                aria-label="Tìm kiếm đơn hàng"
                                onChange={(event) =>
                                    handleSearchChange(event.target.value)
                                }
                                onKeyDown={(event) => {
                                    if (event.key === "Escape") {
                                        handleSearchChange("");
                                    }
                                }}
                                placeholder="Tìm mã đơn, khách hàng, SĐT..."
                                value={searchTerm}
                            />
                        </label>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={18} aria-hidden="true" />
                            Đặt hàng
                        </button>
                        <button
                            className={styles.secondaryButton}
                            disabled={!canEditOrder}
                            type="button"
                        >
                            <FilePenLine size={18} aria-hidden="true" />
                            Sửa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <FileSpreadsheet size={18} aria-hidden="true" />
                            Xuất file
                            <ChevronDown size={16} aria-hidden="true" />
                        </button>
                        <div className={styles.filterMenu}>
                            <button
                                aria-expanded={isFilterOpen}
                                aria-label="Lọc trạng thái đơn hàng"
                                className={`${styles.iconButton} ${
                                    filterStatus !== "Tất cả" ? styles.activeIconButton : ""
                                }`}
                                onClick={() => setIsFilterOpen((value) => !value)}
                                type="button"
                            >
                                <ListFilter size={19} aria-hidden="true" />
                            </button>
                            {isFilterOpen ? (
                                <div className={styles.filterPopover}>
                                    {statusFilters.map((status) => (
                                        <button
                                            className={`${styles.filterOption} ${
                                                filterStatus === status
                                                    ? styles.activeFilterOption
                                                    : ""
                                            }`}
                                            key={status}
                                            onClick={() => selectFilterStatus(status)}
                                            type="button"
                                        >
                                            {status}
                                        </button>
                                    ))}
                                </div>
                            ) : null}
                        </div>
                        <button className={styles.iconButton} type="button">
                            <Settings size={19} aria-hidden="true" />
                        </button>
                    </div>
                </div>

                <div
                    className={`${styles.workspace} ${
                        activeOrder ? "" : styles.workspaceExpanded
                    }`}
                >
                    <div className={styles.tablePanel}>
                        <div className={styles.filterRows}>
                            <div className={styles.filterGroup}>
                                <span className={styles.filterLabel}>
                                    Trạng thái:
                                </span>
                                <div className={styles.quickFilters}>
                                    {quickFilters.map((filter) => (
                                        <button
                                            className={`${styles.filterChip} ${
                                                filterStatus === filter.value ||
                                                (filter.value === "Hoàn/Hoàn tiền" &&
                                                    (filterStatus === "Đã hoàn" ||
                                                        filterStatus === "Đã hoàn tiền"))
                                                    ? styles.activeFilterChip
                                                    : ""
                                            }`}
                                            key={filter.value}
                                            onClick={() =>
                                                selectFilterStatus(filter.value)
                                            }
                                            type="button"
                                        >
                                            {filter.label}{" "}
                                            <span>
                                                {getStatusCount(filter.value)}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className={styles.filterGroup}>
                                <DateQuickFilter
                                    value={dateFilter}
                                    onChange={selectDateFilter}
                                />
                            </div>
                        </div>

                        {selectedOrderIds.length > 0 ? (
                            <div className={styles.bulkBar}>
                                <strong>
                                    Đã chọn {selectedOrderIds.length} đơn
                                </strong>
                                <button type="button">Xuất file</button>
                                <button type="button">In hóa đơn</button>
                                <button type="button">Cập nhật trạng thái</button>
                                <button
                                    className={styles.clearBulk}
                                    onClick={() => setSelectedOrderIds([])}
                                    type="button"
                                >
                                    Bỏ chọn tất cả
                                </button>
                            </div>
                        ) : null}

                        <div className={styles.tableWrap}>
                            <table className={styles.orderTable}>
                            <thead>
                                <tr>
                                    <th>
                                        <button
                                            aria-label="Chọn tất cả đơn hàng đang hiển thị"
                                            className={`${styles.checkbox} ${
                                                isAllVisibleSelected ? styles.checked : ""
                                            }`}
                                            onClick={toggleVisibleOrders}
                                            type="button"
                                        />
                                    </th>
                                    <th>Mã đơn hàng</th>
                                    <th>Trạng thái</th>
                                    <th>Giá trị đơn hàng</th>
                                    <th>Ngày đặt</th>
                                    <th>Nghiệp vụ</th>
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {paginatedOrders.map((order) => (
                                    <tr
                                        className={
                                            activeOrder?.code === order.code
                                                ? styles.activeRow
                                                : ""
                                        }
                                        key={order.code}
                                        onClick={() => setActiveOrder(order)}
                                    >
                                        <td>
                                            <button
                                                aria-label={`Chọn đơn hàng ${order.code}`}
                                                className={`${styles.checkbox} ${
                                                    selectedOrderIds.includes(order.code)
                                                        ? styles.checked
                                                        : ""
                                                }`}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    toggleOrderSelection(order.code);
                                                }}
                                                type="button"
                                            />
                                        </td>
                                        <td className={styles.code}>{order.code}</td>
                                        <td>
                                            <span className={getStatusClassName(order.status)}>
                                                {order.status}
                                            </span>
                                        </td>
                                        <td>
                                            <strong>{order.total}</strong>
                                        </td>
                                        <td>{order.date}</td>
                                        <td>{order.type}</td>
                                        <td className={styles.actionCell}>
                                            <button
                                                aria-expanded={openActionCode === order.code}
                                                aria-label={`Mở thao tác cho đơn ${order.code}`}
                                                className={styles.rowActionButton}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    setOpenActionCode((current) =>
                                                        current === order.code
                                                            ? null
                                                            : order.code
                                                    );
                                                }}
                                                type="button"
                                            >
                                                <MoreHorizontal
                                                    size={18}
                                                    aria-hidden="true"
                                                />
                                            </button>
                                            {openActionCode === order.code ? (
                                                <div className={styles.actionMenu}>
                                                    {[
                                                        "Xem chi tiết",
                                                        "Sửa đơn",
                                                        "In hóa đơn",
                                                        "Hủy đơn",
                                                        "Hoàn hàng"
                                                    ].map((action) => (
                                                        <button
                                                            key={action}
                                                            onClick={(event) => {
                                                                event.stopPropagation();
                                                                handleAction(action, order);
                                                            }}
                                                            type="button"
                                                        >
                                                            {action}
                                                        </button>
                                                    ))}
                                                </div>
                                            ) : null}
                                        </td>
                                    </tr>
                                ))}
                                {filteredOrders.length === 0 ? (
                                    <tr>
                                        <td className={styles.emptyCell} colSpan={7}>
                                            <strong>
                                                Không tìm thấy đơn hàng phù hợp
                                            </strong>
                                            <span>
                                                Thử đổi từ khóa hoặc bộ lọc
                                            </span>
                                        </td>
                                    </tr>
                                ) : null}
                            </tbody>
                            </table>
                        </div>

                        <div className={styles.pagination}>
                            <span>
                                Hiển thị{" "}
                                {filteredOrders.length > 0
                                    ? (safeCurrentPage - 1) * pageSize + 1
                                    : 0}{" "}
                                -{" "}
                                {Math.min(
                                    safeCurrentPage * pageSize,
                                    filteredOrders.length
                                )}{" "}
                                trong {filteredOrders.length} đơn hàng
                            </span>
                            {filteredOrders.length > 0 ? (
                                <div className={styles.pages}>
                                    <button
                                        className={styles.pageButton}
                                        disabled={safeCurrentPage === 1}
                                        onClick={() =>
                                            setCurrentPage((page) =>
                                                Math.max(1, page - 1)
                                            )
                                        }
                                        type="button"
                                    >
                                        <ChevronLeft size={18} aria-hidden="true" />
                                    </button>
                                    {Array.from(
                                        { length: Math.min(pageCount, 5) },
                                        (_, index) => index + 1
                                    ).map((page) => (
                                        <button
                                            className={`${styles.pageButton} ${
                                                page === safeCurrentPage
                                                    ? styles.activePage
                                                    : ""
                                            }`}
                                            key={page}
                                            onClick={() => setCurrentPage(page)}
                                            type="button"
                                        >
                                            {page}
                                        </button>
                                    ))}
                                    {pageCount > 5 ? <span>...</span> : null}
                                    <button
                                        className={styles.pageButton}
                                        disabled={safeCurrentPage === pageCount}
                                        onClick={() =>
                                            setCurrentPage((page) =>
                                                Math.min(pageCount, page + 1)
                                            )
                                        }
                                        type="button"
                                    >
                                        <ChevronRight size={18} aria-hidden="true" />
                                    </button>
                                </div>
                            ) : null}
                        </div>
                    </div>

                    {activeOrder ? (
                        <OrderDetailPanel
                            order={activeOrder}
                            onClose={() => setActiveOrder(null)}
                        />
                    ) : null}
                </div>
            </div>
        </section>
    );
}

function OrderDetailPanel({
    order,
    onClose
}: {
    order: OrderRow;
    onClose: () => void;
}) {
    return (
        <aside className={styles.detailPanel} aria-label="Chi tiết đơn hàng">
            <div className={styles.detailHeader}>
                <h2>Chi tiết đơn hàng</h2>
                <button
                    aria-label="Đóng chi tiết đơn hàng"
                    className={styles.closeButton}
                    onClick={onClose}
                    type="button"
                >
                    <X size={17} aria-hidden="true" />
                </button>
            </div>

            <div className={styles.detailSection}>
                <h3>Thông tin đơn</h3>
                <InfoRow label="Mã đơn hàng" value={order.code} />
                <InfoRow label="Khách hàng" value={order.customerName} />
                <InfoRow label="SĐT" value={order.customerPhone ?? "Khách lẻ"} />
                <InfoRow label="Trạng thái" value={order.status} pillClassName={getStatusClassName(order.status)} />
                <InfoRow label="Giá trị" value={order.total} />
                <InfoRow label="Ngày đặt" value={order.date} />
                <InfoRow label="Nghiệp vụ" value={order.type} />
                <InfoRow label="Nhân viên" value={order.staff} />
                <InfoRow label="Thanh toán" value={order.payment} />
            </div>

            <div className={styles.detailSection}>
                <h3>Sản phẩm</h3>
                <ul className={styles.detailList}>
                    {order.products.map((product) => (
                        <li key={product}>{product}</li>
                    ))}
                </ul>
            </div>

            <div className={styles.detailSection}>
                <h3>Lịch sử trạng thái</h3>
                <ul className={styles.detailList}>
                    {order.history.map((item) => (
                        <li key={item}>{item}</li>
                    ))}
                </ul>
            </div>
        </aside>
    );
}

function InfoRow({
    label,
    value,
    pillClassName
}: {
    label: string;
    value: string;
    pillClassName?: string;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            {pillClassName ? (
                <strong className={pillClassName}>{value}</strong>
            ) : (
                <strong>{value}</strong>
            )}
        </div>
    );
}
