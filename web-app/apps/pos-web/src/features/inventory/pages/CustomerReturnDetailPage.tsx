import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { InspectionStatusBadge } from "../components/CustomerReturnInspectionComponents";
import { customerReturnApi } from "../services/customerReturn.api";
import type { CustomerReturnInspection } from "../types/customer-return-inspection.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const resultLabels = {
    DAMAGED: "Hàng hỏng",
    RESTOCK: "Nhập lại kho",
    WRITE_OFF: "Ghi giảm"
} as const;

export function CustomerReturnDetailPage() {
    const { returnId: inspectionId } = useParams();
    const [detail, setDetail] = useState<CustomerReturnInspection | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!inspectionId) return;
        let active = true;
        void customerReturnApi.getInspection(inspectionId)
            .then((response) => { if (active) setDetail(response); })
            .catch((loadError: unknown) => { if (active) setError(loadError instanceof Error ? loadError.message : "Không thể tải phiếu kiểm định."); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [inspectionId]);

    if (!inspectionId) return <Navigate replace to="/management/inventory/customer-returns" />;

    async function postInspection() {
        if (!detail || detail.status !== "CREATED") return;
        setSubmitting(true);
        setError("");
        try {
            setDetail(await customerReturnApi.postInspection(detail.id));
        } catch (postError) {
            setError(postError instanceof Error ? postError.message : "Không thể xử lý phiếu kiểm định.");
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4"><header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><Link className="text-sm font-black text-[#159734]" to="/management/inventory/customer-returns">Quay lại danh sách</Link><h1 className="text-2xl font-black text-[#172317]">{detail?.inspectionCode ?? "Phiếu kiểm định trả hàng"}</h1>{detail ? <p className="text-sm font-semibold text-[#697684]">{detail.returnCode} · {detail.orderCode ?? "Không có mã đơn hàng"}</p> : null}</div>{detail?.status === "CREATED" ? <button className="h-10 rounded-lg bg-[#159734] px-4 text-sm font-black text-white disabled:opacity-60" disabled={submitting} onClick={() => void postInspection()} type="button">{submitting ? "Đang xử lý..." : "Xác nhận xử lý kho"}</button> : null}</header>{loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}{error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}{detail ? <><section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4"><div><span className="text-xs font-black text-[#697684]">Trạng thái</span><div className="mt-1"><InspectionStatusBadge status={detail.status} /></div></div><Info label="Ngày tạo" value={formatVietnamDateTime(detail.createdAt)} /><Info label="Ngày xử lý" value={detail.postedAt ? formatVietnamDateTime(detail.postedAt) : "Chưa xử lý"} /><Info label="Số dòng hàng" value={formatVietnamNumber(detail.lines.length)} /></section><section className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Sản phẩm</th><th>Số lượng</th><th>Phân loại QC</th><th>Vị trí đích</th><th>Giao dịch kho</th></tr></thead><tbody>{detail.lines.map((line) => <tr key={line.id}><td className="border-t border-[#edf0f4] px-4 py-3"><strong>{line.itemName ?? "Sản phẩm"}</strong><p className="text-xs text-[#697684]">{line.itemCode ?? "Chưa có mã"}</p></td><td className="border-t border-[#edf0f4]">{formatVietnamNumber(Number(line.quantity))}</td><td className="border-t border-[#edf0f4]">{resultLabels[line.result]}</td><td className="border-t border-[#edf0f4]">{line.destinationLocationName ?? (line.result === "WRITE_OFF" ? "Không áp dụng" : "Chưa xác định")}</td><td className="border-t border-[#edf0f4]">{line.posted ? "Đã tạo" : "Chưa tạo"}</td></tr>)}</tbody></table></section>{detail.status === "POSTED" ? <p className="rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800">Phiếu đã được POSTED. Inventory transaction và cập nhật tồn kho đã được backend xử lý; thao tác POST không còn hiển thị để tránh ghi nhận hai lần.</p> : null}</> : null}</section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div><span className="text-xs font-black text-[#697684]">{label}</span><p className="mt-1 text-sm font-black text-[#172317]">{value}</p></div>;
}
