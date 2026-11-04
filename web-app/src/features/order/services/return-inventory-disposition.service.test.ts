import { beforeEach, describe, expect, it, vi } from "vitest";
import { getOrderReturnDetail, getCustomerReturnQcSummary, submitReturnInventoryDisposition } from "./order-return.api";
import { submitVerifiedReturnInventoryDisposition } from "./return-inventory-disposition.service";

vi.mock("./order-return.api", () => ({
    getOrderReturnDetail: vi.fn(), getCustomerReturnQcSummary: vi.fn(), submitReturnInventoryDisposition: vi.fn()
}));

const request = { commandId: "8aea3781-0886-4fe0-a57a-2ec5187f30ba", lines: [
    { returnItemId: "item-1", quantity: 1, result: "WRITE_OFF" as const, destinationLocationId: null, note: null }
] };

describe("refresh before submitting a new disposition identity", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(getOrderReturnDetail).mockResolvedValue({ id: "return-1", status: "RECEIVED",
            isPendingDisposition: false, requiredQuantity: 3, processedQuantity: 1, remainingQuantity: 2 });
        vi.mocked(getCustomerReturnQcSummary).mockResolvedValue({ returnId: "return-1", returnStatus: "RECEIVED", qcStatus: "PARTIAL",
            lines: [{ returnItemId: "item-1", remainingQcQuantity: 2 }] });
    });

    it.each([
        { isPendingDisposition: true }, { hasFailedDisposition: true }, { inventoryProcessingStatus: "FAILED" },
        { physicalDone: true }, { remainingQuantity: 0 }
    ])("blocks a conflicting refreshed Order state: %j", async (state) => {
        const current = await getOrderReturnDetail("return-1");
        vi.mocked(getOrderReturnDetail).mockResolvedValue({ ...current, ...state });
        await expect(submitVerifiedReturnInventoryDisposition("return-1", request)).rejects.toMatchObject({ status: 409 });
        expect(submitReturnInventoryDisposition).not.toHaveBeenCalled();
    });

    it("blocks stale Order remaining when Inventory has already posted that unit", async () => {
        vi.mocked(getCustomerReturnQcSummary).mockResolvedValue({ returnId: "return-1", returnStatus: "RECEIVED", qcStatus: "PARTIAL",
            lines: [{ returnItemId: "item-1", remainingQcQuantity: 0 }] });
        await expect(submitVerifiedReturnInventoryDisposition("return-1", request)).rejects.toMatchObject({ status: 409 });
        expect(submitReturnInventoryDisposition).not.toHaveBeenCalled();
    });

    it("requires a successful refresh of both sources", async () => {
        vi.mocked(getCustomerReturnQcSummary).mockRejectedValue(new Error("Inventory unavailable"));
        await expect(submitVerifiedReturnInventoryDisposition("return-1", request)).rejects.toThrow("Inventory unavailable");
        expect(submitReturnInventoryDisposition).not.toHaveBeenCalled();
    });

    it("rejects a response cached for a different return", async () => {
        const current = await getOrderReturnDetail("return-1");
        vi.mocked(getOrderReturnDetail).mockResolvedValue({ ...current, id: "return-2" });
        await expect(submitVerifiedReturnInventoryDisposition("return-1", request)).rejects.toMatchObject({ status: 409 });
        expect(submitReturnInventoryDisposition).not.toHaveBeenCalled();
    });

    it("allows a verified partial action with its exact commandId", async () => {
        await submitVerifiedReturnInventoryDisposition("return-1", request);
        expect(getOrderReturnDetail).toHaveBeenCalledWith("return-1");
        expect(getCustomerReturnQcSummary).toHaveBeenCalledWith("return-1");
        expect(submitReturnInventoryDisposition).toHaveBeenCalledExactlyOnceWith("return-1", request);
    });
});
