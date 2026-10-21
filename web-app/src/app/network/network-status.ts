/**
 * Trạng thái kết nối tới máy chủ, dùng chung cho cả app.
 *
 * Ba nguồn tín hiệu, theo độ tin cậy tăng dần:
 * 1. Sự kiện online/offline của trình duyệt: nhanh nhưng chỉ biết có
 *    wifi hay không, không biết có ra được internet hay máy chủ đang tắt.
 * 2. Lỗi NETWORK_ERROR từ api-client: chắc chắn vừa gọi không tới máy chủ.
 * 3. Ping định kỳ tới gateway: mọi phản hồi HTTP (kể cả 401/404) đều tính
 *    là "còn kết nối"; chỉ fetch ném lỗi hoặc quá hạn mới là mất mạng.
 */

export interface NetworkStatus {
    online: boolean;

    /** Đang chạy một lượt ping. */
    checking: boolean;

    /** Thời điểm chuyển trạng thái gần nhất (ms). */
    changedAt: number;

    /** Thời điểm ping thành công gần nhất (ms), null nếu chưa có. */
    lastOnlineAt: number | null;

    /** Lần mất kết nối gần nhất (ms), null nếu chưa từng mất trong phiên. */
    lastOfflineAt: number | null;
}

export interface NetworkMonitorOptions {
    /** URL ping. Mặc định: {origin của API}/actuator/health. */
    healthUrl: string;

    /** Chu kỳ ping khi đang online (ms). */
    onlineIntervalMs: number;

    /** Chu kỳ ping khi đang offline (ms), ngắn hơn để báo mạng về sớm. */
    offlineIntervalMs: number;

    /** Timeout cho mỗi lượt ping (ms). */
    pingTimeoutMs: number;
}

type Listener = () => void;

const DEFAULT_OPTIONS: NetworkMonitorOptions = {
    healthUrl: "",
    onlineIntervalMs: 20_000,
    offlineIntervalMs: 5_000,
    pingTimeoutMs: 5_000
};

export function resolveHealthUrl(apiBaseUrl: string): string {
    try {
        return new URL("/actuator/health", apiBaseUrl).toString();
    } catch {
        return "/actuator/health";
    }
}

export function createNetworkMonitor(
    partialOptions: Partial<NetworkMonitorOptions> = {}
) {
    const options: NetworkMonitorOptions = {
        ...DEFAULT_OPTIONS,
        ...partialOptions
    };

    const listeners = new Set<Listener>();
    let status: NetworkStatus = {
        online:
            typeof navigator === "undefined" ||
            typeof navigator.onLine !== "boolean"
                ? true
                : navigator.onLine,
        checking: false,
        changedAt: Date.now(),
        lastOnlineAt: null,
        lastOfflineAt: null
    };
    let timerId: ReturnType<typeof setTimeout> | null = null;
    let pingPromise: Promise<boolean> | null = null;
    let started = false;

    function emit() {
        for (const listener of listeners) {
            listener();
        }
    }

    function setStatus(patch: Partial<NetworkStatus>) {
        const next = { ...status, ...patch };

        if (next.online !== status.online) {
            next.changedAt = Date.now();

            if (!next.online) {
                next.lastOfflineAt = next.changedAt;
            }
        }

        if (
            next.online === status.online &&
            next.checking === status.checking &&
            next.lastOnlineAt === status.lastOnlineAt
        ) {
            return;
        }

        status = next;
        emit();
    }

    function schedule() {
        if (!started) {
            return;
        }

        if (timerId !== null) {
            clearTimeout(timerId);
        }

        timerId = setTimeout(
            () => {
                void ping();
            },
            status.online
                ? options.onlineIntervalMs
                : options.offlineIntervalMs
        );
    }

    async function ping(): Promise<boolean> {
        if (pingPromise) {
            return pingPromise;
        }

        pingPromise = (async () => {
            setStatus({ checking: true });

            const controller = new AbortController();
            const timeoutId = setTimeout(
                () => controller.abort(),
                options.pingTimeoutMs
            );

            try {
                await fetch(options.healthUrl, {
                    method: "GET",
                    cache: "no-store",
                    credentials: "omit",
                    signal: controller.signal
                });

                setStatus({
                    online: true,
                    checking: false,
                    lastOnlineAt: Date.now()
                });
                return true;
            } catch {
                setStatus({ online: false, checking: false });
                return false;
            } finally {
                clearTimeout(timeoutId);
                pingPromise = null;
                schedule();
            }
        })();

        return pingPromise;
    }

    function handleBrowserOnline() {
        // Có mạng lại theo trình duyệt, nhưng chưa chắc tới được máy chủ.
        void ping();
    }

    function handleBrowserOffline() {
        setStatus({ online: false });
        schedule();
    }

    function start() {
        if (started || typeof window === "undefined") {
            return;
        }

        started = true;
        window.addEventListener("online", handleBrowserOnline);
        window.addEventListener("offline", handleBrowserOffline);
        void ping();
    }

    function stop() {
        if (!started) {
            return;
        }

        started = false;
        window.removeEventListener("online", handleBrowserOnline);
        window.removeEventListener("offline", handleBrowserOffline);

        if (timerId !== null) {
            clearTimeout(timerId);
            timerId = null;
        }
    }

    return {
        getSnapshot(): NetworkStatus {
            return status;
        },

        subscribe(listener: Listener): () => void {
            listeners.add(listener);
            start();

            return () => {
                listeners.delete(listener);
            };
        },

        /** Ping ngay, dùng cho nút "Thử lại" trên banner. */
        check(): Promise<boolean> {
            return ping();
        },

        /**
         * api-client vừa gặp lỗi mạng: chuyển sang offline ngay và ping
         * để xác nhận, không chờ tới chu kỳ sau.
         */
        reportNetworkError(): void {
            setStatus({ online: false });
            void ping();
        },

        /**
         * Một request vừa thành công trong lúc đang offline. Không tin ngay:
         * service worker có thể vừa trả bản cache của danh mục. Ping health
         * (không bao giờ bị cache) để xác nhận rồi mới chuyển online.
         */
        reportSuccess(): void {
            if (!status.online) {
                void ping();
            }
        },

        start,
        stop
    };
}

export type NetworkMonitor = ReturnType<typeof createNetworkMonitor>;

const apiBaseUrl =
    import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export const networkMonitor = createNetworkMonitor({
    healthUrl:
        import.meta.env.VITE_HEALTH_URL ?? resolveHealthUrl(apiBaseUrl)
});
