import { AlertTriangle, CheckCircle2, Clock3, PackageCheck, Play, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    ProductionFilterBar,
    ProductionRoleSwitch,
    ProductionSummaryCard,
    ProductionTable
} from "../components/ProductionOrderComponents";
import { useProductionOrderQueryState } from "../hooks/useProductionOrderQueryState";
import { productionOrderApi } from "../services/productionOrder.api";
import type {
    ProductionOrderListResult,
    ProductionOrderOptions,
    ProductionRole
} from "../types/production-order.types";

function readRole(): ProductionRole {
    const stored = window.localStorage.getItem("hvt.production.role");
    return stored &&
        ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER", "ACCOUNTANT", "DIRECTOR", "QC_STAFF"].includes(stored)
        ? (stored as ProductionRole)
        : "WAREHOUSE";
}

export function ProductionOrderListPage() {
    const { query, resetQuery, setQuery } = useProductionOrderQueryState();
    const [role, setRole] = useState<ProductionRole>(readRole);
    const [result, setResult] = useState<ProductionOrderListResult | null>(null);
    const [options, setOptions] = useState<ProductionOrderOptions>({
        finishedSkus: [],
        locations: [],
        materialSkus: [],
        users: []
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        window.localStorage.setItem("hvt.production.role", role);
    }, [role]);

    useEffect(() => {
        void productionOrderApi.getProductionOrderOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });
        void productionOrderApi
            .listProductionOrders(query)
            .then((response) => {
                if (active) setResult(response);
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(loadError instanceof Error ? loadError.message : "Không thể tải lệnh sản xuất.");
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [query, refreshKey]);

    const retry = useCallback(() => setRefreshKey((value) => value + 1), []);
    const orders = result?.content ?? [];

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">Management / Inventory</p>
                    <h1 className="text-2xl font-black text-[#172317]">Lệnh sản xuất</h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">Make-to-Stock theo BOM, giữ nguyên liệu, FEFO, hoàn thành sản xuất và tạo PXK/PNK.</p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <ProductionRoleSwitch onChange={setRole} value={role} />
                    <Link className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card" to="/management/inventory/production-orders/new">Tạo lệnh sản xuất</Link>
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
                <ProductionSummaryCard icon={<Clock3 size={18} />} label="Draft" value={result?.summary.draft ?? 0} />
                <ProductionSummaryCard icon={<ShieldCheck size={18} />} label="Chờ duyệt" value={result?.summary.pendingApproval ?? 0} />
                <ProductionSummaryCard icon={<PackageCheck size={18} />} label="Đã giữ nguyên liệu" value={result?.summary.reserved ?? 0} />
                <ProductionSummaryCard icon={<Play size={18} />} label="Đang sản xuất" value={result?.summary.inProgress ?? 0} />
                <ProductionSummaryCard icon={<CheckCircle2 size={18} />} label="Hoàn thành một phần" value={result?.summary.partiallyCompleted ?? 0} />
                <ProductionSummaryCard icon={<AlertTriangle size={18} />} label="Quá hạn" value={result?.summary.overdue ?? 0} />
            </div>

            <ProductionFilterBar finishedSkus={options.finishedSkus} locations={options.locations} onChange={setQuery} onReset={resetQuery} query={query} users={options.users} />

            {loading ? <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{Array.from({ length: 7 }, (_, index) => <div className="h-11 animate-pulse rounded-lg bg-[#eef3ef]" key={index} />)}</div> : null}
            {!loading && error ? <div className="rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center text-sm font-bold text-red-700">{error}<br/><button className="mt-3 rounded-lg bg-red-700 px-4 py-2 text-white" onClick={retry} type="button">Thử lại</button></div> : null}
            {!loading && !error && orders.length === 0 ? <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center text-sm font-bold text-[#697684]">Không có lệnh sản xuất phù hợp.</div> : null}
            {!loading && !error && orders.length > 0 ? (
                <div className="grid gap-3">
                    <ProductionTable orders={orders} />
                    <Pagination page={query.page + 1} pageCount={result?.totalPages ?? 1} totalItems={result?.totalElements ?? 0} pageSize={query.size} onPageChange={(page) => setQuery({ page: page - 1 })} onPageSizeChange={(size) => setQuery({ page: 0, size })} totalItemsLabel="Tổng số LSX" />
                </div>
            ) : null}
        </section>
    );
}
