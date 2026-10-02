import { ArrowLeft, CheckCircle2, Pencil, Send, XCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

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

const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-middle";

type SlipAction = "submit" | "post" | "cancel" | "review";

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

    async function runAction(action: SlipAction) {
        if (!slip || pendingAction) return;

        const confirmText: Record<SlipAction, string> = {
            submit: "Gửi phiếu này cho kho xử lý?",
            post: "Xác nhận đã đưa đủ hàng lên kệ? Hệ thống sẽ trừ tồn kho và cộng tồn kệ.",
            cancel: "Hủy phiếu nhập kệ này?",
            review: "Duyệt phiếu đã xuất kho lên kệ này?"
        };
        if (!window.confirm(confirmText[action])) return;

        setPendingAction(action);
        setActionError("");
        setNotice("");
        try {
            const updated =
                action === "submit"
                    ? await shelfReplenishmentApi.submit(slip.id)
                    : action === "post"
                      ? await shelfReplenishmentApi.post(slip.id)
                      : action === "review"
                        ? await shelfReplenishmentApi.review(slip.id)
                        : await shelfReplenishmentApi.cancel(slip.id);
            setSlip(updated);
            setNotice(
                action === "submit"
                    ? "Đã gửi phiếu cho kho."
                    : action === "post"
                      ? "Đã chuyển hàng lên kệ."
                      : action === "review"
                        ? "Đã duyệt phiếu."
                        : "Đã hủy phiếu."
            );
        } catch (error) {
            setActionError(errorMessage(error, "Không thực hiện được thao tác."));
            // Tồn kho có thể đã đổi, tải lại để số "còn trong kho" đúng.
            reload();
        } finally {
            setPendingAction(null);
        }
    }

    const busy = pendingAction !== null;
    const actionButton =
        "inline-flex h-10 items-center gap-2 rounded-lg px-4 text-sm font-black transition disabled:opacity-60";

    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <>
                        <button
                            className={`${actionButton} border border-pos-border bg-white text-[#536078] hover:bg-neutral-50`}
                            onClick={() => navigate(routePaths.shelfReplenishments)}
                            type="button"
                        >
                            <ArrowLeft aria-hidden="true" size={16} />
                            Danh sách phiếu
                        </button>
                        {slip?.status === "DRAFT" ? (
                            <>
                                <button
                                    className={`${actionButton} border border-pos-border bg-white text-[#172317] hover:bg-neutral-50`}
                                    disabled={busy}
                                    onClick={() => navigate(shelfReplenishmentEditPath(slip.id))}
                                    type="button"
                                >
                                    <Pencil aria-hidden="true" size={16} />
                                    Sửa
                                </button>
                                <button
                                    className={`${actionButton} bg-[#07883a] text-white hover:opacity-90`}
                                    disabled={busy}
                                    onClick={() => void runAction("submit")}
                                    type="button"
                                >
                                    <Send aria-hidden="true" size={16} />
                                    Gửi kho
                                </button>
                            </>
                        ) : null}
                        {slip?.status === "PENDING_APPROVAL" ? (
                            <button
                                className={`${actionButton} bg-[#07883a] text-white hover:opacity-90`}
                                disabled={busy}
                                onClick={() => void runAction("post")}
                                type="button"
                            >
                                <CheckCircle2 aria-hidden="true" size={16} />
                                {pendingAction === "post" ? "Đang xác nhận..." : "Xác nhận đã lên kệ"}
                            </button>
                        ) : null}
                        {slip?.status === "POSTED" && slip.pendingReview ? (
                            <button
                                className={`${actionButton} bg-[#07883a] text-white hover:opacity-90`}
                                disabled={busy}
                                onClick={() => void runAction("review")}
                                type="button"
                            >
                                <CheckCircle2 aria-hidden="true" size={16} />
                                {pendingAction === "review" ? "Đang duyệt..." : "Duyệt phiếu"}
                            </button>
                        ) : null}
                        {slip && (slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL") ? (
                            <button
                                className={`${actionButton} border border-red-200 bg-white text-red-700 hover:bg-red-50`}
                                disabled={busy}
                                onClick={() => void runAction("cancel")}
                                type="button"
                            >
                                <XCircle aria-hidden="true" size={16} />
                                Hủy phiếu
                            </button>
                        ) : null}
                    </>
                }
                description="Hàng được lấy từ kho theo FEFO (lô hết hạn sớm nhất trước) và cộng vào kệ khi kho xác nhận."
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
                            <table className="w-full min-w-[820px] text-left text-sm">
                                <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                                    <tr>
                                        <th className={headClass} scope="col">#</th>
                                        <th className={headClass} scope="col">Mặt hàng</th>
                                        <th className={`${headClass} text-right`} scope="col">Cần lên kệ</th>
                                        <th className={`${headClass} text-right`} scope="col">Còn trong kho</th>
                                        <th className={`${headClass} text-right`} scope="col">Đang trên kệ</th>
                                        <th className={`${headClass} text-right`} scope="col">Đã chuyển</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {slip.lines.length === 0 ? (
                                        <tr>
                                            <td className="px-4 py-8 text-center font-semibold text-[#697684]" colSpan={6}>
                                                Phiếu chưa có mặt hàng nào.
                                            </td>
                                        </tr>
                                    ) : (
                                        slip.lines.map((line) => {
                                            const open = slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL";
                                            const short = open && line.warehouseAvailable < line.quantity;
                                            return (
                                                <tr key={line.id}>
                                                    <td className={`${cellClass} tabular-nums text-[#697684]`}>
                                                        {line.lineNo}
                                                    </td>
                                                    <td className={cellClass}>
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
                                                    </td>
                                                    <td className={`${cellClass} text-right font-bold tabular-nums text-[#172317]`}>
                                                        {formatReplenishmentQuantity(line.quantity)}
                                                    </td>
                                                    <td
                                                        className={`${cellClass} text-right tabular-nums ${
                                                            short ? "font-bold text-red-700" : "text-[#172317]"
                                                        }`}
                                                    >
                                                        {formatReplenishmentQuantity(line.warehouseAvailable)}
                                                        {short ? " (thiếu)" : ""}
                                                    </td>
                                                    <td className={`${cellClass} text-right tabular-nums text-[#172317]`}>
                                                        {formatReplenishmentQuantity(line.shelfOnHand)}
                                                    </td>
                                                    <td className={`${cellClass} text-right tabular-nums text-[#172317]`}>
                                                        {formatReplenishmentQuantity(line.transferredQuantity)}
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </>
            )}
        </section>
    );
}
