import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { customBundleStatusLabels, bundlePaymentStatusLabels } from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type { CustomBundleListQuery, CustomBundleListResult } from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const tabs: CustomBundleListQuery["tab"][] = ["AWAITING_PAYMENT", "PAID", "PACKING", "PACKED", "FAILED"];

export function CustomBundleManagementListPage() {
    const [query, setQuery] = useState<CustomBundleListQuery>({ code: "", customerName: "", locationId: "ALL", orderCode: "", page: 0, pickupFrom: "", pickupTo: "", size: 10, status: "ALL", tab: "AWAITING_PAYMENT" });
    const [result, setResult] = useState<CustomBundleListResult | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            void customBundleApi.listCustomBundles(query).then((response) => { if (active) setResult(response); }).finally(()=>{ if(active) setLoading(false); });
        });
        return () => { active = false; };
    }, [query]);

    const bundles = result?.content ?? [];
    return <section className="grid gap-4">
        <header className="grid gap-1">
            <p className="text-xs font-black uppercase text-[#159734]">Management / Inventory</p>
            <h1 className="text-2xl font-black text-[#172317]">Custom Tea Bundle</h1>
            <p className="text-sm font-semibold text-[#697684]">Packing queue cho bundle Make-to-Order, commit reservation và tạo PXK CUSTOM_BUNDLE_ISSUE.</p>
        </header>
        <nav className="flex gap-2 overflow-x-auto">{tabs.map((tab)=><button className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${query.tab===tab ? "bg-[#159734] text-white" : "bg-white text-[#536078]"}`} key={tab} onClick={()=>setQuery({...query, tab, page:0})} type="button">{tab}</button>)}</nav>
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setQuery({...query,code:e.currentTarget.value,page:0})} placeholder="Mã bundle" value={query.code}/>
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setQuery({...query,orderCode:e.currentTarget.value,page:0})} placeholder="Mã order" value={query.orderCode}/>
            <input className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setQuery({...query,customerName:e.currentTarget.value,page:0})} placeholder="Khách hàng" value={query.customerName}/>
            <select className="h-10 rounded-lg border border-pos-border px-3 text-sm font-bold" onChange={(e)=>setQuery({...query,status:e.currentTarget.value as CustomBundleListQuery["status"],page:0})} value={query.status}><option value="ALL">Tất cả trạng thái</option>{Object.entries(customBundleStatusLabels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select>
        </section>
        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
        {!loading && bundles.length === 0 ? <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white p-10 text-center text-sm font-bold text-[#697684]">Không có bundle trong queue.</div> : null}
        {bundles.length > 0 ? <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[1080px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]"><tr><th className="px-4 py-3">Bundle code</th><th>Order code</th><th>Khách hàng</th><th>Chi nhánh</th><th>Tổng nguyên liệu</th><th>Payment</th><th>Packing status</th><th>Pickup time</th><th>Time waiting</th></tr></thead><tbody>{bundles.map((bundle)=><tr className="hover:bg-[#f8fbf8]" key={bundle.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/custom-bundles/${bundle.id}`}>{bundle.code}</Link></td><td className="border-t border-[#edf0f4]">{bundle.orderCode}</td><td className="border-t border-[#edf0f4]">{bundle.customerName}</td><td className="border-t border-[#edf0f4]">{bundle.branch.name}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(bundle.totalIngredients)}</td><td className="border-t border-[#edf0f4]">{bundlePaymentStatusLabels[bundle.paymentStatus]}</td><td className="border-t border-[#edf0f4]">{customBundleStatusLabels[bundle.status]}</td><td className="border-t border-[#edf0f4]">{bundle.requestedPickupAt ? formatVietnamDateTime(bundle.requestedPickupAt) : "-"}</td><td className="border-t border-[#edf0f4]">Đang chờ</td></tr>)}</tbody></table></div> : null}
    </section>;
}
