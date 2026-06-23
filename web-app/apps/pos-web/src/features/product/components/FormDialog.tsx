import { X } from "lucide-react";
import { useState } from "react";

import type { FormField, FormValues } from "./CatalogResourceManagement";

interface FormDialogProps {
    title: string;
    fields: FormField[];
    initialValues: Record<string, string>;
    error: string;
    submitting: boolean;
    submitLabel: string;
    onClose(): void;
    onSubmit(values: FormValues): void;
}

export function FormDialog({
    title,
    fields,
    initialValues,
    error,
    submitting,
    submitLabel,
    onClose,
    onSubmit
}: FormDialogProps) {
    const [filePreviews, setFilePreviews] = useState<Record<string, string>>({});
    const [deletedFields, setDeletedFields] = useState<Record<string, boolean>>({});

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

                    // Handle deleted files
                    Object.keys(deletedFields).forEach(key => {
                        if (deletedFields[key]) {
                            values[key] = "";
                        }
                    });

                    onSubmit(values);
                }}
                className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-3xl bg-white p-6 shadow-pos-panel"
            >
                <header className="mb-5 flex items-center justify-between">
                    <h2 className="text-2xl font-bold text-gray-600">
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
                            ) : field.type === "file" ? (
                                <div className="grid gap-2">
                                    {filePreviews[field.name] || (initialValues[field.name] && !deletedFields[field.name]) ? (
                                        <div className="flex items-center gap-3 rounded-xl border border-pos-border p-3">
                                            <div className="relative size-16 overflow-hidden rounded-lg">
                                                <img
                                                    src={filePreviews[field.name] || initialValues[field.name]}
                                                    alt="Preview"
                                                    className="h-full w-full object-cover"
                                                />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs font-semibold text-pos-ink truncate">
                                                    {filePreviews[field.name] ? "Ảnh mới chọn" : "Ảnh hiện tại"}
                                                </p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setFilePreviews(prev => ({ ...prev, [field.name]: "" }));
                                                    setDeletedFields(prev => ({ ...prev, [field.name]: true }));
                                                }}
                                                className="grid size-8 place-items-center rounded-lg bg-red-50 text-red-600 hover:bg-red-100 transition-colors"
                                                aria-label="Xóa ảnh"
                                            >
                                                <X size={16} />
                                            </button>
                                        </div>
                                    ) : (
                                        <div className="mt-1 flex justify-center rounded-xl border-2 border-dashed border-pos-border px-6 pt-5 pb-6 hover:border-hvt-green-700 transition-colors cursor-pointer relative">
                                            <div className="space-y-1 text-center">
                                                <svg
                                                    className="mx-auto h-10 w-10 text-gray-400"
                                                    stroke="currentColor"
                                                    fill="none"
                                                    viewBox="0 0 48 48"
                                                    aria-hidden="true"
                                                >
                                                    <path
                                                        d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                                                        strokeWidth={2}
                                                        strokeLinecap="round"
                                                        strokeLinejoin="round"
                                                    />
                                                </svg>
                                                <div className="flex text-sm text-gray-600 justify-center">
                                                    <span className="relative cursor-pointer rounded-md font-semibold text-hvt-green-800 hover:text-hvt-green-900 focus-within:outline-none">
                                                        <span>Chọn ảnh</span>
                                                        <input
                                                            name={field.name}
                                                            type="file"
                                                            accept="image/*"
                                                            className="sr-only"
                                                            required={field.required && !initialValues[field.name]}
                                                            onChange={(e) => {
                                                                const file = e.target.files?.[0];
                                                                if (file) {
                                                                    const preview = URL.createObjectURL(file);
                                                                    setFilePreviews(prev => ({ ...prev, [field.name]: preview }));
                                                                    setDeletedFields(prev => ({ ...prev, [field.name]: false }));
                                                                }
                                                            }}
                                                        />
                                                    </span>
                                                    <p className="pl-1">hoặc kéo thả</p>
                                                </div>
                                                <p className="text-xs text-gray-500">Tối đa 5MB</p>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <input
                                    name={field.name}
                                    type={field.type ?? "text"}
                                    required={field.required}
                                    defaultValue={initialValues[field.name] ?? ""}
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
