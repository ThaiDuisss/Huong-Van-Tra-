import { FileText } from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { QualityStatusBadge } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type { BatchQualityDetail } from "../types/quality.types";
import { formatExpiryDaysText as daysText, formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function QualityBatchDetailPage() {
    const { batchId } = useParams();
    const { roles } = useAuth();
    const [batch, setBatch] = useState<BatchQualityDetail | null>(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);

    const isAdmin = hasAnyRole(roles, ["ADMIN"]);

    const load = useCallback(() => {
        if (!batchId) return;
        let active = true;
        setLoading(true);
        void qualityApi.getBatchQualityDetail(batchId).then((response) => { if (active) setBatch(response); }).catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải batch."); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [batchId]);

    useEffect(() => { let cleanup: void | (() => void); let cancelled = false; queueMicrotask(() => { if (!cancelled) cleanup = load(); }); return () => { cancelled = true; cleanup?.(); }; }, [load]);
    if (!batchId) return <Navigate to="/management/inventory/quality" replace />;

    async function run(action: () => Promise<BatchQualityDetail>, fallback: string) {
        setError("");
        try { setSubmitting(true); setBatch(await action()); } catch (actionError) { setError(actionError instanceof Error ? actionError.message : fallback); } finally { setSubmitting(false); }
    }

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><Link className="text-sm font-black text-[#159734]" to="/management/inventory/quality">Quay lại dashboard</Link><h1 className="text-2xl font-black text-[#172317]">{batch?.batchCode ?? "Chi tiết lô hàng"}</h1>{batch ? <p className="text-sm font-semibold text-[#697684]">{batch.skuCode} · {batch.supplier}</p> : null}</div></header>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{batch ? <><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div className="grid gap-2"><QualityStatusBadge status={batch.status} /><p className="text-sm font-semibold text-[#536078]">{daysText(batch.expiryDate)} · Tồn vật lý {formatVietnamNumber(batch.onHandQuantity)} {batch.unit} · Giá trị {formatVnd(batch.estimatedValue)}</p></div><div className="flex flex-wrap gap-2"><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void run(() => qualityApi.moveBatchToQuarantine({ batchId: batch.id }), "Không thể chuyển vào khu cách ly.")} type="button">Chuyển vào cách ly</button><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void run(() => qualityApi.releaseBatchFromQuarantine({ batchId: batch.id }), "Không thể giải phóng cách ly.")} type="button">Giải phóng khỏi cách ly</button><button className="h-10 rounded-lg border border-red-200 px-4 text-sm font-black text-red-700" disabled={submitting} onClick={() => void run(() => qualityApi.markBatchDamaged({ batchId: batch.id }), "Không thể đánh dấu hỏng.")} type="button">Đánh dấu hỏng</button><button className="h-10 rounded-lg border border-amber-200 px-4 text-sm font-black text-amber-700" disabled={submitting} onClick={() => void run(() => qualityApi.blockBatch({ batchId: batch.id }), "Không thể khóa.")} type="button">Khóa lô</button><button className="h-10 rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" disabled={submitting} onClick={() => void run(() => qualityApi.unblockBatch({ adminOverride: isAdmin, batchId: batch.id }), "Không thể mở khóa.")} type="button">Mở khóa</button><Link className="inline-flex h-10 items-center rounded-lg border border-[#d8eadc] px-4 text-sm font-black text-[#159734]" to="/management/inventory/supplier-returns/new">Tạo trả NCC</Link><button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white" disabled={submitting} onClick={() => void qualityApi.createDisposalRequest({ lines: [{ batchId: batch.id, evidence: ["quality-evidence.jpg"], quantity: Math.min(1, batch.blockedQuantity + batch.damagedQuantity), reason: batch.reason ?? "Hàng không đạt chất lượng" }], locationId: batch.location.id, method: "DESTROY", reason: batch.reason ?? "Hàng không đạt chất lượng", scheduledAt: "2026-08-05T09:00:00.000Z", witnesses: ["QC", "Kho"] }).then((created) => { window.location.href = `/management/inventory/disposals/${created.id}`; }).catch((createError: unknown) => setError(createError instanceof Error ? createError.message : "Không thể tạo yêu cầu tiêu hủy."))} type="button">Tạo tiêu hủy</button></div></div></section><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><Info label="Sản phẩm/SKU" value={`${batch.skuCode} · ${batch.skuName}`} /><Info label="Nhà cung cấp" value={batch.supplier} /><Info label="Chứng từ gốc" value={batch.receiptCode} /><Info label="Lô nhà cung cấp" value={batch.supplierLot} /><Info label="Ngày sản xuất" value={formatVietnamDateTime(batch.manufacturingDate)} /><Info label="Hạn sử dụng" value={formatVietnamDateTime(batch.expiryDate)} /><Info label="Tạm giữ" value={formatVietnamNumber(batch.reservedQuantity)} /><Info label="Bị khóa" value={formatVietnamNumber(batch.blockedQuantity)} /></section><Panel title="Tồn theo vị trí kho">{batch.locationBalances.map((item) => <p className="text-sm font-semibold text-[#536078]" key={item.location.id}>{item.location.name}: {formatVietnamNumber(item.quantity)} · Khóa {formatVietnamNumber(item.blockedQuantity)}</p>)}</Panel><Panel title="Tạm giữ (Reservation)">{batch.reservations.length === 0 ? <p className="text-sm font-semibold text-[#697684]">Không có thông tin tạm giữ.</p> : batch.reservations.map((item) => <p className="text-sm font-semibold text-[#536078]" key={item.id}>{item.referenceCode}: {formatVietnamNumber(item.quantity)}</p>)}</Panel><Panel title="Lịch sử biến động">{batch.movements.map((item) => <p className="text-sm font-semibold text-[#536078]" key={item.id}>{formatVietnamDateTime(item.happenedAt)} · {item.type} · {formatVietnamNumber(item.quantity)} · {item.referenceCode}</p>)}</Panel><Panel title="Sự cố chất lượng">{batch.qualityEvents.map((item) => <p className="text-sm font-semibold text-[#536078]" key={item.id}>{item.code} · {item.reason} · {item.severity} · {item.recommendedAction}</p>)}</Panel><Panel title="Chứng từ liên quan">{batch.documentLinks.map((item) => <Link className="inline-flex items-center gap-2 text-sm font-black text-[#159734]" key={item.id} to={item.type === "GOODS_RECEIPT" ? `/management/inventory/receipts/${item.id}` : `/management/inventory/documents/${item.id}`}><FileText size={16} />{item.code}</Link>)}</Panel></> : null}</section>;
}

function Panel({ children, title }: { children: ReactNode; title: string }) {
    return <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><h2 className="font-black text-[#172317]">{title}</h2>{children}</section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value || "-"}</strong></div>;
}
