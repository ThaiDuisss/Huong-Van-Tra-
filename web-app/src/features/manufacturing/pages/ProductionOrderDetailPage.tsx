import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { useAuthorization } from "../../../auth/role-utils";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { routePaths } from "../../../routes/path";
import { productionOrderApi } from "../services/production-order.api";
import type { ProductionOrder, ProductionOrderApiStatus } from "../types/production-order.types";
import { ROLES } from "../../../auth/roles";

const STATUS_LABEL: Record<ProductionOrderApiStatus, { label: string; className: string }> = {
    DRAFT: { label: "Mới tạo", className: "bg-neutral-100 text-neutral-700" },
    PENDING_APPROVAL: { label: "Chờ duyệt", className: "bg-purple-50 text-purple-700" },
    APPROVED: { label: "Đã duyệt — đã giữ nguyên liệu", className: "bg-blue-50 text-blue-700" },
    REJECTED: { label: "Từ chối", className: "bg-red-50 text-red-700" },
    CANCELLED: { label: "Đã hủy", className: "bg-red-100 text-red-700" }
};

function StatusPill({ status }: { status: ProductionOrderApiStatus }) {
    const info = STATUS_LABEL[status];
    return (
        <span className={`inline-flex h-7 items-center rounded-lg px-2.5 text-xs font-bold ${info.className}`}>
            {info.label}
        </span>
    );
}

export function ProductionOrderDetailPage() {
    const { id } = useParams();
    const [order, setOrder] = useState<ProductionOrder | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);
    const [mutationLoading, setMutationLoading] = useState(false);

    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelReason, setCancelReason] = useState("");

    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole([ROLES.SUPER_ADMIN, ROLES.MANAGER]);

    const loadOrder = useCallback(() => {
        if (!id) return;
        let active = true;
        setLoading(true);
        setError("");
        void productionOrderApi
            .detail(id)
            .then((res) => {
                if (active) setOrder(res);
            })
            .catch((err: unknown) => {
                if (active) setError(err instanceof Error ? err.message : "Không thể tải chi tiết lệnh sản xuất.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [id]);

    useEffect(() => loadOrder(), [loadOrder]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 3500);
    }

    async function runAction(action: () => Promise<ProductionOrder>, successMessage: string) {
        try {
            setMutationLoading(true);
            const updated = await action();
            setOrder(updated);
            showToast(successMessage, true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Thao tác thất bại.", false);
        } finally {
            setMutationLoading(false);
        }
    }

    async function handleSubmit() {
        if (!order) return;
        await runAction(() => productionOrderApi.submit(order.id), "Đã gửi duyệt lệnh sản xuất.");
    }

    async function handleApprove() {
        if (!order) return;
        await runAction(() => productionOrderApi.approve(order.id), "Đã duyệt và giữ nguyên liệu cho lệnh sản xuất.");
    }

    async function handleReject() {
        if (!order) return;
        await runAction(() => productionOrderApi.reject(order.id), "Đã từ chối lệnh sản xuất.");
    }

    async function handleCancel() {
        if (!order) return;
        if (!cancelReason.trim()) {
            showToast("Vui lòng nhập lý do hủy.", false);
            return;
        }
        await runAction(() => productionOrderApi.cancel(order.id, cancelReason.trim()), "Đã hủy lệnh sản xuất.");
        setCancelOpen(false);
    }

    if (loading) {
        return <div className="p-6 text-sm font-bold text-[#536078]">Đang tải chi tiết lệnh sản xuất...</div>;
    }

    if (!order) {
        return (
            <section className="grid gap-3 rounded-xl border border-red-200 bg-red-50 p-6 text-sm font-bold text-red-700">
                <div>{error || "Không tìm thấy lệnh sản xuất."}</div>
                <div>
                    <button className="rounded-lg bg-red-600 px-4 py-2 text-white hover:bg-red-700" onClick={loadOrder} type="button">
                        Tải lại
                    </button>
                </div>
            </section>
        );
    }

    const canCancel = order.status === "DRAFT" || order.status === "PENDING_APPROVAL" || order.status === "APPROVED";

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

            <Link className="text-sm font-black text-[#159734] hover:underline" to={routePaths.productionOrders}>
                ← Quay lại danh sách lệnh sản xuất
            </Link>

            <header className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="grid gap-2">
                        <div className="flex flex-wrap items-center gap-3">
                            <h1 className="text-2xl font-black text-[#172317] font-mono">{order.orderCode}</h1>
                            <StatusPill status={order.status} />
                        </div>
                        <p className="text-sm font-semibold text-[#536078]">
                            <span className="font-bold text-[#172317]">Số lượng kế hoạch:</span> {order.plannedQuantity}
                        </p>
                        {order.note ? (
                            <p className="text-xs font-semibold italic text-[#697684]">Ghi chú: {order.note}</p>
                        ) : null}
                        {order.status === "CANCELLED" && order.cancelReason ? (
                            <p className="text-xs font-semibold text-red-600">Lý do hủy: {order.cancelReason}</p>
                        ) : null}
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        {order.status === "DRAFT" ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={() => void handleSubmit()}
                                type="button"
                            >
                                Gửi duyệt
                            </button>
                        ) : null}

                        {order.status === "PENDING_APPROVAL" && canApprove ? (
                            <>
                                <button
                                    className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                                    disabled={mutationLoading}
                                    onClick={() => void handleApprove()}
                                    type="button"
                                >
                                    Duyệt (giữ nguyên liệu)
                                </button>
                                <button
                                    className="inline-flex h-10 items-center rounded-lg border border-red-200 bg-white px-4 text-sm font-black text-red-600 hover:bg-red-50 disabled:opacity-50"
                                    disabled={mutationLoading}
                                    onClick={() => void handleReject()}
                                    type="button"
                                >
                                    Từ chối
                                </button>
                            </>
                        ) : null}

                        {canCancel ? (
                            <button
                                className="inline-flex h-10 items-center rounded-lg border border-red-200 bg-white px-4 text-sm font-black text-red-600 hover:bg-red-50 disabled:opacity-50"
                                disabled={mutationLoading}
                                onClick={() => setCancelOpen(true)}
                                type="button"
                            >
                                Hủy
                            </button>
                        ) : null}
                    </div>
                </div>
            </header>

            <div className="overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                <div className="border-b border-[#e8eef0] p-4">
                    <h2 className="text-sm font-black text-[#172317]">Dòng nguyên liệu</h2>
                </div>
                <table className="w-full text-sm">
                    <thead className="bg-neutral-50 text-xs font-bold uppercase text-[#697684]">
                        <tr>
                            <th className="px-4 py-2 text-left">Nguyên liệu</th>
                            <th className="px-4 py-2 text-right">Cần dùng</th>
                            <th className="px-4 py-2 text-right">Đã giữ</th>
                            <th className="px-4 py-2 text-right">Đã tiêu thụ</th>
                        </tr>
                    </thead>
                    <tbody>
                        {order.lines.map((line) => (
                            <tr key={line.id} className="border-t border-[#e8eef0]">
                                <td className="px-4 py-2 font-semibold text-[#172317]">{line.ingredientName ?? "—"}</td>
                                <td className="px-4 py-2 text-right font-mono">{line.requiredQuantity}</td>
                                <td className="px-4 py-2 text-right font-mono">{line.reservedQuantity}</td>
                                <td className="px-4 py-2 text-right font-mono">{line.consumedQuantity}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Hủy lệnh sản xuất</DialogTitle>
                        <DialogDescription>Bạn có chắc chắn muốn hủy lệnh sản xuất này không?</DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <textarea
                            className="w-full rounded-lg border border-[#dce5e1] p-3 text-sm outline-none focus:border-red-500"
                            placeholder="Nhập lý do hủy lệnh sản xuất..."
                            rows={3}
                            value={cancelReason}
                            onChange={(e) => setCancelReason(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={mutationLoading}
                            onClick={() => setCancelOpen(false)}
                            type="button"
                        >
                            Quay lại
                        </button>
                        <button
                            className="h-10 rounded-lg bg-red-600 px-4 text-sm font-black text-white hover:bg-red-700 disabled:opacity-60"
                            disabled={mutationLoading}
                            onClick={() => void handleCancel()}
                            type="button"
                        >
                            {mutationLoading ? "Đang xử lý..." : "Xác nhận hủy"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
