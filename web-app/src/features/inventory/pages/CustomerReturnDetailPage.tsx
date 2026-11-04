import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { ArrowLeft, CheckCircle2, ShieldAlert } from "lucide-react";
import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "../../../auth/role-utils";
import { getReturnErrorMessage, getReturnReasonLabel } from "../../order/utils/order-return.utils";
import type { ReturnReason } from "../../order/types/order-return.types";
import { InspectionStatusBadge } from "../components/CustomerReturnInspectionComponents";
import { CustomerReturnQcForm } from "../components/CustomerReturnQcForm";
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
    const [selectedLineId, setSelectedLineId] = useState<string | null>(null);

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

            void (async () => {
                try {
                    let inspection: CustomerReturnInspection;
                    try {
                        inspection = await customerReturnApi.getInspection(inspectionId);
                    } catch {
                        // Fallback 1: Attempt to check if inspectionId is an OrderReturn ID (UUID)
                        const qcSummary = await customerReturnApi.getQcSummary(inspectionId).catch(() => null);
                        if (qcSummary?.inspectionId) {
                            inspection = await customerReturnApi.getInspection(qcSummary.inspectionId);
                        } else if (qcSummary) {
                            // HTTP 200 from qc-summary with inspectionId: null means return has no inspection created yet
                            if (!active) return;
                            setDetail(null);
                            setLoading(false);
                            return;
                        } else {
                            // Fallback 2: Attempt to search inspections by returnCode
                            const searchResult = await customerReturnApi.listInspections({
                                returnCode: inspectionId,
                                size: 1,
                                page: 0
                            }).catch(() => null);
                            if (searchResult && searchResult.content.length > 0) {
                                inspection = searchResult.content[0];
                            } else {
                                throw new Error("NOT_FOUND");
                            }
                        }
                    }

                    if (!active) return;
                    setDetail(inspection);
                    setDrafts(Object.fromEntries(inspection.lines.map((line) => [line.id, createCustomerReturnQcDraft(line)])));
                    if (canPost && ["CREATED", "PROCESSING"].includes(inspection.status)) {
                        try {
                            const options = await loadCustomerReturnQcLocations();
                            if (active) setLocations(options);
                        } catch {
                            if (active) setLocationError("Không thể tải vị trí kho. Vui lòng tải lại trang để chọn vị trí trước khi nhập kho.");
                        }
                    }
                } catch {
                    if (active) setError("Không thể tải phiếu kiểm định. Vui lòng thử lại hoặc kiểm tra mã phiếu.");
                } finally {
                    if (active) setLoading(false);
                }
            })();
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

    const lines = detail?.lines ?? [];
    const selectedLine = lines.find((line) => line.id === selectedLineId) ?? lines.find((line) => !line.posted) ?? lines[0];
    const postedCount = lines.filter((line) => line.posted).length;
    const readyCount = pendingLines.filter((line) => !getCustomerReturnQcError(line, drafts[line.id] ?? createCustomerReturnQcDraft(line), locations)).length;

    return <section className="flex min-h-full flex-col gap-4 text-[#172317]">
        <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div className="min-w-0">
                <Link className="inline-flex items-center gap-1.5 text-sm font-black text-[#159734] hover:underline" to="/management/inventory/customer-returns"><ArrowLeft size={16} /> Khách trả hàng</Link>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                    <h1 className="text-2xl font-black">{detail?.inspectionCode ?? "Phiếu kiểm định trả hàng"}</h1>
                    {detail ? <InspectionStatusBadge status={detail.status} /> : null}
                </div>
                {detail ? <p className="text-sm font-semibold text-[#697684]">Phiếu hoàn {detail.returnCode} · Đơn {detail.orderCode ?? "--"}</p> : null}
            </div>
        </header>

        {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold text-[#697684]">Đang tải dữ liệu phiếu...</div> : null}
        {error ? <div role="alert" className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700"><ShieldAlert size={18} /> {error}</div> : null}
        {locationError ? <div role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-800">{locationError}</div> : null}
        {successMessage ? <div role="status" className="flex items-center gap-2 rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800"><CheckCircle2 size={18} /> {successMessage}</div> : null}
        {!loading && !error && !detail ? (
            <div className="rounded-xl border border-[#e8eef0] bg-white p-8 text-center shadow-pos-card">
                <p className="font-bold">Không tìm thấy phiếu kiểm định</p>
                <p className="mt-1 text-sm text-[#697684]">Phiếu hoàn chưa có phiếu kiểm định kho hoặc mã phiếu không tồn tại.</p>
                <Link className="mt-4 inline-flex items-center gap-2 rounded-lg bg-[#159734] px-4 py-2 text-sm font-black text-white hover:bg-[#12822c]" to="/management/inventory/customer-returns">
                    Quay lại danh sách khách trả hàng
                </Link>
            </div>
        ) : null}

        {detail ? <>
            <section className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card sm:grid-cols-2 lg:grid-cols-4">
                <Info label="Khách hàng" value={detail.customerName || "Khách lẻ"} />
                <Info label="Thời gian tạo phiếu" value={formatVietnamDateTime(detail.createdAt)} />
                <Info label="Thời gian xử lý kho" value={detail.postedAt ? formatVietnamDateTime(detail.postedAt) : "Chờ xử lý"} />
                <Info label="Tiến độ" value={`${formatVietnamNumber(postedCount)}/${formatVietnamNumber(lines.length)} dòng đã xử lý`} />
            </section>

            {editable ? <p className="rounded-xl bg-[#eaf7ec] p-3 text-sm text-[#172317]">Chọn tình trạng thực tế sau khi kiểm tra hàng (có thể khác khai báo ban đầu). Chỉ hàng thực tế còn nguyên seal mới được nhập lại kho.</p> : null}

            {lines.length === 0 ? <div className="rounded-xl bg-white p-6 text-sm text-[#697684]">Phiếu chưa có sản phẩm kiểm định.</div> : (
                <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
                    <nav aria-label="Dòng hàng cần kiểm" className="grid content-start gap-2 rounded-xl border border-[#e8eef0] bg-white p-2 shadow-pos-card">
                        {lines.map((line) => {
                            const draft = drafts[line.id] ?? createCustomerReturnQcDraft(line);
                            const lineError = line.posted ? null : getCustomerReturnQcError(line, draft, locations);
                            const active = line.id === selectedLine?.id;
                            return <button
                                aria-current={active ? "true" : undefined}
                                className={`grid gap-1 rounded-lg border p-3 text-left transition-colors ${active ? "border-[#159734] bg-[#f4faf5]" : "border-transparent hover:bg-[#f7fbf8]"}`}
                                key={line.id}
                                onClick={() => setSelectedLineId(line.id)}
                                type="button"
                            >
                                <span className="flex items-start justify-between gap-2">
                                    <strong className="min-w-0 truncate text-sm font-black">{line.itemName ?? "Sản phẩm"}</strong>
                                    <span className="shrink-0 text-sm font-black">×{formatVietnamNumber(Number(line.quantity))}</span>
                                </span>
                                <span className="text-xs font-semibold text-[#697684]">{line.itemCode ?? "Chưa có SKU"}</span>
                                <LineState editable={editable} hasError={Boolean(lineError)} posted={line.posted} />
                            </button>;
                        })}
                    </nav>

                    {selectedLine ? <article className="grid content-start gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-[#edf0f4] pb-4">
                            <div className="min-w-0">
                                <h2 className="text-lg font-black">{selectedLine.itemName ?? "Sản phẩm"}</h2>
                                <p className="text-sm font-semibold text-[#697684]">
                                    {selectedLine.itemCode ?? "Chưa có SKU"} · SL kiểm {formatVietnamNumber(Number(selectedLine.quantity))}
                                    {" · "}Lý do trả: {getReturnReasonLabel(selectedLine.returnReason as ReturnReason) || "Khách yêu cầu trả"}
                                </p>
                            </div>
                            {selectedLine.posted ? <div className="grid justify-items-end gap-1 text-xs">
                                <span className="rounded-full bg-green-50 px-2.5 py-1 font-black text-green-700">Đã xử lý</span>
                                <span className="font-semibold text-[#697684]">{selectedLine.result === "WRITE_OFF" ? "Giao dịch tiêu hủy" : "Giao dịch nhận hàng trả"}</span>
                                {selectedLine.disposalRequestId ? <Link className="font-black text-[#159734] hover:underline" to={`/management/inventory/disposals/${selectedLine.disposalRequestId}`}>{selectedLine.disposalRequestCode ?? "Xem phiếu tiêu hủy"}</Link> : null}
                            </div> : null}
                        </header>
                        <CustomerReturnQcForm
                            draft={drafts[selectedLine.id] ?? createCustomerReturnQcDraft(selectedLine)}
                            editable={editable && !selectedLine.posted && !submitting && !loading}
                            error={selectedLine.posted ? null : getCustomerReturnQcError(selectedLine, drafts[selectedLine.id] ?? createCustomerReturnQcDraft(selectedLine), locations)}
                            key={selectedLine.id}
                            line={selectedLine}
                            locations={locations}
                            onChange={(update) => updateDraft(selectedLine.id, update)}
                        />
                    </article> : null}
                </div>
            )}

            {editable && pendingLines.length > 0 ? (
                <footer className="sticky bottom-0 z-[2] flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-[0_-8px_24px_rgba(31,53,43,0.08)]">
                    <div className="min-w-0 text-sm">
                        <strong className="font-black">Đã điền {formatVietnamNumber(readyCount)}/{formatVietnamNumber(pendingLines.length)} dòng chờ xử lý</strong>
                        {validationErrors.length > 0 ? (
                            <p aria-live="polite" className="truncate text-xs font-semibold text-amber-700" title={validationErrors.join("\n")}>
                                {validationErrors[0]}{validationErrors.length > 1 ? ` (+${validationErrors.length - 1} dòng khác)` : ""}
                            </p>
                        ) : <p className="text-xs font-semibold text-[#697684]">Sẵn sàng xác nhận và hạch toán kho.</p>}
                    </div>
                    <button className="h-11 rounded-xl bg-[#159734] px-5 text-sm font-black text-white shadow-sm hover:bg-[#12822c] disabled:opacity-60" disabled={cannotPost} onClick={() => void handlePostInspection()} type="button">
                        {submitting ? "Đang xử lý..." : "Xác nhận xử lý"}
                    </button>
                </footer>
            ) : null}
        </> : null}
    </section>;
}

function LineState({ editable, hasError, posted }: { editable: boolean; hasError: boolean; posted: boolean }) {
    if (posted) return <span className="text-xs font-black text-green-700">✓ Đã xử lý</span>;
    if (!editable) return <span className="text-xs font-black text-amber-700">Chưa xử lý</span>;
    return hasError
        ? <span className="text-xs font-black text-amber-700">Cần điền thông tin</span>
        : <span className="text-xs font-black text-[#159734]">Sẵn sàng</span>;
}

function Info({ label, value }: { label: string; value: string }) {
    return <div>
        <span className="text-xs font-black uppercase text-[#697684]">{label}</span>
        <p className="mt-1 text-sm font-black text-[#172317]">{value}</p>
    </div>;
}
