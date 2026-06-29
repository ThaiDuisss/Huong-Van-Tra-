import type { SalesHistoryOrder } from "../types/sales-history.types";

export const mockSalesHistoryOrders: SalesHistoryOrder[] = [
    {
        orderId: "7d64c7f1-2f81-4dc2-962e-62a8a89a1a01",
        orderCode: "HD00025",
        createdAt: "2026-06-29T08:22:00+07:00",
        customerName: "Khách lẻ",
        customerPhone: null,
        cashierName: "Đỗ Xuân Thái",
        orderStatus: "COMPLETED",
        paymentStatus: "PAID",
        paymentMethod: "CASH",
        totalItems: 3,
        totalAmount: 240000,
        discountAmount: 20000,
        finalAmount: 220000,
        paidAmount: 250000,
        changeAmount: 30000,
        debtAmount: 0,
        items: [
            {
                name: "Trà ô long 100g",
                sku: "TRA_OLONG_100G",
                quantity: 2,
                unitPrice: 80000,
                lineTotal: 160000
            },
            {
                name: "Hộp quà giấy kraft",
                sku: "HOP_QUA_KRAFT",
                quantity: 1,
                unitPrice: 80000,
                lineTotal: 80000
            }
        ]
    },
    {
        orderId: "0c2b8d92-7b56-4886-8896-207f6cc9e9e2",
        orderCode: "HD00026",
        createdAt: "2026-06-29T09:48:00+07:00",
        customerName: "Nguyễn Mai Anh",
        customerPhone: "0908123456",
        cashierName: "Đỗ Xuân Thái",
        orderStatus: "COMPLETED",
        paymentStatus: "PAID",
        paymentMethod: "BANK_TRANSFER",
        totalItems: 5,
        totalAmount: 570000,
        discountAmount: 50000,
        finalAmount: 520000,
        paidAmount: 520000,
        changeAmount: 0,
        debtAmount: 0,
        items: [
            {
                name: "Trà sen Tây Hồ 100g",
                sku: "TRA_SEN_100G",
                quantity: 2,
                unitPrice: 150000,
                lineTotal: 300000
            },
            {
                name: "Trà lài 200g",
                sku: "TRA_LAI_200G",
                quantity: 1,
                unitPrice: 180000,
                lineTotal: 180000
            },
            {
                name: "Túi lọc trà giấy",
                sku: "TUI_LOC_TRA",
                quantity: 2,
                unitPrice: 45000,
                lineTotal: 90000
            }
        ]
    },
    {
        orderId: "b3135f77-7e81-4ba7-a2b3-2fb0d8f8b9c5",
        orderCode: "HD00027",
        createdAt: "2026-06-29T11:15:00+07:00",
        customerName: "Công ty An Nhiên",
        customerPhone: "02839990001",
        cashierName: "Trần Minh Huy",
        orderStatus: "PENDING_PAYMENT",
        paymentStatus: "UNPAID",
        paymentMethod: "DEBT",
        totalItems: 7,
        totalAmount: 1250000,
        discountAmount: 80000,
        finalAmount: 1170000,
        paidAmount: 0,
        changeAmount: 0,
        debtAmount: 1170000,
        items: [
            {
                name: "Set trà biếu Hương Vân",
                sku: "SET_HVT_BIEU",
                quantity: 3,
                unitPrice: 320000,
                lineTotal: 960000
            },
            {
                name: "Trà gừng mật ong",
                sku: "TRA_GUNG_MAT_ONG",
                quantity: 4,
                unitPrice: 72500,
                lineTotal: 290000
            }
        ]
    },
    {
        orderId: "74f5d99f-eaef-4e14-b5ed-ef4cdb4ef1d1",
        orderCode: "HD00028",
        createdAt: "2026-06-29T13:36:00+07:00",
        customerName: "Khách lẻ",
        customerPhone: null,
        cashierName: "Trần Minh Huy",
        orderStatus: "CANCELLED",
        paymentStatus: "CANCELLED",
        paymentMethod: "CASH",
        totalItems: 2,
        totalAmount: 160000,
        discountAmount: 0,
        finalAmount: 0,
        paidAmount: 0,
        changeAmount: 0,
        debtAmount: 0,
        items: [
            {
                name: "Trà đào cam sả túi 10 gói",
                sku: "TRA_DAO_CAM_SA_10",
                quantity: 2,
                unitPrice: 80000,
                lineTotal: 160000
            }
        ]
    },
    {
        orderId: "51f90bb5-718b-49e6-9fdb-28e55359887b",
        orderCode: "HD00029",
        createdAt: "2026-06-29T15:05:00+07:00",
        customerName: "Lê Quốc Bảo",
        customerPhone: "0911777888",
        cashierName: "Đỗ Xuân Thái",
        orderStatus: "COMPLETED",
        paymentStatus: "PAID",
        paymentMethod: "CASH",
        totalItems: 4,
        totalAmount: 390000,
        discountAmount: 0,
        finalAmount: 390000,
        paidAmount: 400000,
        changeAmount: 10000,
        debtAmount: 0,
        items: [
            {
                name: "Trà shan tuyết cổ thụ 100g",
                sku: "TRA_SHAN_100G",
                quantity: 1,
                unitPrice: 260000,
                lineTotal: 260000
            },
            {
                name: "Bình pha trà thủy tinh",
                sku: "BINH_TRA_TT",
                quantity: 1,
                unitPrice: 95000,
                lineTotal: 95000
            },
            {
                name: "Muỗng gỗ trà đạo",
                sku: "MUONG_GO_TRA",
                quantity: 2,
                unitPrice: 17500,
                lineTotal: 35000
            }
        ]
    }
];
