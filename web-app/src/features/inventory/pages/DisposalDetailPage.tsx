import { ArrowLeft, CheckCircle2, ClipboardCheck, ShieldAlert } from "lucide-react";
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { buttonClassName } from "@/components/ui/button-styles";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";

import { useAuthorization } from "../../../auth/useAuthorization";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { DisposalStatusBadge } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type { DisposalRequest } from "../types/quality.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { isCustomerReturnDisposal } from "../utils/inventory-labels";
import { ROLES } from "../../../auth/roles";

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

    const inspectionLink = disposal?.sourceInspectionId
        ? `/management/inventory/customer-returns/${disposal.sourceInspectionId}`
        : null;
    const returnLink = disposal?.returnCode
        ? `/management/inventory/customer-returns?q=${encodeURIComponent(disposal.returnCode)}`
        : null;
    const isPending = disposal?.status === "PENDING_APPROVAL";

    return (
        <section className="flex min-h-full flex-col gap-4 text-[#172317]">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="min-w-0">
                    <Link className="inline-flex items-center gap-1.5 text-sm font-black text-[#159734] hover:underline" to="/management/inventory/disposals">
                        <ArrowLeft size={16} aria-hidden="true" /> Yêu cầu tiêu hủy
                    </Link>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                        <h1 className="text-2xl font-black outline-none" ref={titleRef} tabIndex={-1}>{disposal?.code ?? "Chi tiết tiêu hủy"}</h1>
                        {disposal ? <DisposalStatusBadge customerReturn={isCustomerReturn} status={disposal.status} /> : null}
                    </div>
                    {disposal ? (
                        <p className="text-sm font-semibold text-[#697684]">
                            {isCustomerReturn ? "Nguồn: Khách trả hàng" : "Nguồn: Hàng hỏng / hết hạn"} · Tạo lúc {formatVietnamDateTime(disposal.requestedAt)}
                        </p>
                    ) : null}
                </div>
                {disposal && isPending ? (
                    <div className="flex flex-wrap items-center gap-2">
                        {isCustomerReturn && inspectionLink ? (
                            <Link className={buttonClassName({ variant: "primary", size: "lg" })} to={inspectionLink}>
                                <ClipboardCheck size={17} aria-hidden="true" /> Mở phiếu kiểm định để xác nhận
                            </Link>
                        ) : null}
                        {!isCustomerReturn && canApprove ? (
                            <Button
                                disabled={submitting}
                                onClick={() => {
                                    setError("");
                                    setConfirmOpen(true);
                                }}
                                size="lg"
                                variant="primary"
                            >
                                <CheckCircle2 size={17} aria-hidden="true" /> Phê duyệt
                            </Button>
                        ) : null}
                    </div>
                ) : null}
            </header>

            {loading ? <div className="rounded-xl bg-white p-4 text-sm font-bold text-[#697684]" role="status">Đang tải yêu cầu tiêu hủy...</div> : null}
            {error && !confirmOpen ? (
                <div className="flex items-center gap-2 rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700" role="alert">
                    <ShieldAlert size={16} aria-hidden="true" />{error}
                </div>
            ) : null}
            {success ? (
                <div className="flex items-center gap-2 rounded-xl bg-green-50 p-4 text-sm font-bold text-green-800" role="status">
                    <CheckCircle2 size={16} aria-hidden="true" />{success}
                </div>
            ) : null}

            {disposal ? (
                <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
                    <section className="grid content-start gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                        <div className="flex flex-wrap items-baseline justify-between gap-2">
                            <h2 className="text-sm font-black uppercase text-[#315f3c]">Hàng tiêu hủy</h2>
                            <span className="text-xs font-semibold text-[#697684]">
                                {formatVietnamNumber(disposal.lines.length)} dòng · tổng {formatVietnamNumber(totalQuantity)}
                            </span>
                        </div>
                        <div className="overflow-x-auto">
                            <Table className="min-w-[480px]">
                                <TableHead>
                                    <TableRow>
                                        <TableHeaderCell>Sản phẩm</TableHeaderCell>
                                        <TableHeaderCell>SKU</TableHeaderCell>
                                        <TableHeaderCell className="text-right">Số lượng</TableHeaderCell>
                                    </TableRow>
                                </TableHead>
                                <tbody>
                                    {disposal.lines.map((line) => (
                                        <TableRow key={line.id}>
                                            <TableCell className="font-bold">{line.skuName}</TableCell>
                                            <TableCell className="text-[#536078]">{line.skuCode}</TableCell>
                                            <TableCell className="text-right font-black">
                                                {formatVietnamNumber(line.quantity)}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <TableRow className="border-t border-[#e4ece8] bg-[#f8faf9]">
                                        <TableCell className="font-black" colSpan={2}>Tổng cộng</TableCell>
                                        <TableCell className="text-right font-black">{formatVietnamNumber(totalQuantity)}</TableCell>
                                    </TableRow>
                                </tfoot>
                            </Table>
                        </div>
                        {disposal.note ? (
                            <div className="rounded-lg bg-[#f7f8fb] p-3 text-sm">
                                <span className="block text-xs font-black uppercase text-[#697684]">Ghi chú</span>
                                <p className="mt-1">{disposal.note}</p>
                            </div>
                        ) : null}
                    </section>

                    <aside className="grid content-start gap-4">
                        <SideCard title="Thông tin">
                            <SideRow label="Trạng thái"><DisposalStatusBadge customerReturn={isCustomerReturn} status={disposal.status} /></SideRow>
                            <SideRow label="Ngày tạo">{formatVietnamDateTime(disposal.requestedAt)}</SideRow>
                            {disposal.approvedAt ? (
                                <SideRow label={isCustomerReturn ? "Ngày tiêu hủy" : "Ngày duyệt"}>{formatVietnamDateTime(disposal.approvedAt)}</SideRow>
                            ) : null}
                        </SideCard>
                        <SideCard title="Tham chiếu">
                            <SideRow label="Phiếu trả">
                                {returnLink ? <Link className="font-black text-[#159734] hover:underline" to={returnLink}>{disposal.returnCode}</Link> : "–"}
                            </SideRow>
                            <SideRow label="Phiếu kiểm định">
                                {inspectionLink
                                    ? <Link className="font-black text-[#159734] hover:underline" to={inspectionLink}>{disposal.inspectionCode ?? "Xem phiếu kiểm định"}</Link>
                                    : disposal.inspectionCode ?? "–"}
                            </SideRow>
                        </SideCard>
                        <SideCard title={isCustomerReturn ? "Xử lý hàng trả" : "Phê duyệt"}>
                            <p className="text-sm font-semibold text-[#536078]">
                                {!isPending
                                    ? `${isCustomerReturn ? "Đã tiêu hủy" : "Đã duyệt"}${disposal.approvedAt ? ` lúc ${formatVietnamDateTime(disposal.approvedAt)}` : ""}.`
                                    : isCustomerReturn
                                        ? "QC hoặc quản lý xác nhận xử lý trên phiếu kiểm định; yêu cầu sẽ tự hoàn tất."
                                        : canApprove
                                            ? "Kiểm tra hàng tiêu hủy rồi bấm Phê duyệt để ghi nhận xuất hủy."
                                            : "Yêu cầu đang chờ quản lý chi nhánh hoặc quản lý kho phê duyệt."}
                            </p>
                        </SideCard>
                    </aside>
                </div>
            ) : null}

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

function SideCard({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <h2 className="text-sm font-black uppercase text-[#315f3c]">{title}</h2>
            {children}
        </section>
    );
}

function SideRow({ label, children }: { label: string; children: ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-3 text-sm">
            <span className="font-semibold text-[#697684]">{label}</span>
            <span className="min-w-0 break-all text-right font-black text-[#172317]">{children}</span>
        </div>
    );
}
