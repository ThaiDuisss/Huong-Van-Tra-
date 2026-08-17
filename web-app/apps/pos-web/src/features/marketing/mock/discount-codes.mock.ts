import type { DiscountCode } from "../types/discount-code.types";

export const discountCodes: DiscountCode[] = [
    {
        code: "HVT-TRASEN-20",
        endAt: "2026-08-31T23:59:00.000Z",
        id: "discount-code-1",
        issueType: "Phát hành công khai",
        programName: "Ưu đãi Trà Sen tháng 8",
        scope: "PUBLIC",
        startAt: "2026-08-01T00:00:00.000Z",
        status: "ACTIVE",
        usageLimit: 500,
        usedCount: 84
    },
    {
        code: "CG-VIP-50K",
        customerName: "Nguyễn Minh Anh",
        endAt: "2026-08-15T23:59:00.000Z",
        id: "discount-code-2",
        issueType: "Cá nhân hóa",
        programName: "Chăm sóc khách hàng Cầu Giấy",
        scope: "PERSONAL",
        startAt: "2026-08-02T00:00:00.000Z",
        status: "SCHEDULED",
        usageLimit: 1,
        usedCount: 0
    }
];
