import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { useAuthorization } from "../../../auth/role-utils";
import { Button } from "../../../components/ui/Button";
import { TextArea } from "../../../components/ui/Field";
import { type BadgeTone, StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
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

const STATUS_LABEL: Record<ProductionOrderApiStatus, { label: string; tone: BadgeTone }> = {
    DRAFT: { label: "Mới tạo", tone: "neutral" },
    PENDING_APPROVAL: { label: "Chờ duyệt", tone: "violet" },
    APPROVED: { label: "Đã duyệt — đã giữ nguyên liệu", tone: "info" },
    REJECTED: { label: "Từ chối", tone: "danger" },
    CANCELLED: { label: "Đã hủy", tone: "danger" }
};

function StatusPill({ status }: { status: ProductionOrderApiStatus }) {
    const info = STATUS_LABEL[status];
    return <StatusBadge tone={info.tone}>{info.label}</StatusBadge>;
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
                    <Button onClick={loadOrder} variant="danger">
                        Tải lại
                    </Button>
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
                            <Button disabled={mutationLoading} onClick={() => void handleSubmit()} variant="primary">Gửi duyệt</Button>
                        ) : null}

                        {order.status === "PENDING_APPROVAL" && canApprove ? (
                            <>
                                <Button disabled={mutationLoading} onClick={() => void handleApprove()} variant="primary">Duyệt (giữ nguyên liệu)</Button>
                                <Button disabled={mutationLoading} onClick={() => void handleReject()} variant="dangerOutline">Từ chối</Button>
                            </>
                        ) : null}

                        {canCancel ? (
                            <Button disabled={mutationLoading} onClick={() => setCancelOpen(true)} variant="dangerOutline">Hủy</Button>
                        ) : null}
                    </div>
                </div>
            </header>

            <div className="overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                <div className="border-b border-[#e8eef0] p-4">
                    <h2 className="text-sm font-black text-[#172317]">Dòng nguyên liệu</h2>
                </div>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableHeaderCell>Nguyên liệu</TableHeaderCell>
                            <TableHeaderCell className="text-right">Cần dùng</TableHeaderCell>
                            <TableHeaderCell className="text-right">Đã giữ</TableHeaderCell>
                            <TableHeaderCell className="text-right">Đã tiêu thụ</TableHeaderCell>
                        </TableRow>
                    </TableHead>
                    <tbody>
                        {order.lines.map((line) => (
                            <TableRow key={line.id}>
                                <TableCell className="font-semibold">{line.ingredientName ?? "—"}</TableCell>
                                <TableCell className="text-right font-mono">{line.requiredQuantity}</TableCell>
                                <TableCell className="text-right font-mono">{line.reservedQuantity}</TableCell>
                                <TableCell className="text-right font-mono">{line.consumedQuantity}</TableCell>
                            </TableRow>
                        ))}
                    </tbody>
                </Table>
            </div>

            <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Hủy lệnh sản xuất</DialogTitle>
                        <DialogDescription>Bạn có chắc chắn muốn hủy lệnh sản xuất này không?</DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <TextArea
                            placeholder="Nhập lý do hủy lệnh sản xuất..."
                            rows={3}
                            value={cancelReason}
                            onChange={(e) => setCancelReason(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <Button disabled={mutationLoading} onClick={() => setCancelOpen(false)}>Quay lại</Button>
                        <Button disabled={mutationLoading} onClick={() => void handleCancel()} variant="danger">{mutationLoading ? "Đang xử lý..." : "Xác nhận hủy"}</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
