import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { PosShell } from "../../../components/layout/Pos/PosShell";
import { CustomerReturnStatusBadge } from "../components/CustomerReturnComponents";
import {
    customerReturnConditionLabels,
    customerReturnReasonLabels,
    customerReturnRefundStatusLabels,
    customerReturnResolutionLabels
} from "../constants/customer-return.constants";
import { posCustomerReturnApi as customerReturnApi } from "../services/posCustomerReturn.mock-api";
import type { CustomerReturnDetail } from "../types/customer-return.types";
import { formatVietnamDateTime, formatVietnamNumber, formatVnd } from "../utils/inventory-format";

export function PosCustomerReturnDetailPage() {
    const { returnId } = useParams();
    const [detail, setDetail] = useState<CustomerReturnDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const load = useCallback(() => {
        if (!returnId) return;
        let active = true;
        setLoading(true);
        void customerReturnApi.getCustomerReturn(returnId)
            .then((response) => { if (active) setDetail(response); })
            .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải yêu cầu."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [returnId]);

    useEffect(() => {
        let cleanup: void | (() => void);
        let cancelled = false;
        queueMicrotask(() => { if (!cancelled) cleanup = load(); });
        return () => { cancelled = true; cleanup?.(); };
    }, [load]);

    if (!returnId) return <Navigate to="/pos/customer-returns/new" replace />;

    const checkout = detail ? <aside className="grid gap-3 rounded-2xl bg-white p-4 shadow-pos-panel"><h2 className="text-xl font-black text-[#172317]">{detail.code}</h2><CustomerReturnStatusBadge status={detail.status} /><Info label="Refund/exchange" value={customerReturnRefundStatusLabels[detail.refundStatus]} /><Info label="Payment ref" value={detail.paymentReference ?? "-"} /><Info label="Resolution" value={customerReturnResolutionLabels[detail.desiredResolution]} /><Info label="Giá trị" value={formatVnd(detail.totalValue)} /></aside> : null;

    return <PosShell checkout={checkout}><section className="grid gap-4"><Link className="text-sm font-black text-[#159734]" to="/pos/customer-returns/new">Tạo yêu cầu khác</Link>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{detail ? <><header><h1 className="text-2xl font-black text-[#172317]">{detail.customerName}</h1><p className="text-sm font-semibold text-[#697684]">{detail.orderCode} · {formatVietnamDateTime(detail.requestedAt)}</p></header><div className="grid gap-3 md:grid-cols-2">{detail.lines.map((line) => <article className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card" key={line.id}><strong>{line.skuCode} · {line.productName}</strong><p className="text-sm font-bold text-[#697684]">Qty: {formatVietnamNumber(line.requestedQuantity)} {line.unit}</p><p className="text-sm font-bold text-[#697684]">Reason: {customerReturnReasonLabels[line.reason]}</p><p className="text-sm font-bold text-[#697684]">Condition: {customerReturnConditionLabels[line.condition]}</p>{line.unknownBatch ? <span className="mt-2 inline-flex rounded-full bg-amber-50 px-2 py-1 text-xs font-black text-amber-700">Không xác định lô</span> : null}</article>)}</div><section className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card"><h2 className="font-black">Order/Payment mock</h2><p className="text-sm font-semibold text-[#697684]">Frontend chỉ ghi nhận yêu cầu và hiển thị trạng thái hoàn tiền/đổi hàng, không tự xử lý Payment Service.</p></section></> : null}</section></PosShell>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value}</strong></div>;
}
