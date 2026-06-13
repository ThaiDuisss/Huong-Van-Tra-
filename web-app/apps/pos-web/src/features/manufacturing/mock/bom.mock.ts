import type {
    BomListItem,
    BomSummaryMetric
} from "../types/bom.types";

const teaBoxImage =
    "https://images.unsplash.com/photo-1607006483224-85bca6caf942?auto=format&fit=crop&w=160&q=80";
const teaCakeImage =
    "https://images.unsplash.com/photo-1567922045116-2a00fae2ed03?auto=format&fit=crop&w=160&q=80";
const giftSetImage =
    "https://images.unsplash.com/photo-1513475382585-d06e58bcb0e0?auto=format&fit=crop&w=160&q=80";

export const bomSummaryMetrics: BomSummaryMetric[] = [
    {
        id: "total",
        label: "Tổng BOM",
        value: "186",
        change: "+12.5%",
        tone: "green",
        trend: [3, 5, 4, 7, 6, 10, 8]
    },
    {
        id: "active",
        label: "BOM đang sử dụng",
        value: "152",
        change: "+9.3%",
        tone: "green",
        trend: [2, 4, 3, 5, 4, 7, 5]
    },
    {
        id: "has-components",
        label: "Thành phẩm có BOM",
        value: "148",
        change: "+8.1%",
        tone: "green",
        trend: [4, 6, 5, 7, 6, 5, 4]
    },
    {
        id: "needs-update",
        label: "BOM cần cập nhật",
        value: "12",
        change: "-7.4%",
        tone: "orange",
        trend: [8, 6, 7, 5, 6, 4, 5]
    }
];

export const bomItems: BomListItem[] = [
    {
        id: "bom-tra-sen-100-v2",
        code: "BOM-TRA-SEN-100-V2",
        name: "BOM Trà Sen Tây Hồ 100g",
        productName: "Trà Sen Tây Hồ 100g",
        productSku: "TRA-SEN-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaBoxImage,
        version: "v2",
        componentCount: 5,
        materialCost: 88000,
        packagingCost: 7000,
        totalCost: 95000,
        updatedAt: "2026-06-05",
        effectiveDate: "2026-06-05",
        status: "ACTIVE",
        components: [
            {
                id: "sen-tea",
                name: "Trà Sen Tây Hồ nguyên liệu",
                group: "Nguyên liệu",
                quantity: 80,
                unit: "g",
                cost: 80000
            },
            {
                id: "huong-sen",
                name: "Hương sen tự nhiên",
                group: "Nguyên liệu",
                quantity: 5,
                unit: "g",
                cost: 8000
            },
            {
                id: "filter-bag",
                name: "Túi lọc",
                group: "Bao bì",
                quantity: 1,
                unit: "cái",
                cost: 2000
            },
            {
                id: "label",
                name: "Tem nhãn",
                group: "Bao bì",
                quantity: 1,
                unit: "cái",
                cost: 1000
            },
            {
                id: "box",
                name: "Hộp giấy",
                group: "Bao bì",
                quantity: 1,
                unit: "cái",
                cost: 4000
            }
        ],
        history: [
            {
                id: "history-sen-1",
                date: "2026-06-05",
                description: "Thêm Hương sen tự nhiên",
                actor: "Admin"
            },
            {
                id: "history-sen-2",
                date: "2026-06-04",
                description: "Tạo mới BOM",
                actor: "Admin"
            }
        ]
    },
    {
        id: "bom-olong-100-v3",
        code: "BOM-OLONG-100-V3",
        name: "BOM Trà Ô Long Tứ Quý",
        productName: "Trà Ô Long Tứ Quý 100g",
        productSku: "TRA-OLONG-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaBoxImage,
        version: "v3",
        componentCount: 4,
        materialCost: 103000,
        packagingCost: 7000,
        totalCost: 110000,
        updatedAt: "2026-06-04",
        effectiveDate: "2026-06-04",
        status: "ACTIVE",
        components: [],
        history: []
    },
    {
        id: "bom-shan-100-v1",
        code: "BOM-SHAN-100-V1",
        name: "BOM Trà Shan Tuyết Cổ Thụ",
        productName: "Trà Shan Tuyết Cổ Thụ 100g",
        productSku: "TRA-SHAN-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaCakeImage,
        version: "v1",
        componentCount: 6,
        materialCost: 112000,
        packagingCost: 8000,
        totalCost: 120000,
        updatedAt: "2026-06-03",
        effectiveDate: "2026-06-03",
        status: "ACTIVE",
        components: [],
        history: []
    },
    {
        id: "bom-nhai-100-v2",
        code: "BOM-NHAI-100-V2",
        name: "BOM Trà Nhài Premium",
        productName: "Trà Nhài Premium 100g",
        productSku: "TRA-NHAI-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaBoxImage,
        version: "v2",
        componentCount: 4,
        materialCost: 98000,
        packagingCost: 7000,
        totalCost: 105000,
        updatedAt: "2026-06-03",
        effectiveDate: "2026-06-03",
        status: "PENDING_APPROVAL",
        components: [],
        history: []
    },
    {
        id: "bom-lai-sen-100-v2",
        code: "BOM-LAI-SEN-100-V2",
        name: "BOM Trà Lài Sen 100g",
        productName: "Trà Lài Sen 100g",
        productSku: "TRA-LAI-SEN-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaBoxImage,
        version: "v2",
        componentCount: 5,
        materialCost: 91000,
        packagingCost: 7000,
        totalCost: 98000,
        updatedAt: "2026-06-02",
        effectiveDate: "2026-06-02",
        status: "ACTIVE",
        components: [],
        history: []
    },
    {
        id: "bom-xanh-thai-100-v1",
        code: "BOM-XANH-THAI-100-V1",
        name: "BOM Trà Xanh Thái Nguyên",
        productName: "Trà Xanh Thái Nguyên 100g",
        productSku: "TRA-XANH-THAI-100",
        productType: "PACKAGED_TEA",
        productImageUrl: teaCakeImage,
        version: "v1",
        componentCount: 4,
        materialCost: 78000,
        packagingCost: 7000,
        totalCost: 85000,
        updatedAt: "2026-06-02",
        effectiveDate: "2026-06-02",
        status: "ACTIVE",
        components: [],
        history: []
    },
    {
        id: "bom-set-tet-01",
        code: "BOM-SET-TET-01",
        name: "BOM Set Quà Tết",
        productName: "Set Quà Tết An Lành",
        productSku: "SET-QUA-TET-01",
        productType: "GIFT_SET",
        productImageUrl: giftSetImage,
        version: "v1",
        componentCount: 12,
        materialCost: 320000,
        packagingCost: 30000,
        totalCost: 350000,
        updatedAt: "2026-06-01",
        effectiveDate: "2026-06-01",
        status: "MATERIAL_SHORTAGE",
        components: [],
        history: []
    },
    {
        id: "bom-combo-tra-01",
        code: "BOM-COMBO-TRA-01",
        name: "BOM Combo Quà Tặng",
        productName: "Combo Quà Tặng Trà",
        productSku: "COMBO-TRA-01",
        productType: "GIFT_SET",
        productImageUrl: giftSetImage,
        version: "v1",
        componentCount: 8,
        materialCost: 205000,
        packagingCost: 15000,
        totalCost: 220000,
        updatedAt: "2026-05-31",
        effectiveDate: "2026-05-31",
        status: "PENDING_APPROVAL",
        components: [],
        history: []
    },
    {
        id: "bom-phu-kien-01",
        code: "BOM-PHU-KIEN-01",
        name: "BOM Phụ Kiện Pha Trà",
        productName: "Bộ Phụ Kiện Pha Trà",
        productSku: "PHU-KIEN-TRA-01",
        productType: "ACCESSORY",
        productImageUrl: teaCakeImage,
        version: "v1",
        componentCount: 3,
        materialCost: 39000,
        packagingCost: 6000,
        totalCost: 45000,
        updatedAt: "2026-05-31",
        effectiveDate: "2026-05-31",
        status: "INACTIVE",
        components: [],
        history: []
    },
    {
        id: "bom-hop-tra-01",
        code: "BOM-HOP-TRA-01",
        name: "BOM Hộp Trà Cao Cấp",
        productName: "Hộp Trà Cao Cấp",
        productSku: "HOP-TRA-01",
        productType: "ACCESSORY",
        productImageUrl: giftSetImage,
        version: "v2",
        componentCount: 6,
        materialCost: 56000,
        packagingCost: 9000,
        totalCost: 65000,
        updatedAt: "2026-05-30",
        effectiveDate: "2026-05-30",
        status: "ACTIVE",
        components: [],
        history: []
    }
];

export const bomAttentionItems = [
    {
        id: "attention-sen",
        title: "BOM Trà Sen Tây Hồ 100g",
        subtitle: "Phiên bản v2",
        imageUrl: teaBoxImage
    },
    {
        id: "attention-olong",
        title: "BOM Trà Ô Long Tứ Quý",
        subtitle: "Phiên bản v3",
        imageUrl: teaBoxImage
    },
    {
        id: "attention-set",
        title: "BOM Set Quà Tết",
        subtitle: "Thiếu nguyên liệu",
        imageUrl: giftSetImage
    },
    {
        id: "attention-shan",
        title: "BOM Trà Shan Tuyết",
        subtitle: "Sắp cập nhật",
        imageUrl: teaCakeImage
    }
] as const;
