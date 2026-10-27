import { CloudUpload, Printer, RefreshCw, Trash2, X } from "lucide-react";
import { useEffect, useState } from "react";

import { useNetworkStatus } from "../../../../app/network/useNetworkStatus";
import { printReceipt } from "../../../order/utils/receipt-print";
import { getOfflineOrderDb } from "../../offline/offline-order-db";
import { listOfflineOrders } from "../../offline/offline-order-queue";
import {
    getOfflineOrderSync,
    useOfflineOrderSync
} from "../../offline/offline-order-sync.instance";
import type {
    OfflineOrderRecord,
    OfflineOrderStatus
} from "../../offline/offline-order-types";
import { formatCurrency } from "../../services/money";

interface OfflineOrdersDialogProps {
    onClose: () => void;
}

const statusLabels: Record<OfflineOrderStatus, { label: string; className: string }> = {
    PENDING: {
        label: "Chờ đồng bộ",
        className: "bg-amber-50 text-amber-800 border-amber-200"
    },
    SYNCING: {
        label: "Đang gửi...",
        className: "bg-sky-50 text-sky-800 border-sky-200"
    },
    SYNCED: {
        label: "Đã đồng bộ",
        className: "bg-hvt-green-50 text-hvt-green-900 border-hvt-green-200"
    },
    FAILED: {
        label: "Bị từ chối",
        className: "bg-red-50 text-red-700 border-red-200"
    }
};

function formatSoldAt(value: string) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleString("vi-VN", {
        hour: "2-digit",
        minute: "2-digit",
        day: "2-digit",
        month: "2-digit"
    });
}

/**
 * Danh sách đơn đã bán khi mất mạng: xem trạng thái đồng bộ, thử lại hoặc bỏ đơn bị
 * máy chủ từ chối, in lại hoá đơn (mã tạm hoặc mã chính thức sau khi đồng bộ).
 */
export function OfflineOrdersDialog({ onClose }: OfflineOrdersDialogProps) {
    const sync = useOfflineOrderSync();
    const { online } = useNetworkStatus();
    const [records, setRecords] = useState<OfflineOrderRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [actionError, setActionError] = useState("");

    useEffect(() => {
        let ignore = false;

        listOfflineOrders(getOfflineOrderDb())
            .then((items) => {
                if (!ignore) {
                    setRecords(items);
                }
            })
            .catch((error: unknown) => {
                if (!ignore) {
                    setActionError(
                        error instanceof Error ? error.message : "Không đọc được danh sách đơn."
                    );
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
    }, [sync.revision]);

    async function handleRetry(record: OfflineOrderRecord) {
        setActionError("");

        try {
            await getOfflineOrderSync().retry(record.clientOrderId);
        } catch (error) {
            setActionError(error instanceof Error ? error.message : "Không thử lại được.");
        }
    }

    async function handleDiscard(record: OfflineOrderRecord) {
        const confirmed = window.confirm(
            `Bỏ đơn ${record.tempCode} (${formatCurrency(record.totalAmount)})? Đơn sẽ không được ghi lên máy chủ.`
        );

        if (!confirmed) {
            return;
        }

        setActionError("");

        try {
            await getOfflineOrderSync().discard(record.clientOrderId);
        } catch (error) {
            setActionError(error instanceof Error ? error.message : "Không bỏ được đơn.");
        }
    }

    function handlePrint(record: OfflineOrderRecord) {
        setActionError("");

        try {
            printReceipt(record.receipt);
        } catch (error) {
            setActionError(error instanceof Error ? error.message : "Không in được hóa đơn.");
        }
    }

    return (
        <div
            className="fixed inset-0 z-50 grid place-items-center bg-black/62 px-4"
            role="presentation"
        >
            <section
                aria-labelledby="offline-orders-title"
                aria-modal="true"
                className="grid max-h-[92vh] w-full max-w-[720px] grid-rows-[auto_1fr_auto] overflow-hidden rounded-2xl bg-white shadow-pos-panel"
                role="dialog"
            >
                <header className="flex items-start gap-3 border-b border-[#e8eef0] px-5 py-4">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-amber-50 text-amber-700">
                        <CloudUpload aria-hidden="true" size={22} />
                    </span>
                    <div className="grid flex-1 gap-0.5">
                        <h2 className="text-xl font-black text-[#172317]" id="offline-orders-title">
                            Đơn bán khi mất mạng
                        </h2>
                        <p className="text-sm font-semibold text-[#697684]">
                            {sync.pending > 0
                                ? `${sync.pending} đơn chờ đồng bộ`
                                : "Không còn đơn chờ"}
                            {sync.failed > 0 ? ` · ${sync.failed} đơn bị từ chối` : ""}
                            {sync.syncing ? " · đang gửi..." : ""}
                        </p>
                    </div>
                    <button
                        aria-label="Đóng"
                        className="grid size-9 place-items-center rounded-lg text-[#536078] hover:bg-[#f4f5f3]"
                        onClick={onClose}
                        type="button"
                    >
                        <X aria-hidden="true" size={18} />
                    </button>
                </header>

                <div className="min-h-0 overflow-auto bg-[#f4f6f4] px-5 py-4">
                    {actionError ? (
                        <p
                            className="mb-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
                            role="alert"
                        >
                            {actionError}
                        </p>
                    ) : null}

                    {loading ? (
                        <p className="py-10 text-center text-sm font-semibold text-[#697684]">
                            Đang đọc danh sách...
                        </p>
                    ) : null}

                    {!loading && records.length === 0 ? (
                        <p className="py-10 text-center text-sm font-semibold text-[#697684]">
                            Chưa có đơn nào được bán khi mất mạng.
                        </p>
                    ) : null}

                    <ul className="grid gap-2">
                        {records.map((record) => {
                            const status = statusLabels[record.status];
                            const inventoryIssue =
                                record.status === "SYNCED" &&
                                record.result?.inventoryProcessingStatus === "FAILED";

                            return (
                                <li
                                    key={record.clientOrderId}
                                    className="grid gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 py-3"
                                >
                                    <div className="flex flex-wrap items-center gap-2">
                                        <strong className="text-[14px] text-[#172317]">
                                            {record.result?.orderCode ?? record.tempCode}
                                        </strong>
                                        {record.result?.orderCode ? (
                                            <span className="text-[12px] text-[#697684]">
                                                (mã tạm {record.tempCode})
                                            </span>
                                        ) : null}
                                        <span
                                            className={`rounded-full border px-2 py-0.5 text-[11px] font-bold ${status.className}`}
                                        >
                                            {status.label}
                                        </span>
                                        {inventoryIssue ? (
                                            <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-bold text-amber-800">
                                                Kho chưa trừ được, cần kiểm tra tồn
                                            </span>
                                        ) : null}
                                        <span className="ml-auto text-[13px] font-bold text-[#172317]">
                                            {formatCurrency(record.totalAmount)}
                                        </span>
                                    </div>
                                    <p className="text-[12px] text-[#697684]">
                                        Bán lúc {formatSoldAt(record.soldAt)} · {record.itemCount} mặt hàng
                                        {record.cashierName ? ` · ${record.cashierName}` : ""}
                                        {record.attempts > 0 ? ` · đã thử ${record.attempts} lần` : ""}
                                    </p>
                                    {record.lastError ? (
                                        <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-1.5 text-[12px] font-semibold text-red-700">
                                            {record.lastError}
                                        </p>
                                    ) : null}
                                    <div className="flex flex-wrap justify-end gap-2">
                                        <button
                                            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-[#dce5e1] px-3 text-[12px] font-bold text-[#536078] hover:bg-[#f4f5f3]"
                                            onClick={() => handlePrint(record)}
                                            type="button"
                                        >
                                            <Printer aria-hidden="true" size={14} />
                                            In lại
                                        </button>
                                        {record.status === "FAILED" ? (
                                            <>
                                                <button
                                                    className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-[#159734] px-3 text-[12px] font-bold text-white disabled:opacity-60"
                                                    disabled={!online}
                                                    onClick={() => {
                                                        void handleRetry(record);
                                                    }}
                                                    title={online ? undefined : "Cần có mạng để gửi lại"}
                                                    type="button"
                                                >
                                                    <RefreshCw aria-hidden="true" size={14} />
                                                    Gửi lại
                                                </button>
                                                <button
                                                    className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-red-200 px-3 text-[12px] font-bold text-red-700 hover:bg-red-50"
                                                    onClick={() => {
                                                        void handleDiscard(record);
                                                    }}
                                                    type="button"
                                                >
                                                    <Trash2 aria-hidden="true" size={14} />
                                                    Bỏ đơn
                                                </button>
                                            </>
                                        ) : null}
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                </div>

                <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-[#e8eef0] px-5 py-4">
                    <p className="text-[12px] text-[#697684]">
                        Đơn đã đồng bộ được giữ 7 ngày để in lại, sau đó tự dọn.
                    </p>
                    <div className="flex gap-3">
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078]"
                            onClick={onClose}
                            type="button"
                        >
                            Đóng
                        </button>
                        <button
                            className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60"
                            disabled={!online || sync.syncing || sync.pending === 0}
                            onClick={() => {
                                void getOfflineOrderSync().trigger();
                            }}
                            type="button"
                        >
                            <CloudUpload aria-hidden="true" size={18} />
                            {sync.syncing ? "Đang đồng bộ..." : "Đồng bộ ngay"}
                        </button>
                    </div>
                </footer>
            </section>
        </div>
    );
}
