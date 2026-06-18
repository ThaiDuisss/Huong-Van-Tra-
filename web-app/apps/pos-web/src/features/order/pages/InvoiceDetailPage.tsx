import {
    Box,
    CalendarCheck,
    CheckCircle2,
    ChevronRight,
    FileText,
    Mail,
    MapPin,
    Package,
    Phone,
    Printer,
    Search,
    Send,
    User,
    WalletCards
} from "lucide-react";

import styles from "./InvoiceDetailPage.module.css";

const products = [
    ["🍵", "TRA-SEN-100", "Trà Sen Tây Hồ", "Gói 100g", "125.000đ", "2", "0đ", "250.000đ"],
    ["🫖", "TRA-OLONG-100", "Trà Ô Long", "Gói 100g", "150.000đ", "1", "0đ", "150.000đ"],
    ["🛍", "PHU-KIEN-001", "Túi quà cao cấp", "Cẩm", "50.000đ", "1", "0đ", "50.000đ"],
    ["🍃", "TRA-THAI-100", "Trà Nhài", "Gói 100g", "25.000đ", "1", "0đ", "25.000đ"]
] as const;

export function InvoiceDetailPage() {
    return (
        <section className={styles.page}>
            <div className={styles.main}>
                <div className={styles.header}>
                    <div>
                        <div className={styles.breadcrumbs}>
                            <span>Đơn hàng</span>
                            <ChevronRight size={15} aria-hidden="true" />
                            <span>Hóa đơn</span>
                            <ChevronRight size={15} aria-hidden="true" />
                            <strong>Chi tiết hóa đơn</strong>
                        </div>
                        <div className={styles.title}>
                            <FileText size={25} aria-hidden="true" />
                            <h1>Chi tiết hóa đơn</h1>
                        </div>
                    </div>

                    <div className={styles.actions}>
                        <label className={styles.searchBox}>
                            <Search size={18} aria-hidden="true" />
                            <input
                                aria-label="Tìm kiếm trong hóa đơn"
                                placeholder="Tìm theo mã đơn hàng, tên khách hàng, SĐT..."
                            />
                        </label>
                        <button className={styles.secondaryButton} type="button">
                            <Printer size={17} aria-hidden="true" />
                            In hóa đơn
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <FileText size={17} aria-hidden="true" />
                            Xuất PDF
                        </button>
                        <button className={styles.secondaryButton} type="button">
                            <Send size={17} aria-hidden="true" />
                            Gửi Email
                        </button>
                    </div>
                </div>

                <div className={styles.summary}>
                    <SummaryCard
                        icon={CalendarCheck}
                        label="Giá trị hóa đơn"
                        value="425.000đ"
                    />
                    <SummaryCard icon={Box} label="Số sản phẩm" value="4" />
                    <SummaryCard
                        icon={CheckCircle2}
                        label="Trạng thái"
                        value="Hoàn thành"
                        badge
                    />
                </div>

                <section className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <FileText size={18} aria-hidden="true" />
                        1. Thông tin hóa đơn
                    </div>
                    <div className={styles.infoGrid}>
                        <InfoItem label="Mã hóa đơn" value="HD-202600601" green />
                        <InfoItem label="Ngày tạo" value="05/05/2026 14:20" />
                        <InfoItem label="Chi nhánh" value="Chi nhánh trung tâm" />
                        <InfoItem label="Nhân viên bán hàng" value="Nguyễn Văn A" />
                        <InfoItem label="Kênh bán" value="Bán tại cửa hàng" />
                        <InfoItem label="Trạng thái đơn" value="Hoàn thành" pill />
                    </div>
                </section>

                <section className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <Package size={18} aria-hidden="true" />
                        2. Danh sách sản phẩm
                    </div>
                    <table className={styles.products}>
                        <thead>
                            <tr>
                                <th>Ảnh</th>
                                <th>SKU</th>
                                <th>Tên sản phẩm</th>
                                <th>Đơn giá</th>
                                <th>Số lượng</th>
                                <th>Giảm giá</th>
                                <th>Thành tiền</th>
                            </tr>
                        </thead>
                        <tbody>
                            {products.map((product) => (
                                <tr key={product[1]}>
                                    <td>
                                        <span className={styles.productThumb}>
                                            {product[0]}
                                        </span>
                                    </td>
                                    <td>{product[1]}</td>
                                    <td>
                                        <strong>{product[2]}</strong>
                                        <br />
                                        <span className="text-[#536078]">
                                            {product[3]}
                                        </span>
                                    </td>
                                    <td>{product[4]}</td>
                                    <td>{product[5]}</td>
                                    <td>{product[6]}</td>
                                    <td>
                                        <strong>{product[7]}</strong>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <div className={styles.totals}>
                        <TotalRow label="Tổng tiền hàng" value="450.000đ" />
                        <TotalRow label="Giảm giá" value="25.000đ" green />
                        <TotalRow label="Thuế VAT (0%)" value="0đ" />
                        <TotalRow
                            label="Tổng thanh toán"
                            value="425.000đ"
                            grand
                        />
                    </div>
                </section>

                <section className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <WalletCards size={18} aria-hidden="true" />
                        3. Thông tin giao hàng
                    </div>
                    <div className={styles.infoGrid}>
                        <InfoItem label="Người nhận" value="Đỗ Xuân Thái" />
                        <InfoItem label="SĐT" value="0987654321" />
                        <InfoItem
                            label="Địa chỉ giao hàng"
                            value="12 Thích Sứ, Tây Hồ, Hà Nội"
                        />
                        <InfoItem
                            label="Phương thức giao hàng"
                            value="Giao hàng nội bộ"
                        />
                        <InfoItem label="Trạng thái giao hàng" value="Đã giao" pill />
                    </div>
                </section>
            </div>

            <aside className={styles.side}>
                <CustomerPanel />
                <PaymentPanel />
            </aside>
        </section>
    );
}

function SummaryCard({
    icon: Icon,
    label,
    value,
    badge = false
}: {
    icon: typeof CalendarCheck;
    label: string;
    value: string;
    badge?: boolean;
}) {
    return (
        <article className={styles.summaryCard}>
            <span className={styles.summaryIcon}>
                <Icon size={28} aria-hidden="true" />
            </span>
            <div>
                <span>{label}</span>
                {badge ? (
                    <strong>
                        <span className={styles.greenPill}>{value}</span>
                    </strong>
                ) : (
                    <strong>{value}</strong>
                )}
            </div>
        </article>
    );
}

function InfoItem({
    label,
    value,
    green = false,
    pill = false
}: {
    label: string;
    value: string;
    green?: boolean;
    pill?: boolean;
}) {
    return (
        <div className={styles.infoItem}>
            <span>{label}</span>
            <strong
                className={
                    pill
                        ? styles.greenPill
                        : green
                          ? styles.greenPill
                          : undefined
                }
            >
                {value}
            </strong>
        </div>
    );
}

function TotalRow({
    label,
    value,
    green = false,
    grand = false
}: {
    label: string;
    value: string;
    green?: boolean;
    grand?: boolean;
}) {
    return (
        <div className={styles.totalRow}>
            <span>{label}</span>
            <strong className={grand ? styles.grandTotal : green ? styles.greenText : ""}>
                {value}
            </strong>
        </div>
    );
}

function CustomerPanel() {
    return (
        <section className={styles.sideCard}>
            <div className={styles.customerHeader}>
                <div className={styles.avatar}>
                    <img
                        src="https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=120&q=80"
                        alt="Đỗ Xuân Thái"
                    />
                </div>
                <div className="min-w-0">
                    <div className={styles.customerName}>
                        <h2>Đỗ Xuân Thái</h2>
                        <span className={styles.pill}>VIP Gold</span>
                    </div>
                    <p className="mt-2 text-[12px] font-bold text-[#536078]">
                        Mã khách hàng: KH-002525
                    </p>
                    <p className="mt-1 text-[12px] font-bold text-[#536078]">
                        Ngày tham gia: 15/03/2025
                    </p>
                </div>
            </div>
            <div className={styles.stats}>
                <div className={styles.stat}>
                    Điểm tích lũy
                    <strong>1.250</strong>
                </div>
                <div className={styles.stat}>
                    Tổng chi tiêu
                    <strong>25.600.000đ</strong>
                </div>
                <div className={styles.stat}>
                    Công nợ
                    <strong>0đ</strong>
                </div>
            </div>
            <div className={styles.tabs}>
                <span className={`${styles.tab} ${styles.activeTab}`}>
                    Thông tin
                </span>
                <span className={styles.tab}>Lịch sử mua</span>
                <span className={styles.tab}>Công nợ</span>
                <span className={styles.tab}>Loyalty</span>
            </div>
            <div className={styles.sideInfo}>
                <SideInfo icon={Phone} label="Số điện thoại" value="0987654321" />
                <SideInfo icon={Mail} label="Email" value="thai@gmail.com" />
                <SideInfo icon={User} label="Ngày sinh" value="15/03/1989" />
                <SideInfo icon={User} label="Giới tính" value="Nam" />
                <SideInfo icon={MapPin} label="Địa chỉ" value="Tây Hồ, Hà Nội" />
                <SideInfo icon={User} label="Nguồn khách hàng" value="Facebook" />
                <SideInfo
                    icon={FileText}
                    label="Ghi chú"
                    value="Khách hàng thân thiết, thích mua trà biếu tặng."
                />
            </div>
            <div className="mt-4 flex items-center justify-between border-t border-[#edf0f4] pt-4 text-[12px] font-bold">
                <span className="text-[#536078]">Trạng thái</span>
                <span className={styles.greenPill}>Đang hoạt động</span>
            </div>
        </section>
    );
}

function PaymentPanel() {
    return (
        <section className={styles.sideCard}>
            <h2 className="mb-4 text-[16px] font-black text-[#172317]">
                Thông tin thanh toán
            </h2>
            <div className={styles.sideInfo}>
                <InfoRow label="Phương thức" value="QR Banking" />
                <InfoRow label="Trạng thái" value="Đã thanh toán" pill />
                <InfoRow label="Ngày thanh toán" value="05/05/2026 14:25" />
                <InfoRow label="Mã giao dịch" value="TXN-202605051425" />
                <InfoRow label="Người thu tiền" value="Nguyễn Văn A" />
            </div>
        </section>
    );
}

function SideInfo({
    icon: Icon,
    label,
    value
}: {
    icon: typeof Phone;
    label: string;
    value: string;
}) {
    return (
        <div className={styles.sideInfoRow}>
            <Icon size={14} aria-hidden="true" />
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}

function InfoRow({
    label,
    value,
    pill = false
}: {
    label: string;
    value: string;
    pill?: boolean;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            <strong className={pill ? styles.bluePill : ""}>{value}</strong>
        </div>
    );
}
