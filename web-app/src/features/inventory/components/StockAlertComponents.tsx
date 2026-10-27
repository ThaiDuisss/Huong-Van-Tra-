import { AlertTriangle, CheckCircle2, Clock3 } from "lucide-react";
import { Link } from "react-router-dom";

import {
    stockAlertSeverityLabels,
    stockAlertStatusLabels,
    stockAlertTypeLabels
} from "../constants/stock-alert.constants";
import type {
    ReorderPolicy,
    StockAlert,
    StockAlertSeverity,
    StockAlertStatus
} from "../types/stock-alert.types";
import { alertLocations, alertSkus } from "../mocks/stock-alerts.mock";
import { formatVietnamNumber } from "../utils/inventory-format";

export function StockAlertStatusBadge({ status }: { status: StockAlertStatus }) {
    const tone = status === "OPEN" ? "border-red-200 bg-red-50 text-red-700" : status === "RESOLVED" ? "border-green-200 bg-green-50 text-green-700" : "border-amber-200 bg-amber-50 text-amber-700";
    const icon = status === "RESOLVED" ? <CheckCircle2 size={14} /> : <Clock3 size={14} />;
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}>{icon}{stockAlertStatusLabels[status]}</span>;
}

export function StockAlertSeverityBadge({ severity }: { severity: StockAlertSeverity }) {
    const tone = severity === "CRITICAL" ? "border-red-200 bg-red-50 text-red-700" : severity === "HIGH" ? "border-amber-200 bg-amber-50 text-amber-700" : "border-blue-200 bg-blue-50 text-blue-700";
    return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-black ${tone}`}><AlertTriangle size={14} />{stockAlertSeverityLabels[severity]}</span>;
}

function alertAge(createdAt: string) {
    const days = Math.floor((Date.now() - new Date(createdAt).getTime()) / 86400000);
    if (days <= 0) return "Hôm nay";
    return `${days} ngày`;
}

export function StockAlertTable({ alerts }: { alerts: StockAlert[] }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[1260px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">SKU</th><th>Location</th><th>On hand</th><th>Reserved</th><th>Blocked</th><th>Available</th><th>Minimum</th><th>Reorder point</th><th>Suggested</th><th>Loại cảnh báo</th><th>Severity</th><th>Tuổi</th><th>Status</th></tr></thead><tbody>{alerts.map((alert) => <tr className="hover:bg-[#f8fbf8]" key={alert.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black"><Link className="text-[#159734] hover:underline" to={`/management/inventory/alerts/${alert.id}`}>{alert.sku.skuCode}</Link><p className="text-xs font-semibold text-[#697684]">{alert.sku.skuName}</p></td><td className="border-t border-[#edf0f4]">{alert.location.name}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(alert.balance.onHand)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(alert.balance.reserved)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(alert.balance.blocked)}</td><td className="border-t border-[#edf0f4] font-black">{formatVietnamNumber(alert.balance.available)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(alert.policy.minimumStock)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(alert.policy.reorderPoint)}</td><td className="border-t border-[#edf0f4] font-black">{formatVietnamNumber(alert.suggestedQuantity)}</td><td className="border-t border-[#edf0f4]">{stockAlertTypeLabels[alert.type]}</td><td className="border-t border-[#edf0f4]"><StockAlertSeverityBadge severity={alert.severity} /></td><td className="border-t border-[#edf0f4]">{alertAge(alert.createdAt)}</td><td className="border-t border-[#edf0f4]"><StockAlertStatusBadge status={alert.status} /></td></tr>)}</tbody></table></div>;
}

export function ReorderPolicyTable({ onEdit, policies }: { onEdit: (policy: ReorderPolicy) => void; policies: ReorderPolicy[] }) {
    return <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[1100px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">SKU</th><th>Location</th><th>Minimum</th><th>Safety stock</th><th>Reorder point</th><th>Standard reorder quantity</th><th>Maximum</th><th>Lead time</th><th>Avg/day</th><th>Status</th><th>Action</th></tr></thead><tbody>{policies.map((policy) => { const sku = alertSkus.find((item) => item.id === policy.skuId); const location = alertLocations.find((item) => item.id === policy.locationId); return <tr key={policy.id}><td className="border-t border-[#edf0f4] px-4 py-3 font-black">{sku?.skuCode}<p className="text-xs font-semibold text-[#697684]">{sku?.skuName}</p></td><td className="border-t border-[#edf0f4]">{location?.name}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(policy.minimumStock)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(policy.safetyStock)}</td><td className="border-t border-[#edf0f4] font-black">{formatVietnamNumber(policy.reorderPoint)}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(policy.standardReorderQuantity)}</td><td className="border-t border-[#edf0f4]">{policy.maximumStock ? formatVietnamNumber(policy.maximumStock) : "-"}</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(policy.supplierLeadTimeDays ?? 0)} ngày</td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(policy.averageDailyConsumption ?? 0)}</td><td className="border-t border-[#edf0f4]">{policy.enabled ? "Đang bật" : "Tắt"}</td><td className="border-t border-[#edf0f4]"><button className="font-black text-[#159734]" onClick={() => onEdit(policy)} type="button">Sửa</button></td></tr>; })}</tbody></table></div>;
}
