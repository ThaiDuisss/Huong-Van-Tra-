import { Clock3, FileWarning, PackageCheck, Truck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    SupplierReturnFilterBar,
    SupplierReturnSummaryCard,
    SupplierReturnTable
} from "../components/SupplierReturnComponents";
import { useSupplierReturnQueryState } from "../hooks/useSupplierReturnQueryState";
import { supplierReturnApi } from "../services/supplierReturn.api";
import type { SupplierReturnListResult, SupplierReturnOptions } from "../types/supplier-return.types";

export function SupplierReturnListPage() {
    const { query, resetQuery, setQuery } = useSupplierReturnQueryState();
    const [options, setOptions] = useState<SupplierReturnOptions>({ batches: [], suppliers: [], users: [] });
    const [result, setResult] = useState<SupplierReturnListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => { void supplierReturnApi.getSupplierReturnOptions().then(setOptions); }, []);
    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void supplierReturnApi.listSupplierReturns(query)
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách trả NCC."); })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query]);

    const returns = result?.content ?? [];
    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div>
                    <p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p>
                    <h1 className="text-2xl font-black text-[#172317]">Trả nhà cung cấp</h1>
                    <p className="text-sm font-semibold text-[#697684]">
                        Liên kết receipt/batch gốc, duyệt, staging RETURN_TO_SUPPLIER, ship PXK và theo dõi credit note/hàng thay thế.
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <Link
                        className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card"
                        to="/management/inventory/supplier-returns/new"
                    >
                        Tạo yêu cầu
                    </Link>
                </div>
            </header>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                <SupplierReturnSummaryCard icon={<FileWarning size={18} />} label="Chờ duyệt" value={result?.summary.submitted ?? 0} />
                <SupplierReturnSummaryCard icon={<PackageCheck size={18} />} label="Đang chuẩn bị" value={result?.summary.preparing ?? 0} />
                <SupplierReturnSummaryCard icon={<Truck size={18} />} label="Đã gửi NCC" value={result?.summary.shipped ?? 0} />
                <SupplierReturnSummaryCard icon={<Clock3 size={18} />} label="Chờ credit note" value={result?.summary.creditPending ?? 0} />
                <SupplierReturnSummaryCard icon={<Clock3 size={18} />} label="Chờ hàng thay thế" value={result?.summary.replacementPending ?? 0} />
            </div>
            <SupplierReturnFilterBar onChange={setQuery} onReset={resetQuery} options={options} query={query} />
            {loading ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 6 }, (_, index) => <div className="h-11 animate-pulse rounded-lg bg-[#eef3ef]" key={index} />)}
                </div>
            ) : null}
            {!loading && error ? <div className="rounded-xl bg-red-50 p-6 text-sm font-bold text-red-700">{error}</div> : null}
            {!loading && !error && returns.length === 0 ? (
                <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white p-10 text-center text-sm font-bold text-[#697684]">
                    Không có phiếu trả NCC phù hợp.
                </div>
            ) : null}
            {!loading && !error && returns.length > 0 ? (
                <div className="grid gap-3">
                    <SupplierReturnTable returns={returns} />
                    <Pagination
                        onPageChange={(page) => setQuery({ page: page - 1 })}
                        onPageSizeChange={(size) => setQuery({ page: 0, size })}
                        page={query.page + 1}
                        pageCount={result?.totalPages ?? 1}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalItemsLabel="Tổng số phiếu trả NCC"
                    />
                </div>
            ) : null}
        </section>
    );
}
