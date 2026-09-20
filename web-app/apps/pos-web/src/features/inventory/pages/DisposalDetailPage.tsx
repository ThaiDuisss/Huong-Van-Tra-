import { CheckCircle2, ShieldAlert } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { DisposalStatusBadge } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type { DisposalRequest } from "../types/quality.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

export function DisposalDetailPage() {
    const { disposalId } = useParams();
    const [disposal, setDisposal] = useState<DisposalRequest | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    const load = useCallback(() => {
        if (!disposalId) return;
        let active = true;
        setLoading(true);
        setError("");
        void qualityApi.getDisposalRequest(disposalId)
            .then((response) => {
                if (active) setDisposal(response);
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(loadError instanceof Error
                        ? loadError.message
                        : "Không thể tải yêu cầu tiêu hủy.");
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => { active = false; };
    }, [disposalId]);

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

    const totalQuantity = useMemo(
        () => disposal?.lines.reduce((sum, line) => sum + line.quantity, 0) ?? 0,
        [disposal]
    );

    if (!disposalId) return <Navigate replace to="/management/inventory/disposals" />;

    async function approve() {
        if (!disposal || disposal.status !== "PENDING_APPROVAL") return;
        setSubmitting(true);
        setError("");
        setSuccess("");
        try {
            const updated = await qualityApi.approveDisposalRequest({ disposalId: disposal.id });
            setDisposal(updated);
            setSuccess("Đã duyệt tiêu hủy và ghi nhận giao dịch WRITE_OFF. Phiếu kiểm định liên quan đã được cập nhật.");
        } catch (approveError) {
            setError(approveError instanceof Error ? approveError.message : "Không thể duyệt yêu cầu tiêu hủy.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/disposals">Quay lại danh sách</Link>
                    <h1 className="text-2xl font-black text-[#172317]">{disposal?.code ?? "Chi tiết tiêu hủy"}</h1>
                    {disposal ? <p className="text-sm font-semibold text-[#697684]">Phiếu trả {disposal.returnCode} · Kiểm định {disposal.inspectionCode}</p> : null}
                </div>
                {disposal?.status === "PENDING_APPROVAL" ? (
                    <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={() => void approve()} type="button">
                        {submitting ? "Đang duyệt..." : "Duyệt tiêu hủy"}
                    </button>
                ) : null}
            </header>

            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
            {error ? <div className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700"><ShieldAlert size={16} />{error}</div> : null}
            {success ? <div className="flex items-center gap-2 rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800"><CheckCircle2 size={16} />{success}</div> : null}

            {disposal ? (
                <>
                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
                        <div><span className="text-xs font-black text-[#697684]">Trạng thái</span><div className="mt-1"><DisposalStatusBadge status={disposal.status} /></div></div>
                        <Info label="Số lượng tiêu hủy" value={formatVietnamNumber(totalQuantity)} />
                        <Info label="Thời gian tạo" value={formatVietnamDateTime(disposal.requestedAt)} />
                        <Info label="Thời gian duyệt" value={disposal.approvedAt ? formatVietnamDateTime(disposal.approvedAt) : "Chưa duyệt"} />
                        <Info label="Lý do" value={disposal.reason} />
                        <Info label="Ghi chú" value={disposal.note ?? "-"} />
                        {disposal.sourceInspectionId ? (
                            <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
                                <span className="text-xs font-black text-[#697684]">Phiếu kiểm định</span>
                                <Link className="text-sm font-black text-[#159734] hover:underline" to={`/management/inventory/customer-returns/${disposal.sourceInspectionId}`}>
                                    {disposal.inspectionCode ?? "Xem phiếu kiểm định"}
                                </Link>
                            </div>
                        ) : null}
                    </section>

                    <section className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                        <table className="w-full min-w-[720px] text-left text-sm">
                            <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Sản phẩm</th><th>SKU</th><th>Số lượng</th><th>Kết quả</th></tr></thead>
                            <tbody>{disposal.lines.map((line) => (
                                <tr key={line.id}>
                                    <td className="border-t border-[#edf0f4] px-4 py-3 font-black">{line.skuName}</td>
                                    <td className="border-t border-[#edf0f4]">{line.skuCode}</td>
                                    <td className="border-t border-[#edf0f4]">{formatVietnamNumber(line.quantity)} {line.unit}</td>
                                    <td className="border-t border-[#edf0f4]">{disposal.status === "APPROVED" ? "Đã ghi nhận WRITE_OFF" : "Chờ phê duyệt"}</td>
                                </tr>
                            ))}</tbody>
                        </table>
                    </section>

                    <div className={`rounded-xl border p-4 text-sm font-semibold ${disposal.status === "APPROVED" ? "border-green-200 bg-green-50 text-green-900" : "border-amber-200 bg-amber-50 text-amber-900"}`}>
                        {disposal.status === "APPROVED"
                            ? "Yêu cầu đã terminal. Tải lại trang vẫn giữ trạng thái đã duyệt và thao tác lặp không tạo thêm giao dịch."
                            : "Hàng tiêu hủy chưa được tính là xử lý vật lý và phiếu kiểm định chưa được xác nhận hoàn tất về Order-Service."}
                    </div>
                </>
            ) : null}
        </section>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2"><span className="text-xs font-black text-[#697684]">{label}</span><strong className="text-sm font-black text-[#172317]">{value || "-"}</strong></div>;
}
