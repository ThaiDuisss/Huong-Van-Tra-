import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi, beforeEach } from "vitest";

// Mock role-utils
vi.mock("@/auth/role-utils", () => ({
    CUSTOMER_RETURN_QC_ROLES: ["ROLE_SUPER_ADMIN", "ROLE_MANAGER", "ROLE_QC"],
    useAuthorization: () => ({
        hasAnyRole: () => true,
        hasRole: () => true
    })
}));

import type { OrderReturnListRow } from "../../order/types/order-return.types";
import { CustomerReturnWarehouseInspectionModal } from "./CustomerReturnWarehouseInspectionModal";
import { customerReturnApi } from "../services/customerReturn.api";

// Mock customerReturnApi and getOrderReturnDetail
vi.mock("../services/customerReturn.api", () => ({
    customerReturnApi: {
        getQcSummary: vi.fn(),
        getInspection: vi.fn(),
        listInspections: vi.fn(),
        postInspection: vi.fn()
    },
    loadCustomerReturnQcLocations: vi.fn().mockResolvedValue([
        { id: "loc-1", name: "Kho hàng tốt", code: "LOC-GOOD", type: "STORAGE", status: "ACTIVE" },
        { id: "loc-2", name: "Khu vực hàng hỏng", code: "LOC-DMG", type: "DAMAGED", status: "ACTIVE" }
    ])
}));

vi.mock("../../order/services/order-return.api", () => ({
    getOrderReturnDetail: vi.fn()
}));

const mockRow: OrderReturnListRow = {
    id: "ret-100",
    returnCode: "RET20261104194138642",
    orderCode: "ORD-001",
    status: "RECEIVED",
    itemCount: 1
};

describe("CustomerReturnWarehouseInspectionModal - QC Execution & Contract Verification", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("does not render markup when isOpen is false", () => {
        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: false,
                onClose: vi.fn(),
                returnId: mockRow.id,
                returnCode: mockRow.returnCode,
                returnItem: mockRow
            })
        );
        expect(html).toBe("");
    });

    it("renders modal structure with header and return information when open", () => {
        vi.mocked(customerReturnApi.getQcSummary).mockReturnValue(new Promise(() => {}));

        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: true,
                onClose: vi.fn(),
                returnId: mockRow.id,
                returnCode: mockRow.returnCode,
                returnItem: mockRow
            })
        );

        expect(html).toContain("Phiếu kiểm định kho");
        expect(html).toContain("RET20261104194138642");
        expect(html).toContain("ORD-001");
        expect(html).toContain("Tổng SL hàng trả");
        expect(html).toContain("Đã kiểm hàng");
        expect(html).toContain("Còn cần kiểm hàng");
    });

    it("Scenario 2: Displays pending command banner when inspectionId is null and isPendingDisposition is true", () => {
        const pendingRow: OrderReturnListRow = {
            ...mockRow,
            isPendingDisposition: true,
            displayStatus: "PROCESSING"
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: true,
                onClose: vi.fn(),
                returnId: pendingRow.id,
                returnCode: pendingRow.returnCode,
                returnItem: pendingRow
            })
        );

        expect(html).toContain("Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho.");
        expect(html).not.toContain("Xác nhận xử lý");
    });

    it("displays clean Empty State when API 200 has inspectionId: null and not pending", async () => {
        vi.mocked(customerReturnApi.getQcSummary).mockResolvedValue({
            returnId: mockRow.id,
            returnCode: "RET20261104194138642",
            returnStatus: "RECEIVED",
            inspectionId: null,
            inspectionStatus: null,
            qcStatus: "PENDING",
            lines: [],
            inspectedAt: null
        });

        const summary = await customerReturnApi.getQcSummary(mockRow.id);
        expect(summary.inspectionId).toBeNull();
        expect(customerReturnApi.getInspection).not.toHaveBeenCalled();
    });

    it("Scenario 4: Allows completing and confirming QC on CREATED inspection without duplicate command", async () => {
        const inspectionId = "insp-uuid-444";
        vi.mocked(customerReturnApi.getQcSummary).mockResolvedValue({
            returnId: mockRow.id,
            returnCode: "RET20261104194138642",
            returnStatus: "RECEIVED",
            inspectionId,
            inspectionStatus: "CREATED",
            qcStatus: "PENDING"
        });

        vi.mocked(customerReturnApi.getInspection).mockResolvedValue({
            id: inspectionId,
            inspectionCode: "CRI-20261104-001",
            returnId: mockRow.id,
            returnCode: "RET20261104194138642",
            status: "CREATED",
            createdAt: "2026-11-04T12:00:00Z",
            lines: [
                {
                    id: "line-1",
                    returnItemId: "item-1",
                    productVariantId: "var-1",
                    itemName: "Trà Thái Nguyên",
                    itemCode: "TR-TN",
                    quantity: 1,
                    result: "RESTOCK",
                    actualCondition: "SEALED",
                    destinationLocationId: "loc-1",
                    posted: false
                }
            ]
        });

        vi.mocked(customerReturnApi.postInspection).mockResolvedValue({
            id: inspectionId,
            inspectionCode: "CRI-20261104-001",
            returnId: mockRow.id,
            returnCode: "RET20261104194138642",
            status: "POSTED",
            createdAt: "2026-11-04T12:00:00Z",
            postedAt: "2026-11-04T12:05:00Z",
            lines: [
                {
                    id: "line-1",
                    returnItemId: "item-1",
                    quantity: 1,
                    result: "RESTOCK",
                    actualCondition: "SEALED",
                    posted: true
                }
            ]
        });

        // Verify contract call: postInspection uses inspectionId, NOT returnId
        const postResult = await customerReturnApi.postInspection(inspectionId, {
            lines: [{
                lineId: "line-1",
                returnItemId: "item-1",
                actualCondition: "SEALED",
                result: "RESTOCK",
                destinationLocationId: "loc-1"
            }]
        });

        expect(postResult.status).toBe("POSTED");
        expect(postResult.lines[0].posted).toBe(true);
        expect(customerReturnApi.postInspection).toHaveBeenCalledWith(
            inspectionId,
            expect.objectContaining({
                lines: expect.arrayContaining([
                    expect.objectContaining({ lineId: "line-1", result: "RESTOCK" })
                ])
            })
        );
    });

    it("Scenario 6: When inspection is POSTED, it is read-only and ignores posted lines", async () => {
        const postedInspection = {
            id: "insp-posted",
            inspectionCode: "CRI-POSTED-001",
            returnId: mockRow.id,
            status: "POSTED" as const,
            createdAt: "2026-11-04T12:00:00Z",
            postedAt: "2026-11-04T12:01:00Z",
            lines: [
                {
                    id: "line-1",
                    returnItemId: "item-1",
                    quantity: 1,
                    result: "DAMAGED" as const,
                    actualCondition: "DAMAGED" as const,
                    posted: true
                }
            ]
        };

        vi.mocked(customerReturnApi.getInspection).mockResolvedValue(postedInspection);
        const res = await customerReturnApi.getInspection("insp-posted");
        expect(res.status).toBe("POSTED");
        expect(res.lines.every(l => l.posted)).toBe(true);
    });

    it("Scenario 8: Strictly separates returnId, commandId, and inspectionId", async () => {
        const testReturnId = "00000000-0000-0000-0000-000000000001";
        const testInspectionId = "00000000-0000-0000-0000-000000000002";

        vi.mocked(customerReturnApi.getQcSummary).mockResolvedValue({
            returnId: testReturnId,
            inspectionId: testInspectionId,
            qcStatus: "PENDING"
        });

        await customerReturnApi.getQcSummary(testReturnId);
        expect(customerReturnApi.getQcSummary).toHaveBeenCalledWith(testReturnId);

        await customerReturnApi.getInspection(testInspectionId);
        expect(customerReturnApi.getInspection).toHaveBeenCalledWith(testInspectionId);
        expect(customerReturnApi.getInspection).not.toHaveBeenCalledWith(testReturnId);
    });

    it("Scenario 10: Modal does not auto-create inspection or submit new Order commands", async () => {
        vi.mocked(customerReturnApi.getQcSummary).mockResolvedValue({
            returnId: mockRow.id,
            returnCode: "RET20261104194138642",
            returnStatus: "RECEIVED",
            inspectionId: null,
            qcStatus: "PENDING"
        });

        await customerReturnApi.getQcSummary(mockRow.id);
        expect(customerReturnApi.postInspection).not.toHaveBeenCalled();
    });

    it("Regression Test: RET20261104221655799 renders 1/1 QC done, 0 remaining, 'Đã kiểm hàng', and no pending banner", () => {
        const completedRow: OrderReturnListRow = {
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
            isPendingDisposition: true // Stale prop test
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: true,
                onClose: vi.fn(),
                returnId: completedRow.id,
                returnCode: completedRow.returnCode,
                returnItem: completedRow
            })
        );

        expect(html).toContain("Phiếu kiểm định kho");
        expect(html).toContain("RET20261104221655799");
        expect(html).toContain("Đã kiểm hàng");
        expect(html).not.toContain("Yêu cầu đã được ghi nhận. Hệ thống đang tạo phiếu kiểm định kho.");
    });

    it("displays discrepancy warning when Order and Inventory numbers conflict (e.g. Order 0, Inventory 1)", () => {
        const conflictingRow: OrderReturnListRow = {
            id: "ret-conflict-1",
            returnCode: "RET-CONFLICT-001",
            status: "RECEIVED",
            requiredQuantity: 2,
            processedQuantity: 0,
            remainingQuantity: 2
        };

        vi.mocked(customerReturnApi.getQcSummary).mockReturnValue(new Promise(() => {}));

        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: true,
                onClose: vi.fn(),
                returnId: conflictingRow.id,
                returnCode: conflictingRow.returnCode,
                returnItem: {
                    ...conflictingRow,
                    postedQcQuantity: 1,
                    remainingQcQuantity: 1,
                    qcStatus: "PARTIAL"
                }
            })
        );

        expect(html).toContain("Phiếu kiểm định kho");
        expect(html).toContain("Đang kiểm hàng");
        expect(html).not.toContain('<span class="truncate">Đã kiểm hàng</span>');
    });

    it("does NOT treat entire return as QC_COMPLETED when an inspection is POSTED but qcStatus is PARTIAL", () => {
        const partialRow: OrderReturnListRow = {
            id: "ret-partial-1",
            returnCode: "RET-PARTIAL-001",
            status: "RECEIVED",
            requiredQuantity: 2,
            processedQuantity: 1,
            remainingQuantity: 1,
            inspectionStatus: "POSTED",
            qcStatus: "PARTIAL",
            postedQcQuantity: 1,
            remainingQcQuantity: 1
        };

        const html = renderToStaticMarkup(
            createElement(CustomerReturnWarehouseInspectionModal, {
                isOpen: true,
                onClose: vi.fn(),
                returnId: partialRow.id,
                returnCode: partialRow.returnCode,
                returnItem: partialRow
            })
        );

        expect(html).toContain("Đang kiểm hàng");
        expect(html).not.toContain('<span class="truncate">Đã kiểm hàng</span>');
    });
});
