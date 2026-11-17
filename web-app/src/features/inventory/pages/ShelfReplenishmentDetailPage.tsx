import { ArrowLeft, Ban, CheckCircle2, Pencil, Send, XCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Button } from "../../../components/ui/Button";
import { useAuthorization } from "../../../auth/role-utils";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { TextArea } from "../../../components/ui/Field";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { routePaths } from "../../../routes/path";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import { InventoryErrorState, InventoryPageHeader } from "../components/InventoryManagementComponents";
import { ShelfReplenishmentStatusBadge } from "../components/ShelfReplenishmentStatusBadge";
import { shelfReplenishmentApi } from "../services/shelfReplenishment.api";
import type { ShelfReplenishment } from "../types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    formatReplenishmentQuantity,
    shelfReplenishmentEditPath
} from "../utils/shelfReplenishment.utils";

type SlipAction = "submit" | "approve" | "reject" | "cancel" | "review";

function errorMessage(error: unknown, fallback: string) {
    return error instanceof Error && error.message ? error.message : fallback;
}

export function ShelfReplenishmentDetailPage() {
    const { slipId } = useParams<{ slipId: string }>();
    const navigate = useNavigate();
    const [slip, setSlip] = useState<ShelfReplenishment | null>(null);
    const [loadError, setLoadError] = useState("");
    const [actionError, setActionError] = useState("");
    const [notice, setNotice] = useState("");
    const [pendingAction, setPendingAction] = useState<SlipAction | null>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectReason, setRejectReason] = useState("");
    const { isSuperAdmin, isManager, keycloakSubject } = useAuthorization();

    const reload = useCallback(() => setReloadKey((current) => current + 1), []);

    useEffect(() => {
        if (!slipId) return undefined;
        let active = true;
        shelfReplenishmentApi
            .detail(slipId)
            .then((result) => {
                if (!active) return;
                setSlip(result);
                setLoadError("");
            })
            .catch((error) => {
                if (active) setLoadError(errorMessage(error, "Không tải được phiếu nhập kệ."));
            });
        return () => {
            active = false;
        };
    }, [reloadKey, slipId]);

    // SC-05: Quản lý / Admin duyệt hoặc từ chối nhưng không phải người tạo; người tạo hoặc Admin được hủy.
    const isCreator = Boolean(slip?.createdBy && keycloakSubject && slip.createdBy === keycloakSubject);
    const isApproverRole = isManager() || isSuperAdmin();
    const canDecide = isApproverRole && !isCreator;
    const canCancel = isCreator || isSuperAdmin();

    const successText: Record<SlipAction, string> = {
        submit: "Đã gửi phiếu chờ duyệt.",
        approve: "Đã duyệt phiếu, hàng đã chuyển từ kho lên kệ.",
        reject: "Đã từ chối phiếu.",
        cancel: "Đã hủy phiếu.",
        review: "Đã duyệt phiếu."
    };

    async function execute(action: SlipAction, call: () => Promise<ShelfReplenishment>) {
        setPendingAction(action);
        setActionError("");
        setNotice("");
        try {
            const updated = await call();
            setSlip(updated);
            // Quản lý / Admin tự gửi phiếu thì backend duyệt luôn nếu kho đủ hàng
            setNotice(
                action === "submit" && updated.status === "POSTED"
                    ? "Phiếu đã được duyệt tự động, hàng đã chuyển từ kho lên kệ."
                    : successText[action]
            );
            return true;
        } catch (error) {
            setActionError(errorMessage(error, "Không thực hiện được thao tác."));
            // Tồn kho có thể đã đổi, tải lại để số "còn trong kho" đúng.
            reload();
            return false;
        } finally {
            setPendingAction(null);
        }
    }

    async function submitReject() {
        if (!slip || pendingAction) return;
        const reason = rejectReason.trim();
        if (!reason) {
            setActionError("Vui lòng nhập lý do từ chối.");
            return;
        }
        if (await execute("reject", () => shelfReplenishmentApi.reject(slip.id, reason))) {
            setRejectOpen(false);
            setRejectReason("");
        }
    }

    async function runAction(action: Exclude<SlipAction, "reject">) {
        if (!slip || pendingAction) return;

        const confirmText: Record<Exclude<SlipAction, "reject">, string> = {
            submit: "Gửi phiếu này chờ Quản lý duyệt?",
            approve: "Duyệt phiếu? Hệ thống sẽ trừ tồn kho và cộng tồn kệ theo FEFO.",
            cancel: "Hủy phiếu nhập kệ này?",
            review: "Duyệt phiếu đã xuất kho lên kệ này?"
        };
        if (!window.confirm(confirmText[action])) return;

        const calls: Record<Exclude<SlipAction, "reject">, () => Promise<ShelfReplenishment>> = {
            approve: () => shelfReplenishmentApi.approve(slip.id),
            cancel: () => shelfReplenishmentApi.cancel(slip.id),
            review: () => shelfReplenishmentApi.review(slip.id),
            submit: () => shelfReplenishmentApi.submit(slip.id)
        };
        await execute(action, calls[action]);
    }

    const busy = pendingAction !== null;
    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <>
                        <Button onClick={() => navigate(routePaths.shelfReplenishments)}>
                            <ArrowLeft aria-hidden="true" size={16} />
                            Danh sách phiếu
                        </Button>
                        {slip?.status === "DRAFT" ? (
                            <>
                                <Button disabled={busy} onClick={() => navigate(shelfReplenishmentEditPath(slip.id))}>
                                    <Pencil aria-hidden="true" size={16} />
                                    Sửa
                                </Button>
                                <Button disabled={busy} onClick={() => void runAction("submit")} variant="primary">
                                    <Send aria-hidden="true" size={16} />
                                    Gửi duyệt
                                </Button>
                            </>
                        ) : null}
                        {slip?.status === "PENDING_APPROVAL" && canDecide ? (
                            <>
                                <Button disabled={busy} onClick={() => setRejectOpen(true)} variant="dangerOutline">
                                    <Ban aria-hidden="true" size={16} />
                                    Từ chối
                                </Button>
                                <Button disabled={busy} onClick={() => void runAction("approve")} variant="primary">
                                    <CheckCircle2 aria-hidden="true" size={16} />
                                    {pendingAction === "approve" ? "Đang duyệt..." : "Duyệt & chuyển lên kệ"}
                                </Button>
                            </>
                        ) : null}
                        {slip?.status === "POSTED" && slip.pendingReview && isApproverRole ? (
                            <Button disabled={busy} onClick={() => void runAction("review")} variant="primary">
                                <CheckCircle2 aria-hidden="true" size={16} />
                                {pendingAction === "review" ? "Đang duyệt..." : "Duyệt phiếu"}
                            </Button>
                        ) : null}
                        {slip && (slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL") && canCancel ? (
                            <Button disabled={busy} onClick={() => void runAction("cancel")} variant="dangerOutline">
                                <XCircle aria-hidden="true" size={16} />
                                Hủy phiếu
                            </Button>
                        ) : null}
                    </>
                }
                description="Quản lý duyệt phiếu thì hàng được lấy từ kho theo FEFO (lô hết hạn sớm nhất trước) và cộng vào kệ."
                eyebrow="Kho / Phiếu nhập kệ"
                title={slip ? `Phiếu ${slip.documentCode}` : "Phiếu nhập kệ"}
            />

            {notice ? (
                <p
                    className="rounded-xl border border-[#d8eadc] bg-[#f4faf5] px-4 py-3 text-sm font-bold text-[#07883a]"
                    role="status"
                >
                    {notice}
                </p>
            ) : null}
            {actionError ? (
                <p
                    className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700"
                    role="alert"
                >
                    {actionError}
                </p>
            ) : null}

            {slip?.status === "PENDING_APPROVAL" && !canDecide ? (
                <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-800">
                    "Phiếu đang chờ Quản lý duyệt "
                </p>
            ) : null}

            {slip?.status === "REJECTED" ? (
                <div className="rounded-xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm text-orange-800" role="status">
                    <p className="font-black">
                        Phiếu bị từ chối lúc {formatReplenishmentDateTime(slip.rejectedAt)}. Tồn kho và tồn kệ không thay đổi.
                    </p>
                    {slip.rejectionReason ? (
                        <p className="mt-1 whitespace-pre-line font-semibold">Lý do: {slip.rejectionReason}</p>
                    ) : null}
                </div>
            ) : null}

            {slip?.autoIssued ? (
                <p className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm font-bold text-sky-800">
                    {slip.pendingReview
                        ? "Phiếu này được POS xuất kho ngay khi bán hàng để kịp giao cho khách. Hàng đã lên kệ; cần manager duyệt lại."
                        : `Phiếu được POS xuất kho ngay khi bán hàng, đã duyệt lúc ${formatReplenishmentDateTime(slip.reviewedAt)}.`}
                </p>
            ) : null}

            {loadError && !slip ? (
                <InventoryErrorState message={loadError} onRetry={reload} />
            ) : !slip ? (
                <div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    <DashboardListSkeleton rows={6} />
                </div>
            ) : (
                <>
                    <dl className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card sm:grid-cols-2 lg:grid-cols-4">
                        <div className="grid gap-1">
                            <dt className="text-xs font-black text-[#697684]">Trạng thái</dt>
                            <dd>
                                <ShelfReplenishmentStatusBadge pendingReview={slip.pendingReview} status={slip.status} />
                            </dd>
                        </div>
                        <div className="grid gap-1">
                            <dt className="text-xs font-black text-[#697684]">Kho nguồn</dt>
                            <dd className="text-sm font-bold text-[#172317]">{slip.warehouseName ?? "—"}</dd>
                        </div>
                        <div className="grid gap-1">
                            <dt className="text-xs font-black text-[#697684]">Kệ nhận</dt>
                            <dd className="text-sm font-bold text-[#172317]">{slip.shelfName ?? "—"}</dd>
                        </div>
                        <div className="grid gap-1">
                            <dt className="text-xs font-black text-[#697684]">Tạo lúc</dt>
                            <dd className="text-sm font-bold tabular-nums text-[#172317]">
                                {formatReplenishmentDateTime(slip.createdAt)}
                            </dd>
                        </div>
                        {slip.reviewedAt && !slip.autoIssued ? (
                            <div className="grid gap-1">
                                <dt className="text-xs font-black text-[#697684]">Duyệt lúc</dt>
                                <dd className="text-sm font-bold tabular-nums text-[#172317]">
                                    {formatReplenishmentDateTime(slip.reviewedAt)}
                                </dd>
                            </div>
                        ) : null}
                        {slip.postedAt ? (
                            <div className="grid gap-1">
                                <dt className="text-xs font-black text-[#697684]">Lên kệ lúc</dt>
                                <dd className="text-sm font-bold tabular-nums text-[#172317]">
                                    {formatReplenishmentDateTime(slip.postedAt)}
                                </dd>
                            </div>
                        ) : null}
                        {slip.referenceId ? (
                            <div className="grid gap-1">
                                <dt className="text-xs font-black text-[#697684]">Tham chiếu (đơn POS)</dt>
                                <dd className="break-all font-mono text-xs text-[#172317]">{slip.referenceId}</dd>
                            </div>
                        ) : null}
                        {slip.note ? (
                            <div className="grid gap-1 sm:col-span-2">
                                <dt className="text-xs font-black text-[#697684]">Ghi chú</dt>
                                <dd className="whitespace-pre-line text-sm font-semibold text-[#172317]">
                                    {slip.note}
                                </dd>
                            </div>
                        ) : null}
                    </dl>

                    <div className="min-w-0 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                        <div className="overflow-x-auto">
                            <Table className="min-w-[820px]">
                                <TableHead>
                                    <TableRow>
                                        <TableHeaderCell>#</TableHeaderCell>
                                        <TableHeaderCell>Mặt hàng</TableHeaderCell>
                                        <TableHeaderCell className="text-right">Cần lên kệ</TableHeaderCell>
                                        <TableHeaderCell className="text-right">Còn trong kho</TableHeaderCell>
                                        <TableHeaderCell className="text-right">Đang trên kệ</TableHeaderCell>
                                        <TableHeaderCell className="text-right">Đã chuyển</TableHeaderCell>
                                    </TableRow>
                                </TableHead>
                                <tbody>
                                    {slip.lines.length === 0 ? (
                                        <TableRow>
                                            <TableCell className="py-8 text-center font-semibold text-[#697684]" colSpan={6}>
                                                Phiếu chưa có mặt hàng nào.
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        slip.lines.map((line) => {
                                            const open = slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL";
                                            const short = open && line.warehouseAvailable < line.quantity;
                                            return (
                                                <TableRow key={line.id}>
                                                    <TableCell className="tabular-nums text-[#697684]">
                                                        {line.lineNo}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className="grid">
                                                            <span className="font-bold text-[#172317]">
                                                                {line.itemName}
                                                            </span>
                                                            {line.sku ? (
                                                                <span className="font-mono text-[11px] text-[#8793a1]">
                                                                    {line.sku}
                                                                </span>
                                                            ) : null}
                                                        </div>
                                                    </TableCell>
                                                    <TableCell className="text-right font-bold tabular-nums text-[#172317]">
                                                        {formatReplenishmentQuantity(line.quantity)}
                                                    </TableCell>
                                                    <TableCell
                                                        className={`text-right tabular-nums ${
                                                            short ? "font-bold text-red-700" : "text-[#172317]"
                                                        }`}
                                                    >
                                                        {formatReplenishmentQuantity(line.warehouseAvailable)}
                                                        {short ? " (thiếu)" : ""}
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums text-[#172317]">
                                                        {formatReplenishmentQuantity(line.shelfOnHand)}
                                                    </TableCell>
                                                    <TableCell className="text-right tabular-nums text-[#172317]">
                                                        {formatReplenishmentQuantity(line.transferredQuantity)}
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })
                                    )}
                                </tbody>
                            </Table>
                        </div>
                    </div>
                </>
            )}

            <Dialog
                open={rejectOpen}
                onOpenChange={(open) => {
                    if (!busy) setRejectOpen(open);
                }}
            >
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Từ chối phiếu nhập kệ</DialogTitle>
                        <DialogDescription>
                            Nhập lý do để người tạo phiếu biết cần điều chỉnh gì. Tồn kho và tồn kệ không thay đổi.
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
                        <Button disabled={busy} onClick={() => setRejectOpen(false)}>
                            Hủy
                        </Button>
                        <Button
                            disabled={busy || !rejectReason.trim()}
                            onClick={() => void submitReject()}
                            variant="warning"
                        >
                            {pendingAction === "reject" ? "Đang từ chối..." : "Xác nhận từ chối"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
