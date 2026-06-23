import {
    Bell,
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Download,
    Mail,
    MoreHorizontal,
    Phone,
    Plus,
    Upload,
    User,
    WalletCards
} from "lucide-react";

import { TimeHierarchyFilter } from "../../../components/shared/TimeHierarchyFilter";
import { formatCurrency } from "../../sales/services/money";
import styles from "./CustomerListPageLegacy.module.css";

interface CustomerRow {
    id: string;
    code: string;
    name: string;
    tier: "VIP Gold" | "VIP Silver" | "VIP Diamond" | "Thành viên";
    points: number;
    totalSpent: number;
    orderCount: number;
    phone: string;
    email: string;
    birthday: string;
    gender: string;
    address: string;
    source: string;
    note: string;
    avatarUrl: string;
}

const customers: CustomerRow[] = [
    {
        id: "1",
        code: "KH-000125",
        name: "Đỗ Xuân Thái",
        tier: "VIP Gold",
        points: 1250,
        totalSpent: 25600000,
        orderCount: 86,
        phone: "0987654321",
        email: "thai@gmail.com",
        birthday: "15/08/1998",
        gender: "Nam",
        address: "Tây Hồ, Hà Nội",
        source: "Facebook",
        note: "Khách hàng thân thiết, thường mua trà biếu tặng.",
        avatarUrl:
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "2",
        code: "KH-000126",
        name: "Nguyễn Minh Anh",
        tier: "VIP Silver",
        points: 550,
        totalSpent: 8500000,
        orderCount: 24,
        phone: "0912345678",
        email: "minhanh@gmail.com",
        birthday: "02/04/1995",
        gender: "Nữ",
        address: "Cầu Giấy, Hà Nội",
        source: "Cửa hàng",
        note: "Ưu tiên trà ô long và các set quà nhỏ.",
        avatarUrl:
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "3",
        code: "KH-000127",
        name: "Trần Thu Hà",
        tier: "Thành viên",
        points: 180,
        totalSpent: 2400000,
        orderCount: 8,
        phone: "0902111222",
        email: "thuha@gmail.com",
        birthday: "11/12/1992",
        gender: "Nữ",
        address: "Đống Đa, Hà Nội",
        source: "Zalo",
        note: "Hay mua trà sen theo mùa.",
        avatarUrl:
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "4",
        code: "KH-000128",
        name: "Lê Hoàng Nam",
        tier: "VIP Diamond",
        points: 2260,
        totalSpent: 32800000,
        orderCount: 112,
        phone: "0988111222",
        email: "nam@gmail.com",
        birthday: "20/03/1988",
        gender: "Nam",
        address: "Ba Đình, Hà Nội",
        source: "Giới thiệu",
        note: "Mua sỉ trà biếu doanh nghiệp.",
        avatarUrl:
            "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "5",
        code: "KH-000129",
        name: "Phạm Thu Phương",
        tier: "VIP Gold",
        points: 1680,
        totalSpent: 18750000,
        orderCount: 64,
        phone: "0966555444",
        email: "phuong@gmail.com",
        birthday: "09/09/1990",
        gender: "Nữ",
        address: "Hoàn Kiếm, Hà Nội",
        source: "Website",
        note: "Thích trà nhài và trà túi lọc.",
        avatarUrl:
            "https://images.unsplash.com/photo-1544723795-3fb6469f5b39?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "6",
        code: "KH-000130",
        name: "Võ Đức Tuấn",
        tier: "Thành viên",
        points: 120,
        totalSpent: 1950000,
        orderCount: 6,
        phone: "0909876123",
        email: "tuan@gmail.com",
        birthday: "01/01/1991",
        gender: "Nam",
        address: "Thanh Xuân, Hà Nội",
        source: "Cửa hàng",
        note: "Mua thử sản phẩm mới.",
        avatarUrl:
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "7",
        code: "KH-000131",
        name: "Ngô Thị Hằng",
        tier: "VIP Silver",
        points: 120,
        totalSpent: 2200000,
        orderCount: 28,
        phone: "0933444555",
        email: "hang@gmail.com",
        birthday: "23/07/1996",
        gender: "Nữ",
        address: "Hà Đông, Hà Nội",
        source: "Facebook",
        note: "Thường mua combo nhỏ.",
        avatarUrl:
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=120&q=80"
    },
    {
        id: "8",
        code: "KH-000132",
        name: "Đặng Minh Quân",
        tier: "VIP Gold",
        points: 1350,
        totalSpent: 21300000,
        orderCount: 70,
        phone: "0911222333",
        email: "quan@gmail.com",
        birthday: "19/05/1989",
        gender: "Nam",
        address: "Tây Hồ, Hà Nội",
        source: "Giới thiệu",
        note: "Mua trà làm quà đối tác.",
        avatarUrl:
            "https://images.unsplash.com/photo-1507591064344-4c6ce005b128?auto=format&fit=crop&w=120&q=80"
    }
];

const selectedCustomer = customers[0];

function getTierClassName(tier: CustomerRow["tier"]) {
    const tierClass = {
        "VIP Gold": styles.gold,
        "VIP Silver": styles.silver,
        "VIP Diamond": styles.diamond,
        "Thành viên": styles.member
    }[tier];

    return `${styles.tier} ${tierClass}`;
}

export function CustomerListPage() {
    return (
        <section className={styles.page}>
            <TimeHierarchyFilter />

            <div className={styles.tablePanel}>
                <div className={styles.tableToolbar}>
                    <div className={styles.dateCrumbs}>
                        <span>2026</span>
                        <span>/</span>
                        <span>Quý 2</span>
                        <span>/</span>
                        <span>Tháng 6</span>
                        <span>/</span>
                        <span>17-06-2026</span>
                    </div>
                    <div className={styles.actions}>
                        <button className={styles.primaryButton} type="button">
                            <Plus size={15} aria-hidden="true" />
                            Thêm khách hàng
                        </button>
                        <button className={styles.ghostButton} type="button">
                            <Download size={15} aria-hidden="true" />
                            Import Excel
                        </button>
                        <button className={styles.ghostButton} type="button">
                            <Upload size={15} aria-hidden="true" />
                            Xuất Excel
                        </button>
                    </div>
                </div>

                <div className={styles.tableWrap}>
                    <table className={styles.customerTable}>
                        <thead>
                            <tr>
                                <th>Avatar</th>
                                <th>Mã khách hàng</th>
                                <th>Tên khách hàng</th>
                                <th>Hạng thành viên</th>
                                <th>Trạng thái</th>
                                <th>Hành động</th>
                            </tr>
                        </thead>
                        <tbody>
                            {customers.map((customer) => (
                                <tr key={customer.id}>
                                    <td>
                                        <div className={styles.avatar}>
                                            <img
                                                src={customer.avatarUrl}
                                                alt={customer.name}
                                            />
                                        </div>
                                    </td>
                                    <td>{customer.code}</td>
                                    <td>
                                        <strong>{customer.name}</strong>
                                    </td>
                                    <td>
                                        <span
                                            className={getTierClassName(
                                                customer.tier
                                            )}
                                        >
                                            {customer.tier}
                                        </span>
                                    </td>
                                    <td>
                                        <span className={styles.status}>
                                            Đang hoạt động
                                        </span>
                                    </td>
                                    <td>
                                        <button
                                            className={styles.moreButton}
                                            type="button"
                                            aria-label={`Mở thao tác cho ${customer.name}`}
                                        >
                                            <MoreHorizontal
                                                size={16}
                                                aria-hidden="true"
                                            />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div className={styles.pagination}>
                    <span>Hiển thị 20 / 12.568 khách hàng</span>
                    <div className={styles.pages}>
                        <button className={styles.pageButton} type="button">
                            <ChevronLeft size={14} aria-hidden="true" />
                        </button>
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
                            629
                        </button>
                        <button className={styles.pageButton} type="button">
                            <ChevronRight size={14} aria-hidden="true" />
                        </button>
                    </div>
                </div>
            </div>

            <CustomerDetailPanel customer={selectedCustomer} />
        </section>
    );
}

function CustomerDetailPanel({ customer }: { customer: CustomerRow }) {
    return (
        <aside className={styles.detailPanel} aria-label="Chi tiết khách hàng">
            <div className="flex justify-end">
                <button
                    className={styles.moreButton}
                    type="button"
                    aria-label="Đóng chi tiết khách hàng"
                >
                    ×
                </button>
            </div>

            <div className={styles.detailHeader}>
                <div className={styles.detailAvatar}>
                    <img src={customer.avatarUrl} alt={customer.name} />
                </div>
                <div className="min-w-0">
                    <div className={styles.detailName}>
                        <h2>{customer.name}</h2>
                        <span className={getTierClassName(customer.tier)}>
                            {customer.tier}
                        </span>
                    </div>
                    <p className="mt-2 text-[12px] font-semibold text-[#5f6b7a]">
                        Mã khách hàng&nbsp;&nbsp;
                        <strong className="text-[#172317]">
                            {customer.code}
                        </strong>
                    </p>
                    <p className="mt-1 text-[12px] font-semibold text-[#5f6b7a]">
                        Ngày tham gia&nbsp;&nbsp;
                        <strong className="text-[#172317]">
                            15/03/2025
                        </strong>
                    </p>
                </div>
            </div>

            <div className={styles.stats}>
                <div className={styles.statCard}>
                    <span>Điểm tích lũy</span>
                    <strong>{customer.points.toLocaleString("vi-VN")}</strong>
                </div>
                <div className={styles.statCard}>
                    <span>Tổng chi tiêu</span>
                    <strong>{formatCurrency(customer.totalSpent)}</strong>
                </div>
                <div className={styles.statCard}>
                    <span>Công nợ</span>
                    <strong>0đ</strong>
                </div>
            </div>

            <div className={styles.tabs}>
                <button
                    className={`${styles.tab} ${styles.activeTab}`}
                    type="button"
                >
                    Thông tin
                </button>
                <button className={styles.tab} type="button">
                    Lịch sử mua
                </button>
                <button className={styles.tab} type="button">
                    Công nợ
                </button>
                <button className={styles.tab} type="button">
                    Loyalty
                </button>
            </div>

            <div className={styles.infoList}>
                <InfoRow icon={Phone} label="Số điện thoại" value={customer.phone} />
                <InfoRow icon={Mail} label="Email" value={customer.email} />
                <InfoRow icon={CalendarDays} label="Ngày sinh" value={customer.birthday} />
                <InfoRow icon={User} label="Giới tính" value={customer.gender} />
                <InfoRow icon={WalletCards} label="Địa chỉ" value={customer.address} />
                <InfoRow icon={Bell} label="Nguồn khách hàng" value={customer.source} />
                <InfoRow icon={MoreHorizontal} label="Ghi chú" value={customer.note} />
            </div>

            <div className="mt-auto flex items-center justify-between border-t border-[#edf0f4] pt-4 text-[12px] font-semibold">
                <span className="text-[#5f6b7a]">Trạng thái</span>
                <span className={styles.status}>Đang hoạt động</span>
            </div>
        </aside>
    );
}

function InfoRow({
    icon: Icon,
    label,
    value
}: {
    icon: typeof Phone;
    label: string;
    value: string;
}) {
    return (
        <div className={styles.infoRow}>
            <Icon size={14} aria-hidden="true" />
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}
