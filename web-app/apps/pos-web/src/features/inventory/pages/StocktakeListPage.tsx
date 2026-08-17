import { AlertTriangle, CheckCircle2, ClipboardCheck, FileWarning, Scale } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    StocktakeFilterBar,
    StocktakeRoleSwitch,
    StocktakeSummaryCard,
    StocktakeTable
} from "../components/StocktakeComponents";
import { useStocktakeQueryState } from "../hooks/useStocktakeQueryState";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeListResult, StocktakeOptions, StocktakeRole } from "../types/stocktake.types";

function readRole(): StocktakeRole {
    const stored = window.localStorage.getItem("hvt.stocktake.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored)
        ? stored as StocktakeRole
        : "WAREHOUSE";
}

export function StocktakeListPage() {
    const { query, resetQuery, setQuery } = useStocktakeQueryState();
    const [role, setRole] = useState<StocktakeRole>(readRole);
    const [options, setOptions] = useState<StocktakeOptions>({ categories: [], locations: [], skus: [], users: [] });
    const [result, setResult] = useState<StocktakeListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        window.localStorage.setItem("hvt.stocktake.role", role);
    }, [role]);

    useEffect(() => {
        void stocktakeApi.getStocktakeOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void stocktakeApi.listStocktakes(query)
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => {
                    if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách kiểm kê.");
                })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query, refreshKey]);

    const stocktakes = result?.content ?? [];

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">Management / Inventory</p>
                    <h1 className="text-2xl font-black text-[#172317]">Kiểm kê kho</h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Tạo kỳ kiểm kê, đóng băng snapshot, nhập blind count nhiều vòng, đối chiếu movement sau snapshot và tạo điều chỉnh tồn kho.
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <StocktakeRoleSwitch onChange={setRole} value={role} />
                    <Link className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card" to="/management/inventory/stocktakes/new">
                        Tạo kỳ kiểm kê
                    </Link>
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                <StocktakeSummaryCard icon={<ClipboardCheck size={18} aria-hidden="true" />} label="Đang kiểm kê" value={result?.summary.inProgress ?? 0} />
                <StocktakeSummaryCard icon={<Scale size={18} aria-hidden="true" />} label="Chờ đối chiếu" value={result?.summary.waitingReconcile ?? 0} />
                <StocktakeSummaryCard icon={<FileWarning size={18} aria-hidden="true" />} label="Chờ duyệt" value={result?.summary.pendingApproval ?? 0} />
                <StocktakeSummaryCard icon={<AlertTriangle size={18} aria-hidden="true" />} label="Chênh lệch lớn" value={result?.summary.largeVariance ?? 0} />
                <StocktakeSummaryCard icon={<CheckCircle2 size={18} aria-hidden="true" />} label="Hoàn tất tháng này" value={result?.summary.postedThisMonth ?? 0} />
            </div>

            <StocktakeFilterBar locations={options.locations} onChange={setQuery} onReset={resetQuery} query={query} users={options.users} />

            {loading ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 7 }, (_, index) => <div className="h-11 animate-pulse rounded-lg bg-[#eef3ef]" key={index} />)}
                </div>
            ) : null}

            {!loading && error ? (
                <div className="grid place-items-center rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center">
                    <div className="grid max-w-sm gap-3 justify-items-center">
                        <strong className="text-base font-black text-red-800">Không thể tải dữ liệu</strong>
                        <p className="text-sm font-semibold leading-6 text-red-700">{error}</p>
                        <button className="rounded-lg bg-red-700 px-4 py-2 text-sm font-black text-white" onClick={() => setRefreshKey((value) => value + 1)} type="button">Thử lại</button>
                    </div>
                </div>
            ) : null}

            {!loading && !error && stocktakes.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">Không có kỳ kiểm kê phù hợp</strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">Hãy đổi bộ lọc hoặc tạo kỳ kiểm kê mới.</p>
                    </div>
                </div>
            ) : null}

            {!loading && !error && stocktakes.length > 0 ? (
                <div className="grid gap-3">
                    <StocktakeTable stocktakes={stocktakes} />
                    <Pagination
                        onPageChange={(page) => setQuery({ page: page - 1 })}
                        onPageSizeChange={(size) => setQuery({ page: 0, size })}
                        page={query.page + 1}
                        pageCount={result?.totalPages ?? 1}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalItemsLabel="Tổng số kỳ kiểm kê"
                    />
                </div>
            ) : null}
        </section>
    );
}
