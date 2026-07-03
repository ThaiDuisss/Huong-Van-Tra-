import { useEffect, useState } from "react";

import { customerApi } from "../services/customer.api";
import type {
    CustomerResponse,
    CustomerTierCountResponse,
    CustomerTierResponse,
    PageResponse
} from "../types/customer.types";
import {
    customerErrorMessage,
    toCustomerQuery
} from "../utils/customer-list.utils";

export function useCustomerListData(queryString: string) {
    const [tiers, setTiers] = useState<CustomerTierResponse[]>([]);
    const [tierCounts, setTierCounts] = useState<CustomerTierCountResponse[]>([]);
    const [result, setResult] =
        useState<PageResponse<CustomerResponse> | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        void Promise.all([
            customerApi.tiers(),
            customerApi.tierCounts()
        ])
            .then(([tiersResponse, tierCountsResponse]) => {
                if (active) {
                    setTiers(
                        tiersResponse.filter((tier) => tier.status === "ACTIVE")
                    );
                    setTierCounts(tierCountsResponse);
                }
            })
            .catch(() => {
                if (active) {
                    setTiers([]);
                    setTierCounts([]);
                }
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
        const params = new URLSearchParams(queryString);
        void customerApi
            .list(toCustomerQuery(params, getPageParam(params), getSizeParam(params), tiers))
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
    }, [queryString, reloadKey, tiers]);

    return {
        tiers,
        tierCounts,
        result,
        customers: result?.content ?? [],
        loading,
        error,
        setError,
        reload: () => setReloadKey((value) => value + 1)
    };
}

function getPageParam(params: URLSearchParams) {
    const page = Number(params.get("page"));
    return Number.isInteger(page) && page >= 0 ? page : 0;
}

function getSizeParam(params: URLSearchParams) {
    const size = Number(params.get("size"));
    return Number.isInteger(size) && size > 0 ? size : 10;
}
