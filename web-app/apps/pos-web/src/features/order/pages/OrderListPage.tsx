import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    ClipboardList,
    FilePenLine,
    FileSpreadsheet,
    ListFilter,
    MoreHorizontal,
    Plus,
    RotateCcw,
    Search,
    Settings,
    ShoppingBag,
    WalletCards
} from "lucide-react";

import { TimeHierarchyFilter } from "../../../components/shared/TimeHierarchyFilter";
import styles from "./OrderListPage.module.css";

interface OrderRow {
    code: string;
    date: string;
    type: "Đơn hàng" | "Hoàn hàng" | "Hoàn tiền";
    total: string;
    status: "Đang giao hàng" | "Chờ duyệt" | "Đã hoàn thành" | "Đã hoàn" | "Đã hoàn tiền" | "Đang xử lý";
}

const orders: OrderRow[] = [
    ["DH-2026001", "17/06/2026", "Đơn hàng", "12.500.000đ", "Đang giao hàng"],
    ["DH-2026002", "17/06/2026", "Đơn hàng", "6.800.000đ", "Chờ duyệt"],
    ["DH-2026003", "17/06/2026", "Đơn hàng", "9.600.000đ", "Đã hoàn thành"],
    ["DH-2026004", "17/06/2026", "Hoàn hàng", "2.150.000đ", "Đã hoàn"],
    ["DH-2026005", "16/06/2026", "Đơn hàng", "7.800.000đ", "Chờ duyệt"],
    ["DH-2026006", "16/06/2026", "Hoàn tiền", "5.650.000đ", "Đã hoàn tiền"],
    ["DH-2026007", "15/06/2026", "Đơn hàng", "15.400.000đ", "Đang xử lý"],
    ["DH-2026008", "15/06/2026", "Đơn hàng", "4.250.000đ", "Đã hoàn thành"],
    ["DH-2026009", "15/06/2026", "Hoàn hàng", "550.000đ", "Đã hoàn"],
    ["DH-2026010", "15/06/2026", "Hoàn tiền", "1.200.000đ", "Đã hoàn tiền"]
].map(([code, date, type, total, status]) => ({
    code,
    date,
    type,
    total,
    status
})) as OrderRow[];

const metrics = [
    {
        label: "Tổng đơn hàng",
        value: "124",
        delta: "+15%",
        icon: ClipboardList
    },
    {
        label: "Giá trị đơn hàng",
        value: "284.500.000đ",
        delta: "+9%",
        icon: WalletCards
    },
    {
        label: "Đơn hàng",
        value: "98",
        delta: "+8%",
        icon: ShoppingBag
    },
    {
        label: "Đơn hoàn",
        value: "26",
        delta: "-5%",
        icon: RotateCcw
    }
] as const;

function getStatusClassName(status: OrderRow["status"]) {
    const statusClass = {
        "Đang giao hàng": styles.blue,
        "Chờ duyệt": styles.orange,
        "Đã hoàn thành": styles.green,
        "Đã hoàn": styles.green,
        "Đã hoàn tiền": styles.purple,
        "Đang xử lý": styles.blue
    }[status];

    return `${styles.status} ${statusClass}`;
}

export function OrderListPage() {
    return (
        <section className={styles.page}>
            <TimeHierarchyFilter />

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
                                placeholder="Tìm theo mã đơn hàng, tên người nhận, SĐT..."
                            />
                        </label>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={18} aria-hidden="true" />
                            Đặt hàng
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <FilePenLine size={18} aria-hidden="true" />
                            Sửa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <FileSpreadsheet size={18} aria-hidden="true" />
                            Xuất file
                            <ChevronDown size={16} aria-hidden="true" />
                        </button>
                        <button className={styles.iconButton} type="button">
                            <ListFilter size={19} aria-hidden="true" />
                        </button>
                        <button className={styles.iconButton} type="button">
                            <Settings size={19} aria-hidden="true" />
                        </button>
                    </div>
                </div>

                <div className={styles.metrics}>
                    {metrics.map((metric) => {
                        const Icon = metric.icon;

                        return (
                            <article className={styles.metricCard} key={metric.label}>
                                <span className={styles.metricIcon}>
                                    <Icon size={28} aria-hidden="true" />
                                </span>
                                <div>
                                    <span>{metric.label}</span>
                                    <strong>{metric.value}</strong>
                                    <small>{metric.delta} so với tháng trước</small>
                                </div>
                                <svg
                                    className={styles.sparkline}
                                    viewBox="0 0 88 42"
                                    aria-hidden="true"
                                >
                                    <path
                                        d="M2 34 L18 25 L31 12 L45 29 L61 15 L77 28 L86 12"
                                        fill="none"
                                        stroke="#159734"
                                        strokeWidth="3"
                                    />
                                </svg>
                            </article>
                        );
                    })}
                </div>

                <div className={styles.filters}>
                    {[
                        "Loại đơn hàng",
                        "Trạng thái đơn",
                        "Phương thức thanh toán",
                        "Trạng thái thanh toán"
                    ].map((label) => (
                        <label className={styles.filterLabel} key={label}>
                            {label}
                            <span className={styles.select}>
                                Tất cả
                                <ChevronDown size={17} aria-hidden="true" />
                            </span>
                        </label>
                    ))}
                    <button className={styles.reset} type="button">
                        Xóa bộ lọc
                    </button>
                    <span className={styles.showCount}>
                        Hiển thị: 20 <ChevronDown size={14} aria-hidden="true" />
                    </span>
                </div>

                <div className={styles.tablePanel}>
                    <div className={styles.dateCrumbs}>
                        <span>2026</span>
                        <span>/</span>
                        <span>Quý 2</span>
                        <span>/</span>
                        <span>Tháng 6</span>
                        <span>/</span>
                        <span>17-06-2026</span>
                    </div>

                    <div className={styles.tableWrap}>
                        <table className={styles.orderTable}>
                            <thead>
                                <tr>
                                    <th>
                                        <div className={styles.checkbox} />
                                    </th>
                                    <th>Mã đơn hàng</th>
                                    <th>Ngày đặt</th>
                                    <th>Loại đơn hàng</th>
                                    <th>Giá trị đơn hàng</th>
                                    <th>Trạng thái</th>
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {orders.map((order) => (
                                    <tr key={order.code}>
                                        <td>
                                            <div className={styles.checkbox} />
                                        </td>
                                        <td className={styles.code}>{order.code}</td>
                                        <td>{order.date}</td>
                                        <td>{order.type}</td>
                                        <td>
                                            <strong>{order.total}</strong>
                                        </td>
                                        <td>
                                            <span className={getStatusClassName(order.status)}>
                                                {order.status}
                                            </span>
                                        </td>
                                        <td>
                                            <MoreHorizontal size={18} aria-hidden="true" />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.pagination}>
                        <span>Hiển thị 1 - 10 trong 124 đơn hàng</span>
                        <div className={styles.pages}>
                            <ChevronLeft size={18} aria-hidden="true" />
                            {[1, 2, 3, 4, 5].map((page) => (
                                <button
                                    className={`${styles.pageButton} ${
                                        page === 1 ? styles.activePage : ""
                                    }`}
                                    key={page}
                                    type="button"
                                >
                                    {page}
                                </button>
                            ))}
                            <span>...</span>
                            <button className={styles.pageButton} type="button">
                                13
                            </button>
                            <ChevronRight size={18} aria-hidden="true" />
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
