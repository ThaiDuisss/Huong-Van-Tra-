import {
    AlertTriangle,
    Eye,
    Pencil,
    Plus,
    RefreshCw,
    ToggleLeft
} from "lucide-react";
import { type ReactNode, useState } from "react";
import { Link } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { FilterPanel } from "../../manufacturing/components/shared/FilterPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import { SearchBox } from "../../manufacturing/components/shared/SearchBox";
import { ApiErrorState, ApiLoadingState } from "./ApiState";
import { CatalogStatusBadge } from "./CatalogStatusBadge";
import { ConfirmDialog } from "./ConfirmDialog";
import { FormDialog } from "./FormDialog";
import { IconAction } from "./IconAction";
import { SelectField } from "./SelectField";

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
    breadcrumbs?: string[];
    statusOptions?: Array<{ label: string; value: "ALL" | S }>;
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
    breadcrumbs,
    statusOptions
}: CatalogResourceManagementProps<T, S>) {
    const [selectedItem, setSelectedItem] = useState<T | null>(null);
    const [editingItem, setEditingItem] = useState<T | null>(null);
    const [showForm, setShowForm] = useState(false);
    const [statusTarget, setStatusTarget] = useState<T | null>(null);
    const [toast, setToast] = useState("");
    const [actionError, setActionError] = useState("");
    const [submitting, setSubmitting] = useState(false);

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
            <Breadcrumbs items={breadcrumbs ?? ["Product Management", title]} />

            <PageHeader
                title={title}
                description={description}
                search={
                    searchPlaceholder ? (
                        <SearchBox
                            value={query}
                            placeholder={searchPlaceholder}
                            onChange={onQueryChange}
                        />
                    ) : undefined
                }
                actions={
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
                }
            />

            <div className="grid min-h-[650px] grid-cols-1 gap-4 xl:grid-cols-[240px_minmax(0,1fr)] min-[1800px]:grid-cols-[250px_minmax(0,1fr)_340px]">
                <div className="min-w-0">
                    <FilterPanel>
                        {onStatusChange && status !== undefined ? (
                            <SelectField
                                label="Trạng thái"
                                value={status}
                                options={
                                    statusOptions ?? [
                                        { label: "Tất cả", value: "ALL" },
                                        { label: "Đang hoạt động", value: "ACTIVE" as any },
                                        {
                                            label: "Ngừng hoạt động",
                                            value: "INACTIVE" as any
                                        }
                                    ]
                                }
                                onChange={(value) =>
                                    onStatusChange(
                                        value as "ALL" | S
                                    )
                                }
                            />
                        ) : null}
                        {secondaryFilter ? (
                            <SelectField
                                label={secondaryFilter.label}
                                value={secondaryFilter.value}
                                options={[
                                    { label: "Tất cả", value: "ALL" },
                                    ...secondaryFilter.options
                                ]}
                                onChange={secondaryFilter.onChange}
                            />
                        ) : null}
                        <button
                            type="button"
                            onClick={onRefresh}
                            className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-sm font-bold text-gray-600"
                        >
                            <RefreshCw size={15} aria-hidden="true" />
                            Tải lại dữ liệu
                        </button>
                    </FilterPanel>
                </div>

                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {loading ? <ApiLoadingState /> : null}
                    {!loading && error ? (
                        <ApiErrorState
                            message={error}
                            onRetry={onRefresh}
                        />
                    ) : null}
                    {!loading && !error && items.length === 0 ? (
                        <div className="grid min-h-[560px] place-items-center p-8 text-center">
                            <div>
                                <AlertTriangle
                                    className="mx-auto mb-3 text-pos-gold"
                                    size={34}
                                />
                                <h2 className="font-bold text-pos-ink">
                                    Chưa có dữ liệu
                                </h2>
                                <p className="mt-1 text-sm text-pos-muted">
                                    Thử thay đổi bộ lọc hoặc tạo dữ liệu mới.
                                </p>
                            </div>
                        </div>
                    ) : null}
                    {!loading && !error && items.length > 0 ? (
                        <div className="overflow-x-auto">
                            <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                                <thead className="bg-neutral-50 text-xs font-bold text-gray-600">
                                    <tr>
                                        {columns.map((column) => (
                                            <th
                                                key={column.key}
                                                className="px-4 py-3"
                                            >
                                                {column.label}
                                            </th>
                                        ))}
                                        <th className="px-4 py-3 text-right">
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => (
                                        <tr
                                            key={item.id}
                                            onClick={() =>
                                                setSelectedItem(item)
                                            }
                                            className={`cursor-pointer hover:bg-neutral-50 ${
                                                selectedItem?.id === item.id
                                                    ? "bg-hvt-green-50/70"
                                                    : ""
                                            }`}
                                        >
                                            {renderCells(item)}
                                            <td className="border-b border-pos-border px-4 py-4">
                                                <div className="flex justify-end gap-1">
                                                    {detailPath ? (
                                                        <Link
                                                            to={detailPath(
                                                                item
                                                            )}
                                                            className="grid size-8 place-items-center rounded-lg hover:bg-white"
                                                            aria-label="Xem chi tiết"
                                                        >
                                                            <Eye size={16} />
                                                        </Link>
                                                    ) : (
                                                        <IconAction
                                                            label="Xem chi tiết"
                                                            onClick={() =>
                                                                setSelectedItem(
                                                                    item
                                                                )
                                                            }
                                                        >
                                                            <Eye size={16} />
                                                        </IconAction>
                                                    )}
                                                    <IconAction
                                                        label="Chỉnh sửa"
                                                        onClick={() => {
                                                            setEditingItem(item);
                                                            setActionError("");
                                                            setShowForm(true);
                                                        }}
                                                    >
                                                        <Pencil size={16} />
                                                    </IconAction>
                                                    {onChangeStatus ? (
                                                        <IconAction
                                                            label="Đổi trạng thái"
                                                            onClick={() => {
                                                                setActionError("");
                                                                setStatusTarget(
                                                                    item
                                                                );
                                                            }}
                                                        >
                                                            <ToggleLeft
                                                                size={17}
                                                            />
                                                        </IconAction>
                                                    ) : null}
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    ) : null}
                    {!loading && !error ? (
                        <Pagination
                            page={page}
                            pageCount={pageCount}
                            totalItems={totalItems}
                            pageSize={pageSize}
                            onPageChange={onPageChange}
                            onPageSizeChange={onPageSizeChange}
                        />
                    ) : null}
                </section>

                <div className="hidden min-h-0 min-[1800px]:block [&>aside]:h-full">
                    {selectedItem ? (
                        <DetailPanel
                            title={`Chi tiết ${title.toLowerCase()}`}
                            onClose={() => setSelectedItem(null)}
                        >
                            {renderDetail(selectedItem)}
                        </DetailPanel>
                    ) : (
                        <aside className="grid h-full place-items-center rounded-2xl border border-dashed border-pos-border p-6 text-center text-sm text-pos-muted">
                            Chọn một dòng để xem thông tin chi tiết.
                        </aside>
                    )}
                </div>
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
