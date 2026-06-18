import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Download,
    Plus,
    Printer,
    Search,
    X
} from "lucide-react";
import { Link } from "react-router-dom";

import { TimeHierarchyFilter } from "../../../components/shared/TimeHierarchyFilter";
import { routePaths } from "../../../routes/path";
import styles from "./InvoiceListPage.module.css";

interface InvoiceRow {
    invoiceCode: string;
    orderCode: string;
    customer: string;
    total: string;
    date: string;
    payment: "Đã thanh toán" | "Chờ thanh toán";
    staff: string;
}

const invoices: InvoiceRow[] = [
    ["HD250616-0001", "DH-20260601", "Nguyễn Thị Hương", "1.250.000đ", "16/06/2026 09:15", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0002", "DH-20260602", "Trần Minh Tuấn", "850.000đ", "16/06/2026 09:30", "Đã thanh toán", "Lê Hoàng Anh"],
    ["HD250616-0003", "DH-20260603", "Lê Thị Mai", "2.150.000đ", "16/06/2026 10:05", "Đã thanh toán", "Nguyễn Thu Hà"],
    ["HD250616-0004", "DH-20260604", "Phạm Văn Nam", "680.000đ", "16/06/2026 10:20", "Chờ thanh toán", "Trần Quang Huy"],
    ["HD250616-0005", "DH-20260605", "Hoàng Thị Lan", "1.450.000đ", "16/06/2026 10:45", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0006", "DH-20260606", "Đặng Văn Cường", "320.000đ", "16/06/2026 11:10", "Đã thanh toán", "Lê Hoàng Anh"],
    ["HD250616-0007", "DH-20260607", "Bùi Thị Thảo", "950.000đ", "16/06/2026 11:25", "Đã thanh toán", "Nguyễn Thu Hà"],
    ["HD250616-0008", "DH-20260608", "Nguyễn Đức Mạnh", "420.000đ", "16/06/2026 12:00", "Chờ thanh toán", "Trần Quang Huy"],
    ["HD250616-0009", "DH-20260609", "Võ Thị Hằng", "1.180.000đ", "16/06/2026 12:30", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0010", "DH-20260610", "Trịnh Văn Lâm", "750.000đ", "16/06/2026 12:15", "Đã thanh toán", "Lê Hoàng Anh"]
].map(([invoiceCode, orderCode, customer, total, date, payment, staff]) => ({
    invoiceCode,
    orderCode,
    customer,
    total,
    date,
    payment,
    staff
})) as InvoiceRow[];

const selectedInvoice = invoices[0];

export function InvoiceListPage() {
    return (
        <section className={styles.page}>
            <TimeHierarchyFilter />

            <div className={styles.content}>
                <div className={styles.header}>
                    <div className={styles.title}>
                        <div className={styles.breadcrumbs}>
                            <span>Đơn hàng</span>
                            <ChevronRight size={16} aria-hidden="true" />
                            <span className={styles.greenText}>Hóa đơn</span>
                        </div>
                        <h1>Hóa đơn</h1>
                        <p>
                            Quản lý và theo dõi tất cả hóa đơn bán trà, phụ
                            kiện và đơn giao hàng trong hệ thống.
                        </p>
                    </div>

                    <div className={styles.actions}>
                        <label className={styles.searchBox}>
                            <Search size={18} aria-hidden="true" />
                            <input
                                aria-label="Tìm kiếm hóa đơn"
                                placeholder="Tìm theo mã đơn hàng, tên khách hàng, SĐT..."
                            />
                        </label>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={17} aria-hidden="true" />
                            Tạo hóa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <Printer size={17} aria-hidden="true" />
                            In hóa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </button>
                        <TeaSetArt />
                    </div>
                </div>

                <div className={styles.body}>
                    <div className={styles.tablePanel}>
                        <div className={styles.filters}>
                            {[
                                "Trạng thái TT",
                                "Hình thức TT",
                                "Chi nhánh",
                                "Nhân viên",
                                "Khoảng tiền"
                            ].map((label) => (
                                <label
                                    className={styles.filterLabel}
                                    key={label}
                                >
                                    {label}
                                    <span className={styles.select}>
                                        Tất cả
                                        <ChevronDown
                                            size={15}
                                            aria-hidden="true"
                                        />
                                    </span>
                                </label>
                            ))}
                        </div>

                        <div className={styles.tableWrap}>
                            <table className={styles.invoiceTable}>
                                <thead>
                                    <tr>
                                        <th>
                                            <div className={styles.checkbox} />
                                        </th>
                                        <th>Mã HĐ</th>
                                        <th>Mã ĐH</th>
                                        <th>Khách hàng</th>
                                        <th>Tổng tiền</th>
                                        <th>Ngày</th>
                                        <th>Thanh toán</th>
                                        <th>Nhân viên</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {invoices.map((invoice) => (
                                        <tr key={invoice.invoiceCode}>
                                            <td>
                                                <div
                                                    className={styles.checkbox}
                                                />
                                            </td>
                                            <td>
                                                <Link
                                                    className={styles.invoiceLink}
                                                    to={routePaths.invoiceDetail.replace(
                                                        ":invoiceId",
                                                        invoice.invoiceCode
                                                    )}
                                                >
                                                    {invoice.invoiceCode}
                                                </Link>
                                            </td>
                                            <td>{invoice.orderCode}</td>
                                            <td>
                                                <strong>
                                                    {invoice.customer}
                                                </strong>
                                            </td>
                                            <td>
                                                <strong>{invoice.total}</strong>
                                            </td>
                                            <td>{invoice.date}</td>
                                            <td>
                                                <span
                                                    className={`${styles.status} ${
                                                        invoice.payment ===
                                                        "Đã thanh toán"
                                                            ? styles.paid
                                                            : styles.unpaid
                                                    }`}
                                                >
                                                    {invoice.payment}
                                                </span>
                                            </td>
                                            <td>
                                                <strong>{invoice.staff}</strong>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        <div className={styles.pagination}>
                            <span>
                                Hiển thị&nbsp;&nbsp;
                                <strong>20</strong>&nbsp;&nbsp;/ 50 / 100 dòng
                            </span>
                            <div className={styles.pageTools}>
                                <span>Trang</span>
                                <button
                                    className={styles.pageButton}
                                    type="button"
                                >
                                    <ChevronLeft
                                        size={15}
                                        aria-hidden="true"
                                    />
                                </button>
                                {[1, 2, 3, 4, 5].map((page) => (
                                    <button
                                        className={`${styles.pageButton} ${
                                            page === 1
                                                ? styles.activePage
                                                : ""
                                        }`}
                                        key={page}
                                        type="button"
                                    >
                                        {page}
                                    </button>
                                ))}
                                <span>...</span>
                                <button
                                    className={styles.pageButton}
                                    type="button"
                                >
                                    <ChevronRight
                                        size={15}
                                        aria-hidden="true"
                                    />
                                </button>
                            </div>
                        </div>
                    </div>

                    <InvoiceDetailPanel invoice={selectedInvoice} />
                </div>
            </div>
        </section>
    );
}

function InvoiceDetailPanel({ invoice }: { invoice: InvoiceRow }) {
    return (
        <aside className={styles.detailPanel} aria-label="Thông tin hóa đơn">
            <div className={styles.detailHeader}>
                <h2>Thông tin hóa đơn</h2>
                <button
                    type="button"
                    className="grid size-8 place-items-center rounded-lg text-[#536078] hover:bg-[#f4f6f8]"
                    aria-label="Đóng thông tin hóa đơn"
                >
                    <X size={16} aria-hidden="true" />
                </button>
            </div>

            <div className={styles.detailSection}>
                <h3>Thông tin chung</h3>
                <div className={styles.infoList}>
                    <InfoRow label="Mã hóa đơn" value={invoice.invoiceCode} />
                    <InfoRow label="Mã đơn hàng" value={invoice.orderCode} />
                    <InfoRow label="Khách hàng" value={invoice.customer} />
                    <InfoRow label="SĐT" value="0987 654 321" />
                    <InfoRow label="Nhân viên bán" value={invoice.staff} />
                    <InfoRow label="Chi nhánh" value="Chi nhánh trung tâm" />
                    <InfoRow label="Ngày lập" value="16/06/2026 09:15" />
                    <InfoRow
                        label="Kênh bán"
                        value="Cửa hàng"
                        valueClassName={styles.bluePill}
                    />
                    <InfoRow
                        label="Hình thức TT"
                        value="Chuyển khoản"
                        valueClassName={styles.bluePill}
                    />
                    <InfoRow label="Tổng tiền" value="1.250.000đ" />
                    <InfoRow label="Giảm giá" value="50.000đ" />
                    <InfoRow label="Khách trả" value="1.200.000đ" />
                    <InfoRow label="Phương thức thanh toán" value="QR Banking" />
                    <InfoRow
                        label="Trạng thái"
                        value="Hoàn thành"
                        valueClassName={styles.greenPill}
                    />
                </div>
            </div>

            <div className={styles.detailSection}>
                <h3>Giao hàng</h3>
                <div className={styles.infoList}>
                    <InfoRow label="Đơn vị vận chuyển" value="Giao hàng nội bộ" />
                    <InfoRow label="Người nhận" value={invoice.customer} />
                    <InfoRow
                        label="Địa chỉ nhận"
                        value="125 Nguyễn Lương Bằng, Đống Đa, Hà Nội"
                    />
                    <InfoRow label="Phí giao hàng" value="20.000đ" />
                    <InfoRow
                        label="Trạng thái giao hàng"
                        value="Đã giao"
                        valueClassName={styles.purplePill}
                    />
                </div>
            </div>

            <div className={styles.detailSection}>
                <h3>Timeline</h3>
                <div className={styles.timeline}>
                    {[
                        ["16/06/2026 09:15", "Tạo đơn", "Phạm Thu Hương"],
                        ["16/06/2026 09:16", "Xác nhận", "Phạm Thu Hương"],
                        ["16/06/2026 09:20", "Thanh toán", "Phạm Thu Hương"],
                        ["16/06/2026 10:30", "Đóng gói", "Nhân viên giao hàng"],
                        ["16/06/2026 12:45", "Hoàn thành", "Nhân viên giao hàng"]
                    ].map(([time, title, actor], index) => (
                        <div className={styles.timelineItem} key={time}>
                            <span
                                className={`${styles.dot} ${
                                    index === 3 ? styles.purpleDot : ""
                                }`}
                            />
                            <span>{time}</span>
                            <strong>
                                {title}
                                <br />
                                <span className="font-semibold text-[#536078]">
                                    {actor}
                                </span>
                            </strong>
                        </div>
                    ))}
                </div>
            </div>
        </aside>
    );
}

function InfoRow({
    label,
    value,
    valueClassName
}: {
    label: string;
    value: string;
    valueClassName?: string;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            {valueClassName ? (
                <strong className={valueClassName}>{value}</strong>
            ) : (
                <strong>{value}</strong>
            )}
        </div>
    );
}

function TeaSetArt() {
    return (
        <svg
            className={styles.heroArt}
            viewBox="0 0 164 72"
            aria-hidden="true"
        >
            <path
                d="M8 56c18-8 35-10 52-5 24-20 58-26 93-16-14 17-39 26-69 25-26 8-52 8-76-4Z"
                fill="#4c8d2b"
                opacity="0.22"
            />
            <path
                d="M100 31c12-12 32-12 43 0 8 9 6 21-7 26-16 6-38 2-44-10-3-6 0-12 8-16Z"
                fill="#244322"
            />
            <ellipse cx="118" cy="32" rx="25" ry="8" fill="#dce8db" />
            <ellipse cx="118" cy="32" rx="18" ry="5" fill="#315b2c" />
            <path
                d="M143 38c13 0 15 13 2 17"
                fill="none"
                stroke="#244322"
                strokeWidth="6"
                strokeLinecap="round"
            />
            <path
                d="M92 36c-12-6-20-3-25 6"
                fill="none"
                stroke="#244322"
                strokeWidth="6"
                strokeLinecap="round"
            />
            <ellipse cx="62" cy="52" rx="22" ry="9" fill="#244322" />
            <ellipse cx="62" cy="49" rx="17" ry="6" fill="#dce8db" />
            <ellipse cx="62" cy="49" rx="10" ry="3" fill="#315b2c" />
            <path
                d="M25 50c8-12 20-15 34-9-10 8-22 11-34 9Z"
                fill="#5c9b36"
            />
        </svg>
    );
}
