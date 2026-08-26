import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { StocktakeFilterBar, StocktakeRoleSwitch, StocktakeSummaryCard, StocktakeTable } from "../components/StocktakeComponents";
import { useStocktakeQueryState } from "../hooks/useStocktakeQueryState";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeListResult, StocktakeOptions, StocktakeRole } from "../types/stocktake.types";

function readRole(): StocktakeRole {
    const stored = window.localStorage.getItem("hvt.stocktake.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored) ? stored as StocktakeRole : "WAREHOUSE";
}

export function StocktakeListPage() {
    const { query, resetQuery, setQuery } = useStocktakeQueryState();
    const [role, setRole] = useState<StocktakeRole>(readRole);
    const [, setOptions] = useState<StocktakeOptions>({ batches: [], branches: [], items: [], locations: [], users: [] });
    const [result, setResult] = useState<StocktakeListResult | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        window.localStorage.setItem("hvt.stocktake.role", role);
    }, [role]);

    useEffect(() => {
        void stocktakeApi.getStocktakeOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;
        void Promise.resolve().then(() => {
            if (active) setLoading(true);
            return stocktakeApi.listStocktakes(query);
        }).then((response) => {
            if (active) {
                setResult(response);
                setLoading(false);
            }
        });
        return () => {
            active = false;
        };
    }, [query]);

    const stocktakes = result?.content ?? [];

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">Management / Inventory</p>
                    <h1 className="text-2xl font-black text-[#172317]">Kiểm kê kho</h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">Quản lý phiên kiểm kê, count line theo batch và phiếu điều chỉnh từ variance.</p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <StocktakeRoleSwitch onChange={setRole} value={role} />
                    <Link className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card" to="/management/inventory/stocktakes/new">
                        Tạo phiên kiểm kê
                    </Link>
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                <StocktakeSummaryCard label="Nháp" value={result?.summary.draft ?? 0} />
                <StocktakeSummaryCard label="Đang kiểm kê" value={result?.summary.counting ?? 0} />
                <StocktakeSummaryCard label="Hoàn tất" value={result?.summary.completed ?? 0} />
                <StocktakeSummaryCard label="Chờ điều chỉnh" value={result?.summary.adjustmentReady ?? 0} />
                <StocktakeSummaryCard label="Đã hạch toán" value={result?.summary.posted ?? 0} />
            </div>

            <StocktakeFilterBar onChange={setQuery} onReset={resetQuery} query={query} />

            {loading ? <div className="rounded-xl border border-[#e8eef0] bg-white p-6 text-sm font-bold">Đang tải...</div> : null}

            {!loading && stocktakes.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">Không có phiên kiểm kê phù hợp</strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">Hãy đổi bộ lọc hoặc tạo phiên mới.</p>
                    </div>
                </div>
            ) : null}

            {!loading && stocktakes.length > 0 ? (
                <div className="grid gap-3">
                    <StocktakeTable stocktakes={stocktakes} />
                    <Pagination onPageChange={(page) => setQuery({ page: page - 1 })} onPageSizeChange={(size) => setQuery({ page: 0, size })} page={query.page + 1} pageCount={result?.totalPages ?? 1} pageSize={query.size} totalItems={result?.totalElements ?? 0} totalItemsLabel="Tổng số phiên" />
                </div>
            ) : null}
        </section>
    );
}
