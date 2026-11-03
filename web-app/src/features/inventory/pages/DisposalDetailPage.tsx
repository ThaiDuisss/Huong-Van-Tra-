import { CheckCircle2, ShieldAlert } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuthorization } from "../../../auth/useAuthorization";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { DisposalStatusBadge } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type { DisposalRequest } from "../types/quality.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { isCustomerReturnDisposal } from "../utils/inventory-labels";
import { ROLES } from "../../../auth/roles";
import listStyles from "./CustomerReturnListPage.module.css";
import styles from "./DisposalDetailPage.module.css";

export function DisposalDetailPage() {
    const { disposalId } = useParams();
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole([ROLES.SUPER_ADMIN, ROLES.MANAGER]);
    const [disposal, setDisposal] = useState<DisposalRequest | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus({ preventScroll: true });
    }, [disposalId]);

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
    const isCustomerReturn = isCustomerReturnDisposal(disposal);

    if (!disposalId) return <Navigate replace to="/management/inventory/disposals" />;

    async function approve() {
        if (!disposal || isCustomerReturn || disposal.status !== "PENDING_APPROVAL" || submitting || !canApprove) return;
        setSubmitting(true);
        setError("");
        setSuccess("");
        try {
            const updated = await qualityApi.approveDisposalRequest({ disposalId: disposal.id });
            setDisposal(updated);
            setConfirmOpen(false);
            setSuccess("Đã phê duyệt yêu cầu tiêu hủy.");
        } catch (approveError) {
            setError(approveError instanceof Error ? approveError.message : "Không thể phê duyệt yêu cầu tiêu hủy.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <Link className={styles.backLink} to="/management/inventory/disposals">
                    Quay lại danh sách
                </Link>
                <div className={styles.heading}>
                    <h1 ref={titleRef} tabIndex={-1}>{disposal?.code ?? "Chi tiết tiêu hủy"}</h1>
                    {disposal ? <DisposalStatusBadge customerReturn={isCustomerReturn} status={disposal.status} /> : null}
                </div>
            </header>

            {loading ? <div className={listStyles.notice} role="status">Đang tải yêu cầu tiêu hủy...</div> : null}
            {error && !confirmOpen ? (
                <div className={`${listStyles.error} flex items-center gap-2`} role="alert">
                    <ShieldAlert size={16} />{error}
                </div>
            ) : null}
            {success ? (
                <div className={`${listStyles.notice} flex items-center gap-2`} role="status">
                    <CheckCircle2 size={16} />{success}
                </div>
            ) : null}

            {disposal ? (
                <>
                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <h2 className="text-sm font-black uppercase text-[#315f3c]">Thông tin yêu cầu</h2>
                        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                            <Info label="Mã yêu cầu" value={disposal.code} />
                            <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
                                <span className="text-xs font-black text-[#697684]">Trạng thái</span>
                                <div><DisposalStatusBadge customerReturn={isCustomerReturn} status={disposal.status} /></div>
                            </div>
                            <Info label="Ngày tạo" value={formatVietnamDateTime(disposal.requestedAt)} />
                            {disposal.approvedAt ? (
                                <Info label={isCustomerReturn ? "Ngày xử lý" : "Ngày duyệt"} value={formatVietnamDateTime(disposal.approvedAt)} />
                            ) : null}
                        </div>
                        {disposal.note ? <Info label="Ghi chú" value={disposal.note} /> : null}
                    </section>

                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <h2 className="text-sm font-black uppercase text-[#315f3c]">Tham chiếu</h2>
                        <div className="grid gap-3 sm:grid-cols-2">
                            <Info label="Phiếu trả" value={disposal.returnCode ?? "–"} />
                            <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
                                <span className="text-xs font-black text-[#697684]">Phiếu kiểm định</span>
                                {disposal.sourceInspectionId ? (
                                    <Link
                                        className="text-sm font-black text-[#159734] hover:underline"
                                        to={`/management/inventory/customer-returns/${disposal.sourceInspectionId}`}
                                    >
                                        {disposal.inspectionCode ?? "Xem phiếu kiểm định"}
                                    </Link>
                                ) : <strong className="text-sm text-[#172317]">{disposal.inspectionCode ?? "–"}</strong>}
                            </div>
                        </div>
                    </section>

                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <h2 className="text-sm font-black uppercase text-[#315f3c]">Hàng tiêu hủy</h2>
                        <div className="grid gap-3 sm:grid-cols-2">
                            <Info label="Số dòng" value={formatVietnamNumber(disposal.lines.length)} />
                            <Info label="Tổng số lượng" value={formatVietnamNumber(totalQuantity)} />
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[580px] text-left text-sm">
                                <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                                    <tr>
                                        <th className="px-4 py-3">Sản phẩm</th>
                                        <th className="px-4 py-3">SKU</th>
                                        <th className="px-4 py-3 text-right">Số lượng</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {disposal.lines.map((line) => (
                                        <tr key={line.id}>
                                            <td className="border-t border-[#edf0f4] px-4 py-3 font-bold">{line.skuName}</td>
                                            <td className="border-t border-[#edf0f4] px-4 py-3">{line.skuCode}</td>
                                            <td className="border-t border-[#edf0f4] px-4 py-3 text-right">
                                                {formatVietnamNumber(line.quantity)}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </section>

                    <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <h2 className="text-sm font-black uppercase text-[#315f3c]">{isCustomerReturn ? "Xử lý hàng trả" : "Phê duyệt"}</h2>
                        <div><DisposalStatusBadge customerReturn={isCustomerReturn} status={disposal.status} /></div>
                        {isCustomerReturn && disposal.status === "PENDING_APPROVAL" && disposal.sourceInspectionId ? (
                            <Link
                                className="text-sm font-black text-[#159734] hover:underline"
                                to={`/management/inventory/customer-returns/${disposal.sourceInspectionId}`}
                            >
                                Mở phiếu kiểm định để QC hoặc quản lý xác nhận xử lý
                            </Link>
                        ) : null}
                        {disposal.status === "PENDING_APPROVAL" && canApprove && !isCustomerReturn ? (
                            <button
                                className="w-fit rounded-lg bg-[#159734] px-4 py-2 text-sm font-black text-white disabled:opacity-60"
                                disabled={submitting}
                                onClick={() => {
                                    setError("");
                                    setConfirmOpen(true);
                                }}
                                type="button"
                            >
                                Phê duyệt
                            </button>
                        ) : null}
                        {disposal.status === "PENDING_APPROVAL" && !canApprove && !isCustomerReturn ? (
                            <p className="text-sm font-semibold text-amber-900">
                                Yêu cầu đang chờ quản lý chi nhánh hoặc quản lý kho phê duyệt.
                            </p>
                        ) : null}
                        {disposal.approvedAt ? (
                            <p className="text-sm text-[#536078]">
                                {isCustomerReturn ? "Đã tiêu hủy lúc" : "Đã duyệt lúc"} {formatVietnamDateTime(disposal.approvedAt)}.
                            </p>
                        ) : null}
                    </section>
                </>
            ) : null}

            <footer className={styles.footer}>
                <Link className={styles.closeLink} to="/management/inventory/disposals">
                    Đóng chi tiết
                </Link>
            </footer>

            {confirmOpen && disposal && !isCustomerReturn ? (
                <ConfirmDialog
                    confirmLabel="Xác nhận phê duyệt"
                    description={`Bạn xác nhận phê duyệt yêu cầu tiêu hủy ${disposal.code}?`}
                    error={error}
                    onCancel={() => setConfirmOpen(false)}
                    onConfirm={() => void approve()}
                    submitting={submitting}
                    title="Xác nhận phê duyệt tiêu hủy"
                />
            ) : null}
        </section>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">{value}</strong>
        </div>
    );
}
