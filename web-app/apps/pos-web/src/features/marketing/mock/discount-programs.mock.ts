import type { DiscountProgram } from "../types/discount-program.types";

export const discountPrograms: DiscountProgram[] = [
    {
        applicationType: "DISCOUNT_CODE",
        benefit: {
            aiSuggestion: "Giữ mức giảm vừa phải cho nhóm sản phẩm bán chạy.",
            benefitType: "Giảm theo phần trăm",
            confidence: "Cao",
            expectedProfitMargin: "38%",
            maxDiscount: "50.000 VND",
            minProfitMargin: "30%",
            reason: "Tăng doanh số Trà Sen nhưng vẫn giữ biên lợi nhuận.",
            suggestedValue: "15%",
            value: "20%"
        },
        canStackPromotions: false,
        conditions: [
            { id: "condition-1", name: "Giá trị đơn hàng", operator: ">=", value: "300.000 VND" }
        ],
        createdAt: "2026-07-25T09:00:00.000Z",
        description: "Ưu đãi cho các SKU Trà Sen trong tháng 8.",
        endAt: "2026-08-31T23:59:00.000Z",
        eventName: "Tháng Trà Sen",
        goal: "Tăng doanh số nhóm trà ướp hoa.",
        id: "discount-program-1",
        name: "Ưu đãi Trà Sen tháng 8",
        priority: 10,
        promotionType: "Product promotion",
        scopes: [
            { excluded: false, id: "scope-1", objectType: "PRODUCT", overrideBenefit: "Không", overrideValue: "-", reason: "SKU trọng tâm", target: "Trà Sen 100g" }
        ],
        startAt: "2026-08-01T00:00:00.000Z",
        status: "ACTIVE",
        updatedAt: "2026-08-01T08:00:00.000Z",
        version: 2
    }
];
