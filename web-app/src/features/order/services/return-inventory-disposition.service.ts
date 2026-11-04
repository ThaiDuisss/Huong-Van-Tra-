import { ApiError } from "@/lib/api-client";
import type { SubmitReturnInventoryDispositionPayload } from "../types/order-return.types";
import { canInspectCustomerReturn, toReturnNumber } from "../utils/order-return.utils";
import { getOrderReturnDetail, getCustomerReturnQcSummary, submitReturnInventoryDisposition } from "./order-return.api";

export async function submitVerifiedReturnInventoryDisposition(returnId: string, request: SubmitReturnInventoryDispositionPayload) {
    const [latest, summary] = await Promise.all([getOrderReturnDetail(returnId), getCustomerReturnQcSummary(returnId)]);
    if (latest.id !== returnId || summary.returnId !== returnId || summary.returnStatus !== "RECEIVED"
        || !canInspectCustomerReturn({ ...latest, qcStatus: summary.qcStatus })
        || request.lines.some((line) => line.quantity > toReturnNumber(
            summary.lines?.find((candidate) => candidate.returnItemId === line.returnItemId)?.remainingQcQuantity))) {
        throw new ApiError({ status: 409, code: "RETURN_QC_STATE_CHANGED",
            message: "Phiếu đã thay đổi hoặc có yêu cầu đang chờ đối soát. Vui lòng làm mới." });
    }
    return submitReturnInventoryDisposition(returnId, request);
}
