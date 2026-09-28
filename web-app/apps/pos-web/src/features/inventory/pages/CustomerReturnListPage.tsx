import { useEffect, useState } from "react";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { CustomerReturnQcQueue } from "../components/CustomerReturnQcQueue";
import {
    InspectionEmpty,
    InspectionFilterBar,
    InspectionTable
} from "../components/CustomerReturnInspectionComponents";
import { useCustomerReturnQueryState } from "../hooks/useCustomerReturnQueryState";
import { customerReturnApi } from "../services/customerReturn.api";
import type { CustomerReturnInspectionListResult } from "../types/customer-return-inspection.types";

export function CustomerReturnListPage() {
    const { query, resetQuery, setQuery } = useCustomerReturnQueryState();
    const [result, setResult] = useState<CustomerReturnInspectionListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void customerReturnApi.listInspections(query)
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => {
                    if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách kiểm định trả hàng.");
                })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query, reloadKey]);

    const inspections = result?.content ?? [];

    return <section className="grid gap-4"><header><p className="text-xs font-black uppercase text-[#159734]">Quản lý / Kho</p><h1 className="text-2xl font-black text-[#172317]">Khách trả hàng</h1><p className="text-sm font-semibold text-[#697684]">Theo dõi phiếu hoàn, kiểm định và trạng thái xử lý hàng khách trả.</p></header><CustomerReturnQcQueue onInspectionCreated={() => setReloadKey((key) => key + 1)} /><h2 className="text-lg font-black text-[#172317]">Phiếu kiểm định đã tạo</h2><InspectionFilterBar onChange={setQuery} onReset={resetQuery} query={query} />{loading ? <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{Array.from({ length: 6 }, (_, index) => <div className="h-11 animate-pulse rounded-lg bg-[#eef3ef]" key={index} />)}</div> : null}{!loading && error ? <div className="rounded-xl bg-red-50 p-6 text-sm font-bold text-red-700">{error}</div> : null}{!loading && !error && inspections.length === 0 ? <InspectionEmpty /> : null}{!loading && !error && inspections.length > 0 ? <div className="grid gap-3"><InspectionTable inspections={inspections} /><Pagination onPageChange={(page) => setQuery({ page: page - 1 })} onPageSizeChange={(size) => setQuery({ page: 0, size })} page={query.page + 1} pageCount={result?.totalPages ?? 1} pageSize={query.size} totalItems={result?.totalElements ?? 0} totalItemsLabel="Tổng số phiếu" /></div> : null}</section>;
}
