import { useEffect, useState } from "react";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    CustomerReturnEmpty,
    CustomerReturnFilterBar,
    CustomerReturnRoleSwitch,
    CustomerReturnTable
} from "../components/CustomerReturnComponents";
import { useCustomerReturnQueryState } from "../hooks/useCustomerReturnQueryState";
import { customerReturnApi } from "../services/customerReturn.api";
import type { CustomerReturnListResult, CustomerReturnOptions, CustomerReturnRole } from "../types/customer-return.types";

const tabs: Array<{ id: ReturnType<typeof useCustomerReturnQueryState>["query"]["tab"]; label: string }> = [
    { id: "MANAGER_REVIEW", label: "Chờ Manager" },
    { id: "AWAITING_RECEIVE", label: "Chờ nhận hàng" },
    { id: "PENDING_QC", label: "Chờ QC" },
    { id: "RESTOCK", label: "Được nhập lại kho" },
    { id: "DISPOSAL", label: "Chờ tiêu hủy" },
    { id: "COMPLETED", label: "Hoàn tất" }
];

function readRole(): CustomerReturnRole {
    const stored = window.localStorage.getItem("hvt.customerReturn.role");
    return stored && ["SALES_STAFF", "BRANCH_MANAGER", "QC_STAFF", "ADMIN", "ACCOUNTANT", "WAREHOUSE"].includes(stored)
        ? stored as CustomerReturnRole
        : "BRANCH_MANAGER";
}

export function CustomerReturnListPage() {
    const { query, resetQuery, setQuery } = useCustomerReturnQueryState();
    const [role, setRole] = useState<CustomerReturnRole>(readRole);
    const [options, setOptions] = useState<CustomerReturnOptions>({ branches: [], users: [] });
    const [result, setResult] = useState<CustomerReturnListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => { window.localStorage.setItem("hvt.customerReturn.role", role); }, [role]);
    useEffect(() => { void customerReturnApi.getCustomerReturnOptions().then(setOptions); }, []);
    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void customerReturnApi.listCustomerReturns(query)
                .then((response) => { if (active) setResult(response); })
                .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách trả hàng."); })
                .finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query]);

    const returns = result?.content ?? [];

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p><h1 className="text-2xl font-black text-[#172317]">Khách hàng trả hàng</h1><p className="text-sm font-semibold text-[#697684]">Review điều kiện trả hàng, nhận vào quarantine, QC và tạo PNK hoặc disposal request.</p></div><CustomerReturnRoleSwitch onChange={setRole} value={role} /></header><nav className="flex gap-2 overflow-x-auto">{tabs.map((tab) => <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${query.tab === tab.id ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab.id} onClick={() => setQuery({ page: 0, tab: tab.id })} type="button">{tab.label}</button>)}</nav><CustomerReturnFilterBar branches={options.branches} onChange={setQuery} onReset={resetQuery} query={query} />{loading ? <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">{Array.from({ length: 6 }, (_, index) => <div className="h-11 animate-pulse rounded-lg bg-[#eef3ef]" key={index} />)}</div> : null}{!loading && error ? <div className="rounded-xl bg-red-50 p-6 text-sm font-bold text-red-700">{error}</div> : null}{!loading && !error && returns.length === 0 ? <CustomerReturnEmpty text="Không có yêu cầu trả hàng phù hợp" /> : null}{!loading && !error && returns.length > 0 ? <div className="grid gap-3"><CustomerReturnTable returns={returns} /><Pagination onPageChange={(page) => setQuery({ page: page - 1 })} onPageSizeChange={(size) => setQuery({ page: 0, size })} page={query.page + 1} pageCount={result?.totalPages ?? 1} pageSize={query.size} totalItems={result?.totalElements ?? 0} totalItemsLabel="Tổng số return" /></div> : null}</section>;
}
