import { RotateCcw, Save } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button, IconButton } from "../../../components/ui/Button";
import { SearchInput } from "../../../components/ui/SearchInput";
import { toast } from "../../../components/ui/Toast";
import { replenishmentPolicyApi } from "../services/replenishmentPolicy.api";
import type {
    ReplenishmentPolicyCell,
    ReplenishmentPolicyMatrixRow
} from "../types/replenishment-policy.types";

type PolicyTabType = "PRODUCT_VARIANT" | "INGREDIENT";

interface CellDraft {
    id: string | null;
    minimumStock: string;
    maximumStock: string;
    enabled: boolean;
}

interface RowDraft {
    warehouse: CellDraft;
    shelf: CellDraft;
}

function toCellDraft(cell: ReplenishmentPolicyCell | null): CellDraft {
    if (!cell) {
        return {
            id: null,
            minimumStock: "",
            maximumStock: "",
            enabled: true
        };
    }
    return {
        id: cell.id,
        minimumStock: String(cell.minimumStock),
        maximumStock:
            cell.maximumStock !== null && cell.maximumStock !== undefined
                ? String(cell.maximumStock)
                : "",
        enabled: cell.enabled
    };
}

function toRowDraft(row: ReplenishmentPolicyMatrixRow): RowDraft {
    return {
        warehouse: toCellDraft(row.warehousePolicy),
        shelf: toCellDraft(row.shelfPolicy)
    };
}

function isCellEqual(a: CellDraft, b: CellDraft): boolean {
    return (
        a.minimumStock.trim() === b.minimumStock.trim() &&
        a.maximumStock.trim() === b.maximumStock.trim() &&
        a.enabled === b.enabled
    );
}

function getErrorMessage(error: unknown, fallback: string): string {
    if (error && typeof error === "object" && "message" in error) {
        const message = (error as { message?: unknown }).message;
        if (typeof message === "string" && message.trim()) {
            return message;
        }
    }
    return fallback;
}

function validateCell(label: string, cell: CellDraft): string | null {
    const minStr = cell.minimumStock.trim();
    const maxStr = cell.maximumStock.trim();

    if (minStr === "" && maxStr === "") {
        return null;
    }
    if (minStr === "") {
        return `${label}: vui lòng nhập Tồn tối thiểu khi đã nhập Tồn tối đa.`;
    }
    const minVal = Number(minStr);
    if (Number.isNaN(minVal) || minVal < 0) {
        return `${label}: Tồn tối thiểu phải lớn hơn hoặc bằng 0.`;
    }
    if (maxStr !== "") {
        const maxVal = Number(maxStr);
        if (Number.isNaN(maxVal) || maxVal <= minVal) {
            return `${label}: Tồn tối đa phải lớn hơn Tồn tối thiểu.`;
        }
    }
    return null;
}

export function ReplenishmentPolicyPage() {
    const [activeTab, setActiveTab] = useState<PolicyTabType>("PRODUCT_VARIANT");
    const [rows, setRows] = useState<ReplenishmentPolicyMatrixRow[]>([]);
    const [drafts, setDrafts] = useState<Record<string, RowDraft>>({});
    const [loading, setLoading] = useState(false);
    const [savingIds, setSavingIds] = useState<Record<string, boolean>>({});
    const [savingAll, setSavingAll] = useState(false);

    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const showShelfColumns = activeTab === "PRODUCT_VARIANT";

    const loadMatrix = useCallback(async () => {
        setLoading(true);
        try {
            const response = await replenishmentPolicyApi.matrix({
                search: search.trim() || undefined,
                itemType: activeTab,
                page: page - 1,
                size: pageSize
            });
            setRows(response.content);
            const nextDrafts: Record<string, RowDraft> = {};
            for (const row of response.content) {
                nextDrafts[row.inventoryItemId] = toRowDraft(row);
            }
            setDrafts(nextDrafts);
            setTotalPages(response.totalPages);
            setTotalElements(response.totalElements);
        } catch (error) {
            toast.error(getErrorMessage(error, "Không thể tải bảng ma trận ngưỡng tồn."));
        } finally {
            setLoading(false);
        }
    }, [search, activeTab, page, pageSize]);

    useEffect(() => {
        loadMatrix();
    }, [loadMatrix]);

    const originalsById = useMemo(() => {
        const map: Record<string, RowDraft> = {};
        for (const row of rows) {
            map[row.inventoryItemId] = toRowDraft(row);
        }
        return map;
    }, [rows]);

    const dirtyRowIds = useMemo(() => {
        return rows
            .filter((row) => {
                const draft = drafts[row.inventoryItemId];
                const orig = originalsById[row.inventoryItemId];
                if (!draft || !orig) return false;
                const warehouseChanged = !isCellEqual(draft.warehouse, orig.warehouse);
                const shelfChanged = showShelfColumns && !isCellEqual(draft.shelf, orig.shelf);
                return warehouseChanged || shelfChanged;
            })
            .map((row) => row.inventoryItemId);
    }, [rows, drafts, originalsById, showShelfColumns]);

    const handleTabChange = (nextTab: PolicyTabType) => {
        if (nextTab === activeTab) return;
        setActiveTab(nextTab);
        setPage(1);
    };

    const updateCell = (
        itemId: string,
        locationKey: "warehouse" | "shelf",
        patch: Partial<CellDraft>
    ) => {
        setDrafts((prev) => {
            const current = prev[itemId];
            if (!current) return prev;
            return {
                ...prev,
                [itemId]: {
                    ...current,
                    [locationKey]: {
                        ...current[locationKey],
                        ...patch
                    }
                }
            };
        });
    };

    const resetRow = (itemId: string) => {
        const orig = originalsById[itemId];
        if (!orig) return;
        setDrafts((prev) => ({
            ...prev,
            [itemId]: orig
        }));
    };

    const saveSingleCell = async (
        itemId: string,
        locationType: "WAREHOUSE" | "SHELF",
        draft: CellDraft,
        orig: CellDraft
    ) => {
        if (isCellEqual(draft, orig)) return;

        const minStr = draft.minimumStock.trim();
        const maxStr = draft.maximumStock.trim();

        if (minStr === "" && maxStr === "") {
            if (draft.id) {
                await replenishmentPolicyApi.remove(draft.id);
            }
            return;
        }

        const payload = {
            inventoryItemId: itemId,
            locationType,
            minimumStock: Number(minStr),
            maximumStock: maxStr === "" ? null : Number(maxStr),
            enabled: draft.enabled
        };

        if (draft.id) {
            await replenishmentPolicyApi.update(draft.id, payload);
        } else {
            await replenishmentPolicyApi.create(payload);
        }
    };

    const handleSaveRow = async (row: ReplenishmentPolicyMatrixRow) => {
        const itemId = row.inventoryItemId;
        const draft = drafts[itemId];
        const orig = originalsById[itemId];
        if (!draft || !orig) return;

        const whErr = validateCell(`${row.itemName} (Trong Kho)`, draft.warehouse);
        if (whErr) {
            toast.error(whErr);
            return;
        }
        if (showShelfColumns) {
            const shelfErr = validateCell(`${row.itemName} (Trên Kệ)`, draft.shelf);
            if (shelfErr) {
                toast.error(shelfErr);
                return;
            }
        }

        setSavingIds((prev) => ({ ...prev, [itemId]: true }));
        try {
            await saveSingleCell(itemId, "WAREHOUSE", draft.warehouse, orig.warehouse);
            if (showShelfColumns) {
                await saveSingleCell(itemId, "SHELF", draft.shelf, orig.shelf);
            }
            toast.success(`Đã lưu ngưỡng tồn cho ${row.itemName}.`);
            await loadMatrix();
        } catch (error) {
            toast.error(getErrorMessage(error, `Không thể lưu ngưỡng tồn cho ${row.itemName}.`));
        } finally {
            setSavingIds((prev) => ({ ...prev, [itemId]: false }));
        }
    };

    const handleSaveAll = async () => {
        if (dirtyRowIds.length === 0) return;

        for (const itemId of dirtyRowIds) {
            const row = rows.find((r) => r.inventoryItemId === itemId);
            const draft = drafts[itemId];
            if (!row || !draft) continue;

            const whErr = validateCell(`${row.itemName} (Trong Kho)`, draft.warehouse);
            if (whErr) {
                toast.error(whErr);
                return;
            }
            if (showShelfColumns) {
                const shelfErr = validateCell(`${row.itemName} (Trên Kệ)`, draft.shelf);
                if (shelfErr) {
                    toast.error(shelfErr);
                    return;
                }
            }
        }

        setSavingAll(true);
        try {
            for (const itemId of dirtyRowIds) {
                const draft = drafts[itemId];
                const orig = originalsById[itemId];
                if (!draft || !orig) continue;
                await saveSingleCell(itemId, "WAREHOUSE", draft.warehouse, orig.warehouse);
                if (showShelfColumns) {
                    await saveSingleCell(itemId, "SHELF", draft.shelf, orig.shelf);
                }
            }
            toast.success(`Đã lưu ${dirtyRowIds.length} mặt hàng thay đổi.`);
            await loadMatrix();
        } catch (error) {
            toast.error(getErrorMessage(error, "Có lỗi khi lưu hàng loạt chính sách."));
        } finally {
            setSavingAll(false);
        }
    };

    const renderNumberInput = (
        value: string,
        placeholder: string,
        onChange: (next: string) => void,
        disabled: boolean
    ) => (
        <input
            type="number"
            step="0.01"
            min="0"
            value={value}
            placeholder={placeholder}
            disabled={disabled}
            onChange={(event) => onChange(event.target.value)}
            className="w-full min-w-[76px] rounded border border-stone-200 bg-white px-2 py-1 text-right text-sm text-stone-900 placeholder:text-stone-300 focus:border-emerald-600 focus:outline-none focus:ring-1 focus:ring-emerald-600 disabled:bg-stone-50"
        />
    );

    const totalColumnCount = showShelfColumns ? 8 : 5;

    return (
        <div className="space-y-4">
            <ListPageHeader
                title="Chính sách ngưỡng tồn kho"
                description={
                    showShelfColumns
                        ? "Bảng caro cấu hình ngưỡng tồn tối thiểu / tối đa Trong Kho và Trên Kệ cho Biến thể sản phẩm."
                        : "Bảng caro cấu hình ngưỡng tồn tối thiểu / tối đa Trong Kho cho Nguyên liệu."
                }
                actions={
                    dirtyRowIds.length > 0 ? (
                        <Button onClick={handleSaveAll} disabled={savingAll}>
                            <Save className="h-4 w-4" />
                            {savingAll
                                ? "Đang lưu..."
                                : `Lưu tất cả (${dirtyRowIds.length})`}
                        </Button>
                    ) : undefined
                }
            />

            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-stone-200 bg-white p-3 shadow-sm">
                <div className="inline-flex rounded-lg border border-stone-200 bg-stone-100 p-1">
                    <button
                        type="button"
                        onClick={() => handleTabChange("PRODUCT_VARIANT")}
                        className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                            activeTab === "PRODUCT_VARIANT"
                                ? "bg-white text-emerald-800 shadow-sm"
                                : "text-stone-600 hover:text-stone-900"
                        }`}
                    >
                        Biến thể sản phẩm (Kho &amp; Kệ)
                    </button>
                    <button
                        type="button"
                        onClick={() => handleTabChange("INGREDIENT")}
                        className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                            activeTab === "INGREDIENT"
                                ? "bg-white text-emerald-800 shadow-sm"
                                : "text-stone-600 hover:text-stone-900"
                        }`}
                    >
                        Nguyên liệu (Chỉ Kho)
                    </button>
                </div>

                <div className="w-full sm:w-80">
                    <SearchInput
                        value={search}
                        onChange={(value) => {
                            setSearch(value);
                            setPage(1);
                        }}
                        placeholder={
                            showShelfColumns
                                ? "Tìm theo mã hoặc tên sản phẩm..."
                                : "Tìm theo mã hoặc tên nguyên liệu..."
                        }
                    />
                </div>
            </div>

            <div className="overflow-hidden rounded-xl border border-stone-300 bg-white shadow-sm">
                <div className="overflow-x-auto">
                    <table className="min-w-full border-collapse text-sm">
                        <thead>
                            <tr>
                                <th
                                    rowSpan={2}
                                    className="border border-stone-300 bg-stone-100 px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-stone-700"
                                >
                                    {showShelfColumns ? "Biến thể sản phẩm" : "Nguyên liệu"}
                                </th>
                                <th
                                    colSpan={3}
                                    className="border border-stone-300 bg-amber-50 px-3 py-2 text-center text-xs font-bold uppercase tracking-wide text-amber-900"
                                >
                                    Trong Kho (WAREHOUSE)
                                </th>
                                {showShelfColumns && (
                                    <th
                                        colSpan={3}
                                        className="border border-stone-300 bg-sky-50 px-3 py-2 text-center text-xs font-bold uppercase tracking-wide text-sky-900"
                                    >
                                        Trên Kệ (SHELF)
                                    </th>
                                )}
                                <th
                                    rowSpan={2}
                                    className="border border-stone-300 bg-stone-100 px-3 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-stone-700"
                                >
                                    Thao tác
                                </th>
                            </tr>
                            <tr>
                                <th className="border border-stone-300 bg-amber-50/60 px-2.5 py-2 text-right text-xs font-semibold text-amber-900">
                                    Tồn tối thiểu
                                </th>
                                <th className="border border-stone-300 bg-amber-50/60 px-2.5 py-2 text-right text-xs font-semibold text-amber-900">
                                    Tồn tối đa
                                </th>
                                <th className="border border-stone-300 bg-amber-50/60 px-2 py-2 text-center text-xs font-semibold text-amber-900">
                                    Bật
                                </th>
                                {showShelfColumns && (
                                    <>
                                        <th className="border border-stone-300 bg-sky-50/60 px-2.5 py-2 text-right text-xs font-semibold text-sky-900">
                                            Tồn tối thiểu
                                        </th>
                                        <th className="border border-stone-300 bg-sky-50/60 px-2.5 py-2 text-right text-xs font-semibold text-sky-900">
                                            Tồn tối đa
                                        </th>
                                        <th className="border border-stone-300 bg-sky-50/60 px-2 py-2 text-center text-xs font-semibold text-sky-900">
                                            Bật
                                        </th>
                                    </>
                                )}
                            </tr>
                        </thead>
                        <tbody>
                            {loading ? (
                                <tr>
                                    <td
                                        colSpan={totalColumnCount}
                                        className="border border-stone-200 px-4 py-10 text-center text-stone-500"
                                    >
                                        Đang tải bảng ma trận ngưỡng tồn...
                                    </td>
                                </tr>
                            ) : rows.length === 0 ? (
                                <tr>
                                    <td
                                        colSpan={totalColumnCount}
                                        className="border border-stone-200 px-4 py-10 text-center text-stone-500"
                                    >
                                        Không tìm thấy dữ liệu phù hợp.
                                    </td>
                                </tr>
                            ) : (
                                rows.map((row) => {
                                    const itemId = row.inventoryItemId;
                                    const draft = drafts[itemId] ?? toRowDraft(row);
                                    const orig = originalsById[itemId] ?? toRowDraft(row);
                                    const isDirty =
                                        !isCellEqual(draft.warehouse, orig.warehouse) ||
                                        (showShelfColumns && !isCellEqual(draft.shelf, orig.shelf));
                                    const isSaving = Boolean(savingIds[itemId]) || savingAll;

                                    return (
                                        <tr
                                            key={itemId}
                                            className={
                                                isDirty
                                                    ? "bg-emerald-50/40"
                                                    : "hover:bg-stone-50/70"
                                            }
                                        >
                                            <td className="border border-stone-300 px-3 py-2">
                                                <div className="font-medium text-stone-900">
                                                    {row.itemName}
                                                </div>
                                                <div className="text-xs text-stone-500">
                                                    {row.itemCode}
                                                </div>
                                            </td>

                                            {/* WAREHOUSE */}
                                            <td className="border border-stone-300 bg-amber-50/15 p-1.5">
                                                {renderNumberInput(
                                                    draft.warehouse.minimumStock,
                                                    "—",
                                                    (val) =>
                                                        updateCell(itemId, "warehouse", {
                                                            minimumStock: val
                                                        }),
                                                    isSaving
                                                )}
                                            </td>
                                            <td className="border border-stone-300 bg-amber-50/15 p-1.5">
                                                {renderNumberInput(
                                                    draft.warehouse.maximumStock,
                                                    "—",
                                                    (val) =>
                                                        updateCell(itemId, "warehouse", {
                                                            maximumStock: val
                                                        }),
                                                    isSaving
                                                )}
                                            </td>
                                            <td className="border border-stone-300 bg-amber-50/15 p-1.5 text-center">
                                                <input
                                                    type="checkbox"
                                                    aria-label={`Bật ngưỡng kho cho ${row.itemName}`}
                                                    checked={draft.warehouse.enabled}
                                                    disabled={isSaving}
                                                    onChange={(event) =>
                                                        updateCell(itemId, "warehouse", {
                                                            enabled: event.target.checked
                                                        })
                                                    }
                                                    className="h-4 w-4 cursor-pointer rounded border-stone-300 text-amber-600 focus:ring-amber-500"
                                                />
                                            </td>

                                            {/* SHELF (only for PRODUCT_VARIANT) */}
                                            {showShelfColumns && (
                                                <>
                                                    <td className="border border-stone-300 bg-sky-50/15 p-1.5">
                                                        {renderNumberInput(
                                                            draft.shelf.minimumStock,
                                                            "—",
                                                            (val) =>
                                                                updateCell(itemId, "shelf", {
                                                                    minimumStock: val
                                                                }),
                                                            isSaving
                                                        )}
                                                    </td>
                                                    <td className="border border-stone-300 bg-sky-50/15 p-1.5">
                                                        {renderNumberInput(
                                                            draft.shelf.maximumStock,
                                                            "—",
                                                            (val) =>
                                                                updateCell(itemId, "shelf", {
                                                                    maximumStock: val
                                                                }),
                                                            isSaving
                                                        )}
                                                    </td>
                                                    <td className="border border-stone-300 bg-sky-50/15 p-1.5 text-center">
                                                        <input
                                                            type="checkbox"
                                                            aria-label={`Bật ngưỡng kệ cho ${row.itemName}`}
                                                            checked={draft.shelf.enabled}
                                                            disabled={isSaving}
                                                            onChange={(event) =>
                                                                updateCell(itemId, "shelf", {
                                                                    enabled: event.target.checked
                                                                })
                                                            }
                                                            className="h-4 w-4 cursor-pointer rounded border-stone-300 text-sky-600 focus:ring-sky-500"
                                                        />
                                                    </td>
                                                </>
                                            )}

                                            {/* ACTIONS */}
                                            <td className="border border-stone-300 px-2 py-1.5 text-center">
                                                {isDirty ? (
                                                    <div className="inline-flex items-center gap-1">
                                                        <IconButton
                                                            aria-label={`Lưu ngưỡng tồn ${row.itemName}`}
                                                            title="Lưu dòng này"
                                                            disabled={isSaving}
                                                            onClick={() => handleSaveRow(row)}
                                                        >
                                                            <Save className="h-4 w-4 text-emerald-700" />
                                                        </IconButton>
                                                        <IconButton
                                                            aria-label={`Hoàn tác ${row.itemName}`}
                                                            title="Hoàn tác"
                                                            disabled={isSaving}
                                                            onClick={() => resetRow(itemId)}
                                                        >
                                                            <RotateCcw className="h-4 w-4 text-stone-500" />
                                                        </IconButton>
                                                    </div>
                                                ) : (
                                                    <span className="text-xs text-stone-400">
                                                        —
                                                    </span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                <TablePagination
                    page={page}
                    totalPages={totalPages}
                    totalItems={totalElements}
                    pageSize={pageSize}
                    itemLabel={showShelfColumns ? "sản phẩm" : "nguyên liệu"}
                    onPageChange={setPage}
                    pageSizeOptions={[10, 20, 30, 50]}
                    onPageSizeChange={(nextSize) => {
                        setPageSize(nextSize);
                        setPage(1);
                    }}
                    disabled={loading}
                />
            </div>
        </div>
    );
}
