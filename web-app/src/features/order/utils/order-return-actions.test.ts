import { describe, expect, it } from "vitest";
import {
    canInspectCustomerReturn,
    getCustomerReturnQcProgressStatus,
    getCustomerReturnUiStatus,
    getReturnErrorMessage,
    resolveCustomerReturnQuantities
} from "./order-return.utils";

describe("Customer Return QC Actions & Reservation Rules", () => {
    it("blocks stale QC completion even when Order remaining has not caught up", () => {
        expect(canInspectCustomerReturn({ status: "RECEIVED", requiredQuantity: 1, remainingQuantity: 1,
            qcStatus: "COMPLETED", isPendingDisposition: false })).toBe(false);
    });

    it("allows a verified partial return to continue after its command is processed", () => {
        expect(canInspectCustomerReturn({ status: "RECEIVED", requiredQuantity: 3, remainingQuantity: 2,
            qcStatus: "PARTIAL", inventoryProcessingStatus: "PROCESSING", isPendingDisposition: false })).toBe(true);
    });

    it("blocks legacy FAILED responses which lack the new boolean flags", () => {
        expect(canInspectCustomerReturn({ status: "RECEIVED", requiredQuantity: 1, remainingQuantity: 1,
            inventoryProcessingStatus: "FAILED" })).toBe(false);
    });
    it("Scenario 1: Prevents inspection when a disposition command is in flight (isPendingDisposition = true)", () => {
        const row = {
            status: "RECEIVED",
            physicalDone: false,
            isPendingDisposition: true,
            hasFailedDisposition: false,
            remainingQuantity: 1,
            requiredQuantity: 1,
            processedQuantity: 0
        };
        expect(canInspectCustomerReturn(row)).toBe(false);
    });

    it("Scenario 2: Prevents inspection when displayStatus or inventoryProcessingStatus is PROCESSING", () => {
        expect(canInspectCustomerReturn({
            status: "RECEIVED",
            physicalDone: false,
            displayStatus: "PROCESSING",
            remainingQuantity: 1,
            requiredQuantity: 1
        })).toBe(false);

        expect(canInspectCustomerReturn({
            status: "RECEIVED",
            physicalDone: false,
            inventoryProcessingStatus: "PROCESSING",
            remainingQuantity: 1,
            requiredQuantity: 1
        })).toBe(false);
    });

    it("Scenario 3: Prevents inspection when remaining quantity is 0", () => {
        const row = {
            status: "RECEIVED",
            physicalDone: false,
            isPendingDisposition: false,
            remainingQuantity: 0,
            requiredQuantity: 1,
            processedQuantity: 1
        };
        expect(canInspectCustomerReturn(row)).toBe(false);
    });

    it("Scenario 4: Prevents inspection when physical disposition is already completed (physicalDone = true)", () => {
        const row = {
            status: "RECEIVED",
            physicalDone: true,
            isPendingDisposition: false,
            remainingQuantity: 0,
            requiredQuantity: 1,
            processedQuantity: 1
        };
        expect(canInspectCustomerReturn(row)).toBe(false);
    });

    it("Scenario 5: Prevents inspection when return status is not RECEIVED (CREATED, COMPLETED, CANCELLED)", () => {
        expect(canInspectCustomerReturn({ status: "CREATED", remainingQuantity: 1 })).toBe(false);
        expect(canInspectCustomerReturn({ status: "COMPLETED", remainingQuantity: 1 })).toBe(false);
        expect(canInspectCustomerReturn({ status: "CANCELLED", remainingQuantity: 1 })).toBe(false);
        expect(canInspectCustomerReturn(null)).toBe(false);
    });

    it("Scenario 6: Allows inspection when return is RECEIVED, not pending, not completed, and remaining quantity > 0", () => {
        const row = {
            status: "RECEIVED",
            physicalDone: false,
            isPendingDisposition: false,
            remainingQuantity: 2,
            requiredQuantity: 2,
            processedQuantity: 0
        };
        expect(canInspectCustomerReturn(row)).toBe(true);
    });

    it("Scenario 7: Blocks retry after command failure until reconciliation proves it safe", () => {
        const row = {
            status: "RECEIVED",
            displayStatus: "PROCESSING_FAILED",
            hasFailedDisposition: true,
            isPendingDisposition: false,
            physicalDone: false,
            remainingQuantity: 1,
            requiredQuantity: 1,
            processedQuantity: 0
        };
        expect(canInspectCustomerReturn(row)).toBe(false);
    });

    it("Scenario 8: Maps UI status badges accurately across lifecycle states", () => {
        expect(getCustomerReturnUiStatus({ status: "CREATED" })).toEqual({
            label: "Chờ nhận hàng",
            status: "PENDING"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", isPendingDisposition: true })).toEqual({
            label: "Đang xử lý",
            status: "PROCESSING"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", displayStatus: "PROCESSING_FAILED" })).toEqual({
            label: "Xử lý kho thất bại",
            status: "FAILED"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", hasFailedDisposition: true })).toEqual({
            label: "Xử lý kho thất bại",
            status: "FAILED"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", physicalDone: true })).toEqual({
            label: "Đã kiểm hàng",
            status: "QC_COMPLETED"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", qcStatus: "PARTIAL" })).toEqual({
            label: "Đang kiểm hàng",
            status: "PROCESSING"
        });

        expect(getCustomerReturnUiStatus({ status: "RECEIVED", qcStatus: "PENDING", remainingQuantity: 1 })).toEqual({
            label: "Chờ kiểm hàng",
            status: "PENDING"
        });
    });

    it("Scenario 9: Maps backend disposition error messages to clear user-facing messages", () => {
        const err1 = new Error("No remaining quantity available for inventory disposition");
        expect(getReturnErrorMessage(err1)).toBe(
            "Sản phẩm này đã được xử lý kho hoặc không còn số lượng cần xử lý. Vui lòng tải lại dữ liệu."
        );

        const err2 = new Error("Inventory disposition quantity exceeds return item quantity");
        expect(getReturnErrorMessage(err2)).toBe(
            "Sản phẩm này đã được xử lý kho hoặc không còn số lượng cần xử lý. Vui lòng tải lại dữ liệu."
        );
    });

    it("Scenario 10: Correctly resolves customer return quantities with fallback hierarchy", () => {
        // Direct quantities present
        expect(resolveCustomerReturnQuantities({
            requiredQuantity: 5,
            processedQuantity: 2,
            remainingQuantity: 3
        })).toMatchObject({ required: 5, processed: 2, remaining: 3 });

        // From items array and postedQcQuantity
        expect(resolveCustomerReturnQuantities({
            items: [{ quantity: 2 }, { quantity: 3 }],
            postedQcQuantity: 2,
            remainingQcQuantity: 3
        })).toMatchObject({ required: 5, processed: 2, remaining: 3 });

        // Null / undefined fallback
        expect(resolveCustomerReturnQuantities(null)).toMatchObject({ required: 0, processed: 0, remaining: 0 });
    });

    describe("Regression Test: RET20261104221655799 (QC COMPLETED + PENDING_REFUND)", () => {
        const liveReturnData = {
            id: "ret-20261104-live",
            returnCode: "RET20261104221655799",
            status: "RECEIVED" as const,
            physicalDone: true,
            requiredQuantity: 1,
            processedQuantity: 1,
            remainingQuantity: 0,
            inventoryProcessingStatus: "COMPLETED",
            displayStatus: "PENDING_REFUND" as const,
            qcStatus: "COMPLETED",
            inspectionStatus: "POSTED",
            postedQcQuantity: 1,
            remainingQcQuantity: 0,
            isPendingDisposition: false
        };

        it("maps overall UI status to 'Chờ hoàn tiền' (PENDING), NOT 'Hoàn tất' and NOT 'Đang chờ xử lý kiểm định tại kho'", () => {
            const uiStatus = getCustomerReturnUiStatus(liveReturnData);
            expect(uiStatus).toEqual({
                label: "Chờ hoàn tiền",
                status: "PENDING"
            });
            expect(uiStatus.label).not.toBe("Hoàn tất");
            expect(uiStatus.label).not.toBe("Đang chờ xử lý kiểm định tại kho");
        });

        it("ignores stale isPendingDisposition when physicalDone is true, maintaining 'Chờ hoàn tiền'", () => {
            const staleReturnData = {
                ...liveReturnData,
                isPendingDisposition: true
            };
            const uiStatus = getCustomerReturnUiStatus(staleReturnData);
            expect(uiStatus).toEqual({
                label: "Chờ hoàn tiền",
                status: "PENDING"
            });
        });

        it("maps QC progress status specifically to 'Đã kiểm hàng' (QC_COMPLETED)", () => {
            const qcStatus = getCustomerReturnUiStatus({
                status: "RECEIVED",
                physicalDone: true,
                qcStatus: "COMPLETED"
            });
            expect(qcStatus).toEqual({
                label: "Đã kiểm hàng",
                status: "QC_COMPLETED"
            });
        });

        it("correctly resolves 1/1 processed quantity and 0 remaining without discrepancy", () => {
            const quantities = resolveCustomerReturnQuantities(liveReturnData);
            expect(quantities).toEqual({
                required: 1,
                processed: 1,
                remaining: 0,
                source: "INVENTORY",
                hasDiscrepancy: false,
                orderProcessedQuantity: 1,
                inventoryPostedQuantity: 1
            });
        });

        it("blocks starting inspection when physicalDone is true and qcStatus is COMPLETED", () => {
            expect(canInspectCustomerReturn(liveReturnData)).toBe(false);
        });
    });

    describe("Regression Test: Discrepancy & Consistency Scenarios", () => {
        it("Scenario 1: Order 0/2, Inventory 1/2 -> Uses Inventory authoritative progress and flags discrepancy", () => {
            const data = {
                requiredQuantity: 2,
                processedQuantity: 0,
                remainingQuantity: 2,
                postedQcQuantity: 1,
                remainingQcQuantity: 1,
                qcStatus: "PARTIAL",
                status: "RECEIVED"
            };

            const resolved = resolveCustomerReturnQuantities(data);
            expect(resolved.required).toBe(2);
            expect(resolved.processed).toBe(1); // Authoritative from Inventory, NOT 0
            expect(resolved.remaining).toBe(1);
            expect(resolved.source).toBe("INVENTORY");
            expect(resolved.hasDiscrepancy).toBe(true);
            expect(resolved.orderProcessedQuantity).toBe(0);
            expect(resolved.inventoryPostedQuantity).toBe(1);

            const qcStatus = getCustomerReturnQcProgressStatus(data);
            expect(qcStatus).toEqual({
                label: "Đang kiểm hàng",
                status: "PROCESSING"
            });
        });

        it("Scenario 2: Order 2/2, Inventory 1/2 -> Does NOT force complete to 2/2 from physicalDone; uses Inventory 1/2", () => {
            const data = {
                requiredQuantity: 2,
                processedQuantity: 2,
                remainingQuantity: 0,
                physicalDone: true,
                postedQcQuantity: 1,
                remainingQcQuantity: 1,
                qcStatus: "PARTIAL",
                status: "RECEIVED"
            };

            const resolved = resolveCustomerReturnQuantities(data);
            expect(resolved.required).toBe(2);
            expect(resolved.processed).toBe(1); // Stays at 1 from Inventory, does NOT force 2
            expect(resolved.remaining).toBe(1); // Does NOT force 0
            expect(resolved.source).toBe("INVENTORY");
            expect(resolved.hasDiscrepancy).toBe(true);
            expect(resolved.orderProcessedQuantity).toBe(2);
            expect(resolved.inventoryPostedQuantity).toBe(1);

            // QC progress status must reflect Inventory PARTIAL, not Order physicalDone
            const qcStatus = getCustomerReturnQcProgressStatus(data);
            expect(qcStatus).toEqual({
                label: "Đang kiểm hàng",
                status: "PROCESSING"
            });
            expect(qcStatus.label).not.toBe("Đã kiểm hàng");
        });

        it("Scenario 3: Inspection POSTED but whole return is PARTIAL -> Does NOT mark whole return as completed", () => {
            const data = {
                requiredQuantity: 2,
                status: "RECEIVED",
                inspectionStatus: "POSTED",
                qcStatus: "PARTIAL",
                postedQcQuantity: 1,
                remainingQcQuantity: 1
            };

            const qcStatus = getCustomerReturnQcProgressStatus(data);
            expect(qcStatus).toEqual({
                label: "Đang kiểm hàng",
                status: "PROCESSING"
            });

            // Eligibility allows continuing QC for remaining 1 item
            expect(canInspectCustomerReturn({ ...data, isPendingDisposition: false })).toBe(true);
        });

        it("Scenario 4: QC Summary not loaded (null) -> Uses Order fallback with clear source and does not synthesize completion", () => {
            const dataWithOrderFallback = {
                requiredQuantity: 2,
                processedQuantity: 1,
                remainingQuantity: 1,
                postedQcQuantity: null,
                remainingQcQuantity: null,
                qcStatus: null
            };

            const resolvedFallback = resolveCustomerReturnQuantities(dataWithOrderFallback);
            expect(resolvedFallback.source).toBe("ORDER_FALLBACK");
            expect(resolvedFallback.processed).toBe(1);
            expect(resolvedFallback.remaining).toBe(1);
            expect(resolvedFallback.hasDiscrepancy).toBe(false);

            const emptyData = {
                requiredQuantity: 2,
                items: [{ quantity: 2 }]
            };
            const resolvedEmpty = resolveCustomerReturnQuantities(emptyData);
            expect(resolvedEmpty.source).toBe("UNAVAILABLE");
            expect(resolvedEmpty.processed).toBe(0);
            expect(resolvedEmpty.remaining).toBe(2);
        });

        it("Scenario 5: Command PENDING old but physicalDone=true -> Lifecycle stays 'Chờ hoàn tiền', pending guard active when physicalDone=false", () => {
            // Case A: physicalDone=true with stale pending flag
            const stalePending = {
                status: "RECEIVED",
                physicalDone: true,
                displayStatus: "PENDING_REFUND",
                isPendingDisposition: true,
                qcStatus: "COMPLETED"
            };
            expect(getCustomerReturnUiStatus(stalePending)).toEqual({
                label: "Chờ hoàn tiền",
                status: "PENDING"
            });

            // Case B: physicalDone=false with active pending command -> strictly guarded
            const activePending = {
                status: "RECEIVED",
                physicalDone: false,
                displayStatus: "PROCESSING",
                isPendingDisposition: true,
                requiredQuantity: 2,
                remainingQuantity: 2
            };
            expect(getCustomerReturnUiStatus(activePending)).toEqual({
                label: "Đang xử lý",
                status: "PROCESSING"
            });
            expect(canInspectCustomerReturn(activePending)).toBe(false);
        });
    });
});

