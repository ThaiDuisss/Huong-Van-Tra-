import { ApiError } from "@/lib/api-client";
import { useCallback, useEffect, useState } from "react";

import type { PageResponse } from "../types/inventory.types";

function messageOf(error: unknown) {
    if (error instanceof ApiError || error instanceof Error) {
        return error.message;
    }

    return "Đã xảy ra lỗi. Vui lòng thử lại sau.";
}

export function useInventoryPage<T>(
    load: () => Promise<PageResponse<T>>
) {
    const [data, setData] = useState<PageResponse<T> | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        let active = true;

        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void load()
            .then((response) => {
                if (active) {
                    setData(response);
                }
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(messageOf(loadError));
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [load, refreshKey]);

    return {
        data,
        loading,
        error,
        refresh: useCallback(
            () => setRefreshKey((value) => value + 1),
            []
        )
    };
}

export function useInventoryResource<T>(load: () => Promise<T>) {
    const [data, setData] = useState<T | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;

        void load()
            .then((response) => {
                if (active) {
                    setData(response);
                }
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(messageOf(loadError));
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [load]);

    return { data, loading, error };
}

export { messageOf };
