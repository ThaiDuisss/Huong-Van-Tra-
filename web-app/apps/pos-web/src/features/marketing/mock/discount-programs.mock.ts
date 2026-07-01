import type { DiscountProgram } from "../types/discount-program.types";

export const discountPrograms: DiscountProgram[] = [
    {
        id: "discount-500k-20",
        name: "Giảm 20% cho đơn từ 500K",
        eventName: "Tết Sum Vầy 2026",
        applicationType: "DISCOUNT_CODE",
        startAt: "2026-02-01T00:00:00",
        endAt: "2026-02-20T23:59:00",
        status: "SCHEDULED",
        description:
            "Áp dụng cho khách mua online trong dịp Tết",
        goal: "Tăng doanh số nhóm trà cao cấp trong mùa Tết",
        promotionType: "Giảm theo phần trăm",
        canStackPromotions: false,
        priority: 10,
        createdAt: "2026-01-15T10:30:00",
        updatedAt: "2026-01-20T14:45:00",
        version: 0,
        benefit: {
            benefitType: "Giảm theo phần trăm",
            value: "20%",
            maxDiscount: "100.000đ",
            minProfitMargin: "15%",
            expectedProfitMargin: "25%",
            suggestedValue: "18%",
            confidence: "92,5%",
            aiSuggestion: "Không",
            reason: "Sản phẩm có biên lợi nhuận cao, giảm 20% vẫn đảm bảo lợi nhuận tối thiểu"
        },
        conditions: [
            {
                id: "min-order",
                name: "Giá trị đơn hàng tối thiểu",
                operator: "GTE",
                value: "500.000đ"
            },
            {
                id: "sales-channel",
                name: "Kênh bán",
                operator: "IN",
                value: "ONLINE"
            },
            {
                id: "customer-tier",
                name: "Hạng khách hàng",
                operator: "IN",
                value: "Tất cả khách hàng"
            }
        ],
        scopes: [
            {
                id: "scope-category-premium",
                objectType: "CATEGORY",
                target: "Trà cao cấp",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Áp dụng cho nhóm trà chủ lực"
            },
            {
                id: "scope-product-oolong",
                objectType: "PRODUCT",
                target: "Trà Oolong Premium",
                excluded: false,
                overrideBenefit: "PERCENT_DISCOUNT",
                overrideValue: "15%",
                reason: "Tăng ưu đãi cho sản phẩm nổi bật"
            },
            {
                id: "scope-product-standard",
                objectType: "PRODUCT",
                target: "Trà Phổ Thông",
                excluded: true,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Không áp dụng cho dòng phổ thông"
            }
        ]
    },
    {
        id: "free-shipping-300k",
        name: "Miễn phí vận chuyển từ 300K",
        eventName: "Tết Sum Vầy 2026",
        applicationType: "FREE_SHIPPING",
        startAt: "2026-02-01T00:00:00",
        endAt: "2026-03-20T23:59:00",
        status: "ACTIVE",
        description: "Hỗ trợ phí vận chuyển cho đơn online.",
        goal: "Tăng tỷ lệ hoàn tất đơn hàng online",
        promotionType: "Miễn phí vận chuyển",
        canStackPromotions: true,
        priority: 8,
        createdAt: "2026-01-18T09:20:00",
        updatedAt: "2026-01-28T11:10:00",
        version: 1,
        benefit: {
            benefitType: "Miễn phí vận chuyển",
            value: "Tối đa 30.000đ",
            maxDiscount: "30.000đ",
            minProfitMargin: "12%",
            expectedProfitMargin: "21%",
            suggestedValue: "25.000đ",
            confidence: "89%",
            aiSuggestion: "Có",
            reason: "Ngưỡng 300K giúp giữ biên lợi nhuận ổn định."
        },
        conditions: [
            {
                id: "min-order",
                name: "Giá trị đơn hàng tối thiểu",
                operator: "GTE",
                value: "300.000đ"
            },
            {
                id: "sales-channel",
                name: "Kênh bán",
                operator: "IN",
                value: "ONLINE"
            }
        ],
        scopes: [
            {
                id: "scope-all-products",
                objectType: "CATEGORY",
                target: "Tất cả sản phẩm",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Áp dụng toàn bộ danh mục"
            }
        ]
    },
    {
        id: "buy-2-get-1-oolong",
        name: "Mua 2 tặng 1 - Trà Oolong",
        eventName: "Tea Lovers Month",
        applicationType: "BUY_X_GET_Y",
        startAt: "2026-03-05T00:00:00",
        endAt: "2026-03-20T23:59:00",
        status: "SCHEDULED",
        description: "Khuyến khích mua combo trà Oolong.",
        goal: "Tăng số lượng sản phẩm mỗi đơn",
        promotionType: "Mua X tặng Y",
        canStackPromotions: false,
        priority: 7,
        createdAt: "2026-02-18T08:30:00",
        updatedAt: "2026-02-22T16:00:00",
        version: 0,
        benefit: {
            benefitType: "Tặng sản phẩm",
            value: "Mua 2 tặng 1",
            maxDiscount: "150.000đ",
            minProfitMargin: "14%",
            expectedProfitMargin: "24%",
            suggestedValue: "Mua 3 tặng 1",
            confidence: "86%",
            aiSuggestion: "Có",
            reason: "Ưu đãi phù hợp cho nhóm sản phẩm có vòng quay nhanh."
        },
        conditions: [
            {
                id: "min-quantity",
                name: "Số lượng mua tối thiểu",
                operator: "GTE",
                value: "2"
            }
        ],
        scopes: [
            {
                id: "scope-oolong",
                objectType: "PRODUCT",
                target: "Trà Oolong Premium",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Sản phẩm trọng tâm của chiến dịch"
            }
        ]
    },
    {
        id: "amount-off-700k",
        name: "Giảm 50K cho đơn từ 700K",
        eventName: "Spring Sale 2026",
        applicationType: "AMOUNT_OFF",
        startAt: "2026-04-01T00:00:00",
        endAt: "2026-04-15T23:59:00",
        status: "ACTIVE",
        description: "Giảm trực tiếp cho đơn hàng giá trị cao.",
        goal: "Tăng giá trị đơn hàng trung bình",
        promotionType: "Giảm tiền",
        canStackPromotions: false,
        priority: 6,
        createdAt: "2026-03-01T13:00:00",
        updatedAt: "2026-03-15T10:20:00",
        version: 2,
        benefit: {
            benefitType: "Giảm tiền",
            value: "50.000đ",
            maxDiscount: "50.000đ",
            minProfitMargin: "16%",
            expectedProfitMargin: "27%",
            suggestedValue: "40.000đ",
            confidence: "91%",
            aiSuggestion: "Không",
            reason: "Mức giảm phù hợp với nhóm đơn hàng từ 700K."
        },
        conditions: [
            {
                id: "min-order",
                name: "Giá trị đơn hàng tối thiểu",
                operator: "GTE",
                value: "700.000đ"
            }
        ],
        scopes: [
            {
                id: "scope-spring",
                objectType: "CATEGORY",
                target: "Bộ quà Xuân",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Ưu tiên nhóm sản phẩm mùa vụ"
            }
        ]
    },
    {
        id: "vip-premium-tea-gift",
        name: "Tặng 1 hộp trà cao cấp",
        eventName: "Khách hàng VIP",
        applicationType: "GIFT",
        startAt: "2026-01-01T00:00:00",
        endAt: "2026-02-28T23:59:00",
        status: "INACTIVE",
        description: "Tặng quà cho khách hàng VIP.",
        goal: "Tăng mức độ gắn bó của khách hàng VIP",
        promotionType: "Tặng sản phẩm",
        canStackPromotions: true,
        priority: 5,
        createdAt: "2025-12-15T09:00:00",
        updatedAt: "2026-02-28T23:59:00",
        version: 3,
        benefit: {
            benefitType: "Tặng sản phẩm",
            value: "1 hộp trà cao cấp",
            maxDiscount: "200.000đ",
            minProfitMargin: "18%",
            expectedProfitMargin: "31%",
            suggestedValue: "1 hộp trà nhỏ",
            confidence: "88%",
            aiSuggestion: "Có",
            reason: "Quà tặng giúp tăng tỷ lệ quay lại của nhóm VIP."
        },
        conditions: [
            {
                id: "customer-tier",
                name: "Hạng khách hàng",
                operator: "IN",
                value: "VIP"
            }
        ],
        scopes: [
            {
                id: "scope-vip",
                objectType: "CATEGORY",
                target: "Khách hàng VIP",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Chỉ áp dụng cho nhóm VIP"
            }
        ]
    },
    {
        id: "member-freeship-20k",
        name: "Freeship tối đa 20K",
        eventName: "Ưu đãi thành viên",
        applicationType: "FREE_SHIPPING",
        startAt: "2026-01-01T00:00:00",
        endAt: "2026-01-31T23:59:00",
        status: "ACTIVE",
        description: "Ưu đãi vận chuyển cho thành viên.",
        goal: "Tăng tỷ lệ kích hoạt thành viên",
        promotionType: "Miễn phí vận chuyển",
        canStackPromotions: true,
        priority: 4,
        createdAt: "2025-12-20T10:00:00",
        updatedAt: "2026-01-10T15:30:00",
        version: 1,
        benefit: {
            benefitType: "Miễn phí vận chuyển",
            value: "Tối đa 20.000đ",
            maxDiscount: "20.000đ",
            minProfitMargin: "10%",
            expectedProfitMargin: "22%",
            suggestedValue: "15.000đ",
            confidence: "85%",
            aiSuggestion: "Không",
            reason: "Ưu đãi nhỏ, dễ kiểm soát ngân sách."
        },
        conditions: [
            {
                id: "member",
                name: "Nhóm khách hàng",
                operator: "IN",
                value: "Thành viên"
            }
        ],
        scopes: [
            {
                id: "scope-member",
                objectType: "CATEGORY",
                target: "Tất cả sản phẩm",
                excluded: false,
                overrideBenefit: "-",
                overrideValue: "-",
                reason: "Áp dụng cho thành viên"
            }
        ]
    }
];
