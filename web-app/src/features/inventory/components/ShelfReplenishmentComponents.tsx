import { CheckCircle2, XCircle } from "lucide-react";
import { useState } from "react";

import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "@/components/ui/Table";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import type { ShelfReplenishment, ShelfReplenishmentStatus } from "../types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    formatReplenishmentQuantity,
    shelfReplenishmentStatusLabel
} from "../utils/shelfReplenishment.utils";

/**
 * Thanh tiến trình vòng đời (Lifecycle Stepper) trực quan cho Phiếu nhập kệ
 */
export function ShelfReplenishmentLifecycleStepper({
    status,
    pendingReview = false,
    autoIssued = false,
    rejectionReason,
    rejectedAt
}: {
    status: ShelfReplenishmentStatus;
    pendingReview?: boolean;
    autoIssued?: boolean;
    rejectionReason?: string | null;
    rejectedAt?: string | null;
}) {
    const steps: Array<{ key: ShelfReplenishmentStatus; label: string; sub: string }> = [
        { key: "DRAFT", label: "1. Bản nháp", sub: "Lập danh sách hàng lên kệ" },
        { key: "PENDING_APPROVAL", label: "2. Chờ duyệt", sub: "Quản lý xem xét" },
        {
            key: "POSTED",
            label: "3. Đã lên kệ",
            sub: autoIssued && pendingReview ? "Chờ quản lý duyệt lại" : "Hàng đã chuyển lên kệ"
        }
    ];

    const orderMap: Record<string, number> = {
        DRAFT: 0,
        PENDING_APPROVAL: 1,
        POSTED: 2
    };

    const isRejected = status === "REJECTED";
    const isCancelled = status === "CANCELLED";
    const currentStepIndex = orderMap[status] ?? 0;

    if (isRejected || isCancelled) {
        return (
            <div
                className={`rounded-xl border p-4 ${
                    isRejected
                        ? "border-red-200 bg-red-50 text-red-800"
                        : "border-slate-200 bg-slate-50 text-slate-700"
                }`}
            >
                <div className="flex items-center gap-2 font-black text-base">
                    <XCircle size={18} />
                    Phiếu nhập kệ ở trạng thái: {shelfReplenishmentStatusLabel[status]}
                </div>
                <p className="mt-1 text-sm font-semibold">
                    {isRejected
                        ? `Phiếu nhập kệ đã bị từ chối phê duyệt${
                              rejectedAt ? ` lúc ${formatReplenishmentDateTime(rejectedAt)}` : ""
                          }. Tồn kho và tồn kệ không thay đổi.${
                              rejectionReason ? ` Lý do: ${rejectionReason}` : ""
                          }`
                        : "Phiếu nhập kệ đã bị hủy trước khi hoàn tất chuyển hàng lên kệ."}
                </p>
            </div>
        );
    }

    return (
        <div className="rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
            <div className="mb-3 text-xs font-black uppercase tracking-wider text-[#697684]">
                Tiến trình vòng đời phiếu nhập kệ
            </div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                {steps.map((step, idx) => {
                    const isPassed = idx < currentStepIndex || status === "POSTED";
                    const isCurrent = idx === currentStepIndex && status !== "POSTED";

                    return (
                        <div
                            key={step.key}
                            className={`flex flex-col justify-between rounded-lg border p-2.5 transition-all ${
                                isCurrent
                                    ? "border-[#159734] bg-[#f4faf5] text-[#159734] ring-2 ring-[#159734]/20"
                                    : isPassed
                                      ? "border-[#d8eadc] bg-[#fafdfa] text-[#315f3c]"
                                      : "border-[#eef2f0] bg-slate-50 text-[#8c9ba5]"
                            }`}
                        >
                            <div className="flex items-center justify-between">
                                <span
                                    className={`text-xs font-black ${
                                        isCurrent
                                            ? "text-[#159734]"
                                            : isPassed
                                              ? "text-[#315f3c]"
                                              : "text-[#697684]"
                                    }`}
                                >
                                    {step.label}
                                </span>
                                {isPassed ? (
                                    <CheckCircle2 size={14} className="text-[#159734]" />
                                ) : isCurrent ? (
                                    <span className="size-2 rounded-full bg-[#159734] animate-pulse" />
                                ) : null}
                            </div>
                            <span className="mt-1 text-[11px] font-semibold text-[#697684] line-clamp-1">
                                {step.sub}
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

/**
 * Bảng dòng hàng phiếu nhập kệ kèm phân trang
 */
export function ShelfReplenishmentLineTable({
    slip
}: {
    slip: ShelfReplenishment;
}) {
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const totalLines = slip.lines.length;
    const totalPages = Math.max(1, Math.ceil(totalLines / pageSize));
    const pagedLines = slip.lines.slice((page - 1) * pageSize, page * pageSize);

    return (
        <div className="grid gap-3">
            <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white">
                <Table className="min-w-[850px]">
                    <TableHead>
                        <TableRow>
                            <TableHeaderCell className="w-12 text-center">#</TableHeaderCell>
                            <TableHeaderCell>Mặt hàng</TableHeaderCell>
                            <TableHeaderCell className="text-right">Cần lên kệ</TableHeaderCell>
                            <TableHeaderCell className="text-right">Còn trong kho</TableHeaderCell>
                            <TableHeaderCell className="text-right">Đang trên kệ</TableHeaderCell>
                            <TableHeaderCell className="text-right">Đã chuyển</TableHeaderCell>
                        </TableRow>
                    </TableHead>
                    <tbody>
                        {pagedLines.length === 0 ? (
                            <TableRow>
                                <TableCell className="py-8 text-center font-semibold text-[#697684]" colSpan={6}>
                                    Phiếu chưa có mặt hàng nào.
                                </TableCell>
                            </TableRow>
                        ) : (
                            pagedLines.map((line) => {
                                const open = slip.status === "DRAFT" || slip.status === "PENDING_APPROVAL";
                                const short = open && line.warehouseAvailable < line.quantity;
                                return (
                                    <TableRow key={line.id}>
                                        <TableCell className="text-center tabular-nums text-[#697684]">
                                            {line.lineNo}
                                        </TableCell>
                                        <TableCell>
                                            <div className="grid">
                                                <span className="font-bold text-[#172317]">{line.itemName}</span>
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
            {totalLines > pageSize ? (
                <Pagination
                    onPageChange={(p: number) => setPage(p)}
                    onPageSizeChange={(s: number) => {
                        setPageSize(s);
                        setPage(1);
                    }}
                    page={page}
                    pageCount={totalPages}
                    pageSize={pageSize}
                    totalItems={totalLines}
                    totalItemsLabel="Tổng số mặt hàng"
                />
            ) : null}
        </div>
    );
}
