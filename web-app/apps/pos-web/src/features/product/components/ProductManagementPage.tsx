import {
    AlertTriangle,
    Eye,
    Pencil,
    Plus,
    RefreshCw,
    ToggleLeft,
    X
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
import type {
    CatalogStatus,
    EntityId
} from "../types/product.types";
import { ApiErrorState, ApiLoadingState } from "./ApiState";
import { CatalogStatusBadge } from "./CatalogStatusBadge";

interface RowBase {
    id: EntityId;
    status: CatalogStatus;
}

interface Column {
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

interface ProductManagementPageProps<T extends RowBase> {
    title: string;
    description: string;
    createLabel: string;
    searchPlaceholder?: string;
    columns: Column[];
    items: T[];
    loading: boolean;
    error: string;
    query: string;
    status: "ALL" | CatalogStatus;
    page: number;
    pageCount: number;
    totalItems: number;
    pageSize: number;
    renderCells(item: T): ReactNode;
    renderDetail(item: T): ReactNode;
    formFields: FormField[] | ((editingItem: T | null) => FormField[]);
    getInitialValues(item: T): Record<string, string>;
    onQueryChange(value: string): void;
    onStatusChange(value: "ALL" | CatalogStatus): void;
    onPageChange(page: number): void;
    onPageSizeChange(pageSize: number): void;
    onRefresh(): void;
    onSubmit(values: FormValues, editingItem: T | null): Promise<void>;
    onChangeStatus(item: T): Promise<void>;
    detailPath?(item: T): string;
    secondaryFilter?: {
        label: string;
        value: string;
        options: Array<{ label: string; value: string }>;
        onChange(value: string): void;
    };
}

export function ProductManagementPage<T extends RowBase>({
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
    secondaryFilter
}: ProductManagementPageProps<T>) {
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
        if (!statusTarget) {
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
            <Breadcrumbs items={["Product Management", title]} />

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

            <div className="grid min-h-[650px] grid-cols-[250px_minmax(0,1fr)_340px] gap-4">
                <FilterPanel>
                    <SelectField
                        label="Trạng thái"
                        value={status}
                        options={[
                            { label: "Tất cả", value: "ALL" },
                            { label: "Đang hoạt động", value: "ACTIVE" },
                            { label: "Ngừng hoạt động", value: "INACTIVE" }
                        ]}
                        onChange={(value) =>
                            onStatusChange(
                                value as "ALL" | CatalogStatus
                            )
                        }
                    />
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
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-sm font-bold text-pos-ink"
                    >
                        <RefreshCw size={15} aria-hidden="true" />
                        Tải lại dữ liệu
                    </button>
                </FilterPanel>

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
                                <thead className="bg-neutral-50 text-xs font-bold text-pos-muted">
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

                {selectedItem ? (
                    <DetailPanel
                        title={`Chi tiết ${title.toLowerCase()}`}
                        onClose={() => setSelectedItem(null)}
                    >
                        {renderDetail(selectedItem)}
                    </DetailPanel>
                ) : (
                    <aside className="grid place-items-center rounded-2xl border border-dashed border-pos-border p-6 text-center text-sm text-pos-muted">
                        Chọn một dòng để xem thông tin chi tiết.
                    </aside>
                )}
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

export function TableCell({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <td
            className={`border-b border-pos-border px-4 py-4 ${className}`}
        >
            {children}
        </td>
    );
}

export function DetailList({
    items
}: {
    items: Array<{ label: string; value: ReactNode }>;
}) {
    return (
        <dl className="grid gap-4">
            {items.map((item) => (
                <div
                    key={item.label}
                    className="border-b border-pos-border pb-3 last:border-0"
                >
                    <dt className="text-xs font-semibold uppercase tracking-wide text-pos-muted">
                        {item.label}
                    </dt>
                    <dd className="mt-1 font-semibold text-pos-ink">
                        {item.value}
                    </dd>
                </div>
            ))}
        </dl>
    );
}

function SelectField({
    label,
    value,
    options,
    onChange
}: {
    label: string;
    value: string;
    options: Array<{ label: string; value: string }>;
    onChange(value: string): void;
}) {
    return (
        <label className="grid gap-2 text-sm font-bold text-pos-ink">
            {label}
            <select
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
                className="h-11 rounded-xl border border-pos-border bg-white px-3 font-medium"
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}

function IconAction({
    label,
    children,
    onClick
}: {
    label: string;
    children: ReactNode;
    onClick(): void;
}) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            onClick={(event) => {
                event.stopPropagation();
                onClick();
            }}
            className="grid size-8 place-items-center rounded-lg hover:bg-white"
        >
            {children}
        </button>
    );
}

function FormDialog({
    title,
    fields,
    initialValues,
    error,
    submitting,
    submitLabel,
    onClose,
    onSubmit
}: {
    title: string;
    fields: FormField[];
    initialValues: Record<string, string>;
    error: string;
    submitting: boolean;
    submitLabel: string;
    onClose(): void;
    onSubmit(values: FormValues): void;
}) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-6">
            <form
                onSubmit={(event) => {
                    event.preventDefault();
                    const formData = new FormData(event.currentTarget);
                    const values: FormValues = {};

                    formData.forEach((value, key) => {
                        values[key] = value;
                    });
                    onSubmit(values);
                }}
                className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-3xl bg-white p-6 shadow-pos-panel"
            >
                <header className="mb-5 flex items-center justify-between">
                    <h2 className="text-2xl font-bold text-pos-ink">
                        {title}
                    </h2>
                    <button
                        type="button"
                        onClick={onClose}
                        className="grid size-9 place-items-center rounded-lg hover:bg-neutral-100"
                        aria-label="Đóng biểu mẫu"
                    >
                        <X size={20} />
                    </button>
                </header>
                <div className="grid gap-4">
                    {fields.map((field) => (
                        <label
                            key={field.name}
                            className="grid gap-2 text-sm font-bold text-pos-ink"
                        >
                            {field.label}
                            {field.type === "textarea" ? (
                                <textarea
                                    name={field.name}
                                    rows={4}
                                    required={field.required}
                                    defaultValue={
                                        initialValues[field.name] ?? ""
                                    }
                                    className="rounded-xl border border-pos-border px-3 py-2 font-normal outline-none focus:border-hvt-green-700"
                                />
                            ) : field.type === "select" ? (
                                <select
                                    name={field.name}
                                    required={field.required}
                                    defaultValue={
                                        initialValues[field.name] ??
                                        field.options?.[0]?.value ??
                                        ""
                                    }
                                    className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"
                                >
                                    {field.options?.map((option) => (
                                        <option
                                            key={option.value}
                                            value={option.value}
                                        >
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                            ) : (
                                <input
                                    name={field.name}
                                    type={field.type ?? "text"}
                                    required={field.required}
                                    defaultValue={
                                        field.type === "file"
                                            ? undefined
                                            : initialValues[field.name] ?? ""
                                    }
                                    className="h-11 rounded-xl border border-pos-border px-3 font-normal outline-none focus:border-hvt-green-700"
                                />
                            )}
                        </label>
                    ))}
                </div>
                {error ? (
                    <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                        {error}
                    </p>
                ) : null}
                <footer className="mt-6 flex justify-end gap-3 border-t border-pos-border pt-5">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={submitting}
                        className="h-11 rounded-xl border border-pos-border px-5 text-sm font-bold"
                    >
                        Hủy
                    </button>
                    <button
                        type="submit"
                        disabled={submitting}
                        className="h-11 rounded-xl bg-hvt-green-900 px-5 text-sm font-bold text-white disabled:opacity-60"
                    >
                        {submitting ? "Đang lưu..." : submitLabel}
                    </button>
                </footer>
            </form>
        </div>
    );
}

function ConfirmDialog({
    error,
    submitting,
    onCancel,
    onConfirm
}: {
    error: string;
    submitting: boolean;
    onCancel(): void;
    onConfirm(): void;
}) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center bg-black/35 p-6">
            <section className="w-full max-w-md rounded-3xl bg-white p-6 shadow-pos-panel">
                <h2 className="text-xl font-bold text-pos-ink">
                    Xác nhận thay đổi trạng thái
                </h2>
                <p className="mt-2 text-sm text-pos-muted">
                    Thao tác này sẽ thay đổi khả năng sử dụng dữ liệu trong hệ
                    thống.
                </p>
                {error ? (
                    <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                        {error}
                    </p>
                ) : null}
                <div className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={submitting}
                        className="h-10 rounded-xl border border-pos-border px-4 text-sm font-bold"
                    >
                        Hủy
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={submitting}
                        className="h-10 rounded-xl bg-hvt-green-900 px-4 text-sm font-bold text-white disabled:opacity-60"
                    >
                        {submitting ? "Đang cập nhật..." : "Xác nhận"}
                    </button>
                </div>
            </section>
        </div>
    );
}

function getErrorMessage(error: unknown) {
    return error instanceof Error
        ? error.message
        : "Có lỗi xảy ra khi xử lý yêu cầu.";
}

export { CatalogStatusBadge };
