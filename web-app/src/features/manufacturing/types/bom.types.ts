// ============================================================
// Types khớp với API thật: 1 product-variant có đúng 1 danh sách
// nguyên liệu (bom_items), không còn khái niệm BOM riêng (mã/version/
// trạng thái/mặc định).
// ============================================================

export interface BomItemResponse {
    id: string;
    ingredient: {
        id: string;
        name: string;
    };
    quantity: number;
    unit: {
        id: string;
        name: string;
        symbol: string;
    };
    wasteRate: number;
}

export interface BomItemInput {
    ingredientId: string;
    quantity: number;
    unitId: string;
    wasteRate: number;
}

// ============================================================
// Backward compatibility cho các widget dashboard dùng chung
// (ProductionOrderListPage tái sử dụng BomSummary/BomAttentionList
// để hiển thị số liệu tổng quan, không liên quan tới entity BOM cũ)
// ============================================================

export interface BomSummaryMetric {
    id: string;
    label: string;
    value: string;
    change: string;
    tone: "green" | "orange";
    trend: number[];
}
