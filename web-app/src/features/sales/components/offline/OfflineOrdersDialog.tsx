import { CloudUpload, Printer, RefreshCw, Trash2, X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { ModalShell } from "../../../../components/ui/Overlay";
import { StatusBadge, type BadgeTone } from "../../../../components/ui/StatusBadge";
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

const statusLabels: Record<OfflineOrderStatus, { label: string; tone: BadgeTone }> = {
    PENDING: { label: "Chờ đồng bộ", tone: "warning" },
    SYNCING: { label: "Đang gửi...", tone: "sky" },
    SYNCED: { label: "Đã đồng bộ", tone: "success" },
    FAILED: { label: "Bị từ chối", tone: "danger" }
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
        <ModalShell
            className="w-full max-w-[720px]"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="offline-orders-title"
            onClose={onClose}
            scroll={false}
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
                <IconButton aria-label="Đóng" onClick={onClose}>
                    <X aria-hidden="true" size={18} />
                </IconButton>
            </header>

            <div className="min-h-0 flex-1 overflow-auto bg-[#f4f6f4] px-5 py-4">
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
                                    <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                                    {inventoryIssue ? (
                                        <StatusBadge tone="warning">
                                            Kho chưa trừ được, cần kiểm tra tồn
                                        </StatusBadge>
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
                                    <Button onClick={() => handlePrint(record)} size="sm">
                                        <Printer aria-hidden="true" size={14} />
                                        In lại
                                    </Button>
                                    {record.status === "FAILED" ? (
                                        <>
                                            <Button
                                                disabled={!online}
                                                onClick={() => {
                                                    void handleRetry(record);
                                                }}
                                                size="sm"
                                                title={online ? undefined : "Cần có mạng để gửi lại"}
                                                variant="primary"
                                            >
                                                <RefreshCw aria-hidden="true" size={14} />
                                                Gửi lại
                                            </Button>
                                            <Button
                                                onClick={() => {
                                                    void handleDiscard(record);
                                                }}
                                                size="sm"
                                                variant="dangerOutline"
                                            >
                                                <Trash2 aria-hidden="true" size={14} />
                                                Bỏ đơn
                                            </Button>
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
                    <Button onClick={onClose}>Đóng</Button>
                    <Button
                        disabled={!online || sync.syncing || sync.pending === 0}
                        onClick={() => {
                            void getOfflineOrderSync().trigger();
                        }}
                        variant="primary"
                    >
                        <CloudUpload aria-hidden="true" size={18} />
                        {sync.syncing ? "Đang đồng bộ..." : "Đồng bộ ngay"}
                    </Button>
                </div>
            </footer>
        </ModalShell>
    );
}
