import { Pencil } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuthorization } from "../../../auth/role-utils";
import { Button } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { TextArea } from "../../../components/ui/Field";
import { routePaths } from "../../../routes/path";
import {
    ShelfReplenishmentLifecycleStepper,
    ShelfReplenishmentLineTable
} from "../components/ShelfReplenishmentComponents";
import { ShelfReplenishmentStatusBadge } from "../components/ShelfReplenishmentStatusBadge";
import { shelfReplenishmentApi } from "../services/shelfReplenishment.api";
import type { ShelfReplenishment } from "../types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    shelfReplenishmentEditPath
} from "../utils/shelfReplenishment.utils";

export function ShelfReplenishmentDetailPage() {
    const { slipId } = useParams<{ slipId: string }>();
    const [slip, setSlip] = useState<ShelfReplenishment | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);

    // Dialog state
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectReason, setRejectReason] = useState("");
    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelNote, setCancelNote] = useState("");

    const [mutationLoading, setMutationLoading] = useState(false);

    const { isSuperAdmin, isManager, keycloakSubject, userId, user } = useAuthorization();

    const loadSlip = useCallback(() => {
        if (!slipId) return;

        let active = true;
        setLoading(true);
        setError("");

        shelfReplenishmentApi
            .detail(slipId)
            .then((result) => {
                if (active) setSlip(result);
            })
            .catch((loadErr: unknown) => {
                if (active) {
                    setError(
                        loadErr instanceof Error && loadErr.message
                            ? loadErr.message
                            : "Không thể tải chi tiết phiếu nhập kệ."
                    );
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [slipId]);

    useEffect(() => {
        const cleanup = loadSlip();
        return () => {
            cleanup?.();
        };
    }, [loadSlip]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 3500);
    }

    // SC-05: Quản lý / Admin duyệt hoặc từ chối nhưng không phải người tạo; người tạo mới được hủy.
    const currentActorIds = [keycloakSubject, userId, user?.id].filter(Boolean) as string[];
    const isCreator = Boolean(
        slip?.createdBy &&
        currentActorIds.some((id) => id.toLowerCase() === slip.createdBy?.toLowerCase())
    );
    const isApproverRole = isManager() || isSuperAdmin();
    const canDecide = isApproverRole && !isCreator;
    const canCancel = isCreator;

    // 1. Submit for Approval (DRAFT -> PENDING_APPROVAL / POSTED if manager created)
    async function submitSlip() {
        if (!slip || mutationLoading) return;
        try {
            setMutationLoading(true);
            const updated = await shelfReplenishmentApi.submit(slip.id);
            setSlip(updated);
            showToast(
                updated.status === "POSTED"
                    ? "Phiếu đã được duyệt tự động, hàng đã chuyển từ kho lên kệ."
                    : "Gửi duyệt phiếu nhập kệ thành công. Chờ Quản lý phê duyệt.",
                true
            );
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Gửi duyệt thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 2. Approve (PENDING_APPROVAL -> POSTED)
    async function approveSlip() {
        if (!slip || mutationLoading) return;
        try {
            setMutationLoading(true);
            const updated = await shelfReplenishmentApi.approve(slip.id);
            setSlip(updated);
            showToast("Đã duyệt phiếu thành công, hàng đã chuyển từ kho lên kệ theo FEFO.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Duyệt phiếu thất bại.", false);
            loadSlip();
        } finally {
            setMutationLoading(false);
        }
    }

    // 3. Reject (PENDING_APPROVAL -> REJECTED)
    async function submitReject() {
        if (!slip || mutationLoading) return;
        const reason = rejectReason.trim();
        if (!reason) {
            showToast("Vui lòng nhập lý do từ chối.", false);
            return;
        }
        try {
            setMutationLoading(true);
            const updated = await shelfReplenishmentApi.reject(slip.id, reason);
            setSlip(updated);
            setRejectOpen(false);
            setRejectReason("");
            showToast("Đã từ chối duyệt phiếu nhập kệ.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Từ chối duyệt thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 4. Review POS auto-issued slip
    async function reviewSlip() {
        if (!slip || mutationLoading) return;
        try {
            setMutationLoading(true);
            const updated = await shelfReplenishmentApi.review(slip.id);
            setSlip(updated);
            showToast("Đã duyệt xác nhận phiếu nhập kệ bán lẻ thành công.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Thao tác thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    // 5. Cancel slip (DRAFT / PENDING_APPROVAL -> CANCELLED)
    async function submitCancel() {
        if (!slip || mutationLoading) return;
        try {
            setMutationLoading(true);
            const updated = await shelfReplenishmentApi.cancel(slip.id, cancelNote);
            setSlip(updated);
            setCancelOpen(false);
            setCancelNote("");
            showToast("Đã hủy phiếu nhập kệ thành công.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Hủy phiếu thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    if (!slipId) {
        return <Navigate replace to={routePaths.shelfReplenishments} />;
    }

    if (loading) {
        return (
            <section className="grid min-h-[300px] place-items-center rounded-xl bg-white p-8">
                <div className="text-sm font-bold text-[#315f3c]">Đang tải dữ liệu phiếu nhập kệ...</div>
            </section>
        );
    }

    if (error || !slip) {
        return (
            <section className="grid gap-3 rounded-xl border border-red-200 bg-red-50 p-6 text-sm font-bold text-red-700">
                <div>{error || "Không tìm thấy dữ liệu phiếu nhập kệ."}</div>
                <div>
                    <Button onClick={loadSlip} variant="danger">
                        Tải lại
                    </Button>
                </div>
            </section>
        );
    }

    return (
        <section className="grid gap-4">
            {toast ? (
                <div
                    className={`fixed right-6 top-6 z-50 rounded-xl px-5 py-3 text-sm font-black text-white shadow-2xl transition-all ${
                        toast.success ? "bg-[#159734]" : "bg-red-600"
                    }`}
                >
                    {toast.message}
                </div>
            ) : null}

            <div className="flex flex-wrap items-center justify-between gap-3">
                <Link
                    className="text-sm font-black text-[#159734] hover:underline"
                    to={routePaths.shelfReplenishments}
                >
                    ← Quay lại danh sách phiếu nhập kệ
                </Link>
            </div>

            {/* HEADER PHIẾU NHẬP KỆ */}
            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="text-xs font-bold text-[#697684]">
                            Kho vận › Phiếu nhập kệ › Chi tiết
                        </div>
                        <div className="flex flex-wrap items-center gap-3">
                            <h1 className="text-2xl font-black text-[#172317] font-mono">{slip.documentCode}</h1>
                            <ShelfReplenishmentStatusBadge
                                pendingReview={slip.pendingReview}
                                status={slip.status}
                            />
                        </div>

                        <div className="flex flex-col gap-2 text-sm font-semibold text-[#536078] sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                            {/* Cột 1: Kho nguồn & Kệ nhận */}
                            <div className="space-y-1">
                                <div>
                                    <span className="font-bold text-[#172317]">Kho nguồn:</span>{" "}
                                    {slip.warehouseName ?? "—"}
                                </div>
                                <div>
                                    <span className="font-bold text-[#172317]">Kệ nhận:</span>{" "}
                                    {slip.shelfName ?? "—"}
                                </div>
                            </div>

                            {/* Cột 2: Thời gian tạo / duyệt & Ghi chú */}
                            <div className="space-y-1 sm:text-right">
                                <div>
                                    <span className="font-bold text-[#172317]">Thời gian tạo:</span>{" "}
                                    {formatReplenishmentDateTime(slip.createdAt)}
                                </div>
                                {slip.reviewedAt ? (
                                    <div>
                                        <span className="font-bold text-[#172317]">Duyệt lúc:</span>{" "}
                                        {formatReplenishmentDateTime(slip.reviewedAt)}
                                    </div>
                                ) : null}
                                {slip.postedAt ? (
                                    <div>
                                        <span className="font-bold text-[#172317]">Lên kệ lúc:</span>{" "}
                                        {formatReplenishmentDateTime(slip.postedAt)}
                                    </div>
                                ) : null}
                                {slip.note ? (
                                    <p className="text-xs font-semibold italic text-[#697684]">
                                        Ghi chú: {slip.note}
                                    </p>
                                ) : null}
                            </div>
                        </div>

                        {slip.status === "PENDING_APPROVAL" && !canDecide ? (
                            <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
                                Phiếu đang chờ Quản lý phê duyệt.
                            </div>
                        ) : null}

                        {slip.autoIssued ? (
                            <div className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs font-semibold text-sky-800">
                                {slip.pendingReview
                                    ? "Phiếu này được POS xuất kho ngay khi bán hàng để kịp giao cho khách. Hàng đã lên kệ; cần Quản lý duyệt lại."
                                    : `Phiếu được POS xuất kho ngay khi bán hàng, đã duyệt lúc ${formatReplenishmentDateTime(
                                          slip.reviewedAt
                                      )}.`}
                            </div>
                        ) : null}
                    </div>

                    {/* VÙNG NÚT HÀNH ĐỘNG */}
                    <div className="flex flex-wrap items-center gap-2">
                        {slip.status === "DRAFT" && (isCreator || isSuperAdmin()) ? (
                            <Link
                                className={buttonClassName({ variant: "secondary" })}
                                to={shelfReplenishmentEditPath(slip.id)}
                            >
                                <Pencil size={15} aria-hidden="true" />
                                Chỉnh sửa
                            </Link>
                        ) : null}

                        {slip.status === "DRAFT" ? (
                            <Button disabled={mutationLoading} onClick={submitSlip} variant="primary">
                                Gửi phê duyệt
                            </Button>
                        ) : null}

                        {slip.status === "PENDING_APPROVAL" && canDecide ? (
                            <>
                                <Button disabled={mutationLoading} onClick={approveSlip} variant="primary">
                                    Phê duyệt
                                </Button>
                                <Button
                                    disabled={mutationLoading}
                                    onClick={() => setRejectOpen(true)}
                                    variant="dangerOutline"
                                >
                                    Từ chối duyệt
                                </Button>
                            </>
                        ) : null}

                        {slip.status === "POSTED" && slip.pendingReview && isApproverRole ? (
                            <Button disabled={mutationLoading} onClick={reviewSlip} variant="primary">
                                Duyệt phiếu
                            </Button>
                        ) : null}

                        {/* Nút hủy: Chỉ người tạo mới được hiện! */}
                        {(slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL") && canCancel ? (
                            <Button
                                disabled={mutationLoading}
                                onClick={() => setCancelOpen(true)}
                                variant="dangerOutline"
                            >
                                Hủy phiếu
                            </Button>
                        ) : null}
                    </div>
                </div>

                {/* THANH TIẾN TRÌNH VÒNG ĐỜI STEPPER */}
                <ShelfReplenishmentLifecycleStepper
                    autoIssued={slip.autoIssued}
                    pendingReview={slip.pendingReview}
                    rejectedAt={slip.rejectedAt}
                    rejectionReason={slip.rejectionReason}
                    status={slip.status}
                />
            </header>

            {/* DÒNG HÀNG LÊN KỆ */}
            <ShelfReplenishmentLineTable slip={slip} />

            {/* MODAL TỪ CHỐI DUYỆT */}
            <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Từ chối duyệt phiếu nhập kệ</DialogTitle>
                        <DialogDescription>
                            Nhập lý do để người tạo phiếu biết cần điều chỉnh gì. Tồn kho và tồn kệ sẽ không thay đổi.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <TextArea
                            maxLength={1000}
                            placeholder="Ví dụ: Kệ còn đủ hàng, số lượng yêu cầu quá nhiều..."
                            rows={3}
                            value={rejectReason}
                            onChange={(e) => setRejectReason(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <Button disabled={mutationLoading} onClick={() => setRejectOpen(false)}>
                            Đóng
                        </Button>
                        <Button
                            disabled={mutationLoading || !rejectReason.trim()}
                            onClick={() => void submitReject()}
                            variant="warning"
                        >
                            {mutationLoading ? "Đang xử lý..." : "Xác nhận từ chối"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* MODAL HỦY PHIẾU */}
            <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Hủy phiếu nhập kệ</DialogTitle>
                        <DialogDescription>
                            Bạn có chắc chắn muốn hủy phiếu nhập kệ này? Thao tác này không thể hoàn tác.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <TextArea
                            maxLength={500}
                            placeholder="Nhập lý do hủy phiếu (tùy chọn)..."
                            rows={3}
                            value={cancelNote}
                            onChange={(e) => setCancelNote(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <Button disabled={mutationLoading} onClick={() => setCancelOpen(false)}>
                            Đóng
                        </Button>
                        <Button
                            disabled={mutationLoading}
                            onClick={() => void submitCancel()}
                            variant="danger"
                        >
                            {mutationLoading ? "Đang hủy..." : "Xác nhận hủy"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
