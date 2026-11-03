import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { ArrowLeft, CheckCircle2, ShieldAlert } from "lucide-react";
import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "../../../auth/role-utils";
import { getReturnErrorMessage, getReturnReasonLabel } from "../../order/utils/order-return.utils";
import type { ReturnReason } from "../../order/types/order-return.types";
import { InspectionStatusBadge } from "../components/CustomerReturnInspectionComponents";
import { CustomerReturnQcFields } from "../components/CustomerReturnQcFields";
import { customerReturnApi, loadCustomerReturnQcLocations } from "../services/customerReturn.api";
import type { CustomerReturnInspection, CustomerReturnQcDraft, CustomerReturnQcLocation } from "../types/customer-return-inspection.types";
import { createCustomerReturnQcDraft, getCustomerReturnQcError } from "../utils/customer-return-qc";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

export function CustomerReturnDetailPage() {
    const { hasAnyRole } = useAuthorization();
    const canPost = hasAnyRole(CUSTOMER_RETURN_QC_ROLES);
    const { returnId: inspectionId } = useParams();
    const [detail, setDetail] = useState<CustomerReturnInspection | null>(null);
    const [drafts, setDrafts] = useState<Record<string, CustomerReturnQcDraft>>({});
    const [locations, setLocations] = useState<CustomerReturnQcLocation[]>([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [locationError, setLocationError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    useEffect(() => {
        if (!inspectionId) return;
        let active = true;
        const timerId = window.setTimeout(() => {
            setLoading(true);
            setError("");
            setLocationError("");
            setDetail(null);
            setDrafts({});
            setLocations([]);
            void customerReturnApi.getInspection(inspectionId)
                .then(async (response) => {
                    if (!active) return;
                    setDetail(response);
                    setDrafts(Object.fromEntries(response.lines.map((line) => [line.id, createCustomerReturnQcDraft(line)])));
                    if (canPost && ["CREATED", "PROCESSING"].includes(response.status)) {
                        try {
                            const options = await loadCustomerReturnQcLocations();
                            if (active) setLocations(options);
                        } catch {
                            if (active) setLocationError("Không thể tải vị trí kho. Vui lòng tải lại trang để chọn vị trí trước khi nhập kho.");
                        }
                    }
                })
                .catch(() => { if (active) setError("Không thể tải phiếu kiểm định. Vui lòng thử lại."); })
                .finally(() => { if (active) setLoading(false); });
        }, 0);
        return () => { active = false; window.clearTimeout(timerId); };
    }, [canPost, inspectionId]);

    if (!inspectionId) return <Navigate replace to="/management/inventory/customer-returns" />;

    const editable = canPost && Boolean(detail && ["CREATED", "PROCESSING"].includes(detail.status));
    const pendingLines = detail?.lines.filter((line) => !line.posted) ?? [];
    const validationErrors = pendingLines.flatMap((line) => {
        const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
        const message = getCustomerReturnQcError(line, draft, locations);
        return message ? [`${line.itemName || line.itemCode || "Sản phẩm"}: ${message}`] : [];
    });
    const cannotPost = loading || submitting || pendingLines.length === 0 || validationErrors.length > 0;

    function updateDraft(lineId: string, update: Partial<CustomerReturnQcDraft>) {
        setDrafts((current) => ({ ...current, [lineId]: { ...current[lineId], ...update } }));
        setError("");
        setSuccessMessage("");
    }

    async function handlePostInspection() {
        if (!detail || !editable || cannotPost) return;
        setSubmitting(true);
        setError("");
        setSuccessMessage("");
        const lines = pendingLines.map((line) => {
            const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
            return {
                lineId: line.id,
                returnItemId: line.returnItemId,
                actualCondition: draft.actualCondition || undefined,
                result: draft.result,
                destinationLocationId: draft.result === "WRITE_OFF" ? undefined : draft.destinationLocationId || undefined,
                note: draft.note.trim()
            };
        });
        try {
            const updated = await customerReturnApi.postInspection(detail.id, { lines });
            setDetail(updated);
            setDrafts(Object.fromEntries(updated.lines.map((line) => [line.id, createCustomerReturnQcDraft(line)])));
            setSuccessMessage(updated.status === "PROCESSING" ? "Phiếu còn dòng chưa hoàn tất xử lý." : "Đã xác nhận tình trạng thực tế và hạch toán hàng trả theo kết quả kiểm định.");
        } catch (postError) {
            setError(getReturnErrorMessage(postError));
        } finally {
            setSubmitting(false);
        }
    }

    return <section className="grid gap-4">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
                <Link className="inline-flex items-center gap-1.5 text-sm font-black text-[#159734] hover:underline" to="/management/inventory/customer-returns"><ArrowLeft size={16} /> Quay lại danh sách</Link>
                <h1 className="mt-1 text-2xl font-black text-[#172317]">{detail?.inspectionCode ?? "Phiếu kiểm định trả hàng"}</h1>
                {detail ? <p className="text-sm font-semibold text-[#697684]">Mã phiếu hoàn: {detail.returnCode} · Mã đơn hàng: {detail.orderCode ?? "Không có mã đơn hàng"}</p> : null}
            </div>
            {editable ? <button className="h-10 rounded-lg bg-[#159734] px-5 text-sm font-black text-white shadow-sm hover:bg-[#12822c] disabled:opacity-60" disabled={cannotPost} onClick={() => void handlePostInspection()} type="button">{submitting ? "Đang xử lý..." : "Xác nhận xử lý"}</button> : null}
        </header>
        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold text-[#697684]">Đang tải dữ liệu phiếu...</div> : null}
        {error ? <div role="alert" className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700"><ShieldAlert size={18} /> {error}</div> : null}
        {locationError ? <div role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-800">{locationError}</div> : null}
        {successMessage ? <div role="status" className="flex items-center gap-2 rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800"><CheckCircle2 size={18} /> {successMessage}</div> : null}
        {detail ? <>
            <section className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card md:grid-cols-2 lg:grid-cols-4">
                <div><span className="text-xs font-black uppercase text-[#697684]">Trạng thái phiếu</span><div className="mt-1"><InspectionStatusBadge status={detail.status} /></div></div>
                <Info label="Khách hàng" value={detail.customerName || "Khách lẻ"} />
                <Info label="Thời gian tạo phiếu" value={formatVietnamDateTime(detail.createdAt)} />
                <Info label="Thời gian xử lý kho" value={detail.postedAt ? formatVietnamDateTime(detail.postedAt) : "Chờ xử lý"} />
            </section>
            {editable ? <p className="rounded-xl bg-[#eaf7ec] p-4 text-sm text-[#172317]">QC chọn tình trạng thực tế sau khi kiểm tra hàng. Tình trạng thực tế có thể khác khai báo ban đầu. Chỉ hàng thực tế còn nguyên seal mới được nhập lại kho.</p> : null}
            {editable && !loading && validationErrors.length > 0 ? <ul aria-live="polite" className="grid gap-1 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">{validationErrors.map((message) => <li key={message}>{message}</li>)}</ul> : null}
            {detail.lines.length === 0 ? <div className="rounded-xl bg-white p-6 text-sm text-[#697684]">Phiếu chưa có sản phẩm kiểm định.</div> : <section className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                <table className="w-full min-w-[1200px] text-left text-sm">
                    <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]"><tr><th className="px-4 py-3">Sản phẩm / Lý do trả</th><th>Số lượng kiểm định</th><th>Tình trạng khai báo</th><th>Tình trạng thực tế</th><th>Phân loại xử lý</th><th>Vị trí đích</th><th>Ghi chú kiểm định</th><th>Giao dịch kho</th></tr></thead>
                    <tbody>{detail.lines.map((line) => <tr className="hover:bg-[#f8fbf8]" key={line.id}>
                        <td className="border-t border-[#edf0f4] px-4 py-3"><strong>{line.itemName ?? "Sản phẩm"}</strong><p className="text-xs text-[#697684]">{line.itemCode ?? "Chưa có SKU"}</p><p className="text-xs text-[#697684]">{getReturnReasonLabel(line.returnReason as ReturnReason) || "Khách yêu cầu trả"}</p></td>
                        <td className="border-t border-[#edf0f4] font-bold">{formatVietnamNumber(Number(line.quantity))}</td>
                        <CustomerReturnQcFields line={line} draft={drafts[line.id] ?? createCustomerReturnQcDraft(line)} editable={editable && !line.posted && !submitting && !loading} locations={locations} onChange={(update) => updateDraft(line.id, update)} />
                        <td className="border-t border-[#edf0f4] px-3 py-3">{line.posted ? <div className="grid gap-1"><span className="text-green-700">Đã xử lý</span><span className="text-xs">{line.result === "WRITE_OFF" ? "Giao dịch tiêu hủy" : "Giao dịch nhận hàng trả"}</span>{line.disposalRequestId ? <Link className="text-xs text-[#159734] hover:underline" to={`/management/inventory/disposals/${line.disposalRequestId}`}>{line.disposalRequestCode ?? "Xem phiếu tiêu hủy"}</Link> : null}</div> : <span className="text-amber-700">Chưa xử lý</span>}</td>
                    </tr>)}</tbody>
                </table>
            </section>}
        </> : null}
    </section>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div><span className="text-xs font-black uppercase text-[#697684]">{label}</span><p className="mt-1 text-sm font-black text-[#172317]">{value}</p></div>;
}
