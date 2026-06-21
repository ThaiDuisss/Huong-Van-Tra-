import { X } from "lucide-react";

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
