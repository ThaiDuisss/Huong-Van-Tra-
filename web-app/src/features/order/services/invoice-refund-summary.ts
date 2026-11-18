import { getOrderRefundDetail, getOrderReturns } from "./order-return.api";

/** Read the order-level cumulative amount from Payment evidence, never sum it per return. */
export async function getInvoiceRefundedAmount(orderId: string): Promise<number> {
    const returns = await getOrderReturns(orderId);
    const candidates = returns.filter(row => row.refundRequired !== false && row.status !== "CANCELLED");
    const selected = candidates.find(row => row.status === "COMPLETED") ?? candidates[0];
    if (!selected) throw new Error("Refund evidence is unavailable");
    const detail = await getOrderRefundDetail(selected.id);
    const raw = detail.totalAmountRefunded;
    const amount = raw == null || String(raw).trim() === "" ? NaN : Number(raw);
    if (detail.orderId !== orderId || !Number.isFinite(amount) || amount < 0) {
        throw new Error("Refund evidence is unavailable");
    }
    return amount;
}
