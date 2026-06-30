import type { DiscountCode } from "../types/discount-code.types";

export const discountCodes: DiscountCode[] = [
    {
        id: "tet2026-20",
        code: "TET2026-20",
        issueType: "Phát hành thủ công",
        programName: "Giảm 20% cho đơn từ 500K",
        scope: "PUBLIC",
        startAt: "2026-02-01T00:00:00",
        endAt: "2026-02-20T23:59:00",
        usedCount: 0,
        usageLimit: 1,
        status: "ACTIVE"
    },
    {
        id: "freeship300",
        code: "FREESHIP300",
        issueType: "Phát hành tự động",
        programName: "Miễn phí vận chuyển từ 300K",
        scope: "PUBLIC",
        startAt: "2026-02-01T00:00:00",
        endAt: "2026-02-20T23:59:00",
        usedCount: 1,
        usageLimit: 1,
        status: "USED"
    },
    {
        id: "viptea-001",
        code: "VIPTEA-001",
        issueType: "Khách hàng: Nguyễn Văn An",
        programName: "Tặng 1 hộp trà cao cấp",
        scope: "PERSONAL",
        customerName: "Nguyễn Văn An",
        startAt: "2026-02-01T00:00:00",
        endAt: "2026-02-28T23:59:00",
        usedCount: 0,
        usageLimit: 1,
        status: "ACTIVE"
    },
    {
        id: "welcome-001",
        code: "WELCOME-001",
        issueType: "Khách hàng: Trần Thu Hà",
        programName: "Freeship tối đa 20K",
        scope: "PERSONAL",
        customerName: "Trần Thu Hà",
        startAt: "2026-01-01T00:00:00",
        endAt: "2026-01-31T23:59:00",
        usedCount: 1,
        usageLimit: 1,
        status: "USED"
    },
    {
        id: "spring50k",
        code: "SPRING50K",
        issueType: "Phát hành thủ công",
        programName: "Giảm 50K cho đơn từ 700K",
        scope: "PUBLIC",
        startAt: "2026-04-01T00:00:00",
        endAt: "2026-04-15T23:59:00",
        usedCount: 0,
        usageLimit: 1,
        status: "SCHEDULED"
    },
    {
        id: "oldtet-ex",
        code: "OLDTET-EX",
        issueType: "Phát hành thủ công",
        programName: "Giảm 20% cho đơn Tết cũ",
        scope: "PUBLIC",
        startAt: "2025-01-01T00:00:00",
        endAt: "2025-01-15T23:59:00",
        usedCount: 0,
        usageLimit: 1,
        status: "EXPIRED"
    },
    {
        id: "oolong15",
        code: "OOLONG15",
        issueType: "Phát hành thủ công",
        programName: "Mua 2 tặng 1 - Trà Oolong",
        scope: "PUBLIC",
        startAt: "2026-03-05T00:00:00",
        endAt: "2026-03-20T23:59:00",
        usedCount: 0,
        usageLimit: 3,
        status: "SCHEDULED"
    },
    {
        id: "member20",
        code: "MEMBER20",
        issueType: "Khách hàng: Lê Minh Anh",
        programName: "Ưu đãi thành viên",
        scope: "PERSONAL",
        customerName: "Lê Minh Anh",
        startAt: "2026-01-01T00:00:00",
        endAt: "2026-01-31T23:59:00",
        usedCount: 0,
        usageLimit: 1,
        status: "ACTIVE"
    }
];
