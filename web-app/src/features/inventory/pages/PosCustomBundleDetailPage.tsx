import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { bundlePaymentStatusLabels, bundleReservationLabels, customBundleStatusLabels } from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type { CustomBundleDetail } from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function PosCustomBundleDetailPage() {
    const { bundleId } = useParams();
    const [bundle, setBundle] = useState<CustomBundleDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const load = useCallback(() => {
        if (!bundleId) return;
        let active = true;
        setLoading(true);
        void customBundleApi.getCustomBundle(bundleId)
            .then((response) => { if (active) setBundle(response); })
            .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải bundle."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [bundleId]);

    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) cleanup = load();
        });
        return () => {
            cancelled = true;
            cleanup?.();
        };
    }, [load]);

    async function mockPay(success: boolean) {
        if (!bundle) return;
        try {
            setSubmitting(true);
            const updated = await customBundleApi.mockPaymentResult(bundle.id, success);
            setBundle(updated);
        } finally {
            setSubmitting(false);
        }
    }

    async function release() {
        if (!bundle) return;
        try {
            setSubmitting(true);
            setBundle(await customBundleApi.releaseCustomBundleReservation(bundle.id));
        } finally {
            setSubmitting(false);
        }
    }

    if (!bundleId) return <Navigate to="/pos/custom-bundles/new" replace />;
    const checkout = bundle ? <aside className="grid gap-3 rounded-2xl bg-white p-4 shadow-pos-panel">
        <h2 className="text-xl font-black text-[#172317]">{bundle.code}</h2>
        <Info label="Trạng thái" value={customBundleStatusLabels[bundle.status]} />
        <Info label="Payment" value={bundlePaymentStatusLabels[bundle.paymentStatus]} />
        <Info label="Reservation" value={bundleReservationLabels[bundle.reservationStatus]} />
        <Info label="Giá dự kiến" value={formatVnd(bundle.estimatedPrice)} />
        <Info label="Nhận lúc" value={bundle.requestedPickupAt ? formatVietnamDateTime(bundle.requestedPickupAt) : "-"} />
        {bundle.status === "RESERVATION_FAILED" ? <div className="rounded-lg bg-red-50 p-3 text-sm font-bold text-red-700">{bundle.lines.filter((line)=>line.quantity>line.availableQuantity).map((line)=>`${line.skuCode}: available ${line.availableQuantity}, required ${line.quantity}`).join("; ") || "Giữ nguyên liệu thất bại."}</div> : null}
        <button className="h-10 rounded-lg bg-[#159734] text-sm font-black text-white disabled:opacity-60" disabled={submitting || bundle.reservationStatus !== "RESERVED"} onClick={()=>void mockPay(true)} type="button">Mock thanh toán thành công</button>
        <button className="h-10 rounded-lg border border-red-200 text-sm font-black text-red-700 disabled:opacity-60" disabled={submitting || bundle.reservationStatus !== "RESERVED"} onClick={()=>void mockPay(false)} type="button">Mock thanh toán thất bại</button>
        <button className="h-10 rounded-lg border border-[#dce5e1] text-sm font-black text-[#536078] disabled:opacity-60" disabled={submitting || bundle.reservationStatus !== "RESERVED"} onClick={()=>void release()} type="button">Release reservation</button>
    </aside> : null;

    return <PosShell checkout={checkout}>
        <section className="grid gap-4">
            <Link className="text-sm font-black text-[#159734]" to="/pos/custom-bundles/new">Tạo bundle khác</Link>
            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
            {error ? <div className="rounded-xl bg-red-50 p-6 text-sm font-bold text-red-700">{error}</div> : null}
            {bundle ? <div className="grid gap-3">
                <h1 className="text-2xl font-black text-[#172317]">{bundle.displayName || bundle.bundleName || bundle.code}</h1>
                <div className="grid gap-3 md:grid-cols-2">
                    {bundle.lines.map((line)=><div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card" key={line.id}><strong>{line.ingredientName}</strong><p className="text-sm font-bold text-[#697684]">{line.skuCode}</p><p>{formatVietnamNumber(line.quantity)} {line.unit}</p><p>{formatVnd(line.price)}</p></div>)}
                </div>
            </div> : null}
        </section>
    </PosShell>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}
