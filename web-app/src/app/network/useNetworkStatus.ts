import { useSyncExternalStore } from "react";

import { networkMonitor, type NetworkStatus } from "./network-status";

const serverSnapshot: NetworkStatus = {
    online: true,
    checking: false,
    changedAt: 0,
    lastOnlineAt: null,
    lastOfflineAt: null
};

export function useNetworkStatus(): NetworkStatus {
    return useSyncExternalStore(
        networkMonitor.subscribe,
        networkMonitor.getSnapshot,
        () => serverSnapshot
    );
}
