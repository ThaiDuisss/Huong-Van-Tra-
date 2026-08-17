import { AlertTriangle, ClipboardList, Clock3, Truck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    TransferFilterBar,
    TransferRoleSwitch,
    TransferSummaryCard,
    TransferTable
} from "../components/TransferComponents";
import { transferTabLabels, transferTabs } from "../constants/transfer.constants";
import { useTransferQueryState } from "../hooks/useTransferQueryState";
import { transferApi } from "../services/transfer.api";
import type {
    TransferListResult,
    TransferOptions,
    TransferRole
} from "../types/transfer.types";

function readRole(): TransferRole {
    const stored = window.localStorage.getItem("hvt.transfer.role");
    return stored && ["BRANCH_MANAGER", "WAREHOUSE", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored)
        ? stored as TransferRole
        : "BRANCH_MANAGER";
}

export function TransferListPage() {
    const { query, resetQuery, setQuery } = useTransferQueryState();
    const [role, setRole] = useState<TransferRole>(readRole);
    const [options, setOptions] = useState<TransferOptions>({ locations: [], skus: [], users: [] });
    const [result, setResult] = useState<TransferListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        window.localStorage.setItem("hvt.transfer.role", role);
    }, [role]);

    useEffect(() => {
        void transferApi.getTransferOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void transferApi.listTransfers(query)
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => {
                    if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách chuyển kho.");
                })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query, refreshKey]);

    const transfers = result?.content ?? [];

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">Management / Inventory</p>
                    <h1 className="text-2xl font-black text-[#172317]">Chuyển kho</h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Quản lý yêu cầu chuyển kho từ chi nhánh, reserve FEFO, xuất kho nguồn, theo dõi IN_TRANSIT và nhận hàng có kiểm soát chênh lệch.
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <TransferRoleSwitch onChange={setRole} value={role} />
                    <Link className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card" to="/management/inventory/transfers/new">
                        Tạo yêu cầu
                    </Link>
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <TransferSummaryCard icon={<ClipboardList size={18} aria-hidden="true" />} label="Yêu cầu mới" value={result?.summary.newRequests ?? 0} />
                <TransferSummaryCard icon={<Truck size={18} aria-hidden="true" />} label="Đang vận chuyển" value={result?.summary.inTransit ?? 0} />
                <TransferSummaryCard icon={<Clock3 size={18} aria-hidden="true" />} label="Trễ dự kiến" value={result?.summary.overdue ?? 0} />
                <TransferSummaryCard icon={<AlertTriangle size={18} aria-hidden="true" />} label="Có chênh lệch" value={result?.summary.discrepancy ?? 0} />
            </div>

            <nav className="flex gap-2 overflow-x-auto">
                {transferTabs.map((tab) => (
                    <button
                        className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${query.tab === tab ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`}
                        key={tab}
                        onClick={() => setQuery({ page: 0, tab })}
                        type="button"
                    >
                        {transferTabLabels[tab]}
                    </button>
                ))}
            </nav>

            <TransferFilterBar locations={options.locations} onChange={setQuery} onReset={resetQuery} query={query} users={options.users} />

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

            {!loading && !error && transfers.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">Không có yêu cầu phù hợp</strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">Hãy đổi bộ lọc hoặc tạo yêu cầu chuyển kho mới.</p>
                    </div>
                </div>
            ) : null}

            {!loading && !error && transfers.length > 0 ? (
                <div className="grid gap-3">
                    <TransferTable transfers={transfers} />
                    <Pagination
                        onPageChange={(page) => setQuery({ page: page - 1 })}
                        onPageSizeChange={(size) => setQuery({ page: 0, size })}
                        page={query.page + 1}
                        pageCount={result?.totalPages ?? 1}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalItemsLabel="Tổng số transfer"
                    />
                </div>
            ) : null}
        </section>
    );
}
