import { useEffect, useState } from "react";

import { StocktakeFilterBar, StocktakePageHeader, StocktakeTable } from "../components/StocktakeComponents";
import { useStocktakeQueryState } from "../hooks/useStocktakeQueryState";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeListResult, StocktakeOptions } from "../types/stocktake.types";

const emptyOptions: StocktakeOptions = { batches: [], branches: [], items: [], locations: [], users: [] };

export function StocktakeListPage() {
    const { query, resetQuery, setQuery } = useStocktakeQueryState();
    const [options, setOptions] = useState<StocktakeOptions>(emptyOptions);
    const [result, setResult] = useState<StocktakeListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        void stocktakeApi.getStocktakeOptions().then((response) => {
            if (active) setOptions(response);
        }).catch(() => {
            if (active) setOptions(emptyOptions);
        });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (active) { setLoading(true); setError(""); }
        });
        void stocktakeApi.listStocktakes(query)
            .then((response) => { if (active) setResult(response); })
            .catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách kiểm kê.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [query, reloadKey]);

    return (
        <section className="flex h-full min-h-0 flex-col gap-4 text-[#172317]">
            <StocktakePageHeader onRefresh={() => setReloadKey((value) => value + 1)} />
            <StocktakeFilterBar branches={options.branches} onChange={setQuery} onReset={resetQuery} query={query} />
            {error ? <div className="flex items-center justify-between rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700"><span>{error}</span><button onClick={() => setReloadKey((value) => value + 1)} type="button">Thử lại</button></div> : null}
            <StocktakeTable loading={loading} onPageChange={(page) => setQuery({ page })} page={query.page} stocktakes={result?.content ?? []} totalElements={result?.totalElements ?? 0} totalPages={result?.totalPages ?? 0} />
        </section>
    );
}
