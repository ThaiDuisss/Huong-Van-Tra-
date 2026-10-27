import { QueryClient, onlineManager } from "@tanstack/react-query";

import { networkMonitor } from "./network/network-status";

/**
 * QueryClient dùng chung cho POS.
 *
 * networkMode "offlineFirst": khi mất mạng vẫn chạy queryFn một lần (service
 * worker có thể trả bản cache của danh mục), nếu lỗi thì giữ dữ liệu cũ và
 * tự refetch khi mạng về. retry để false vì api-client đã tự thử lại GET.
 */
export function createPosQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: {
                networkMode: "offlineFirst",
                retry: false,
                staleTime: 30_000,
                gcTime: 60 * 60 * 1000,
                refetchOnWindowFocus: false,
                refetchOnReconnect: true
            },
            mutations: {
                networkMode: "offlineFirst",
                retry: false
            }
        }
    });
}

/**
 * Cho TanStack Query dùng cùng một nguồn trạng thái mạng với banner và
 * api-client (thay cho navigator.onLine vốn hay sai).
 */
export function bindQueryOnlineManager(): void {
    onlineManager.setEventListener((setOnline) => {
        setOnline(networkMonitor.getSnapshot().online);

        return networkMonitor.subscribe(() => {
            setOnline(networkMonitor.getSnapshot().online);
        });
    });
}

export const queryClient = createPosQueryClient();

bindQueryOnlineManager();
