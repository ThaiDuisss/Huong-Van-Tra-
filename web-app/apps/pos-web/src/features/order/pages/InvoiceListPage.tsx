import {
    ChevronLeft,
    ChevronRight,
    Download,
    MoreHorizontal,
    Plus,
    Printer,
    Search,
    X
} from "lucide-react";
import { useMemo, useState } from "react";

import { DateQuickFilter } from "../../../components/shared/DateQuickFilter";
import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    matchDateFilter,
    matchYearFilter,
    type DateFilterKey
} from "../../../components/shared/dateFilters";
import styles from "./InvoiceListPage.module.css";

interface InvoiceRow {
    invoiceCode: string;
    orderCode: string;
    customer: string;
    phone: string;
    total: string;
    date: string;
    payment: "Đã thanh toán" | "Chờ thanh toán" | "Chưa thanh toán";
    staff: string;
}

type PaymentFilterStatus = "ALL" | "PAID" | "UNPAID";

const invoices: InvoiceRow[] = [
    ["HD250616-0001", "DH-20260601", "Nguyễn Thị Hương", "0987 654 321", "1.250.000đ", "16/06/2026 09:15", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0002", "DH-20260602", "Trần Minh Tuấn", "0912 345 678", "850.000đ", "16/06/2026 09:30", "Đã thanh toán", "Lê Hoàng Anh"],
    ["HD250616-0003", "DH-20260603", "Lê Thị Mai", "0904 112 233", "2.150.000đ", "16/06/2026 10:05", "Đã thanh toán", "Nguyễn Thu Hà"],
    ["HD250616-0004", "DH-20260604", "Phạm Văn Nam", "0936 778 899", "680.000đ", "16/06/2026 10:20", "Chờ thanh toán", "Trần Quang Huy"],
    ["HD250616-0005", "DH-20260605", "Hoàng Thị Lan", "0933 456 789", "1.450.000đ", "16/06/2026 10:45", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0006", "DH-20260606", "Đặng Văn Cường", "0977 888 999", "320.000đ", "16/06/2026 11:10", "Đã thanh toán", "Lê Hoàng Anh"],
    ["HD250616-0007", "DH-20260607", "Bùi Thị Thảo", "0966 121 212", "950.000đ", "16/06/2026 11:25", "Đã thanh toán", "Nguyễn Thu Hà"],
    ["HD250616-0008", "DH-20260608", "Nguyễn Đức Mạnh", "0945 667 788", "420.000đ", "16/06/2026 12:00", "Chờ thanh toán", "Trần Quang Huy"],
    ["HD250616-0009", "DH-20260609", "Võ Thị Hằng", "0981 222 333", "1.180.000đ", "16/06/2026 12:30", "Đã thanh toán", "Phạm Thu Hương"],
    ["HD250616-0010", "DH-20260610", "Trịnh Văn Lâm", "0922 444 555", "750.000đ", "16/06/2026 12:15", "Đã thanh toán", "Lê Hoàng Anh"]
].map(([invoiceCode, orderCode, customer, phone, total, date, payment, staff]) => ({
    invoiceCode,
    orderCode,
    customer,
    phone,
    total,
    date,
    payment,
    staff
})) as InvoiceRow[];

function normalizeSearch(value: string) {
    return value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLocaleLowerCase("vi-VN");
}

const paymentFilters: { label: string; value: PaymentFilterStatus }[] = [
    { label: "Tất cả", value: "ALL" },
    { label: "Đã thanh toán", value: "PAID" },
    { label: "Chưa thanh toán", value: "UNPAID" }
];

function matchesPaymentFilter(
    invoice: InvoiceRow,
    filterStatus: PaymentFilterStatus
) {
    if (filterStatus === "ALL") {
        return true;
    }

    if (filterStatus === "PAID") {
        return invoice.payment === "Đã thanh toán";
    }

    return (
        invoice.payment === "Chờ thanh toán" ||
        invoice.payment === "Chưa thanh toán"
    );
}

export function InvoiceListPage() {
    const [searchTerm, setSearchTerm] = useState("");
    const [filterPaymentStatus, setFilterPaymentStatus] =
        useState<PaymentFilterStatus>("ALL");
    const [dateFilter, setDateFilter] = useState<DateFilterKey>("ALL");
    const [selectedTimeYear, setSelectedTimeYear] = useState<number | null>(
        null
    );
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [currentPage, setCurrentPage] = useState(1);
    const [selectedInvoice, setSelectedInvoice] = useState<InvoiceRow | null>(
        null
    );
    const [selectedInvoiceCodes, setSelectedInvoiceCodes] = useState<string[]>(
        []
    );
    const [openActionCode, setOpenActionCode] = useState<string | null>(null);

    const filteredInvoices = useMemo(() => {
        const normalizedTerm = normalizeSearch(searchTerm);

        return invoices.filter((invoice) =>
            matchesPaymentFilter(invoice, filterPaymentStatus) &&
            matchDateFilter(invoice.date, dateFilter) &&
            matchYearFilter(invoice.date, selectedTimeYear) &&
            normalizeSearch(
                [
                    invoice.invoiceCode,
                    invoice.orderCode,
                    invoice.customer,
                    invoice.phone
                ].join(" ")
            ).includes(normalizedTerm)
        );
    }, [dateFilter, filterPaymentStatus, searchTerm, selectedTimeYear]);

    const pageSize = 20;
    const pageCount = Math.max(1, Math.ceil(filteredInvoices.length / pageSize));
    const safeCurrentPage = Math.min(currentPage, pageCount);
    const paginatedInvoices = filteredInvoices.slice(
        (safeCurrentPage - 1) * pageSize,
        safeCurrentPage * pageSize
    );

    const summary = useMemo(
        () => ({
            total: invoices.length,
            paid: invoices.filter(
                (invoice) => invoice.payment === "Đã thanh toán"
            ).length,
            unpaid: invoices.filter(
                (invoice) => invoice.payment === "Chờ thanh toán"
            ).length
        }),
        []
    );

    const selectedVisibleInvoices = paginatedInvoices.filter((invoice) =>
        selectedInvoiceCodes.includes(invoice.invoiceCode)
    );
    const isAllVisibleSelected =
        paginatedInvoices.length > 0 &&
        selectedVisibleInvoices.length === paginatedInvoices.length;
    const canPrintInvoice = selectedInvoiceCodes.length === 1;

    const toggleInvoiceSelection = (invoiceCode: string) => {
        setSelectedInvoiceCodes((current) =>
            current.includes(invoiceCode)
                ? current.filter((code) => code !== invoiceCode)
                : [...current, invoiceCode]
        );
    };

    const toggleVisibleInvoices = () => {
        setSelectedInvoiceCodes((current) => {
            const visibleCodes = paginatedInvoices.map(
                (invoice) => invoice.invoiceCode
            );

            if (isAllVisibleSelected) {
                return current.filter((code) => !visibleCodes.includes(code));
            }

            return Array.from(new Set([...current, ...visibleCodes]));
        });
    };

    const handleAction = (action: string, invoice: InvoiceRow) => {
        if (action === "Xem chi tiết") {
            setSelectedInvoice(invoice);
        }

        setOpenActionCode(null);
    };

    const selectPaymentFilter = (status: PaymentFilterStatus) => {
        setFilterPaymentStatus(status);
        setCurrentPage(1);
    };

    const selectDateFilter = (status: DateFilterKey) => {
        setDateFilter(status);
        setSelectedInvoiceCodes([]);
        setCurrentPage(1);
    };

    const selectTimeYear = (year: number) => {
        setSelectedTimeYear((currentYear) =>
            currentYear === year ? null : year
        );
        setSelectedInvoiceCodes([]);
        setCurrentPage(1);
    };

    return (
        <section
            className={`${styles.page} ${
                isTimeCollapsed ? styles.pageTimeCollapsed : ""
            }`}
        >
            <TimeSidebar
                collapsed={isTimeCollapsed}
                selectedYear={selectedTimeYear}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
                onYearChange={selectTimeYear}
            />

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
                                onChange={(event) => {
                                    setSearchTerm(event.target.value);
                                    setCurrentPage(1);
                                }}
                                placeholder="Tìm mã HĐ, mã ĐH, khách hàng, SĐT..."
                                value={searchTerm}
                            />
                        </label>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={17} aria-hidden="true" />
                            Tạo thủ công
                        </button>
                        <button
                            className={styles.secondaryButton}
                            disabled={!canPrintInvoice}
                            type="button"
                        >
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

                <div
                    className={`${styles.body} ${
                        selectedInvoice ? "" : styles.bodyExpanded
                    }`}
                >
                    <div className={styles.tablePanel}>
                        <div className={styles.summary}>
                            <div className={styles.filterStack}>
                                <div className={styles.filterGroup}>
                                    <span className={styles.filterLabel}>
                                        Thanh toán:
                                    </span>
                                    <div className={styles.paymentFilters}>
                                        {paymentFilters.map((filter) => (
                                            <button
                                                className={`${styles.paymentChip} ${
                                                    filterPaymentStatus ===
                                                    filter.value
                                                        ? styles.activePaymentChip
                                                        : ""
                                                }`}
                                                key={filter.value}
                                                onClick={() =>
                                                    selectPaymentFilter(
                                                        filter.value
                                                    )
                                                }
                                                type="button"
                                            >
                                                {filter.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>

                                <DateQuickFilter
                                    value={dateFilter}
                                    onChange={selectDateFilter}
                                />
                            </div>
                            <span className={styles.summaryText}>
                                Tổng: {summary.total} hóa đơn · Đã thanh toán:{" "}
                                {summary.paid} · Chờ thanh toán:{" "}
                                {summary.unpaid}
                            </span>
                        </div>

                        <div className={styles.tableWrap}>
                            <table className={styles.invoiceTable}>
                                <thead>
                                    <tr>
                                        <th>
                                            <button
                                                aria-label="Chọn tất cả hóa đơn đang hiển thị"
                                                className={`${styles.checkbox} ${
                                                    isAllVisibleSelected
                                                        ? styles.checked
                                                        : ""
                                                }`}
                                                onClick={toggleVisibleInvoices}
                                                type="button"
                                            />
                                        </th>
                                        <th>Mã HĐ</th>
                                        <th>Thanh toán</th>
                                        <th>Tổng tiền</th>
                                        <th>Khách hàng</th>
                                        <th>Ngày</th>
                                        <th>Mã ĐH</th>
                                        <th>Nhân viên</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {paginatedInvoices.map((invoice) => (
                                        <tr
                                            className={
                                                selectedInvoice?.invoiceCode ===
                                                invoice.invoiceCode
                                                    ? styles.activeRow
                                                    : ""
                                            }
                                            key={invoice.invoiceCode}
                                            onClick={() =>
                                                setSelectedInvoice(invoice)
                                            }
                                        >
                                            <td>
                                                <button
                                                    aria-label={`Chọn hóa đơn ${invoice.invoiceCode}`}
                                                    className={`${styles.checkbox} ${
                                                        selectedInvoiceCodes.includes(
                                                            invoice.invoiceCode
                                                        )
                                                            ? styles.checked
                                                            : ""
                                                    }`}
                                                    onClick={(event) => {
                                                        event.stopPropagation();
                                                        toggleInvoiceSelection(
                                                            invoice.invoiceCode
                                                        );
                                                    }}
                                                    type="button"
                                                />
                                            </td>
                                            <td>
                                                <button
                                                    className={styles.invoiceLink}
                                                    type="button"
                                                >
                                                    {invoice.invoiceCode}
                                                </button>
                                            </td>
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
                                                <strong>{invoice.total}</strong>
                                            </td>
                                            <td>
                                                <strong>
                                                    {invoice.customer}
                                                </strong>
                                            </td>
                                            <td>{invoice.date}</td>
                                            <td>{invoice.orderCode}</td>
                                            <td>
                                                <strong>{invoice.staff}</strong>
                                            </td>
                                            <td className={styles.actionCell}>
                                                <button
                                                    aria-expanded={
                                                        openActionCode ===
                                                        invoice.invoiceCode
                                                    }
                                                    aria-label={`Mở thao tác cho hóa đơn ${invoice.invoiceCode}`}
                                                    className={
                                                        styles.rowActionButton
                                                    }
                                                    onClick={(event) => {
                                                        event.stopPropagation();
                                                        setOpenActionCode(
                                                            (current) =>
                                                                current ===
                                                                invoice.invoiceCode
                                                                    ? null
                                                                    : invoice.invoiceCode
                                                        );
                                                    }}
                                                    type="button"
                                                >
                                                    <MoreHorizontal
                                                        size={17}
                                                        aria-hidden="true"
                                                    />
                                                </button>
                                                {openActionCode ===
                                                invoice.invoiceCode ? (
                                                    <div
                                                        className={
                                                            styles.actionMenu
                                                        }
                                                    >
                                                        {[
                                                            "Xem chi tiết",
                                                            "In hóa đơn",
                                                            "Tải PDF",
                                                            "Gửi hóa đơn",
                                                            "Hoàn tiền"
                                                        ].map((action) => (
                                                            <button
                                                                key={action}
                                                                onClick={(
                                                                    event
                                                                ) => {
                                                                    event.stopPropagation();
                                                                    handleAction(
                                                                        action,
                                                                        invoice
                                                                    );
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
                                    {filteredInvoices.length === 0 ? (
                                        <tr>
                                            <td
                                                className={styles.emptyCell}
                                                colSpan={9}
                                            >
                                                <strong>
                                                    Không tìm thấy hóa đơn phù hợp
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
                                <strong>
                                    {filteredInvoices.length > 0
                                        ? (safeCurrentPage - 1) * pageSize + 1
                                        : 0}
                                    {" - "}
                                    {Math.min(
                                        safeCurrentPage * pageSize,
                                        filteredInvoices.length
                                    )}
                                </strong>{" "}
                                trong {filteredInvoices.length} dòng
                            </span>
                            <div className={styles.pageTools}>
                                <span>Trang</span>
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
                                    <ChevronLeft
                                        size={15}
                                        aria-hidden="true"
                                    />
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
                                    <ChevronRight
                                        size={15}
                                        aria-hidden="true"
                                    />
                                </button>
                            </div>
                        </div>
                    </div>

                    {selectedInvoice ? (
                        <InvoiceDetailPanel
                            invoice={selectedInvoice}
                            onClose={() => setSelectedInvoice(null)}
                        />
                    ) : null}
                </div>
            </div>
        </section>
    );
}

function InvoiceDetailPanel({
    invoice,
    onClose
}: {
    invoice: InvoiceRow;
    onClose: () => void;
}) {
    return (
        <aside className={styles.detailPanel} aria-label="Thông tin hóa đơn">
            <div className={styles.detailHeader}>
                <h2>Thông tin hóa đơn</h2>
                <button
                    type="button"
                    className="grid size-8 place-items-center rounded-lg text-[#536078] hover:bg-[#f4f6f8]"
                    aria-label="Đóng thông tin hóa đơn"
                    onClick={onClose}
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
                    <InfoRow label="SĐT" value={invoice.phone} />
                    <InfoRow label="Nhân viên bán" value={invoice.staff} />
                    <InfoRow label="Chi nhánh" value="Chi nhánh trung tâm" />
                    <InfoRow label="Ngày lập" value={invoice.date} />
                    <InfoRow
                        label="Kênh bán"
                        value="Cửa hàng"
                        valueClassName={styles.bluePill}
                    />
                </div>
            </div>

            <div className={styles.detailSection}>
                <h3>Thanh toán</h3>
                <div className={styles.infoList}>
                    <InfoRow
                        label="Hình thức TT"
                        value="Chuyển khoản"
                        valueClassName={styles.bluePill}
                    />
                    <InfoRow label="Tổng tiền" value={invoice.total} />
                    <InfoRow label="Giảm giá" value="50.000đ" />
                    <InfoRow label="Khách trả" value="1.200.000đ" />
                    <InfoRow label="Phương thức thanh toán" value="QR Banking" />
                    <InfoRow
                        label="Trạng thái"
                        value={invoice.payment}
                        valueClassName={
                            invoice.payment === "Đã thanh toán"
                                ? styles.greenPill
                                : styles.bluePill
                        }
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
