import { X } from "lucide-react";
import { useState, useEffect } from "react";

import type { FormField, FormValues } from "./CatalogResourceManagement";
import { fileApi } from "../../../api/file.api";

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
    const [uploadingField, setUploadingField] = useState<string | null>(null);
    const [formValues, setFormValues] = useState<Record<string, string>>(() => {
        const init: Record<string, string> = {};
        fields.forEach(f => {
            init[f.name] = initialValues[f.name] ?? (f.type === "select" ? (f.options?.[0]?.value ?? "") : "");
        });

        // Prefill variantName with selected product name when creating a new variant
        if (!initialValues.variantName && init.productId) {
            const productField = fields.find(f => f.name === "productId");
            const selectedOpt = productField?.options?.find(o => o.value === init.productId);
            if (selectedOpt) {
                init.variantName = selectedOpt.label + " ";
            }
        }
        return init;
    });

    useEffect(() => {
        setFormValues(prev => {
            let updated = { ...prev };
            let changed = false;
            fields.forEach(f => {
                if (f.type === "select" && !prev[f.name] && f.options && f.options.length > 0) {
                    const defaultValue = f.options[0].value;
                    updated[f.name] = defaultValue;
                    changed = true;

                    if (f.name === "productId" && !initialValues.variantName && !prev.variantName) {
                        updated.variantName = f.options[0].label + " ";
                    }
                }
            });
            return changed ? updated : prev;
        });
    }, [fields, initialValues.variantName]);

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
                className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-3xl bg-white p-6 shadow-pos-panel"
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
                <div className="grid grid-cols-2 gap-4">
                    {fields.map((field) => {
                        const isFieldHidden = typeof field.hidden === "function"
                            ? field.hidden(formValues)
                            : !!field.hidden;
                        if (isFieldHidden) {
                            return null;
                        }
                        const isFieldDisabled = typeof field.disabled === "function"
                            ? field.disabled(formValues)
                            : !!field.disabled;
                        const isFieldRequired = typeof field.required === "function"
                            ? field.required(formValues)
                            : !!field.required;
                        if (field.type === "checkbox") {
                            return (
                                <label
                                    key={field.name}
                                    className={`flex items-center gap-3 py-1 cursor-pointer text-sm font-bold text-gray-500 ${isFieldDisabled ? "opacity-60 cursor-not-allowed" : ""} col-span-2`}
                                >
                                    <input
                                        type="checkbox"
                                        name={field.name}
                                        checked={formValues[field.name] === "true"}
                                        disabled={isFieldDisabled}
                                        onChange={(e) => {
                                            const checked = e.target.checked;
                                            setFormValues(prev => {
                                                const updated = {
                                                    ...prev,
                                                    [field.name]: checked ? "true" : "false"
                                                };
                                                if (field.name === "isBaseUnit") {
                                                    if (checked) {
                                                        updated.baseUnitId = "";
                                                        updated.conversionRate = "1";
                                                    }
                                                }
                                                return updated;
                                            });
                                        }}
                                        className="size-5 rounded border-pos-border text-[#07883a] focus:ring-[#07883a] focus:ring-offset-0 cursor-pointer"
                                    />
                                    <span>
                                        {field.label}
                                        {isFieldRequired && <span className="text-red-500 ml-1">*</span>}
                                    </span>
                                </label>
                            );
                        }

                        return (
                            <div
                                key={field.name}
                                className={`grid gap-2 text-sm font-bold text-gray-500 ${isFieldDisabled ? "opacity-60 cursor-not-allowed" : ""} ${
                                    field.type === "textarea" || field.type === "file" ? "col-span-2" : ""
                                }`}
                            >
                                <span>
                                    {field.label}
                                    {isFieldRequired && <span className="text-red-500 ml-1">*</span>}
                                </span>
                                {field.render ? (
                                    <>
                                        <input type="hidden" name={field.name} value={formValues[field.name] || ""} />
                                        {field.render(formValues[field.name] || "", (val) => {
                                            setFormValues(prev => {
                                                const updated = { ...prev, [field.name]: val };
                                                if (field.name === "productId") {
                                                    const oldProductOpt = field.options?.find(o => o.value === prev.productId);
                                                    const newProductOpt = field.options?.find(o => o.value === val);
                                                    if (newProductOpt) {
                                                        const oldPrefix = oldProductOpt ? oldProductOpt.label : "";
                                                        const currentName = prev.variantName || "";
                                                        if (oldPrefix && currentName.startsWith(oldPrefix)) {
                                                            updated.variantName = currentName.replace(oldPrefix, newProductOpt.label);
                                                        } else {
                                                            updated.variantName = newProductOpt.label + " " + currentName;
                                                        }
                                                    }
                                                }
                                                return updated;
                                            });
                                        })}
                                    </>
                                ) : field.type === "textarea" ? (
                                    <textarea
                                        name={field.name}
                                        rows={4}
                                        required={isFieldRequired && !isFieldDisabled}
                                        disabled={isFieldDisabled}
                                        placeholder={field.placeholder}
                                        value={formValues[field.name]}
                                        onChange={(e) => {
                                            const val = e.target.value;
                                            setFormValues(prev => ({
                                                ...prev,
                                                [field.name]: val
                                            }));
                                        }}
                                        className="rounded-xl border border-pos-border px-3 py-2 font-normal outline-none focus:border-hvt-green-700 disabled:bg-neutral-50 disabled:cursor-not-allowed"
                                    />
                                ) : field.type === "select" ? (
                                    <select
                                        name={field.name}
                                        required={isFieldRequired && !isFieldDisabled}
                                        disabled={isFieldDisabled}
                                        value={formValues[field.name]}
                                        onChange={(e) => {
                                            const val = e.target.value;
                                            setFormValues(prev => {
                                                const updated = { ...prev, [field.name]: val };
                                                if (field.name === "productId") {
                                                    const oldProductOpt = field.options?.find(o => o.value === prev.productId);
                                                    const newProductOpt = field.options?.find(o => o.value === val);
                                                    if (newProductOpt) {
                                                        const oldPrefix = oldProductOpt ? oldProductOpt.label : "";
                                                        const currentName = prev.variantName || "";
                                                        if (oldPrefix && currentName.startsWith(oldPrefix)) {
                                                            updated.variantName = currentName.replace(oldPrefix, newProductOpt.label);
                                                        } else {
                                                            updated.variantName = newProductOpt.label + " " + currentName;
                                                        }
                                                    }
                                                }
                                                if (field.name === "baseUnitId" && !val) {
                                                    updated.conversionRate = "1";
                                                }
                                                return updated;
                                            });
                                        }}
                                        className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal disabled:bg-neutral-50 disabled:cursor-not-allowed"
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
                                    <input
                                        type="hidden"
                                        name={field.name}
                                        value={formValues[field.name] || ""}
                                    />
                                    {uploadingField === field.name ? (
                                        <div className="flex items-center justify-center gap-3 rounded-xl border border-pos-border p-6 h-[88px] bg-neutral-50/50">
                                            <div className="size-5 animate-spin rounded-full border-2 border-hvt-green-700 border-t-transparent" />
                                            <span className="text-sm font-semibold text-gray-500">Đang tải ảnh lên...</span>
                                        </div>
                                    ) : filePreviews[field.name] || (initialValues[field.name] && !deletedFields[field.name]) ? (
                                        <div className="flex items-center gap-3 rounded-xl border border-pos-border p-3">
                                            <div className="relative size-16 overflow-hidden rounded-lg">
                                                <img
                                                    src={filePreviews[field.name] || initialValues[field.name]}
                                                    alt="Preview"
                                                    className="h-full w-full object-cover"
                                                />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs font-semibold text-gray-500 truncate">
                                                    {filePreviews[field.name] ? "Ảnh mới chọn" : "Ảnh hiện tại"}
                                                </p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setFilePreviews(prev => ({ ...prev, [field.name]: "" }));
                                                    setDeletedFields(prev => ({ ...prev, [field.name]: true }));
                                                    setFormValues(prev => ({ ...prev, [field.name]: "", [field.name + "Id"]: "" }));
                                                }}
                                                className="grid size-8 place-items-center rounded-lg bg-red-50 text-red-600 hover:bg-red-100 transition-colors"
                                                aria-label="Xóa ảnh"
                                            >
                                                <X size={16} />
                                            </button>
                                        </div>
                                    ) : (
                                        <label className="mt-1 flex justify-center rounded-xl border-2 border-dashed border-pos-border px-6 pt-5 pb-6 hover:border-hvt-green-700 transition-colors cursor-pointer relative group">
                                            <input
                                                type="file"
                                                accept="image/*"
                                                className="sr-only"
                                                required={field.required && !initialValues[field.name]}
                                                onChange={async (e) => {
                                                    const file = e.target.files?.[0];
                                                    if (file) {
                                                        const objectUrl = URL.createObjectURL(file);
                                                        setFilePreviews(prev => ({ ...prev, [field.name]: objectUrl }));
                                                        setDeletedFields(prev => ({ ...prev, [field.name]: false }));
                                                        setUploadingField(field.name);
                                                        try {
                                                            const uploadRes = await fileApi.uploadFile(file, {
                                                                purpose: (field.purpose as any) ?? "PRODUCT_IMAGE"
                                                            });
                                                            const url = uploadRes.publicUrl || "";
                                                            if (url) {
                                                                setFilePreviews(prev => ({ ...prev, [field.name]: url }));
                                                            }
                                                            setFormValues(prev => ({
                                                                ...prev,
                                                                [field.name]: url || objectUrl,
                                                                [field.name + "Id"]: uploadRes.fileId
                                                            }));
                                                        } catch (err) {
                                                            console.error("Upload error:", err);
                                                            alert("Lỗi tải lên hình ảnh!");
                                                            setFilePreviews(prev => ({ ...prev, [field.name]: "" }));
                                                        } finally {
                                                            setUploadingField(null);
                                                        }
                                                    }
                                                }}
                                            />
                                            <div className="space-y-1 text-center pointer-events-none">
                                                <svg
                                                    className="mx-auto h-10 w-10 text-gray-400 group-hover:text-hvt-green-700 transition-colors"
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
                                                    <span className="relative rounded-md font-semibold text-hvt-green-800 group-hover:text-hvt-green-900 focus-within:outline-none">
                                                        <span>Chọn ảnh</span>
                                                    </span>
                                                    <p className="pl-1">hoặc kéo thả</p>
                                                </div>
                                                <p className="text-xs text-gray-500">Tối đa 5MB</p>
                                            </div>
                                        </label>
                                    )}
                                </div>
                            ) : (
                                <input
                                    name={field.name}
                                    type={field.type ?? "text"}
                                    required={isFieldRequired && !isFieldDisabled}
                                    disabled={isFieldDisabled}
                                    placeholder={field.placeholder}
                                    value={formValues[field.name]}
                                    onChange={(e) => {
                                        const val = e.target.value;
                                        setFormValues(prev => ({
                                            ...prev,
                                            [field.name]: val
                                        }));
                                    }}
                                    className="h-11 rounded-xl border border-pos-border px-3 font-normal outline-none focus:border-hvt-green-700 disabled:bg-neutral-50 disabled:cursor-not-allowed"
                                />
                            )}
                        </div>
                        );
                    })}
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
                        className="h-11 rounded-lg border border-[#dfe6ee] bg-white text-[#172317] px-5 text-sm font-black transition-all cursor-pointer hover:bg-neutral-50"
                    >
                        Hủy
                    </button>
                    <button
                        type="submit"
                        disabled={submitting || uploadingField !== null}
                        className="h-11 rounded-lg border-0 bg-[#07883a] text-white px-5 text-sm font-black shadow-[0_6px_16px_rgba(7,136,58,0.18)] disabled:opacity-60 transition-all cursor-pointer hover:opacity-90"
                    >
                        {submitting ? "Đang lưu..." : (uploadingField ? "Đang tải ảnh..." : submitLabel)}
                    </button>
                </footer>
            </form>
        </div>
    );
}
