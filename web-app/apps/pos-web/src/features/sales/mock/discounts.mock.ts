import type { SaleDiscountOption } from "../types/sale.types";

export const mockDiscounts: SaleDiscountOption[] = [
    {
        id: "none",
        label: "Không áp dụng",
        code: "",
        type: "NONE",
        value: 0,
        description: "Không áp dụng khuyến mãi",
        status: "AVAILABLE",
        condition: "Có thể bán hàng không dùng mã giảm giá",
        minSubtotal: 200000,
        expiresIn: "Còn 3 ngày"
    },
    {
        id: "tea-10",
        label: "Giảm 10% cho hóa đơn từ 200.000đ",
        code: "HVT10",
        type: "PERCENT",
        value: 10,
        description: "Tiết kiệm tối đa theo giá trị đơn hàng",
        status: "RECOMMENDED",
        condition: "Yêu cầu tổng tiền hàng từ 200.000đ",
        minSubtotal: 200000,
        expiresIn: "Còn 3 ngày"
    },
    {
        id: "voucher-20k",
        label: "Voucher 20.000đ",
        code: "HVT20K",
        type: "AMOUNT",
        value: 20000,
        description: "Giảm trực tiếp 20.000đ cho đơn hiện tại",
        status: "AVAILABLE",
        condition: "Áp dụng cho mọi hóa đơn",
        minSubtotal: 200000,
        expiresIn: "Còn 3 ngày"
    },
    {
        id: "vip-10",
        label: "VIP10",
        code: "VIP10",
        type: "PERCENT",
        value: 10,
        description: "Giảm 10% toàn bộ đơn hàng",
        status: "AVAILABLE",
        condition: "Dành cho khách hàng thành viên",
        minSubtotal: null,
        expiresIn: "Còn 5 ngày"
    },
    {
        id: "vip-20",
        label: "VIP20",
        code: "VIP20",
        type: "PERCENT",
        value: 20,
        description: "Giảm 20% cho hóa đơn từ 500.000đ",
        status: "AVAILABLE",
        condition: "Yêu cầu tổng tiền hàng từ 500.000đ",
        minSubtotal: 500000,
        expiresIn: "Còn 4 ngày"
    },
    {
        id: "birthday",
        label: "BIRTHDAY",
        code: "BIRTHDAY",
        type: "AMOUNT",
        value: 30000,
        description: "Giảm 30.000đ cho sinh nhật",
        status: "AVAILABLE",
        condition: "Áp dụng trong tháng sinh nhật",
        minSubtotal: 30000,
        expiresIn: "Còn 5 ngày"
    },
    {
        id: "freeship-15",
        label: "FREESHIP15",
        code: "FREESHIP15",
        type: "AMOUNT",
        value: 15000,
        description: "Freeship cho đơn từ 150.000đ",
        status: "AVAILABLE",
        condition: "Yêu cầu tổng tiền hàng từ 150.000đ",
        minSubtotal: 150000,
        expiresIn: "Còn 3 ngày"
    },
    {
        id: "hvt-20",
        label: "HVT20",
        code: "HVT20",
        type: "PERCENT",
        value: 20,
        description: "Giảm 20% cho hóa đơn từ 300.000đ",
        status: "INELIGIBLE",
        condition: "Yêu cầu tổng tiền tối thiểu 300.000đ",
        minSubtotal: 300000,
        expiresIn: "Còn 2 ngày"
    },
    {
        id: "summer-2025",
        label: "SUMMER2025",
        code: "SUMMER2025",
        type: "PERCENT",
        value: 15,
        description: "Giảm 15% toàn bộ đơn hàng",
        status: "EXPIRED",
        condition: "Chương trình đã hết hạn",
        minSubtotal: null,
        expiresIn: "Đã hết hạn"
    }
];
