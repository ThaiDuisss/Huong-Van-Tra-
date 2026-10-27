import { useEffect, useMemo, useState } from "react";
import { Printer, ReceiptText } from "lucide-react";

import { getSalesOrderDetail } from "../../../order/services/sales-history.api";
import type { SalesOrderDetail } from "../../../order/types/sales-history.types";
import {
    buildReceiptHtml,
    printReceipt
} from "../../../order/utils/receipt-print";

interface ReceiptDialogProps {
    /** Mã định danh đơn vừa thanh toán trên máy chủ; null thì không tải. */
    orderId: string | null;
    /** Hoá đơn dựng tại máy (đơn bán offline, mã tạm); ưu tiên hơn orderId. */
    localOrder?: SalesOrderDetail | null;
    /** Tiêu đề thay cho "Thanh toán thành công" (vd đơn offline). */
    title?: string;
    /** Ghi chú dưới tiêu đề, vd nhắc đơn sẽ đồng bộ sau. */
    hint?: string;
    onClose: () => void;
}

export function ReceiptDialog({ orderId, localOrder, title, hint, onClose }: ReceiptDialogProps) {
    if (localOrder) {
        return (
            <ReceiptDialogContent
                key={localOrder.orderCode ?? "local"}
                localOrder={localOrder}
                title={title}
                hint={hint}
                onClose={onClose}
            />
        );
    }

    if (!orderId) {
        return null;
    }

    // key theo orderId để mỗi hóa đơn có trạng thái tải riêng.
    return (
        <ReceiptDialogContent
            key={orderId}
            orderId={orderId}
            title={title}
            hint={hint}
            onClose={onClose}
        />
    );
}

interface ReceiptDialogContentProps {
    orderId?: string;
    localOrder?: SalesOrderDetail;
    title?: string;
    hint?: string;
    onClose: () => void;
}

function ReceiptDialogContent({
    orderId,
    localOrder,
    title = "Thanh toán thành công",
    hint,
    onClose
}: ReceiptDialogContentProps) {
    const [order, setOrder] = useState<SalesOrderDetail | null>(localOrder ?? null);
    const [loading, setLoading] = useState(!localOrder);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!orderId || localOrder) {
            return undefined;
        }

        let ignore = false;

        getSalesOrderDetail(orderId)
            .then((detail) => {
                if (!ignore) {
                    setOrder(detail);
                }
            })
            .catch(() => {
                if (!ignore) {
                    setError("Không tải được hóa đơn. Bạn có thể in lại ở mục Bán hàng hôm nay.");
                }
            })
            .finally(() => {
                if (!ignore) {
                    setLoading(false);
                }
            });

        return () => {
            ignore = true;
        };
    }, [orderId, localOrder]);

    const receiptHtml = useMemo(
        () => (order ? buildReceiptHtml(order) : ""),
        [order]
    );

    function handlePrint() {
        if (!order) {
            return;
        }

        try {
            printReceipt(order);
        } catch (printError) {
            setError(
                printError instanceof Error
                    ? printError.message
                    : "Không in được hóa đơn."
            );
        }
    }

    return (
        <div
            className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
            role="presentation"
        >
            <section
                aria-labelledby="receipt-dialog-title"
                aria-modal="true"
                className="grid max-h-[92vh] w-full max-w-[460px] grid-rows-[auto_1fr_auto] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start gap-3 border-b border-[#e8eef0] px-5 py-4">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-[#159734]">
                        <ReceiptText aria-hidden="true" size={22} />
                    </span>
                    <div className="grid gap-0.5">
                        <h2
                            className="text-xl font-black text-[#172317]"
                            id="receipt-dialog-title"
                        >
                            {title}
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            {order?.orderCode
                                ? `Hóa đơn ${order.orderCode}`
                                : "Hóa đơn bán hàng"}
                        </p>
                        {hint ? (
                            <p className="text-[12px] font-semibold text-amber-800">{hint}</p>
                        ) : null}
                    </div>
                </header>

                <div className="min-h-0 overflow-auto bg-[#f4f6f4] px-5 py-4">
                    {loading ? (
                        <p className="py-10 text-center text-sm font-semibold text-[#697684]">
                            Đang tải hóa đơn...
                        </p>
                    ) : null}

                    {error ? (
                        <p
                            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            role="alert"
                        >
                            {error}
                        </p>
                    ) : null}

                    {order ? (
                        <iframe
                            className="h-[56vh] w-full rounded-lg border border-[#dce5e1] bg-white"
                            srcDoc={receiptHtml}
                            title="Xem trước hóa đơn"
                        />
                    ) : null}
                </div>

                <footer className="flex flex-wrap justify-end gap-3 border-t border-[#e8eef0] px-5 py-4">
                    <button
                        className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                        onClick={onClose}
                        type="button"
                    >
                        Đóng
                    </button>
                    <button
                        className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                        disabled={!order}
                        onClick={handlePrint}
                        type="button"
                    >
                        <Printer aria-hidden="true" size={18} />
                        In hóa đơn
                    </button>
                </footer>
            </section>
        </div>
    );
}
