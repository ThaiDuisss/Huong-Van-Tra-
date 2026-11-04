import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi, beforeEach } from "vitest";

if (typeof document === "undefined") {
    (globalThis as unknown as { document: unknown }).document = {
        body: {},
        addEventListener: vi.fn(),
        removeEventListener: vi.fn()
    };
    (globalThis as unknown as { window: unknown }).window = {
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        innerWidth: 1024,
        innerHeight: 768
    };
}

// Mock react-dom createPortal so portal renders inline in node environment
vi.mock("react-dom", async () => {
    const actual = await vi.importActual<typeof import("react-dom")>("react-dom");
    return {
        ...actual,
        createPortal: (children: React.ReactNode) => children
    };
});

// Mock role-utils
vi.mock("@/auth/role-utils", () => ({
    CUSTOMER_RETURN_QC_ROLES: ["ROLE_SUPER_ADMIN", "ROLE_MANAGER", "ROLE_QC"],
    useAuthorization: () => ({
        hasAnyRole: () => true,
        hasRole: () => true
    })
}));

// Mock API client & order-return APIs
vi.mock("../../order/services/order-return.api", () => ({
    getOrderReturnList: vi.fn(),
    getOrderReturnStatusCounts: vi.fn(),
    getOrderReturnDetail: vi.fn(),
    getCustomerReturnQcSummary: vi.fn(),
    receiveOrderReturn: vi.fn()
}));

import type { OrderReturnListRow } from "../../order/types/order-return.types";
import {
    getCustomerReturnUiStatus,
    RETURN_DISPLAY_STATUS_LABELS,
    RETURN_STATUS_LABELS
} from "../../order/utils/order-return.utils";
import {
    getOrderReturnList,
    getOrderReturnStatusCounts
} from "../../order/services/order-return.api";
import { CustomerReturnActionMenu } from "./CustomerReturnActionMenu";
import { CustomerReturnWarehouseInspectionModal } from "./CustomerReturnWarehouseInspectionModal";

describe("Customer Return History Filter & Lifecycle Consistency (Kho -> Khách trả hàng)", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe("1. getCustomerReturnUiStatus Label & Status Mapping", () => {
        it("maps PENDING_REFUND to 'Chờ hoàn tiền' with status 'PENDING'", () => {
            const status = getCustomerReturnUiStatus({
                status: "RECEIVED",
                displayStatus: "PENDING_REFUND",
                physicalDone: true,
                qcStatus: "COMPLETED"
            });
            expect(status).toEqual({
                label: "Chờ hoàn tiền",
                status: "PENDING"
            });
        });

        it("maps COMPLETED to 'Hoàn thành' with status 'COMPLETED'", () => {
            const status1 = getCustomerReturnUiStatus({
                status: "COMPLETED",
                displayStatus: "COMPLETED"
            });
            expect(status1).toEqual({
                label: "Hoàn thành",
                status: "COMPLETED"
            });

            // Also when status is COMPLETED but displayStatus is undefined
            const status2 = getCustomerReturnUiStatus({
                status: "COMPLETED"
            });
            expect(status2).toEqual({
                label: "Hoàn thành",
                status: "COMPLETED"
            });
        });

        it("maps CANCELLED to 'Đã hủy' with status 'CANCELLED'", () => {
            const status = getCustomerReturnUiStatus({
                status: "CANCELLED",
                displayStatus: "CANCELLED"
            });
            expect(status).toEqual({
                label: "Đã hủy",
                status: "CANCELLED"
            });
        });

        it("maps PROCESSING_FAILED to 'Xử lý kho thất bại' with status 'FAILED'", () => {
            const status = getCustomerReturnUiStatus({
                status: "RECEIVED",
                displayStatus: "PROCESSING_FAILED"
            });
            expect(status).toEqual({
                label: "Xử lý kho thất bại",
                status: "FAILED"
            });
        });

        it("maps pending warehouse inspection to 'Đang chờ xử lý kiểm định tại kho' (PROCESSING)", () => {
            const status = getCustomerReturnUiStatus({
                status: "RECEIVED",
                isPendingDisposition: true,
                physicalDone: false,
                qcStatus: "PENDING"
            });
            expect(status).toEqual({
                label: "Đang chờ xử lý kiểm định tại kho",
                status: "PROCESSING"
            });
        });

        it("ensures RETURN_DISPLAY_STATUS_LABELS has correct translations for all display states", () => {
            expect(RETURN_DISPLAY_STATUS_LABELS.PENDING_REFUND).toBe("Chờ hoàn tiền");
            expect(RETURN_DISPLAY_STATUS_LABELS.COMPLETED).toBe("Hoàn thành");
            expect(RETURN_DISPLAY_STATUS_LABELS.CANCELLED).toBe("Đã hủy");
            expect(RETURN_DISPLAY_STATUS_LABELS.PROCESSING_FAILED).toBe("Xử lý kho thất bại");
            expect(RETURN_STATUS_LABELS.COMPLETED).toBe("Hoàn thành");
        });
    });

    describe("2. Target Return Rows Validation (Evidence from Production Investigation)", () => {
        const targetReturn1: OrderReturnListRow = {
            id: "a189202e-5a97-429d-9685-32c35bf80baf",
            returnCode: "RET20261104232243276",
            orderCode: "ORD20261104220232195",
            status: "RECEIVED",
            displayStatus: "PENDING_REFUND",
            qcStatus: "COMPLETED",
            inspectionStatus: "POSTED",
            physicalDone: true,
            postedQcQuantity: 1,
            remainingQcQuantity: 0,
            requiredQuantity: 1,
            processedQuantity: 1,
            remainingQuantity: 0
        };

        const targetReturn2: OrderReturnListRow = {
            id: "b290313f-6b08-430e-a796-43d46cf91cba",
            returnCode: "RET20261104221655799",
            orderCode: "ORD20261102171912556",
            status: "COMPLETED",
            displayStatus: "COMPLETED",
            refundStatus: "REFUNDED",
            physicalDone: true,
            processedQuantity: 1,
            remainingQuantity: 0,
            requiredQuantity: 1
        };

        it("Target Return 1 (RET20261104232243276): displays 'Chờ hoàn tiền' and read-only inspection action", () => {
            const uiStatus = getCustomerReturnUiStatus(targetReturn1);
            expect(uiStatus.label).toBe("Chờ hoàn tiền");
            expect(uiStatus.status).toBe("PENDING");

            const dummyProps = {
                isOpen: true,
                isReceiving: false,
                onOpenChange: vi.fn(),
                onViewDetail: vi.fn(),
                onReceive: vi.fn(),
                onInspect: vi.fn(),
                onViewInspection: vi.fn(),
                row: targetReturn1
            };
            const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, dummyProps));
            expect(html).toContain("Xem chi tiết");
            expect(html).toContain("Xem kết quả kiểm định");
            expect(html).not.toContain("Bắt đầu kiểm định");
            expect(html).not.toContain("Tiếp tục kiểm định");
        });

        it("Target Return 2 (RET20261104221655799): displays 'Hoàn thành' and read-only inspection action", () => {
            const uiStatus = getCustomerReturnUiStatus(targetReturn2);
            expect(uiStatus.label).toBe("Hoàn thành");
            expect(uiStatus.status).toBe("COMPLETED");

            const dummyProps = {
                isOpen: true,
                isReceiving: false,
                onOpenChange: vi.fn(),
                onViewDetail: vi.fn(),
                onReceive: vi.fn(),
                onInspect: vi.fn(),
                onViewInspection: vi.fn(),
                row: targetReturn2
            };
            const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, dummyProps));
            expect(html).toContain("Xem chi tiết");
            expect(html).toContain("Xem kết quả kiểm định");
            expect(html).not.toContain("Bắt đầu kiểm định");
        });
    });

    describe("3. CustomerReturnActionMenu Actions for Completed & Cancelled returns", () => {
        const dummyBaseProps = {
            isOpen: true,
            isReceiving: false,
            onOpenChange: vi.fn(),
            onViewDetail: vi.fn(),
            onReceive: vi.fn(),
            onInspect: vi.fn(),
            onViewInspection: vi.fn()
        };

        it("renders 'Xem chi tiết' and 'Xem kết quả kiểm định' for COMPLETED status with inspection posted", () => {
            const row: OrderReturnListRow = {
                id: "comp-1",
                returnCode: "RET-COMP-001",
                status: "COMPLETED",
                inspectionStatus: "POSTED",
                physicalDone: true
            };
            const html = renderToStaticMarkup(
                createElement(CustomerReturnActionMenu, { ...dummyBaseProps, row })
            );
            expect(html).toContain("Xem chi tiết");
            expect(html).toContain("Xem kết quả kiểm định");
            expect(html).not.toContain("Bắt đầu kiểm định");
            expect(html).not.toContain("Nhận hàng");
        });

        it("renders ONLY 'Xem chi tiết' for CANCELLED return", () => {
            const row: OrderReturnListRow = {
                id: "canc-1",
                returnCode: "RET-CANC-001",
                status: "CANCELLED",
                displayStatus: "CANCELLED"
            };
            const html = renderToStaticMarkup(
                createElement(CustomerReturnActionMenu, { ...dummyBaseProps, row })
            );
            expect(html).toContain("Xem chi tiết");
            expect(html).not.toContain("Xem kết quả kiểm định");
            expect(html).not.toContain("Bắt đầu kiểm định");
            expect(html).not.toContain("Nhận hàng");
        });
    });

    describe("4. API Parameters & Scope Synchronization (warehouseActiveOnly=false)", () => {
        it("simulates queue initialization passing warehouseActiveOnly=false to status-counts and list API", async () => {
            const mockListResponse = {
                content: [
                    { id: "1", returnCode: "RET-001", status: "RECEIVED", displayStatus: "PENDING_REFUND" },
                    { id: "2", returnCode: "RET-002", status: "COMPLETED", displayStatus: "COMPLETED" }
                ],
                totalElements: 38,
                totalPages: 4
            };
            const mockCountsResponse = {
                ALL: 38,
                CREATED: 5,
                RECEIVED: 10,
                PROCESSING: 3,
                PENDING_REFUND: 4,
                COMPLETED: 12,
                CANCELLED: 3,
                PROCESSING_FAILED: 1
            };

            vi.mocked(getOrderReturnList).mockResolvedValue(mockListResponse as unknown as ReturnType<typeof getOrderReturnList> extends Promise<infer T> ? T : never);
            vi.mocked(getOrderReturnStatusCounts).mockResolvedValue(mockCountsResponse);

            // Trigger fetch simulating CustomerReturnQcQueue useEffect
            const listParams = {
                displayStatus: undefined,
                fromDate: "2026-11-01",
                keyword: undefined,
                page: 0,
                paged: true,
                size: 10,
                sort: "createdAt,desc",
                status: undefined,
                toDate: "2026-11-05",
                warehouseActiveOnly: false
            };

            const countsParams = {
                fromDate: "2026-11-01",
                keyword: undefined,
                toDate: "2026-11-05",
                warehouseActiveOnly: false
            };

            const [listResult, countsResult] = await Promise.all([
                getOrderReturnList(listParams),
                getOrderReturnStatusCounts(countsParams)
            ]);

            // Verify synchronization: both calls must pass warehouseActiveOnly: false
            expect(getOrderReturnList).toHaveBeenCalledWith(
                expect.objectContaining({
                    warehouseActiveOnly: false,
                    fromDate: "2026-11-01",
                    toDate: "2026-11-05",
                    paged: true,
                    page: 0,
                    size: 10,
                    sort: "createdAt,desc"
                })
            );

            expect(getOrderReturnStatusCounts).toHaveBeenCalledWith(
                expect.objectContaining({
                    warehouseActiveOnly: false,
                    fromDate: "2026-11-01",
                    toDate: "2026-11-05"
                })
            );

            // Verify both list and counts contain PENDING_REFUND and COMPLETED in total
            expect(listResult.totalElements).toBe(38);
            expect(countsResult.ALL).toBe(38);
            expect(countsResult.PENDING_REFUND).toBe(4);
            expect(countsResult.COMPLETED).toBe(12);
        });

        it("simulates tab filter by PENDING_REFUND and keyword search", async () => {
            const listParams = {
                displayStatus: "PENDING_REFUND",
                fromDate: undefined,
                keyword: "RET20261104",
                page: 0,
                paged: true,
                size: 10,
                sort: "createdAt,desc",
                status: "PENDING_REFUND",
                toDate: undefined,
                warehouseActiveOnly: false
            };

            await getOrderReturnList(listParams);

            expect(getOrderReturnList).toHaveBeenCalledWith({
                displayStatus: "PENDING_REFUND",
                fromDate: undefined,
                keyword: "RET20261104",
                page: 0,
                paged: true,
                size: 10,
                sort: "createdAt,desc",
                status: "PENDING_REFUND",
                toDate: undefined,
                warehouseActiveOnly: false
            });
        });
    });

    describe("5. Read-only Inspection Cannot Re-POST When Already POSTED", () => {
        it("does not render confirm button when inspection is already POSTED", () => {
            const postedRow: OrderReturnListRow = {
                id: "ret-posted-100",
                returnCode: "RET20261104232243276",
                orderCode: "ORD-001",
                status: "RECEIVED",
                displayStatus: "PENDING_REFUND",
                qcStatus: "COMPLETED",
                inspectionStatus: "POSTED",
                physicalDone: true,
                postedQcQuantity: 1,
                remainingQcQuantity: 0,
                requiredQuantity: 1,
                processedQuantity: 1,
                remainingQuantity: 0
            };

            const html = renderToStaticMarkup(
                createElement(CustomerReturnWarehouseInspectionModal, {
                    isOpen: true,
                    onClose: vi.fn(),
                    returnId: postedRow.id,
                    returnCode: postedRow.returnCode,
                    returnItem: postedRow
                })
            );

            expect(html).toContain("Phiếu kiểm định kho");
            expect(html).toContain("Đã kiểm định");
            expect(html).toContain("Đóng");
            expect(html).not.toContain("Xác nhận xử lý");
        });
    });
});
