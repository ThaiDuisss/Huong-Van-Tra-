import type { SaleCustomer } from "../types/sale.types";

export const mockCustomers: SaleCustomer[] = [
    {
        id: "customer-do-xuan-thai",
        name: "Đỗ Xuân Thái",
        phone: "0345 102 022",
        tier: "VIP Bạc",
        points: 1250,
        orderCount: 58,
        totalSpent: 12500000,
        avatarUrl:
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=96&q=80"
    },
    {
        id: "customer-nguyen-van-a",
        name: "Nguyễn Văn A",
        phone: "0987 654 321",
        tier: "VIP Vàng",
        points: 860,
        orderCount: 32,
        totalSpent: 8750000,
        avatarUrl:
            "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=96&q=80"
    },
    {
        id: "customer-tran-thi-b",
        name: "Trần Thị B",
        phone: "0912 345 678",
        tier: "Khách quen",
        points: 420,
        orderCount: 18,
        totalSpent: 4200000,
        avatarUrl: ""
    },
    {
        id: "customer-le-minh-cuong",
        name: "Lê Minh Cường",
        phone: "0909 111 222",
        tier: "VIP Kim Cương",
        points: 2750,
        orderCount: 85,
        totalSpent: 18600000,
        avatarUrl:
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=96&q=80"
    },
    {
        id: "customer-pham-hoang-dung",
        name: "Phạm Hoàng Dũng",
        phone: "0868 222 333",
        tier: "VIP Vàng",
        points: 1980,
        orderCount: 47,
        totalSpent: 11300000,
        avatarUrl:
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=96&q=80"
    }
];
