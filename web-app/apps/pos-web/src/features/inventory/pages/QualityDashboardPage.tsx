import { AlertTriangle, Clock3, PackageX, ShieldAlert } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
    QualityStatusBadge
} from "../components/QualityComponents";
import {
    qualityBatchStatusLabels,
    qualityBatchStatuses,
    qualityTabLabels,
    qualityTabs
} from "../constants/quality.constants";
import { qualityApi } from "../services/quality.api";
import type { QualityBatchQuery, QualityDashboardResult } from "../types/quality.types";
import { formatExpiryDaysText as daysText, formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

const initialQuery: QualityBatchQuery = { batchCode: "", expiryFrom: "", expiryTo: "", locationId: "ALL", minValue: 0, productType: "ALL", quarantineDays: 0, reason: "", sku: "", status: "ALL", supplier: "", tab: "NEAR_EXPIRY" };

export function QualityDashboardPage() {
    const [query, setQuery] = useState<QualityBatchQuery>(initialQuery);
    const [result, setResult] = useState<QualityDashboardResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void qualityApi.listQualityBatches(query).then((response) => { if (active) setResult(response); }).catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải quality dashboard."); }).finally(() => { if (active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query]);

    const batches = result?.content ?? [];
    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p><h1 className="text-2xl font-black text-[#172317]">Chất lượng và hạn sử dụng</h1><p className="text-sm font-semibold text-[#697684]">Theo dõi sắp hết hạn, hết hạn, quarantine, damaged và disposal. Hàng hết hạn bị blocked nhưng không tự biến mất khỏi tồn vật lý.</p></div></header><div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6"><Card icon={<Clock3 size={18} />} label="Hết hạn trong 7 ngày" value={result?.summary.expiry7Days ?? 0} /><Card icon={<Clock3 size={18} />} label="Hết hạn trong 30 ngày" value={result?.summary.expiry30Days ?? 0} /><Card icon={<AlertTriangle size={18} />} label="Đã hết hạn chưa xử lý" value={result?.summary.expiredUnhandled ?? 0} /><Card icon={<ShieldAlert size={18} />} label="Quarantine quá SLA" value={result?.summary.quarantineOverSla ?? 0} /><Card icon={<PackageX size={18} />} label="Giá trị hàng hỏng" valueText={formatVnd(result?.summary.damagedValue ?? 0)} /><Card icon={<AlertTriangle size={18} />} label="Disposal chờ duyệt" value={result?.summary.pendingDisposalApprovals ?? 0} /></div><nav className="flex gap-2 overflow-x-auto">{qualityTabs.map((tab) => <button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${query.tab === tab ? "bg-[#159734] text-white" : "bg-white text-[#536078] shadow-pos-card"}`} key={tab} onClick={() => setQuery({ ...query, tab })} type="button">{qualityTabLabels[tab]}</button>)}</nav><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, sku: event.currentTarget.value })} placeholder="SKU" value={query.sku} /><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, batchCode: event.currentTarget.value })} placeholder="Batch" value={query.batchCode} /><input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, supplier: event.currentTarget.value })} placeholder="Supplier" value={query.supplier} /><select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(event) => setQuery({ ...query, status: event.currentTarget.value as QualityBatchQuery["status"] })} value={query.status}><option value="ALL">Tất cả status</option>{qualityBatchStatuses.map((status) => <option key={status} value={status}>{qualityBatchStatusLabels[status]}</option>)}</select></section>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{!loading && !error ? <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[1180px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Batch code</th><th>SKU</th><th>Location</th><th>On hand</th><th>Reserved</th><th>Blocked</th><th>Available</th><th>MFG</th><th>EXP</th><th>Days</th><th>Status</th><th>Value</th><th>Action</th></tr></thead><tbody>{batches.map((batch) => <tr className="hover:bg-[#f8fbf8]" key={batch.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black">{batch.batchCode}</td><td className="border-t border-[#edf0f4]">{batch.skuCode}</td><td className="border-t border-[#edf0f4]">{batch.location.name}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(batch.onHandQuantity)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(batch.reservedQuantity)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(batch.blockedQuantity)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(batch.availableQuantity)}</td><td className="border-t border-[#edf0f4]">{formatVietnamDateTime(batch.manufacturingDate)}</td><td className="border-t border-[#edf0f4]">{formatVietnamDateTime(batch.expiryDate)}</td><td className="border-t border-[#edf0f4] font-black">{daysText(batch.expiryDate)}</td><td className="border-t border-[#edf0f4]"><QualityStatusBadge status={batch.status} /></td><td className="border-t border-[#edf0f4]">{formatVnd(batch.estimatedValue)}</td><td className="border-t border-[#edf0f4]"><Link className="text-sm font-black text-[#159734] hover:underline" to={`/management/inventory/quality/batches/${batch.id}`}>Chi tiết</Link></td></tr>)}</tbody></table></div> : null}</section>;
}

function Card({ icon, label, value, valueText }: { icon: ReactNode; label: string; value?: number; valueText?: string }) {
    return <article className="grid min-h-[90px] gap-2 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex items-center gap-2 text-sm font-black text-[#315f3c]"><span className="grid size-8 place-items-center rounded-lg bg-[#edf8ef] text-[#159734]">{icon}</span>{label}</div><strong className="text-2xl font-black text-[#172317]">{valueText ?? formatVietnamNumber(value ?? 0)}</strong></article>;
}
