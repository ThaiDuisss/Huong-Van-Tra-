import { useEffect } from "react";

import { useAuth } from "../../../auth/auth-context";
import { getOfflineOrderSync } from "./offline-order-sync.instance";

/**
 * Bật bộ đồng bộ đơn offline khi đã đăng nhập (request cần token), tắt khi đăng xuất.
 * Không render gì.
 */
export function OfflineOrderSyncRunner() {
    const { authenticated } = useAuth();

    useEffect(() => {
        if (!authenticated) {
            return undefined;
        }

        const sync = getOfflineOrderSync();
        sync.start();

        return () => {
            sync.stop();
        };
    }, [authenticated]);

    return null;
}
