import { ApiError } from "@hvt/api-client";
import { useCallback, useEffect, useState } from "react";

import type { PageResult } from "../types/product.types";

function getErrorMessage(error: unknown) {
    if (error instanceof ApiError) {
        return error.message;
    }

    if (error instanceof Error) {
        return error.message;
    }

    return "Không thể tải dữ liệu từ máy chủ.";
}

export function usePagedApiResource<T>(
    load: () => Promise<PageResult<T>>
) {
    const [data, setData] = useState<PageResult<T> | null>(null);
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
                    setError(getErrorMessage(loadError));
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

    const refresh = useCallback(() => {
        setRefreshKey((current) => current + 1);
    }, []);

    return {
        data,
        loading,
        error,
        refresh
    };
}

export function useApiResource<T>(load: () => Promise<T>) {
    const [data, setData] = useState<T | null>(null);
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
                    setError(getErrorMessage(loadError));
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

    const refresh = useCallback(() => {
        setRefreshKey((current) => current + 1);
    }, []);

    return { data, loading, error, refresh };
}
