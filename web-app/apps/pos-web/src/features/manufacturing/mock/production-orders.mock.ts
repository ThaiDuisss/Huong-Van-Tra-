import type { BomSummaryMetric } from "../types/bom.types";
import type { ProductionOrderListItem } from "../types/production-order.types";

const teaBoxImage =
    "https://images.unsplash.com/photo-1607006483224-85bca6caf942?auto=format&fit=crop&w=160&q=80";
const teaCakeImage =
    "https://images.unsplash.com/photo-1567922045116-2a00fae2ed03?auto=format&fit=crop&w=160&q=80";
const giftSetImage =
    "https://images.unsplash.com/photo-1513475382585-d06e58bcb0e0?auto=format&fit=crop&w=160&q=80";

export const productionOrderMetrics: BomSummaryMetric[] = [
    {
        id: "running",
        label: "Lệnh đang thực hiện",
        value: "18",
        change: "+20%",
        tone: "green",
        trend: [3, 5, 4, 6, 5, 8, 6]
    },
    {
        id: "today",
        label: "Hoàn thành hôm nay",
        value: "25",
        change: "+25%",
        tone: "green",
        trend: [4, 7, 5, 8, 7, 10, 8]
    },
    {
        id: "waiting",
        label: "Chờ nguyên liệu",
        value: "6",
        change: "-14.3%",
        tone: "orange",
        trend: [8, 6, 7, 5, 6, 4, 3]
    },
    {
        id: "month",
        label: "Tổng sản lượng tháng",
        value: "3.250 Kg",
        change: "+12.8%",
        tone: "green",
        trend: [5, 7, 6, 8, 7, 10, 8]
    }
];

export const productionOrderAttentionItems = [
    {
        id: "lsx-125",
        title: "LSX-2026-00125",
        subtitle: "Đang sản xuất",
        imageUrl: teaBoxImage
    },
    {
        id: "lsx-126",
        title: "LSX-2026-00126",
        subtitle: "Chờ nguyên liệu",
        imageUrl: teaCakeImage
    },
    {
        id: "lsx-127",
        title: "LSX-2026-00127",
        subtitle: "Sắp hoàn thành",
        imageUrl: giftSetImage
    },
    {
        id: "lsx-128",
        title: "LSX-2026-00128",
        subtitle: "Quá hạn 1 ngày",
        imageUrl: teaBoxImage
    }
] as const;

export const productionOrders: ProductionOrderListItem[] = [
    {
        id: "lsx-2026-00125",
        code: "LSX-2026-00125",
        productName: "Trà Sen Tây Hồ 100g",
        productSku: "TRA-SEN-100",
        productImageUrl: teaBoxImage,
        bomCode: "BOM-TRA-SEN-100-V2",
        plannedQuantity: 500,
        completedQuantity: 320,
        unit: "hộp",
        startDate: "2026-06-05",
        dueDate: "2026-06-08",
        status: "IN_PROGRESS",
        owner: "Nguyễn Văn An",
        workshop: "PACKAGING",
        materialUsage: [
            {
                id: "tea",
                name: "Trà Sen nguyên liệu",
                required: "40kg",
                issued: "40kg"
            },
            {
                id: "flavor",
                name: "Hương sen",
                required: "2.5kg",
                issued: "2.5kg"
            },
            {
                id: "bag",
                name: "Túi lọc",
                required: "500",
                issued: "500"
            }
        ],
        logs: [
            {
                id: "log-1",
                time: "2026-06-05 08:30",
                message: "Tạo lệnh sản xuất",
                actor: "Admin"
            },
            {
                id: "log-2",
                time: "2026-06-06 09:15",
                message: "Xuất nguyên liệu",
                actor: "Thủ kho"
            },
            {
                id: "log-3",
                time: "2026-06-07 10:20",
                message: "Hoàn thành 320 sản phẩm",
                actor: "Nhân viên SX"
            }
        ]
    },
    {
        id: "lsx-2026-00126",
        code: "LSX-2026-00126",
        productName: "Trà Shan Tuyết 100g",
        productSku: "TRA-SHAN-100",
        productImageUrl: teaCakeImage,
        bomCode: "BOM-SHAN-100-V1",
        plannedQuantity: 300,
        completedQuantity: 0,
        unit: "hộp",
        startDate: "2026-06-06",
        dueDate: "2026-06-10",
        status: "WAITING_MATERIAL",
        owner: "Trần Thị Bình",
        workshop: "PACKAGING",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00127",
        code: "LSX-2026-00127",
        productName: "Set Quà Tết An Lành",
        productSku: "SET-QUA-TET-01",
        productImageUrl: giftSetImage,
        bomCode: "BOM-SET-TET-01",
        plannedQuantity: 100,
        completedQuantity: 95,
        unit: "bộ",
        startDate: "2026-06-05",
        dueDate: "2026-06-07",
        status: "NEAR_COMPLETION",
        owner: "Phạm Hoàng Nam",
        workshop: "GIFT_ASSEMBLY",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00128",
        code: "LSX-2026-00128",
        productName: "Trà Ô Long Tứ Quý 100g",
        productSku: "TRA-OLONG-100",
        productImageUrl: teaBoxImage,
        bomCode: "BOM-OLONG-100-V3",
        plannedQuantity: 200,
        completedQuantity: 50,
        unit: "hộp",
        startDate: "2026-06-04",
        dueDate: "2026-06-06",
        status: "OVERDUE",
        owner: "Lê Minh Tuấn",
        workshop: "PACKAGING",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00124",
        code: "LSX-2026-00124",
        productName: "Trà Nhài Premium 100g",
        productSku: "TRA-NHAI-100",
        productImageUrl: teaBoxImage,
        bomCode: "BOM-NHAI-100-V2",
        plannedQuantity: 400,
        completedQuantity: 400,
        unit: "hộp",
        startDate: "2026-06-02",
        dueDate: "2026-06-05",
        status: "COMPLETED",
        owner: "Nguyễn Thị Hằng",
        workshop: "PACKAGING",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00123",
        code: "LSX-2026-00123",
        productName: "Combo Quà Tặng Trà",
        productSku: "COMBO-TRA-01",
        productImageUrl: giftSetImage,
        bomCode: "BOM-COMBO-TRA-01",
        plannedQuantity: 150,
        completedQuantity: 150,
        unit: "bộ",
        startDate: "2026-06-01",
        dueDate: "2026-06-03",
        status: "COMPLETED",
        owner: "Đỗ Quốc Huy",
        workshop: "GIFT_ASSEMBLY",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00122",
        code: "LSX-2026-00122",
        productName: "Trà Xanh Thái Nguyên 100g",
        productSku: "TRA-XANH-THAI-100",
        productImageUrl: teaCakeImage,
        bomCode: "BOM-XANH-THAI-100-V1",
        plannedQuantity: 600,
        completedQuantity: 120,
        unit: "hộp",
        startDate: "2026-06-05",
        dueDate: "2026-06-12",
        status: "IN_PROGRESS",
        owner: "Vũ Hồng Phúc",
        workshop: "MIXING",
        materialUsage: [],
        logs: []
    },
    {
        id: "lsx-2026-00121",
        code: "LSX-2026-00121",
        productName: "Bộ Phụ Kiện Pha Trà",
        productSku: "PHU-KIEN-TRA-01",
        productImageUrl: teaCakeImage,
        bomCode: "BOM-PHU-KIEN-01",
        plannedQuantity: 80,
        completedQuantity: 10,
        unit: "bộ",
        startDate: "2026-06-06",
        dueDate: "2026-06-09",
        status: "DRAFT",
        owner: "Hoàng Kim Ngân",
        workshop: "GIFT_ASSEMBLY",
        materialUsage: [],
        logs: []
    }
];
