import {
    AlertTriangle,
    Eye,
    Pencil,
    Plus,
    ToggleLeft,
    MoreHorizontal
} from "lucide-react";
import { type ReactNode, useState } from "react";
import { Link } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import { ApiErrorState, ApiLoadingState } from "./ApiState";
import { CatalogStatusBadge } from "./CatalogStatusBadge";
import { ConfirmDialog } from "./ConfirmDialog";
import { FormDialog } from "./FormDialog";


// Re-export helper components for backward compatibility
export { TableCell } from "./TableCell";
export { DetailList } from "./DetailList";
export { CatalogStatusBadge };

export interface RowBase {
    id: string | number;
    status?: string;
}

export interface Column {
    key: string;
    label: string;
}

export type FormValues = Record<string, string | File>;

export interface FormField {
    name: string;
    label: string;
    type?: "text" | "textarea" | "number" | "date" | "select" | "file";
    required?: boolean;
    options?: Array<{ label: string; value: string }>;
    placeholder?: string;
    purpose?: string;
}

export interface CatalogFilter {
    label: string;
    value: string;
    options: Array<{ label: string; value: string }>;
    onChange(value: string): void;
}

export interface CatalogResourceManagementProps<T extends RowBase, S extends string = string> {
    title: string;
    description: string;
    createLabel: string;
    searchPlaceholder?: string;
    columns: Column[];
    items: T[];
    loading: boolean;
    error: string;
    query: string;
    status?: "ALL" | S;
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    renderCells(item: T): ReactNode;
    renderDetail(item: T): ReactNode;
    formFields: FormField[] | ((editingItem: T | null) => FormField[]);
    getInitialValues(item: T): Record<string, string>;
    onQueryChange(value: string): void;
    onStatusChange?(value: "ALL" | S): void;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
    onRefresh(): void;
    onSubmit(values: FormValues, editingItem: T | null): Promise<void>;
    onChangeStatus?(item: T): Promise<void>;
    detailPath?(item: T): string;
    secondaryFilter?: {
        label: string;
        value: string;
        options: Array<{ label: string; value: string }>;
        onChange(value: string): void;
    };
    statusOptions?: Array<{ label: string; value: "ALL" | S }>;
    extraHeaderActions?: ReactNode;
    totalItemsLabel?: string;
    filters?: CatalogFilter[];
}

export function CatalogResourceManagement<T extends RowBase, S extends string = string>({
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
    page,
    pageCount,
    totalItems,
    pageSize,
    renderCells,
    renderDetail,
    formFields,
    getInitialValues,
    onQueryChange,
    onStatusChange,
    onPageChange,
    onPageSizeChange,
    onRefresh,
    onSubmit,
    onChangeStatus,
    detailPath,
    secondaryFilter,
    statusOptions,
    extraHeaderActions,
    totalItemsLabel,
    filters
}: CatalogResourceManagementProps<T, S>) {
    const [selectedItem, setSelectedItem] = useState<T | null>(null);
    const [editingItem, setEditingItem] = useState<T | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [statusTarget, setStatusTarget] = useState<T | null>(null);
    const [toast, setToast] = useState("");
    const [actionError, setActionError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);

    const activeFilters: CatalogFilter[] = [];

    if (onStatusChange && status !== undefined) {
        activeFilters.push({
            label: "Trạng thái",
            value: String(status),
            options: (statusOptions ?? [
                { label: "Tất cả", value: "ALL" },
                { label: "Đang hoạt động", value: "ACTIVE" as any },
                { label: "Ngừng hoạt động", value: "INACTIVE" as any }
            ]).map(opt => ({ label: opt.label, value: String(opt.value) })),
            onChange: (val) => onStatusChange(val as any)
        });
    }

    if (secondaryFilter) {
        activeFilters.push({
            label: secondaryFilter.label,
            value: secondaryFilter.value,
            options: [
                { label: "Tất cả", value: "ALL" },
                ...secondaryFilter.options
            ],
            onChange: secondaryFilter.onChange
        });
    }

    if (filters) {
        activeFilters.push(...filters);
    }

    function notify(message: string) {
        setToast(message);
        window.setTimeout(() => setToast(""), 2500);
    }

    async function submitForm(values: FormValues) {
        setSubmitting(true);
        setActionError("");

        try {
            await onSubmit(values, editingItem);
            setShowForm(false);
            notify(
                editingItem
                    ? "Đã cập nhật dữ liệu thành công."
                    : "Đã tạo dữ liệu thành công."
            );
            onRefresh();
        } catch (submitError) {
            setActionError(getErrorMessage(submitError));
        } finally {
            setSubmitting(false);
        }
    }

    async function confirmStatusChange() {
        if (!statusTarget || !onChangeStatus) {
            return;
        }

        setSubmitting(true);
        setActionError("");

        try {
            await onChangeStatus(statusTarget);
            setStatusTarget(null);
            notify("Đã cập nhật trạng thái thành công.");
            onRefresh();
        } catch (statusError) {
            setActionError(getErrorMessage(statusError));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-5">
            {/* <Breadcrumbs items={breadcrumbs ?? ["Product Management", title]} /> */}

            <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div className="grid gap-1 min-w-[240px]">
                    <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">
                        {title}
                    </h1>
                    <p className="text-sm text-gray-500">
                        {description}
                    </p>
                </div>

                <div className="flex items-center gap-3 min-w-[120px] justify-end">
                    {extraHeaderActions}
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
                </div>
            </header>

            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                {searchPlaceholder ? (
                    <div className="border-b border-[#f3f5f7] pb-3">
                        {(() => {
                            const topFilter = activeFilters.find(f => f.label === "Danh mục" || f.label === "Sản phẩm");
                            return (
                                <div className="flex items-center gap-3 justify-between">
                                    <div className="relative flex-1">
                                        <SearchBox
                                            value={query}
                                            placeholder={searchPlaceholder}
                                            onChange={onQueryChange}
                                            className="w-full"
                                        />
                                    </div>
                                    {topFilter && (
                                        <div className="min-w-[180px] shrink-0">
                                            <select
                                                value={topFilter.value}
                                                onChange={(e) => topFilter.onChange(e.target.value)}
                                                className="h-10 w-full rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                            >
                                                <option value="ALL">{topFilter.label}</option>
                                                {topFilter.options
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
                                </div>
                            );
                        })()}
                    </div>
                ) : null}

                {(() => {
                    const remainingFilters = activeFilters.filter(f => f.label !== "Danh mục" && f.label !== "Sản phẩm");
                    if (remainingFilters.length === 0) return null;
                    return (
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-x-8 gap-y-[10px] pt-3">
                            {remainingFilters.map((filter, index) => (
                                <div
                                    key={filter.label + "-" + index}
                                    className="flex flex-wrap items-center gap-2"
                                >
                                    <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                                        {filter.label}:
                                    </span>
                                    <div className="flex flex-wrap gap-2 items-center">
                                        {filter.options.map((opt) => {
                                            const isActive = filter.value === opt.value;
                                            return (
                                                <button
                                                    key={opt.value}
                                                    onClick={() => filter.onChange(opt.value)}
                                                    type="button"
                                                    className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                                        isActive
                                                            ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                            : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                                    }`}
                                                >
                                                    {opt.label}
                                                </button>
                                            );
                                        })}
                                    </div>
                                </div>
                            ))}
                        </div>
                    );
                })()}
            </div>

            <div className="grid gap-4 grid-cols-1">
                <section className="flex flex-col min-h-[580px] min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <div className="flex-1">
                        {loading ? <ApiLoadingState /> : null}
                        {!loading && error ? (
                            <ApiErrorState
                                message={error}
                                onRetry={onRefresh}
                            />
                        ) : null}
                        {!loading && !error && items.length === 0 ? (
                            <div className="grid min-h-[500px] place-items-center p-8 text-center">
                                <div>
                                    <AlertTriangle
                                        className="mx-auto mb-3 text-pos-gold"
                                        size={34}
                                    />
                                    <h2 className="font-bold text-gray-500">
                                        Chưa có dữ liệu
                                    </h2>
                                    <p className="mt-1 text-sm text-gray-500">
                                        Thử thay đổi bộ lọc hoặc tạo dữ liệu mới.
                                    </p>
                                </div>
                            </div>
                        ) : null}
                        {!loading && !error && items.length > 0 ? (
                            <div className="overflow-x-auto min-h-[280px]">
                                <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                                    <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900">
                                        <tr>
                                            {columns.map((column) => (
                                                <th
                                                    key={column.key}
                                                    className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] first:pl-[18px]"
                                                >
                                                    {column.label}
                                                </th>
                                            ))}
                                            <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">
                                                Thao tác
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {items.map((item, idx) => {
                                            const isSelected = selectedItem?.id === item.id;
                                            const isEven = idx % 2 === 1;
                                            const rowBgClass = isSelected
                                                ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                                                : isEven
                                                    ? "bg-[#f2faf1]/50"
                                                    : "bg-white";

                                            return (
                                                <tr
                                                    key={item.id}
                                                    onClick={() => setSelectedItem(item)}
                                                    className={`cursor-pointer hover:bg-neutral-50 transition-all ${rowBgClass}`}
                                                >
                                                    {renderCells(item)}
                                                    <td className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 pr-[18px] relative ${activeActionMenuId === String(item.id) ? "z-30" : "z-20"}`} onClick={(e) => e.stopPropagation()}>
                                                        <div className="flex justify-end relative">
                                                            <button
                                                                type="button"
                                                                onClick={() => setActiveActionMenuId(activeActionMenuId === String(item.id) ? null : String(item.id))}
                                                                className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 text-gray-500 hover:text-gray-700 cursor-pointer"
                                                            >
                                                                <MoreHorizontal size={16} />
                                                            </button>

                                                            {activeActionMenuId === String(item.id) && (
                                                                <>
                                                                    <div className="fixed inset-0 z-30" onClick={() => setActiveActionMenuId(null)} />
                                                                    <div className={`absolute right-0 ${idx >= 2 && idx >= items.length - 2 ? "bottom-8 mb-1" : "top-8 mt-1"} w-44 rounded-xl border border-pos-border bg-white p-1.5 shadow-lg z-40 text-left`}>
                                                                        {detailPath ? (
                                                                            <Link
                                                                                to={detailPath(item)}
                                                                                onClick={() => setActiveActionMenuId(null)}
                                                                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                            >
                                                                                <Eye size={14} />
                                                                                Xem chi tiết
                                                                            </Link>
                                                                        ) : (
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => { setSelectedItem(item); setActiveActionMenuId(null); }}
                                                                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                            >
                                                                                <Eye size={14} />
                                                                                Xem chi tiết
                                                                            </button>
                                                                        )}
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => {
                                                                                setEditingItem(item);
                                                                                setActionError("");
                                                                                setShowForm(true);
                                                                                setActiveActionMenuId(null);
                                                                            }}
                                                                            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                        >
                                                                            <Pencil size={14} />
                                                                            Chỉnh sửa
                                                                        </button>
                                                                        {onChangeStatus && (
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => {
                                                                                    setActionError("");
                                                                                    setStatusTarget(item);
                                                                                    setActiveActionMenuId(null);
                                                                                }}
                                                                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                            >
                                                                                <ToggleLeft size={14} />
                                                                                Đổi trạng thái
                                                                            </button>
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
                            </div>
                        ) : null}
                    </div>
                    {!loading && !error ? (
                        <Pagination
                            page={page}
                            pageCount={pageCount}
                            totalItems={totalItems}
                            pageSize={pageSize}
                            onPageChange={onPageChange}
                            onPageSizeChange={onPageSizeChange}
                            totalItemsLabel={totalItemsLabel}
                        />
                    ) : null}
                </section>

                {selectedItem ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-5"
                        onClick={() => setSelectedItem(null)}
                        role="presentation"
                    >
                        <div
                            aria-modal="true"
                            className="w-full max-w-xl max-h-[85vh] overflow-y-auto"
                            onClick={(event) => event.stopPropagation()}
                            role="dialog"
                        >
                            <DetailPanel
                                title={`Chi tiết ${title.toLowerCase()}`}
                                onClose={() => setSelectedItem(null)}
                            >
                                {renderDetail(selectedItem)}
                            </DetailPanel>
                        </div>
                    </div>
                ) : null}
            </div>

            {showForm ? (
                <FormDialog
                    title={editingItem ? `Chỉnh sửa ${title}` : createLabel}
                    fields={
                        typeof formFields === "function"
                            ? formFields(editingItem)
                            : formFields
                    }
                    initialValues={
                        editingItem ? getInitialValues(editingItem) : {}
                    }
                    error={actionError}
                    submitting={submitting}
                    submitLabel={editingItem ? "Lưu thay đổi" : "Tạo mới"}
                    onClose={() => setShowForm(false)}
                    onSubmit={submitForm}
                />
            ) : null}

            {statusTarget ? (
                <ConfirmDialog
                    error={actionError}
                    submitting={submitting}
                    onCancel={() => setStatusTarget(null)}
                    onConfirm={confirmStatusChange}
                />
            ) : null}

            {toast ? (
                <div className="fixed bottom-6 right-6 z-50 rounded-xl bg-hvt-green-900 px-5 py-3 text-sm font-bold text-white shadow-pos-panel">
                    {toast}
                </div>
            ) : null}
        </section>
    );
}

function getErrorMessage(error: unknown) {
    return error instanceof Error
        ? error.message
        : "Có lỗi xảy ra khi xử lý yêu cầu.";
}
