import {
    Eye,
    Pencil,
    Plus,
    MoreHorizontal,
    Trash2,
    X
} from "lucide-react";
import { type ReactNode, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import { InventoryEmpty, InventoryError, InventoryLoading } from "./InventoryState";
import { InventoryStatusBadge } from "./InventoryBadges";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { FormDialog } from "../../product/components/FormDialog";

export interface TreeRowBase {
    id: string | number;
    status?: string;
    level?: number;
    tier?: number;
}

export interface Column {
    key: string;
    label: string;
    align?: "left" | "center" | "right";
    width?: string;
}

export type FormValues = Record<string, string | File>;

export interface FormField {
    name: string;
    label: string;
    type?: "text" | "textarea" | "number" | "date" | "select" | "file" | "checkbox";
    required?: boolean | ((values: FormValues) => boolean);
    options?: Array<{ label: string; value: string }>;
    placeholder?: string;
    purpose?: string;
    disabled?: boolean | ((values: FormValues) => boolean);
    hidden?: boolean | ((values: FormValues) => boolean);
}

export interface InventoryFilter {
    label: string;
    value: string;
    options: Array<{ label: string; value: string }>;
    onChange(value: string): void;
    render?: ReactNode;
    isTop?: boolean;
}

export interface InventoryTreeResourceManagementProps<T extends TreeRowBase, S extends string = string> {
    title: string;
    description: string;
    createLabel?: string;
    searchPlaceholder?: string;
    columns: Column[];
    items: T[];
    loading: boolean;
    error: string;
    query: string;
    status?: "ALL" | S;
    totalItemsLabel?: string;
    renderCells(item: T): ReactNode;
    renderDetail(item: T): ReactNode;
    formFields?: FormField[] | ((editingItem: T | null) => FormField[]);
    getInitialValues?(item: T): Record<string, string>;
    customForm?(editingItem: T | null, onClose: () => void): ReactNode;
    onQueryChange(value: string): void;
    onStatusChange?(value: "ALL" | S): void;
    onRefresh(): void;
    onSubmit?(values: FormValues, editingItem: T | null): Promise<void>;
    onChangeStatus?(item: T): Promise<void>;
    onDelete?(item: T): Promise<void>;
    detailPath?(item: T): string;
    secondaryFilter?: {
        label: string;
        value: string;
        options: Array<{ label: string; value: string }>;
        onChange(value: string): void;
    };
    statusOptions?: Array<{ label: string; value: "ALL" | S }>;
    filters?: InventoryFilter[];
    renderExtraRowActions?(item: T, closeMenu: () => void): ReactNode;
    controlledEditingItem?: T | null;
    controlledShowForm?: boolean;
    onControlledShowFormChange?(show: boolean): void;
    onControlledEditingItemChange?(item: T | null): void;
    extraHeaderActions?: ReactNode;
    getRowClassName?(item: T): string;
}

export function TreeTableCell({
    children,
    strong,
    className = ""
}: {
    children: ReactNode;
    strong?: boolean;
    className?: string;
}) {
    return (
        <td className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 first:pl-[18px] ${className}`}>
            <span
                className={`text-[13px] ${
                    strong
                        ? "font-extrabold text-[#172317]"
                        : "font-semibold text-gray-500"
                }`}
            >
                {children}
            </span>
        </td>
    );
}

export function InventoryTreeResourceManagement<T extends TreeRowBase, S extends string = string>({
    title,
    description,
    createLabel,
    searchPlaceholder,
    columns,
    items,
    loading,
    error,
    query,
    status,
    totalItemsLabel,
    renderCells,
    renderDetail,
    formFields,
    getInitialValues,
    onQueryChange,
    onStatusChange,
    onRefresh,
    onSubmit,
    onChangeStatus,
    detailPath,
    statusOptions,
    secondaryFilter,
    filters,
    renderExtraRowActions,
    onDelete,
    customForm,
    controlledEditingItem,
    controlledShowForm,
    onControlledShowFormChange,
    onControlledEditingItemChange,
    extraHeaderActions,
    getRowClassName
}: InventoryTreeResourceManagementProps<T, S>) {
    const navigate = useNavigate();

    const [uncontrolledEditingItem, setUncontrolledEditingItem] = useState<T | null>(null);
    const [uncontrolledShowForm, setUncontrolledShowForm] = useState(false);
    const [selectedItem, setSelectedItem] = useState<T | null>(null);
    const [statusTarget, setStatusTarget] = useState<T | null>(null);
    const [deletingItem, setDeletingItem] = useState<T | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [actionError, setActionError] = useState("");
    const [toast, setToast] = useState("");
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);

    const isFormControlled = controlledShowForm !== undefined;
    const isEditingItemControlled = controlledEditingItem !== undefined;

    const showForm = isFormControlled ? controlledShowForm : uncontrolledShowForm;
    const editingItem = isEditingItemControlled ? controlledEditingItem : uncontrolledEditingItem;

    const setShowForm = (show: boolean) => {
        if (isFormControlled) {
            onControlledShowFormChange?.(show);
        } else {
            setUncontrolledShowForm(show);
        }
    };

    const setEditingItem = (item: T | null) => {
        if (isEditingItemControlled) {
            onControlledEditingItemChange?.(item);
        } else {
            setUncontrolledEditingItem(item);
        }
    };

    const defaultRowClass = (item: T) => {
        const isParent = (item.level ?? 0) === 0;
        if (isParent) {
            // Thằng cha: Màu đậm hơn
            return "bg-[#eef6f0] hover:bg-[#e3f0e6] transition-colors";
        }
        // Thằng con: Nền trắng bình thường
        return "bg-white hover:bg-neutral-50 transition-colors";
    };

    async function submitForm(values: FormValues) {
        if (!onSubmit) return;
        setActionError("");
        setSubmitting(true);
        try {
            await onSubmit(values, editingItem);
            setShowForm(false);
            setEditingItem(null);
            setToast(editingItem ? "Cập nhật thành công!" : "Tạo mới thành công!");
            window.setTimeout(() => setToast(""), 2500);
            onRefresh();
        } catch (err: any) {
            setActionError(err?.message || "Không thể lưu dữ liệu.");
        } finally {
            setSubmitting(false);
        }
    }

    async function confirmStatusChange() {
        if (!statusTarget || !onChangeStatus) return;
        setActionError("");
        setSubmitting(true);
        try {
            await onChangeStatus(statusTarget);
            setStatusTarget(null);
            setToast("Cập nhật trạng thái thành công!");
            window.setTimeout(() => setToast(""), 2500);
        } catch (err: any) {
            setActionError(err?.message || "Không thể thay đổi trạng thái.");
        } finally {
            setSubmitting(false);
        }
    }

    const defaultStatusOptions = statusOptions ?? [
        { label: "Tất cả trạng thái", value: "ALL" as any },
        { label: "Đang hoạt động", value: "ACTIVE" as any },
        { label: "Ngừng hoạt động", value: "INACTIVE" as any }
    ];

    const topFilters = filters?.filter(f => f.isTop) ?? [];
    const remainingFilters = filters?.filter(f => !f.isTop) ?? [];
    const hasRemainingFilters = remainingFilters.length > 0;

    return (
        <section className="grid gap-5">
            <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div className="grid gap-1 min-w-[240px]">
                    <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                        {title}
                    </h1>
                    <p className="text-sm text-gray-500">
                        {description}
                    </p>
                </div>

                {(createLabel || extraHeaderActions) && (
                    <div className="flex flex-wrap items-center gap-3 w-full justify-start sm:w-auto sm:justify-end lg:ml-auto">
                        {extraHeaderActions}
                        {createLabel && (
                            <ActionButton
                                variant="primary"
                                onClick={() => {
                                    setEditingItem(null);
                                    setActionError("");
                                    setShowForm(true);
                                }}
                            >
                                <Plus size={17} aria-hidden="true" />
                                {createLabel}
                            </ActionButton>
                        )}
                    </div>
                )}
            </header>

            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                {searchPlaceholder ? (
                    <div className={`${hasRemainingFilters ? "border-b border-[#f3f5f7]" : ""} pb-2`}>
                        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 justify-between">
                            <div className="relative flex-1">
                                <SearchBox
                                    onChange={onQueryChange}
                                    placeholder={searchPlaceholder}
                                    value={query}
                                />
                            </div>

                            <div className="flex items-center gap-3 flex-wrap sm:flex-nowrap">
                                {secondaryFilter && (
                                    <div className="min-w-[140px] shrink-0">
                                        <select
                                            value={secondaryFilter.value}
                                            onChange={(e) => secondaryFilter.onChange(e.target.value)}
                                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                        >
                                            <option value="ALL">{secondaryFilter.label}</option>
                                            {secondaryFilter.options
                                                .filter(opt => opt.value !== "ALL")
                                                .map((opt) => (
                                                    <option key={opt.value} value={opt.value}>
                                                        {opt.label}
                                                    </option>
                                                ))
                                            }
                                        </select>
                                    </div>
                                )}

                                {topFilters.length > 0 && (
                                    <div className="flex items-center gap-3 flex-wrap sm:flex-nowrap">
                                        {topFilters.map(filter => (
                                            <div key={filter.label} className="min-w-[150px] shrink-0">
                                                {filter.render ? (
                                                    filter.render
                                                ) : (
                                                    <select
                                                        value={filter.value}
                                                        onChange={(e) => filter.onChange(e.target.value)}
                                                        className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                                    >
                                                        <option value="ALL">{filter.label}</option>
                                                        {filter.options
                                                            .filter(opt => opt.value !== "ALL")
                                                            .map((opt) => (
                                                                <option key={opt.value} value={opt.value}>
                                                                    {opt.label}
                                                                </option>
                                                            ))
                                                        }
                                                    </select>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {onStatusChange && (
                                    <div className="min-w-[150px] shrink-0">
                                        <select
                                            value={status ?? "ALL"}
                                            onChange={(e) => onStatusChange(e.target.value as any)}
                                            className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                        >
                                            {defaultStatusOptions.map((opt) => (
                                                <option key={opt.value} value={opt.value}>
                                                    {opt.label}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                ) : null}

                {hasRemainingFilters && (
                    <div className="flex flex-col gap-3 pt-2">
                        {remainingFilters.map((filter) => {
                            if (filter.render) {
                                return (
                                    <div key={filter.label} className="w-full">
                                        {filter.render}
                                    </div>
                                );
                            }
                            return (
                                <div key={filter.label} className="flex items-center gap-2">
                                    <span className="text-xs font-bold text-gray-500 shrink-0">{filter.label}:</span>
                                    <select
                                        value={filter.value}
                                        onChange={(e) => filter.onChange(e.target.value)}
                                        className="h-9 rounded-lg border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                    >
                                        <option value="ALL">Tất cả</option>
                                        {filter.options
                                            .filter((opt) => opt.value !== "ALL")
                                            .map((opt) => (
                                                <option key={opt.value} value={opt.value}>
                                                    {opt.label}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* Tree Table View */}
            <div className="flex flex-col gap-4">
                <section className="flex flex-col rounded-2xl bg-white shadow-pos-card border border-neutral-100">
                    <div className="overflow-x-auto min-h-[300px]">
                        {loading ? (
                            <InventoryLoading />
                        ) : error ? (
                            <InventoryError message={error} onRetry={onRefresh} />
                        ) : items.length === 0 ? (
                            <InventoryEmpty />
                        ) : (
                            <table className="w-full border-collapse text-left">
                                <thead>
                                    <tr className="border-b border-[#edf0f4] bg-neutral-50/50">
                                        {columns.map((column) => (
                                            <th
                                                key={column.key}
                                                style={{ width: column.width }}
                                                className={`h-11 px-4 text-xs font-bold uppercase tracking-wider text-gray-500 first:pl-[18px] ${
                                                    column.align === "right"
                                                        ? "text-right"
                                                        : column.align === "center"
                                                        ? "text-center"
                                                        : "text-left"
                                                }`}
                                            >
                                                {column.label}
                                            </th>
                                        ))}
                                        <th className="h-11 w-20 px-4 text-right text-xs font-bold uppercase tracking-wider text-gray-500 pr-[18px]">
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item, idx) => {
                                        const rowClassName = getRowClassName
                                            ? getRowClassName(item)
                                            : defaultRowClass(item);
                                        return (
                                            <tr
                                                key={item.id}
                                                className={`group ${rowClassName}`}
                                            >
                                                {renderCells(item)}
                                                <td className="border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 pr-[18px] text-right">
                                                    <div className="relative inline-block text-left">
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                setActiveActionMenuId(
                                                                    activeActionMenuId === String(item.id)
                                                                        ? null
                                                                        : String(item.id)
                                                                )
                                                            }
                                                            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 text-gray-500 hover:text-gray-700 cursor-pointer"
                                                        >
                                                            <MoreHorizontal size={16} />
                                                        </button>

                                                        {activeActionMenuId === String(item.id) && (
                                                            <>
                                                                <div
                                                                    className="fixed inset-0 z-30"
                                                                    onClick={() => setActiveActionMenuId(null)}
                                                                />
                                                                <div
                                                                    className={`absolute right-0 ${
                                                                        idx >= 2 && idx >= items.length - 2
                                                                            ? "bottom-8 mb-1"
                                                                            : "top-8 mt-1"
                                                                    } w-44 rounded-xl border border-pos-border bg-white p-1.5 shadow-lg z-40 text-left`}
                                                                >
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => {
                                                                            if (detailPath) {
                                                                                navigate(detailPath(item));
                                                                            } else {
                                                                                setSelectedItem(item);
                                                                            }
                                                                            setActiveActionMenuId(null);
                                                                        }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#159734] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <Eye size={14} />
                                                                        Xem chi tiết
                                                                    </button>
                                                                    {onSubmit && (
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => {
                                                                                setEditingItem(item);
                                                                                setActionError("");
                                                                                setShowForm(true);
                                                                                setActiveActionMenuId(null);
                                                                            }}
                                                                            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#159734] font-medium transition-colors cursor-pointer"
                                                                        >
                                                                            <Pencil size={14} />
                                                                            Chỉnh sửa
                                                                        </button>
                                                                    )}
                                                                    {onChangeStatus && (
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => {
                                                                                setStatusTarget(item);
                                                                                setActiveActionMenuId(null);
                                                                            }}
                                                                            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-gray-600 hover:bg-neutral-50 font-medium transition-colors cursor-pointer"
                                                                        >
                                                                            Đổi trạng thái
                                                                        </button>
                                                                    )}
                                                                    {renderExtraRowActions &&
                                                                        renderExtraRowActions(item, () =>
                                                                            setActiveActionMenuId(null)
                                                                        )}
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        )}
                    </div>

                    {/* Footer tóm tắt số lượng - không phân trang */}
                    {!loading && !error && items.length > 0 && (
                        <div className="flex items-center justify-between px-5 py-3 border-t border-neutral-100 text-xs font-semibold text-gray-500">
                            <span>
                                {totalItemsLabel || "Tổng số vị trí"}:{" "}
                                <strong className="text-gray-800">{items.length}</strong>
                            </span>
                        </div>
                    )}
                </section>

                {/* Detail Panel */}
                {selectedItem ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-[#152a1c]/45 p-6 backdrop-blur-[2px] transition-opacity duration-300"
                        onClick={() => setSelectedItem(null)}
                    >
                        <section
                            aria-label={`Chi tiết ${title}`}
                            aria-modal="true"
                            className="relative flex max-h-[85vh] w-[min(92vw,960px)] flex-col overflow-hidden rounded-2xl border border-[#dfe8e3] bg-white shadow-[0_28px_90px_rgba(21,43,30,0.24)] max-md:h-[92vh] max-md:w-[calc(100vw-24px)]"
                            role="dialog"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <header className="sticky top-0 z-20 flex min-h-14 shrink-0 items-center justify-between gap-3 border-b border-[#e2e9e5] bg-white px-4 py-2.5">
                                <h2 className="truncate text-base font-black text-[#172317]">
                                    Chi tiết {title.toLowerCase()}
                                </h2>
                                <button
                                    aria-label="Đóng"
                                    className="grid size-9 place-items-center rounded-lg text-[#657282] hover:bg-[#eef3f0] hover:text-[#172317] cursor-pointer"
                                    onClick={() => setSelectedItem(null)}
                                    type="button"
                                >
                                    <X size={20} aria-hidden="true" />
                                </button>
                            </header>

                            <main className="grid min-h-0 flex-1 grid-cols-[280px_minmax(0,1fr)] max-md:block max-md:overflow-y-auto">
                                <aside className="min-h-0 border-r border-[#e2e9e5] bg-white max-md:border-b max-md:border-r-0 overflow-y-auto px-6 py-6">
                                    <div className="flex items-start gap-4 border-b border-[#eef2f0] pb-6">
                                        <span className="grid size-16 shrink-0 place-items-center rounded-full bg-[#eaf8ee] text-2xl font-black text-[#116f28] ring-1 ring-[#c9ead2]">
                                            {((selectedItem as any).name || title).charAt(0).toUpperCase()}
                                        </span>
                                        <div className="min-w-0 flex-1">
                                            <strong className="block truncate text-lg font-black text-[#172317]">
                                                {(selectedItem as any).name || title}
                                            </strong>
                                            <span className="mt-1 block text-xs font-bold text-[#718078]">
                                                {title}
                                            </span>
                                        </div>
                                    </div>
                                    <div className="mt-6 grid gap-4">
                                        {(() => {
                                            const rawCode = (selectedItem as any).code;
                                            if (!rawCode) return null;
                                            return (
                                                <div className="grid gap-1">
                                                    <span className="text-[11px] font-bold uppercase tracking-wider text-gray-400">Mã vị trí</span>
                                                    <span className="text-sm font-semibold text-[#172317]">#{rawCode}</span>
                                                </div>
                                            );
                                        })()}
                                        {(selectedItem as any).status && (
                                            <div className="grid gap-1">
                                                <span className="text-[11px] font-bold uppercase tracking-wider text-gray-400">Trạng thái</span>
                                                <div>
                                                    <InventoryStatusBadge status={(selectedItem as any).status} />
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </aside>

                                <section className="flex min-h-0 min-w-0 flex-col overflow-y-auto p-6 bg-slate-50/50">
                                    {renderDetail(selectedItem)}
                                </section>
                            </main>

                            <footer className="sticky bottom-0 z-20 flex min-h-16 shrink-0 items-center gap-3 border-t border-[#e2e9e5] bg-white px-4 py-3">
                                {onDelete && (
                                    <button
                                        className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg border border-[#efc9c2] bg-white px-4 text-sm font-black text-[#c4422e] hover:bg-[#fff5f3] cursor-pointer"
                                        onClick={() => {
                                            setDeletingItem(selectedItem);
                                            setSelectedItem(null);
                                        }}
                                        type="button"
                                    >
                                        <Trash2 size={16} aria-hidden="true" />
                                        Xóa
                                    </button>
                                )}
                                <div className="ml-auto flex shrink-0 gap-2">
                                    <button
                                        className="inline-flex h-10 shrink-0 items-center justify-center rounded-lg border border-[#dce5e1] bg-white px-4 text-sm font-black text-[#536171] hover:bg-[#f4f8f5] cursor-pointer"
                                        onClick={() => setSelectedItem(null)}
                                        type="button"
                                    >
                                        Đóng
                                    </button>
                                    {onSubmit && (
                                        <button
                                            className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-sm hover:bg-[#12862d] cursor-pointer"
                                            onClick={() => {
                                                setEditingItem(selectedItem);
                                                setActionError("");
                                                setShowForm(true);
                                                setSelectedItem(null);
                                            }}
                                            type="button"
                                        >
                                            <Pencil size={16} aria-hidden="true" />
                                            Chỉnh sửa
                                        </button>
                                    )}
                                </div>
                            </footer>
                        </section>
                    </div>
                ) : null}
            </div>

            {showForm ? (
                customForm ? (
                    customForm(editingItem, () => setShowForm(false))
                ) : (
                    <FormDialog
                        title={editingItem ? `Chỉnh sửa ${title}` : (createLabel || "")}
                        fields={
                            typeof formFields === "function"
                                ? formFields!(editingItem)
                                : formFields!
                        }
                        initialValues={
                            editingItem && getInitialValues ? getInitialValues(editingItem) : {}
                        }
                        error={actionError}
                        submitting={submitting}
                        submitLabel={editingItem ? "Lưu thay đổi" : "Tạo mới"}
                        onClose={() => setShowForm(false)}
                        onSubmit={submitForm}
                    />
                )
            ) : null}

            {statusTarget ? (
                <ConfirmDialog
                    error={actionError}
                    submitting={submitting}
                    onCancel={() => setStatusTarget(null)}
                    onConfirm={confirmStatusChange}
                />
            ) : null}

            {deletingItem ? (
                <ConfirmDialog
                    error={actionError}
                    submitting={submitting}
                    onCancel={() => setDeletingItem(null)}
                    onConfirm={async () => {
                        if (!onDelete) return;
                        setActionError("");
                        setSubmitting(true);
                        try {
                            await onDelete(deletingItem);
                            setToast("Xóa thành công!");
                            setDeletingItem(null);
                            onRefresh();
                        } catch (err: any) {
                            setActionError(err?.message || "Không thể xóa mục này.");
                        } finally {
                            setSubmitting(false);
                        }
                    }}
                />
            ) : null}

            {toast && (
                <div className="fixed bottom-5 right-5 z-50 flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white shadow-xl animate-in fade-in slide-in-from-bottom duration-300">
                    <span>{toast}</span>
                </div>
            )}
        </section>
    );
}
