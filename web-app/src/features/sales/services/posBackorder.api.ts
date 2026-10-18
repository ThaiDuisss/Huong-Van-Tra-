import { apiClient } from "../../../api/api-client";
import type { PosCheckoutPaymentResponse, PosOrderDto } from "./posOrder.api";

export type AvailabilityScenario =
    | "AVAILABLE"
    | "IN_PRODUCTION"
    | "PRODUCE_FROM_STOCK_MATERIALS"
    | "WAIT_MATERIAL_RECEIPT"
    | "WAIT_MATERIAL_PURCHASE"
    | "NOT_PRODUCIBLE";

export interface AvailabilityMaterialEstimate {
    ingredientId: string;
    name?: string | null;
    requiredQuantity: number;
    availableQuantity: number;
    missingQuantity: number;
    expectedArrivalAt?: string | null;
    /** IN_STOCK, OPEN_RECEIPT, RECEIPT_HISTORY hoặc DEFAULT */
    arrivalSource?: string | null;
    receiptCode?: string | null;
}

export interface AvailabilityLineEstimate {
    productVariantId: string;
    itemName?: string | null;
    requestedQuantity: number;
    shelfQuantity?: number;
    warehouseQuantity?: number;
    missingQuantity: number;
    scenario: AvailabilityScenario;
    estimatedAvailableAt?: string | null;
    estimatedWaitDays?: number | null;
    productionDays?: number | null;
    /** VARIANT_HISTORY, ALL_HISTORY hoặc DEFAULT */
    durationSource?: string | null;
    productionOrderCode?: string | null;
    depositRecommended: boolean;
    materials?: AvailabilityMaterialEstimate[];
    message?: string | null;
}

export interface AvailabilityEstimate {
    backorder: boolean;
    estimatedAvailableAt?: string | null;
    lines: AvailabilityLineEstimate[];
}

export interface AvailabilityEstimateLineInput {
    productVariantId: string;
    quantity: number;
}

export interface BackorderOrderDto extends PosOrderDto {
    customerName?: string | null;
    customerPhone?: string | null;
    paymentMethod?: string | null;
    paymentStatusLabel?: string | null;
    createdAt?: string | null;
}

export interface BackorderAvailability {
    orderId: string;
    ready: boolean;
    estimatedAvailableAt?: string | null;
    lines: AvailabilityLineEstimate[];
}

export interface BackorderPage {
    content: BackorderOrderDto[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface FulfillBackorderResponse extends BackorderOrderDto {
    payment?: PosCheckoutPaymentResponse;
}

const uuidPattern =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const posBackorderApi = {
    /** Ước tính ngày có hàng cho các mặt hàng khách đặt (tổng số lượng trong đơn). Chỉ đọc. */
    estimate(input: {
        branchId?: string | null;
        orderId?: string | null;
        lines: AvailabilityEstimateLineInput[];
    }) {
        return apiClient.post<
            AvailabilityEstimate,
            {
                branchId?: string;
                orderId?: string;
                lines: AvailabilityEstimateLineInput[];
            }
        >("/inventory/availability/estimate", {
            branchId:
                input.branchId && uuidPattern.test(input.branchId)
                    ? input.branchId
                    : undefined,
            orderId: input.orderId ?? undefined,
            lines: input.lines
        });
    },

    getWaitingOrders(params: { keyword?: string; page?: number; size?: number }) {
        return apiClient.get<BackorderPage>("/orders/backorders", {
            query: {
                keyword: params.keyword?.trim() || undefined,
                page: params.page ?? 0,
                size: params.size ?? 20
            }
        });
    },

    /** Kiểm tra đơn chờ đã đủ hàng để giao chưa (đồng thời nhắc kho chuẩn bị hàng nếu trước đó bị lỗi). */
    checkAvailability(orderId: string) {
        return apiClient.post<BackorderAvailability, Record<string, never>>(
            `/orders/${encodeURIComponent(orderId)}/backorder/check`,
            {}
        );
    },

    /** Giao hàng cho đơn chờ: trừ tồn, thu phần còn lại (nếu đơn mới đặt cọc) và hoàn tất đơn. */
    fulfill(orderId: string, receivedAmount?: number) {
        return apiClient.post<FulfillBackorderResponse, { receivedAmount?: number }>(
            `/orders/${encodeURIComponent(orderId)}/backorder/fulfill`,
            { receivedAmount }
        );
    }
};

const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh"
});

export function formatAvailabilityDate(value?: string | null) {
    if (!value) {
        return "Chưa xác định";
    }

    const date = new Date(value);

    return Number.isNaN(date.getTime()) ? "Chưa xác định" : dateFormatter.format(date);
}

export function formatQuantity(value: number | null | undefined) {
    return new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(
        Number(value ?? 0)
    );
}

const scenarioLabels: Record<AvailabilityScenario, string> = {
    AVAILABLE: "Đủ hàng",
    IN_PRODUCTION: "Đang sản xuất",
    PRODUCE_FROM_STOCK_MATERIALS: "Sản xuất theo đơn",
    WAIT_MATERIAL_RECEIPT: "Chờ nguyên liệu đang về",
    WAIT_MATERIAL_PURCHASE: "Chờ nhập nguyên liệu",
    NOT_PRODUCIBLE: "Chưa ước tính được"
};

export function getScenarioLabel(scenario: AvailabilityScenario) {
    return scenarioLabels[scenario] ?? scenario;
}

/** Lỗi backend khi số đặt vượt số có thể bán: dùng để mở hộp thoại đặt trước thay vì báo lỗi. */
export function isOutOfStockError(error: unknown) {
    const message =
        typeof error === "object" && error !== null && "message" in error
            ? String((error as { message?: unknown }).message ?? "")
            : "";

    return message.includes("vượt quá số lượng có thể bán");
}
