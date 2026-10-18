import { CloudUpload, RefreshCw, WifiOff, Wifi } from "lucide-react";
import { useEffect, useState } from "react";

import { networkMonitor } from "./network-status";
import { useNetworkStatus } from "./useNetworkStatus";
import { useOfflineOrderSync } from "../../features/sales/offline/offline-order-sync.instance";

const RECONNECTED_VISIBLE_MS = 3_000;

/**
 * Thanh báo mất kết nối, nổi trên mọi màn hình.
 *
 * Offline: banner đỏ, có nút thử lại. Khi mạng về: banner xanh hiện 3 giây
 * rồi tự ẩn, để thu ngân biết có thể tiếp tục thao tác.
 */
export function NetworkStatusBanner() {
    const { online, checking, changedAt, lastOfflineAt } = useNetworkStatus();
    const offlineSync = useOfflineOrderSync();
    // changedAt của lần "mạng về" mà banner xanh đã tự ẩn.
    const [dismissedChangeAt, setDismissedChangeAt] = useState(0);
    const showReconnected =
        online && lastOfflineAt !== null && dismissedChangeAt !== changedAt;

    useEffect(() => {
        if (!showReconnected) {
            return undefined;
        }

        const timeoutId = window.setTimeout(() => {
            setDismissedChangeAt(changedAt);
        }, RECONNECTED_VISIBLE_MS);

        return () => window.clearTimeout(timeoutId);
    }, [changedAt, showReconnected]);

    if (online && !showReconnected) {
        if (offlineSync.pending === 0 && offlineSync.failed === 0) {
            return null;
        }

        // Mạng có nhưng còn đơn bán offline chưa lên máy chủ: nhắc nhẹ, không chặn thao tác.
        const failedOnly = offlineSync.pending === 0;

        return (
            <div
                role="status"
                className="pointer-events-none fixed inset-x-0 top-0 z-[100] flex justify-center px-3 pt-2"
            >
                <div
                    className={`flex items-center gap-2 rounded-full border px-4 py-2 text-[13px] font-semibold shadow-md ${
                        failedOnly
                            ? "border-red-200 bg-red-50 text-red-700"
                            : "border-amber-200 bg-amber-50 text-amber-800"
                    }`}
                >
                    <CloudUpload
                        size={16}
                        aria-hidden="true"
                        className={offlineSync.syncing ? "animate-pulse" : ""}
                    />
                    {failedOnly
                        ? `${offlineSync.failed} đơn bán offline bị máy chủ từ chối. Mở biểu tượng đồng bộ ở thanh bên để xử lý.`
                        : `${offlineSync.syncing ? "Đang đồng bộ" : "Chờ đồng bộ"} ${offlineSync.pending} đơn bán offline${
                              offlineSync.failed > 0 ? `, ${offlineSync.failed} đơn bị từ chối` : ""
                          }.`}
                </div>
            </div>
        );
    }

    if (online) {
        return (
            <div
                role="status"
                className="pointer-events-none fixed inset-x-0 top-0 z-[100] flex justify-center px-3 pt-2"
            >
                <div className="flex items-center gap-2 rounded-full border border-hvt-green-200 bg-hvt-green-50 px-4 py-2 text-[13px] font-semibold text-hvt-green-900 shadow-md">
                    <Wifi size={16} aria-hidden="true" />
                    Đã kết nối lại máy chủ.
                </div>
            </div>
        );
    }

    return (
        <div
            role="alert"
            aria-live="assertive"
            className="fixed inset-x-0 top-0 z-[100] flex items-center justify-center gap-3 bg-red-600 px-4 py-2 text-[13px] font-semibold text-white shadow-md"
        >
            <WifiOff size={16} aria-hidden="true" />
            <span>
                Mất kết nối máy chủ. Vẫn bán được bằng tiền mặt: đơn lưu trên máy
                này, in hoá đơn mã tạm và tự đồng bộ khi có mạng. Chuyển khoản tạm khoá.
            </span>
            <button
                type="button"
                onClick={() => {
                    void networkMonitor.check();
                }}
                disabled={checking}
                className="inline-flex items-center gap-1 rounded-md bg-white/15 px-2.5 py-1 text-[12px] font-semibold hover:bg-white/25 disabled:cursor-wait disabled:opacity-70"
            >
                <RefreshCw
                    size={14}
                    aria-hidden="true"
                    className={checking ? "animate-spin" : ""}
                />
                {checking ? "Đang kiểm tra..." : "Thử lại"}
            </button>
        </div>
    );
}
