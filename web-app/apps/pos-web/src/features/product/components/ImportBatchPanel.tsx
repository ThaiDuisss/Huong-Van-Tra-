import { ApiError } from "@hvt/api-client";
import type { ComponentType } from "react";
import { useCallback, useEffect, useState } from "react";

import { useAuthorization } from "../../../auth/role-utils";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { importBatchApi as defaultImportBatchApi, type ImportBatchApi } from "../services/importBatch.api";
import type { ImportBatch, ImportBatchItem, ImportBatchStatus, ImportType } from "../types/import-batch.types";

const STATUS_LABEL: Record<ImportBatchStatus, { label: string; className: string }> = {
    DRAFT: { label: "Bản nháp", className: "bg-neutral-100 text-neutral-700" },
    PENDING_APPROVAL: { label: "Chờ duyệt", className: "bg-blue-50 text-blue-700" },
    APPROVED: { label: "Đã duyệt", className: "bg-emerald-50 text-emerald-700" },
    REJECTED: { label: "Bị từ chối", className: "bg-red-50 text-red-700" }
};

export function StatusPill({ status }: { status: ImportBatchStatus }) {
    const info = STATUS_LABEL[status];
    return (
        <span className={`inline-flex h-7 items-center rounded-full px-2.5 text-xs font-bold ${info.className}`}>
            {info.label}
        </span>
    );
}

/** Mỗi loại import (PRODUCT, PRODUCT_VARIANT, ...) tự cung cấp 1 component render toàn bộ danh sách dòng
 * của TRANG hiện tại — để được tự quản lý state/lookup dùng chung giữa các dòng (vd danh sách danh mục,
 * nguyên liệu) chỉ tải 1 lần cho cả trang thay vì tải lại theo từng dòng. */
export interface ImportItemsListProps {
    items: ImportBatchItem[];
    editable: boolean;
    expandedId: string | null;
    onToggle(itemId: string): void;
    onSave(itemId: string, payload: Record<string, unknown>): Promise<void>;
    onDelete(itemId: string): Promise<void>;
}

interface ImportBatchPanelProps {
    batchId: string;
    /** Called whenever the batch is (re)loaded or changes status/version, for parents that need to react (e.g. refresh a list). */
    onBatchChange?: (batch: ImportBatch) => void;
    /** Component hiển thị danh sách dòng theo từng loại import — bắt buộc, thêm loại mới chỉ cần thêm 1 entry ở đây. */
    itemsListRegistry: Partial<Record<ImportType, ComponentType<ImportItemsListProps>>>;
    /** API import-batch dùng cho panel này — mặc định trỏ Catalog, service khác (vd Inventory) truyền bộ riêng qua createImportBatchApi(). */
    api?: ImportBatchApi;
}

export function ImportBatchPanel({ batchId, onBatchChange, itemsListRegistry, api = defaultImportBatchApi }: ImportBatchPanelProps) {
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole(["ADMIN", "BRANCH_MANAGER"]);

    const [batch, setBatch] = useState<ImportBatch | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [expandedId, setExpandedId] = useState<string | null>(null);
    const [itemsPage, setItemsPage] = useState(1);
    const [itemsPageSize, setItemsPageSize] = useState(10);
    const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);
    const [actionLoading, setActionLoading] = useState(false);

    const [rejectOpen, setRejectOpen] = useState(false);
    const [rejectReason, setRejectReason] = useState("");

    const applyBatch = useCallback(
        (updated: ImportBatch) => {
            setBatch(updated);
            onBatchChange?.(updated);
        },
        [onBatchChange]
    );

    const loadBatch = useCallback(() => {
        let active = true;
        setLoading(true);
        setError("");
        api
            .detail(batchId)
            .then((res) => {
                if (active) applyBatch(res);
            })
            .catch((err: unknown) => {
                if (active) setError(err instanceof Error ? err.message : "Không thể tải phiếu import.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [batchId, applyBatch, api]);

    useEffect(() => loadBatch(), [loadBatch]);

    useEffect(() => {
        setItemsPage(1);
    }, [batchId]);

    function showToast(message: string, success: boolean) {
        setToast({ message, success });
        window.setTimeout(() => setToast(null), 3500);
    }

    async function handleUpdateItem(itemId: string, payload: Record<string, unknown>) {
        if (!batch) return;
        try {
            const updated = await api.updateItem(batch.id, itemId, payload);
            applyBatch(updated);
            showToast("Đã lưu dòng.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Lưu dòng thất bại.", false);
        }
    }

    async function handleDeleteItem(itemId: string) {
        if (!batch) return;
        try {
            const updated = await api.deleteItem(batch.id, itemId);
            applyBatch(updated);
            showToast("Đã xóa dòng.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Xóa dòng thất bại.", false);
        }
    }

    async function runVersionedAction(action: () => Promise<ImportBatch>, successMessage: string) {
        setActionLoading(true);
        try {
            const updated = await action();
            applyBatch(updated);
            showToast(successMessage, true);
        } catch (err) {
            if (err instanceof ApiError && err.status === 409) {
                showToast("Dữ liệu đã bị thay đổi bởi người khác — đã tải lại, vui lòng thử lại.", false);
                loadBatch();
            } else {
                showToast(err instanceof Error ? err.message : "Thao tác thất bại.", false);
            }
        } finally {
            setActionLoading(false);
        }
    }

    async function handleSaveDraft() {
        loadBatch();
        showToast("Đã lưu nháp.", true);
    }

    async function handleSubmit() {
        if (!batch) return;
        await runVersionedAction(() => api.submit(batch.id, batch.version), "Đã gửi quản lý duyệt.");
    }

    async function handleApprove() {
        if (!batch) return;
        await runVersionedAction(
            () => api.approve(batch.id, batch.version),
            "Đã duyệt — dữ liệu đã được ghi chính thức vào hệ thống."
        );
    }

    async function handleReject() {
        if (!batch) return;
        if (!rejectReason.trim()) {
            showToast("Vui lòng nhập lý do từ chối.", false);
            return;
        }
        await runVersionedAction(
            () => api.reject(batch.id, batch.version, rejectReason.trim()),
            "Đã từ chối phiếu import."
        );
        setRejectOpen(false);
        setRejectReason("");
    }

    if (loading) {
        return <div className="p-6 text-sm font-bold text-[#536078]">Đang tải phiếu import...</div>;
    }

    if (error || !batch) {
        return (
            <section className="grid gap-3 rounded-xl border border-red-200 bg-red-50 p-6 text-sm font-bold text-red-700">
                <div>{error || "Không tìm thấy phiếu import."}</div>
                <div>
                    <button className="rounded-lg bg-red-600 px-4 py-2 text-white hover:bg-red-700" onClick={loadBatch} type="button">
                        Tải lại
                    </button>
                </div>
            </section>
        );
    }

    const isEditable = batch.status === "DRAFT" || batch.status === "REJECTED";
    const hasInvalidItems = batch.items.some((i) => i.validationErrors.length > 0);
    const canSubmit = isEditable && batch.items.length > 0 && !hasInvalidItems && !actionLoading;
    const showApprovalActions = batch.status === "PENDING_APPROVAL" && canApprove;

    const itemsPageCount = Math.max(1, Math.ceil(batch.items.length / itemsPageSize));
    const safeItemsPage = Math.min(itemsPage, itemsPageCount);
    const pagedItems = batch.items.slice((safeItemsPage - 1) * itemsPageSize, safeItemsPage * itemsPageSize);

    const ItemsList = itemsListRegistry[batch.importType];

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

            <header className="grid gap-2 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <div className="flex flex-wrap items-center gap-3">
                    <h1 className="text-2xl font-black text-[#172317] font-mono">{batch.batchCode}</h1>
                    <StatusPill status={batch.status} />
                </div>
                <p className="text-sm font-semibold text-[#536078]">
                    File: {batch.sourceFileName ?? "—"} · {batch.items.length} dòng
                </p>
                {batch.status === "REJECTED" && batch.rejectionReason ? (
                    <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-bold text-red-700">
                        Lý do từ chối: {batch.rejectionReason}
                    </div>
                ) : null}
                {batch.status === "PENDING_APPROVAL" ? (
                    <div className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm font-bold text-blue-700">
                        Đang chờ quản lý duyệt — không thể chỉnh sửa.
                    </div>
                ) : null}
                {batch.status === "APPROVED" ? (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm font-bold text-emerald-700">
                        Đã duyệt — dữ liệu đã được ghi chính thức vào hệ thống.
                    </div>
                ) : null}
            </header>

            <div className="overflow-hidden rounded-xl border border-pos-border bg-white">
                <div className="grid gap-2 p-3">
                    {ItemsList ? (
                        <ItemsList
                            editable={isEditable}
                            expandedId={expandedId}
                            items={pagedItems}
                            onDelete={handleDeleteItem}
                            onSave={handleUpdateItem}
                            onToggle={(itemId) => setExpandedId(expandedId === itemId ? null : itemId)}
                        />
                    ) : (
                        <div className="p-4 text-sm font-bold text-red-600">
                            Loại import "{batch.importType}" chưa được hỗ trợ hiển thị trên giao diện.
                        </div>
                    )}
                </div>
                <Pagination
                    onPageChange={setItemsPage}
                    onPageSizeChange={(size) => {
                        setItemsPageSize(size);
                        setItemsPage(1);
                    }}
                    page={safeItemsPage}
                    pageCount={itemsPageCount}
                    pageSize={itemsPageSize}
                    totalItems={batch.items.length}
                    totalItemsLabel="Tổng số dòng"
                />
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2">
                {isEditable ? (
                    <>
                        <button
                            className="inline-flex h-10 items-center rounded-lg border border-pos-border bg-white px-4 text-sm font-black text-[#536078] hover:bg-neutral-50"
                            onClick={() => void handleSaveDraft()}
                            type="button"
                        >
                            Lưu nháp
                        </button>
                        <button
                            className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                            disabled={!canSubmit}
                            onClick={() => void handleSubmit()}
                            type="button"
                        >
                            Gửi quản lý duyệt
                        </button>
                    </>
                ) : null}
                {showApprovalActions ? (
                    <>
                        <button
                            className="inline-flex h-10 items-center rounded-lg border border-red-200 bg-white px-4 text-sm font-black text-red-600 hover:bg-red-50 disabled:opacity-50"
                            disabled={actionLoading}
                            onClick={() => setRejectOpen(true)}
                            type="button"
                        >
                            Từ chối
                        </button>
                        <button
                            className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                            disabled={actionLoading}
                            onClick={() => void handleApprove()}
                            type="button"
                        >
                            Duyệt
                        </button>
                    </>
                ) : null}
            </div>

            <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
                <DialogContent className="max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Từ chối phiếu import</DialogTitle>
                        <DialogDescription>Nhập lý do để người tạo biết cần sửa gì trước khi gửi duyệt lại.</DialogDescription>
                    </DialogHeader>
                    <div className="px-6 py-4">
                        <textarea
                            className="w-full rounded-lg border border-[#dce5e1] p-3 text-sm outline-none focus:border-red-500"
                            onChange={(e) => setRejectReason(e.target.value)}
                            placeholder="Lý do từ chối..."
                            rows={3}
                            value={rejectReason}
                        />
                    </div>
                    <DialogFooter>
                        <button
                            className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-bold text-[#536078]"
                            disabled={actionLoading}
                            onClick={() => setRejectOpen(false)}
                            type="button"
                        >
                            Quay lại
                        </button>
                        <button
                            className="h-10 rounded-lg bg-red-600 px-4 text-sm font-black text-white hover:bg-red-700 disabled:opacity-60"
                            disabled={actionLoading}
                            onClick={() => void handleReject()}
                            type="button"
                        >
                            {actionLoading ? "Đang xử lý..." : "Xác nhận từ chối"}
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}
