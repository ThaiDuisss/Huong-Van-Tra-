import { AlertTriangle, PackageCheck, ShoppingCart, Truck } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
    StockAlertRoleSwitch,
    StockAlertTable
} from "../components/StockAlertComponents";
import {
    stockAlertSeverities,
    stockAlertSeverityLabels,
    stockAlertStatuses,
    stockAlertStatusLabels,
    stockAlertTypeLabels,
    stockAlertTypes
} from "../constants/stock-alert.constants";
import { stockAlertApi } from "../services/stockAlert.api";
import { inventoryMasterLocations } from "../mocks/inventory-master-data.mock";
import type {
    StockAlertListQuery,
    StockAlertListResult,
    StockAlertRole
} from "../types/stock-alert.types";
import { formatVietnamNumber } from "../utils/inventory-format";

function readRole(): StockAlertRole {
    const stored = window.localStorage.getItem("hvt.stockAlert.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "ACCOUNTANT", "DIRECTOR", "SALES_STAFF"].includes(stored) ? stored as StockAlertRole : "WAREHOUSE";
}

const initialQuery: StockAlertListQuery = {
    createdFrom: "",
    hasAction: "ALL",
    locationId: "ALL",
    page: 0,
    role: "WAREHOUSE",
    severity: "ALL",
    size: 10,
    skuOrCategory: "",
    status: "ALL",
    type: "ALL"
};

export function StockAlertListPage() {
    const [role, setRole] = useState<StockAlertRole>(readRole);
    const [query, setQuery] = useState<StockAlertListQuery>({ ...initialQuery, role });
    const [result, setResult] = useState<StockAlertListResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        window.localStorage.setItem("hvt.stockAlert.role", role);
        queueMicrotask(() => {
            setQuery((current) => ({ ...current, page: 0, role }));
        });
    }, [role]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void stockAlertApi.listStockAlerts(query).then((response) => {
                if (active) setResult(response);
            }).catch((loadError: unknown) => {
                if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải cảnh báo tồn kho.");
            }).finally(() => {
                if (active) setLoading(false);
            });
        });
        return () => { active = false; };
    }, [query]);

    const alerts = result?.content ?? [];
    return <section className="grid gap-4">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p>
                <h1 className="text-2xl font-black text-[#172317]">Cảnh báo tồn kho</h1>
                <p className="text-sm font-semibold text-[#697684]">Theo dõi thiếu hàng, sắp hết hàng, reservation cao và đề xuất bổ sung. Không tự động đặt mua hàng.</p>
            </div>
            <div className="flex flex-wrap items-end gap-2">
                <StockAlertRoleSwitch onChange={setRole} value={role} />
                <Link className="inline-flex h-10 items-center rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" to="/management/inventory/reorder-policies">Reorder policies</Link>
            </div>
        </header>

        <section className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
            <Summary icon={<AlertTriangle size={18} />} label="Hết hàng" value={result?.summary.outOfStock ?? 0} />
            <Summary icon={<AlertTriangle size={18} />} label="Dưới reorder point" value={result?.summary.belowReorderPoint ?? 0} />
            <Summary icon={<Truck size={18} />} label="Cần chuyển kho" value={result?.summary.transferNeeded ?? 0} />
            <Summary icon={<ShoppingCart size={18} />} label="Cần mua hàng" value={result?.summary.purchaseNeeded ?? 0} />
            <Summary icon={<AlertTriangle size={18} />} label="Nghiêm trọng" value={result?.summary.critical ?? 0} />
            <Summary icon={<PackageCheck size={18} />} label="Quá SLA" value={result?.summary.overSla ?? 0} />
        </section>

        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
            <label className="grid gap-1 text-xs font-black text-[#536078]">Loại cảnh báo<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, type: event.currentTarget.value as StockAlertListQuery["type"] })} value={query.type}><option value="ALL">Tất cả</option>{stockAlertTypes.map((type) => <option key={type} value={type}>{stockAlertTypeLabels[type]}</option>)}</select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Severity<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, severity: event.currentTarget.value as StockAlertListQuery["severity"] })} value={query.severity}><option value="ALL">Tất cả</option>{stockAlertSeverities.map((severity) => <option key={severity} value={severity}>{stockAlertSeverityLabels[severity]}</option>)}</select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Location<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, locationId: event.currentTarget.value, page: 0 })} value={query.locationId}><option value="ALL">Tất cả</option><option value={inventoryMasterLocations.central.id}>{inventoryMasterLocations.central.name}</option><option value={inventoryMasterLocations.branchCauGiay.id}>{inventoryMasterLocations.branchCauGiay.name}</option><option value={inventoryMasterLocations.branchTayMo.id}>{inventoryMasterLocations.branchTayMo.name}</option></select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">SKU/category<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, skuOrCategory: event.currentTarget.value })} value={query.skuOrCategory} /></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Status<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, page: 0, status: event.currentTarget.value as StockAlertListQuery["status"] })} value={query.status}><option value="ALL">Tất cả</option>{stockAlertStatuses.map((status) => <option key={status} value={status}>{stockAlertStatusLabels[status]}</option>)}</select></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Created date<input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, createdFrom: event.currentTarget.value, page: 0 })} type="date" value={query.createdFrom} /></label>
            <label className="grid gap-1 text-xs font-black text-[#536078]">Có action<select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, hasAction: event.currentTarget.value as StockAlertListQuery["hasAction"], page: 0 })} value={query.hasAction}><option value="ALL">Tất cả</option><option value="YES">Đã có action</option><option value="NO">Chưa có action</option></select></label>
        </section>

        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
        {!loading && !error && alerts.length === 0 ? <div className="rounded-xl bg-white p-8 text-center text-sm font-bold text-[#697684]">Không có cảnh báo phù hợp.</div> : null}
        {!loading && !error && alerts.length > 0 ? <StockAlertTable alerts={alerts} /> : null}
        {result ? <footer className="flex items-center justify-between rounded-xl bg-white p-3 text-sm font-bold text-[#536078]"><span>Trang {result.page + 1}/{result.totalPages} · {formatVietnamNumber(result.totalElements)} cảnh báo</span><div className="flex gap-2"><button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page === 0} onClick={() => setQuery({ ...query, page: Math.max(0, query.page - 1) })} type="button">Trước</button><button className="h-9 rounded-lg border border-[#d8eadc] px-3 font-black" disabled={query.page + 1 >= result.totalPages} onClick={() => setQuery({ ...query, page: query.page + 1 })} type="button">Sau</button></div></footer> : null}
    </section>;
}

function Summary({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
    return <article className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex items-center gap-2 text-sm font-black text-[#315f3c]"><span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{icon}</span>{label}</div><strong className="text-2xl font-black text-[#172317]">{formatVietnamNumber(value)}</strong></article>;
}
