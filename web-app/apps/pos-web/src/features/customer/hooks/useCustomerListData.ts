import { useCallback, useEffect, useState } from "react";

import { customerApi } from "../services/customer.api";
import type {
    CustomerResponse,
    CustomerTierResponse,
    PageResponse
} from "../types/customer.types";
import {
    customerErrorMessage,
    toCustomerQuery
} from "../utils/customer-list.utils";

export function useCustomerListData(queryString: string) {
    const [tiers, setTiers] = useState<CustomerTierResponse[]>([]);
    const [result, setResult] =
        useState<PageResponse<CustomerResponse> | null>(null);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        void customerApi
            .tiers()
            .then((response) => {
                if (active) {
                    setTiers(
                        response.filter((tier) => tier.status === "ACTIVE")
                    );
                }
            })
            .catch(() => {
                if (active) setTiers([]);
            });
        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });
        void customerApi
            .list(toCustomerQuery(new URLSearchParams(queryString), 0, 10))
            .then((response) => {
                if (active) setResult(response);
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setResult(null);
                    setError(customerErrorMessage(loadError));
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [queryString, reloadKey]);

    const loadMore = useCallback(async () => {
        if (!result || result.last || loadingMore) return;
        setLoadingMore(true);
        setError("");
        try {
            const next = await customerApi.list(
                toCustomerQuery(
                    new URLSearchParams(queryString),
                    result.page + 1,
                    result.size
                )
            );
            setResult({
                ...next,
                content: [...result.content, ...next.content]
            });
        } catch (loadError) {
            setError(customerErrorMessage(loadError));
        } finally {
            setLoadingMore(false);
        }
    }, [loadingMore, queryString, result]);

    return {
        tiers,
        result,
        customers: result?.content ?? [],
        loading,
        loadingMore,
        error,
        setError,
        loadMore,
        reload: () => setReloadKey((value) => value + 1)
    };
}
