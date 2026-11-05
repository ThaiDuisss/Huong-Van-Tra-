import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

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

// Mock role-utils so tests can run without full Keycloak / Auth context
vi.mock("@/auth/role-utils", () => ({
    CUSTOMER_RETURN_QC_ROLES: ["ROLE_SUPER_ADMIN", "ROLE_MANAGER", "ROLE_QC"],
    useAuthorization: () => ({
        hasAnyRole: () => true,
        hasRole: () => true
    })
}));

import {
    canInspectCustomerReturn,
    getCustomerReturnQcProgressStatus,
    resolveCustomerReturnQuantities
} from "../../order/utils/order-return.utils";
import type { OrderReturnListRow } from "../../order/types/order-return.types";
import { CustomerReturnActionMenu } from "./CustomerReturnActionMenu";

describe("canInspectCustomerReturn & Customer Return QC", () => {
    it("returns false for undefined or null data", () => {
        expect(canInspectCustomerReturn(null)).toBe(false);
        expect(canInspectCustomerReturn(undefined)).toBe(false);
    });

    it("returns false when return status is CREATED (pending receipt)", () => {
        const row: OrderReturnListRow = {
            id: "ret-1",
            status: "CREATED",
            itemCount: 2
        };
        expect(canInspectCustomerReturn(row)).toBe(false);
        expect(getCustomerReturnQcProgressStatus(row).status).toBe("PENDING");
    });

    it("returns false when return is already physicalDone or pending disposition", () => {
        expect(
            canInspectCustomerReturn({
                status: "RECEIVED",
                physicalDone: true,
                itemCount: 2
            })
        ).toBe(false);

        expect(
            canInspectCustomerReturn({
                status: "RECEIVED",
                isPendingDisposition: true,
                itemCount: 2
            })
        ).toBe(false);
    });

    it("blocks an incomplete summary until remaining quantities are refreshed", () => {
        const summaryRow: OrderReturnListRow = {
            id: "ret-100",
            returnCode: "RET-20261104-001",
            orderCode: "ORD-001",
            status: "RECEIVED",
            itemCount: 3,
            physicalDone: false,
            isPendingDisposition: false
        };

        expect(canInspectCustomerReturn(summaryRow)).toBe(false);
        expect(getCustomerReturnQcProgressStatus(summaryRow).status).toBe("PENDING");
    });

    it("returns false when all items are inspected (remaining is 0)", () => {
        const completedRow: OrderReturnListRow = {
            id: "ret-102",
            status: "RECEIVED",
            requiredQuantity: 5,
            processedQuantity: 5,
            remainingQuantity: 0,
            qcStatus: "COMPLETED",
            physicalDone: true
        };

        expect(canInspectCustomerReturn(completedRow)).toBe(false);
        expect(getCustomerReturnQcProgressStatus(completedRow).status).toBe("QC_COMPLETED");
    });

    it("resolves quantities correctly when detailed items are provided", () => {
        const detailData = {
            items: [{ quantity: 3 }, { quantity: 2 }],
            processedQuantity: 2
        };

        const resolved = resolveCustomerReturnQuantities(detailData);
        expect(resolved.required).toBe(5);
        expect(resolved.processed).toBe(2);
        expect(resolved.remaining).toBe(3);
    });
});

describe("CustomerReturnActionMenu - 12 Lifecycle & QC Scenarios", () => {
    const dummyProps = {
        isOpen: true,
        isReceiving: false,
        onOpenChange: vi.fn(),
        onViewDetail: vi.fn(),
        onReceive: vi.fn(),
        onInspect: vi.fn(),
        onViewInspection: vi.fn()
    };

    it("Scenario 1: Command chưa tồn tại -> Hiển thị Bắt đầu kiểm định", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-a",
            returnCode: "RET-001",
            status: "RECEIVED",
            requiredQuantity: 2,
            remainingQuantity: 2,
            isPendingDisposition: false,
            physicalDone: false
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).toContain("Bắt đầu kiểm định");
        expect(html).toContain("Xem chi tiết");
    });

    it("Scenario 2: Command PENDING, inspectionId=null -> Chờ, không hiển thị Bắt đầu kiểm định (không gửi trùng)", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-b",
            returnCode: "RET-002",
            status: "RECEIVED",
            requiredQuantity: 1,
            remainingQuantity: 0,
            isPendingDisposition: true,
            displayStatus: "PROCESSING"
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).not.toContain("Bắt đầu kiểm định");
        expect(html).not.toContain("Kiểm định hàng");
        expect(html).toContain("Xem phiếu kiểm định kho");
    });

    it("Scenario 3 & 4: Inspection CREATED (chưa post) -> Hiển thị Mở phiếu kiểm định", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-c",
            returnCode: "RET-003",
            status: "RECEIVED",
            inspectionStatus: "CREATED",
            requiredQuantity: 2,
            remainingQuantity: 2,
            isPendingDisposition: false
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).toContain("Mở phiếu kiểm định");
        expect(html).not.toContain("Bắt đầu kiểm định");
    });

    it("Scenario 5: Inspection đang xử lý (PROCESSING) -> Hiển thị Tiếp tục kiểm định", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-d",
            returnCode: "RET-004",
            status: "RECEIVED",
            inspectionStatus: "PROCESSING",
            qcStatus: "PARTIAL",
            requiredQuantity: 2,
            remainingQuantity: 1,
            isPendingDisposition: false
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).toContain("Tiếp tục kiểm định");
        expect(html).not.toContain("Bắt đầu kiểm định");
    });

    it("Scenario 6: Inspection đã POSTED -> Hiển thị Xem kết quả kiểm định (chỉ đọc)", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-e",
            returnCode: "RET-005",
            status: "RECEIVED",
            inspectionStatus: "POSTED",
            qcStatus: "COMPLETED",
            physicalDone: true,
            requiredQuantity: 2,
            remainingQuantity: 0,
            isPendingDisposition: false
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).toContain("Xem kết quả kiểm định");
        expect(html).not.toContain("Bắt đầu kiểm định");
        expect(html).not.toContain("Mở phiếu kiểm định");
        expect(html).not.toContain("Tiếp tục kiểm định");
    });

    it("Scenario 7: Command FAILED -> Không auto-retry, hiển thị Xem phiếu kiểm định kho", () => {
        const row: OrderReturnListRow = {
            id: "ret-case-f",
            returnCode: "RET-006",
            status: "RECEIVED",
            hasFailedDisposition: true,
            displayStatus: "PROCESSING_FAILED",
            requiredQuantity: 1,
            remainingQuantity: 1
        };
        const html = renderToStaticMarkup(createElement(CustomerReturnActionMenu, { ...dummyProps, row }));
        expect(html).toContain("Xem chi tiết");
        expect(html).toContain("Xem phiếu kiểm định kho");
    });

    it("renders action menu trigger button for a customer return row", () => {
        const row: OrderReturnListRow = {
            id: "ret-1",
            returnCode: "RET-001",
            status: "RECEIVED",
            itemCount: 2
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnActionMenu, {
                ...dummyProps,
                row,
                isOpen: false
            })
        );

        expect(html).toContain("Mở thao tác cho phiếu RET-001");
    });

    it("renders Nhận hàng action when row status is CREATED", () => {
        const row: OrderReturnListRow = {
            id: "ret-2",
            returnCode: "RET-002",
            status: "CREATED",
            itemCount: 1
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnActionMenu, {
                ...dummyProps,
                row,
                isOpen: true
            })
        );

        expect(html).toContain("Xem chi tiết");
        expect(html).toContain("Nhận hàng");
        expect(html).not.toContain("Bắt đầu kiểm định");
    });

    it("Regression Test: RET20261104221655799 renders 'Xem chi tiết' and 'Xem kết quả kiểm định'", () => {
        const liveRow: OrderReturnListRow = {
            id: "a189202e-5a97-429d-9685-32c35bf80baf",
            returnCode: "RET20261104221655799",
            orderCode: "ORD20261102171934829",
            status: "RECEIVED",
            physicalDone: true,
            requiredQuantity: 1,
            processedQuantity: 1,
            remainingQuantity: 0,
            inventoryProcessingStatus: "COMPLETED",
            displayStatus: "PENDING_REFUND",
            qcStatus: "COMPLETED",
            inspectionStatus: "POSTED",
            postedQcQuantity: 1,
            remainingQcQuantity: 0,
            isPendingDisposition: false
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnActionMenu, {
                ...dummyProps,
                row: liveRow,
                isOpen: true
            })
        );

        expect(html).toContain("Xem chi tiết");
        expect(html).toContain("Xem kết quả kiểm định");
        expect(html).not.toContain("Bắt đầu kiểm định");
        expect(html).not.toContain("Mở phiếu kiểm định");
        expect(html).not.toContain("Tiếp tục kiểm định");
    });

    it("Regression Test: Inspection is POSTED but return is PARTIAL -> Renders 'Tiếp tục kiểm định' for remaining", () => {
        const partialPostedRow: OrderReturnListRow = {
            id: "ret-partially-posted-1",
            returnCode: "RET-PARTIAL-002",
            orderCode: "ORD-002",
            status: "RECEIVED",
            requiredQuantity: 2,
            processedQuantity: 1,
            remainingQuantity: 1,
            inspectionStatus: "POSTED",
            qcStatus: "PARTIAL",
            postedQcQuantity: 1,
            remainingQcQuantity: 1,
            isPendingDisposition: false
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnActionMenu, {
                ...dummyProps,
                row: partialPostedRow,
                isOpen: true
            })
        );

        expect(html).toContain("Xem chi tiết");
        expect(html).toContain("Tiếp tục kiểm định");
        expect(html).toContain("Xem kết quả kiểm định");
    });
});
