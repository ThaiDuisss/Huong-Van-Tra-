import { ApiError } from "@hvt/api-client";
import { ChevronDown, ChevronUp, Trash2 } from "lucide-react";
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
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import { categoryApi } from "../services/category.api";
import { importBatchApi } from "../services/importBatch.api";
import type { ImportBatch, ImportBatchItem, ImportBatchStatus } from "../types/import-batch.types";

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

interface ItemRowProps {
    item: ImportBatchItem;
    editable: boolean;
    expanded: boolean;
    onToggle(): void;
    onSave(payload: Record<string, unknown>): Promise<void>;
    onDelete(): Promise<void>;
    categoryOptions: LookupItem[];
    onCategorySearch(term: string): void;
    onLoadMoreCategories(): void;
    categoryHasMore: boolean;
    categoryLoading: boolean;
    categoryNameById: Record<string, string>;
}

function ItemRow({
    item,
    editable,
    expanded,
    onToggle,
    onSave,
    onDelete,
    categoryOptions,
    onCategorySearch,
    onLoadMoreCategories,
    categoryHasMore,
    categoryLoading,
    categoryNameById
}: ItemRowProps) {
    const [name, setName] = useState(String(item.payload.name ?? ""));
    const [categoryId, setCategoryId] = useState(item.payload.categoryId ? String(item.payload.categoryId) : "");
    const [description, setDescription] = useState(String(item.payload.description ?? ""));
    const [status, setStatus] = useState(String(item.payload.status ?? "ACTIVE"));
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setName(String(item.payload.name ?? ""));
        setCategoryId(item.payload.categoryId ? String(item.payload.categoryId) : "");
        setDescription(String(item.payload.description ?? ""));
        setStatus(String(item.payload.status ?? "ACTIVE"));
    }, [item]);

    const errorByField = new Map(item.validationErrors.map((e) => [e.field, e.message]));
    const isValid = item.validationErrors.length === 0;
    const categoryName = categoryId ? categoryNameById[categoryId] : undefined;

    async function handleSave() {
        setSaving(true);
        try {
            await onSave({
                categoryId: categoryId || null,
                name,
                description: description.trim() ? description : null,
                status
            });
        } finally {
            setSaving(false);
        }
    }

    return (
        <div className="overflow-hidden rounded-xl border border-pos-border bg-white">
            <button
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-neutral-50"
                onClick={onToggle}
                type="button"
            >
                <div className="flex min-w-0 items-center gap-3">
                    {expanded ? <ChevronUp size={16} aria-hidden="true" /> : <ChevronDown size={16} aria-hidden="true" />}
                    <div className="min-w-0">
                        <div className="truncate font-bold text-[#172317]">{name || `Dòng ${item.rowIndex}`}</div>
                        <div className="truncate text-xs text-[#697684]">{categoryName ?? "Chưa chọn danh mục"}</div>
                    </div>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                    {item.occurrenceCount > 1 ? (
                        <span className="inline-flex h-6 items-center rounded-full bg-amber-50 px-2 text-[11px] font-bold text-amber-700">
                            Trùng {item.occurrenceCount} dòng — đã gộp
                        </span>
                    ) : null}
                    <span
                        className={`inline-flex h-6 items-center rounded-full px-2 text-[11px] font-bold ${
                            isValid ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"
                        }`}
                    >
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </span>
                </div>
            </button>

            {expanded ? (
                <div className="grid gap-3 border-t border-pos-border p-4">
                    {item.occurrenceCount > 1 ? (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-700">
                            Dòng này xuất hiện {item.occurrenceCount} lần giống hệt nhau trong file Excel gốc — hệ thống đã tự
                            gộp thành 1 dòng duy nhất.
                        </div>
                    ) : null}
                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Danh mục *</label>
                        <SearchableDropdown
                            disabled={!editable}
                            hasMore={categoryHasMore}
                            loading={categoryLoading}
                            onChange={setCategoryId}
                            onLoadMore={onLoadMoreCategories}
                            onSearch={onCategorySearch}
                            options={categoryOptions}
                            placeholder="Chọn danh mục..."
                            value={categoryId}
                        />
                        {errorByField.get("categoryId") ? (
                            <p className="text-xs font-bold text-red-600">{errorByField.get("categoryId")}</p>
                        ) : null}
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Tên sản phẩm *</label>
                        <input
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setName(e.target.value)}
                            type="text"
                            value={name}
                        />
                        {errorByField.get("name") ? (
                            <p className="text-xs font-bold text-red-600">{errorByField.get("name")}</p>
                        ) : null}
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Mô tả</label>
                        <textarea
                            className="rounded-lg border border-pos-border px-3 py-2 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={2}
                            value={description}
                        />
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Trạng thái</label>
                        <select
                            className="h-10 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600"
                            disabled={!editable}
                            onChange={(e) => setStatus(e.target.value)}
                            value={status}
                        >
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </select>
                    </div>

                    {editable ? (
                        <div className="flex items-center justify-between pt-1">
                            <button
                                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3 text-xs font-bold text-red-600 hover:bg-red-50"
                                onClick={() => void onDelete()}
                                type="button"
                            >
                                <Trash2 size={14} aria-hidden="true" />
                                Xóa dòng
                            </button>
                            <button
                                className="inline-flex h-9 items-center rounded-lg bg-[#159734] px-4 text-xs font-black text-white hover:bg-[#12802c] disabled:opacity-50"
                                disabled={saving}
                                onClick={() => void handleSave()}
                                type="button"
                            >
                                {saving ? "Đang lưu..." : "Lưu dòng này"}
                            </button>
                        </div>
                    ) : null}
                </div>
            ) : null}
        </div>
    );
}

interface ImportBatchPanelProps {
    batchId: string;
    /** Called whenever the batch is (re)loaded or changes status/version, for parents that need to react (e.g. refresh a list). */
    onBatchChange?: (batch: ImportBatch) => void;
}

export function ImportBatchPanel({ batchId, onBatchChange }: ImportBatchPanelProps) {
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

    const [categoryOptions, setCategoryOptions] = useState<LookupItem[]>([]);
    const [categoryPage, setCategoryPage] = useState(0);
    const [categoryHasMore, setCategoryHasMore] = useState(true);
    const [categoryLoading, setCategoryLoading] = useState(false);
    const [categoryNameById, setCategoryNameById] = useState<Record<string, string>>({});

    const loadCategoryOptions = useCallback(async (search: string, page: number) => {
        try {
            setCategoryLoading(true);
            const res = await categoryApi.lookup(search, page, 20);
            const content = res.content ?? [];
            setCategoryOptions((prev) => (page === 0 ? content : [...prev, ...content]));
            setCategoryHasMore(!res.last);
            setCategoryNameById((prev) => {
                const next = { ...prev };
                content.forEach((c) => {
                    next[c.id] = c.name;
                });
                return next;
            });
        } catch (err) {
            console.error("Lỗi tải danh mục", err);
            setCategoryHasMore(false);
        } finally {
            setCategoryLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadCategoryOptions("", 0);
    }, [loadCategoryOptions]);

    const handleCategorySearch = useCallback(
        (term: string) => {
            setCategoryPage(0);
            void loadCategoryOptions(term, 0);
        },
        [loadCategoryOptions]
    );

    const handleLoadMoreCategories = useCallback(() => {
        if (!categoryLoading && categoryHasMore) {
            const nextPage = categoryPage + 1;
            setCategoryPage(nextPage);
            void loadCategoryOptions("", nextPage);
        }
    }, [categoryLoading, categoryHasMore, categoryPage, loadCategoryOptions]);

    const applyBatch = useCallback(
        (updated: ImportBatch) => {
            setBatch(updated);
            onBatchChange?.(updated);
        },
        [onBatchChange]
    );

    // Danh mục đã chọn của 1 dòng có thể không nằm trong trang lookup mặc định (chỉ 20 kết quả đầu) —
    // nếu không resolve riêng thì dropdown/tóm tắt sẽ không hiện đúng tên danh mục đã có sẵn từ Excel.
    useEffect(() => {
        if (!batch) return;
        const idsNeeded = Array.from(
            new Set(
                batch.items
                    .map((item) => (item.payload.categoryId ? String(item.payload.categoryId) : null))
                    .filter((id): id is string => Boolean(id) && !categoryNameById[id as string])
            )
        );
        if (idsNeeded.length === 0) return;
        let active = true;
        Promise.all(
            idsNeeded.map((id) =>
                categoryApi
                    .detail(id)
                    .then((category) => ({ id, name: category.name }))
                    .catch(() => null)
            )
        ).then((resolved) => {
            if (!active) return;
            const valid = resolved.filter((r): r is { id: string; name: string } => r !== null);
            if (valid.length === 0) return;
            setCategoryNameById((prev) => {
                const next = { ...prev };
                valid.forEach((v) => {
                    next[v.id] = v.name;
                });
                return next;
            });
            setCategoryOptions((prev) => {
                const existingIds = new Set(prev.map((o) => o.id));
                const additions = valid.filter((v) => !existingIds.has(v.id));
                return additions.length > 0 ? [...prev, ...additions] : prev;
            });
        });
        return () => {
            active = false;
        };
    }, [batch, categoryNameById]);

    const loadBatch = useCallback(() => {
        let active = true;
        setLoading(true);
        setError("");
        importBatchApi
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
    }, [batchId, applyBatch]);

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
            const updated = await importBatchApi.updateItem(batch.id, itemId, payload);
            applyBatch(updated);
            showToast("Đã lưu dòng.", true);
        } catch (err) {
            showToast(err instanceof Error ? err.message : "Lưu dòng thất bại.", false);
        }
    }

    async function handleDeleteItem(itemId: string) {
        if (!batch) return;
        try {
            const updated = await importBatchApi.deleteItem(batch.id, itemId);
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
        await runVersionedAction(() => importBatchApi.submit(batch.id, batch.version), "Đã gửi quản lý duyệt.");
    }

    async function handleApprove() {
        if (!batch) return;
        await runVersionedAction(
            () => importBatchApi.approve(batch.id, batch.version),
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
            () => importBatchApi.reject(batch.id, batch.version, rejectReason.trim()),
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
                    {pagedItems.map((item) => {
                        // Danh mục đang chọn của dòng này phải luôn có mặt trong options của riêng nó,
                        // bất kể ô tìm kiếm chung đang lọc ra gì — nếu không dropdown sẽ rớt về nhãn "Đã chọn"
                        // mỗi khi có dòng khác (dùng chung state search) đổi từ khoá tìm kiếm.
                        const itemCategoryId = item.payload.categoryId ? String(item.payload.categoryId) : "";
                        const itemCategoryName = itemCategoryId ? categoryNameById[itemCategoryId] : undefined;
                        const itemCategoryOptions =
                            itemCategoryId && itemCategoryName && !categoryOptions.some((o) => o.id === itemCategoryId)
                                ? [{ id: itemCategoryId, name: itemCategoryName }, ...categoryOptions]
                                : categoryOptions;

                        return (
                            <ItemRow
                                categoryHasMore={categoryHasMore}
                                categoryLoading={categoryLoading}
                                categoryNameById={categoryNameById}
                                categoryOptions={itemCategoryOptions}
                                editable={isEditable}
                                expanded={expandedId === item.id}
                                item={item}
                                key={item.id}
                                onCategorySearch={handleCategorySearch}
                                onDelete={() => handleDeleteItem(item.id)}
                                onLoadMoreCategories={handleLoadMoreCategories}
                                onSave={(payload) => handleUpdateItem(item.id, payload)}
                                onToggle={() => setExpandedId(expandedId === item.id ? null : item.id)}
                            />
                        );
                    })}
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
