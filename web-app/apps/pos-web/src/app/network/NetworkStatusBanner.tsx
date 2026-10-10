import { RefreshCw, WifiOff, Wifi } from "lucide-react";
import { useEffect, useState } from "react";

import { networkMonitor } from "./network-status";
import { useNetworkStatus } from "./useNetworkStatus";

const RECONNECTED_VISIBLE_MS = 3_000;

/**
 * Thanh báo mất kết nối, nổi trên mọi màn hình.
 *
 * Offline: banner đỏ, có nút thử lại. Khi mạng về: banner xanh hiện 3 giây
 * rồi tự ẩn, để thu ngân biết có thể tiếp tục thao tác.
 */
export function NetworkStatusBanner() {
    const { online, checking, changedAt, lastOfflineAt } = useNetworkStatus();
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
        return null;
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
                Mất kết nối máy chủ. Đơn đang bán được giữ trên máy này, chỉ thanh
                toán tiền mặt khi mạng trở lại.
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
