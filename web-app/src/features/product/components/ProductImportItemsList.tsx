import { ChevronDown, ChevronUp, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { NativeSelect, TextArea, TextInput } from "../../../components/ui/Field";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";
import type { ImportItemsListProps } from "./ImportBatchPanel";
import { categoryApi } from "../services/category.api";
import type { ImportBatchItem } from "../types/import-batch.types";

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
                        <StatusBadge tone="warning">
                            Trùng {item.occurrenceCount} dòng — đã gộp
                        </StatusBadge>
                    ) : null}
                    <StatusBadge tone={isValid ? "success" : "danger"}>
                        {isValid ? "Hợp lệ" : `${item.validationErrors.length} lỗi`}
                    </StatusBadge>
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
                        <TextInput
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
                        <TextArea
                            disabled={!editable}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={2}
                            value={description}
                        />
                    </div>

                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Trạng thái</label>
                        <NativeSelect
                            disabled={!editable}
                            onChange={(e) => setStatus(e.target.value)}
                            value={status}
                        >
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </NativeSelect>
                    </div>

                    {editable ? (
                        <div className="flex items-center justify-between pt-1">
                            <Button onClick={() => void onDelete()} size="sm" variant="dangerOutline">
                                <Trash2 size={14} aria-hidden="true" />
                                Xóa dòng
                            </Button>
                            <Button disabled={saving} onClick={() => void handleSave()} size="sm" variant="primary">
                                {saving ? "Đang lưu..." : "Lưu dòng này"}
                            </Button>
                        </div>
                    ) : null}
                </div>
            ) : null}
        </div>
    );
}

export function ProductImportItemsList({ items, editable, expandedId, onToggle, onSave, onDelete }: ImportItemsListProps) {
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

    // Danh mục đã chọn của 1 dòng có thể không nằm trong trang lookup mặc định (chỉ 20 kết quả đầu) —
    // nếu không resolve riêng thì dropdown/tóm tắt sẽ không hiện đúng tên danh mục đã có sẵn từ Excel.
    useEffect(() => {
        const idsNeeded = Array.from(
            new Set(
                items
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
    }, [items, categoryNameById]);

    return (
        <>
            {items.map((item) => {
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
                        editable={editable}
                        expanded={expandedId === item.id}
                        item={item}
                        key={item.id}
                        onCategorySearch={handleCategorySearch}
                        onDelete={() => onDelete(item.id)}
                        onLoadMoreCategories={handleLoadMoreCategories}
                        onSave={(payload) => onSave(item.id, payload)}
                        onToggle={() => onToggle(item.id)}
                    />
                );
            })}
        </>
    );
}
