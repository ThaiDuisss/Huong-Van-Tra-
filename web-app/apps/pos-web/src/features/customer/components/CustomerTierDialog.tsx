import { AlertCircle } from "lucide-react";
import { type FormEvent, useEffect, useMemo, useState } from "react";

import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import type {
    CustomerTierMock,
    CustomerTierStatus
} from "./TierCard";

export type CustomerTierFormValues = Omit<CustomerTierMock, "id" | "customerCount">;

interface CustomerTierDialogProps {
    tier?: CustomerTierMock | null;
    onCancel: () => void;
    onSubmit: (values: CustomerTierFormValues) => void;
}

interface FormState {
    name: string;
    code: string;
    colorCode: string;
    status: CustomerTierStatus;
    minSpending: string;
    minOrderCount: string;
    discountRate: string;
}

type FormErrors = Partial<Record<keyof FormState, string>>;

const DEFAULT_FORM_STATE: FormState = {
    name: "",
    code: "",
    colorCode: "#159734",
    status: "ACTIVE",
    minSpending: "0",
    minOrderCount: "0",
    discountRate: "0"
};

export function CustomerTierDialog({
    tier,
    onCancel,
    onSubmit
}: CustomerTierDialogProps) {
    const [form, setForm] = useState<FormState>(() => toFormState(tier));
    const [errors, setErrors] = useState<FormErrors>({});
    const editing = Boolean(tier);
    const previewTier = useMemo(
        () => toPreviewTier(form, tier?.customerCount ?? 0),
        [form, tier?.customerCount]
    );

    useEffect(() => {
        setForm(toFormState(tier));
        setErrors({});
    }, [tier]);

    function updateField<Key extends keyof FormState>(
        key: Key,
        value: FormState[Key]
    ) {
        setForm((current) => ({
            ...current,
            [key]: key === "code" ? value.toUpperCase() : value
        }));
        setErrors((current) => ({ ...current, [key]: undefined }));
    }

    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const nextErrors = validateForm(form);
        setErrors(nextErrors);

        if (Object.keys(nextErrors).length > 0) {
            return;
        }

        onSubmit({
            name: form.name.trim(),
            code: form.code.trim().toUpperCase(),
            colorCode: form.colorCode,
            status: form.status,
            minSpending: Number(form.minSpending),
            minOrderCount: Number(form.minOrderCount),
            discountRate: Number(form.discountRate)
        });
    }

    return (
        <DialogContent className="w-[min(800px,calc(100vw-32px))] [margin-block-end:auto] [margin-block-start:7vh]">
            <form onSubmit={submit}>
                <DialogHeader>
                    <DialogTitle>
                        {editing
                            ? "Chỉnh sửa hạng khách hàng"
                            : "Tạo hạng khách hàng"}
                    </DialogTitle>
                    <DialogDescription>
                        Thiết lập điều kiện xét hạng và quyền lợi áp dụng.
                    </DialogDescription>
                </DialogHeader>

                <div className="grid max-h-[calc(86vh-170px)] gap-5 overflow-y-auto px-6 py-5">
                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="grid gap-4">
                            <TextField
                                error={errors.name}
                                label="Tên hạng"
                                onChange={(value) => updateField("name", value)}
                                placeholder="Ví dụ: Gold"
                                value={form.name}
                            />
                            <TextField
                                error={errors.code}
                                label="Mã hạng"
                                onChange={(value) => updateField("code", value)}
                                placeholder="Ví dụ: GOLD"
                                value={form.code}
                            />
                            <ColorField
                                label="Màu nhận diện"
                                onChange={(value) => updateField("colorCode", value)}
                                value={form.colorCode}
                            />
                            <StatusField
                                onChange={(value) => updateField("status", value)}
                                value={form.status}
                            />
                        </div>

                        <div className="grid content-start gap-4">
                            <TextField
                                error={errors.minSpending}
                                inputMode="numeric"
                                label="Chi tiêu tối thiểu"
                                onChange={(value) => updateField("minSpending", value)}
                                placeholder="0"
                                type="number"
                                value={form.minSpending}
                            />
                            <TextField
                                error={errors.minOrderCount}
                                inputMode="numeric"
                                label="Số đơn tối thiểu"
                                onChange={(value) => updateField("minOrderCount", value)}
                                placeholder="0"
                                type="number"
                                value={form.minOrderCount}
                            />
                            <TextField
                                error={errors.discountRate}
                                inputMode="numeric"
                                label="Mức giảm giá (%)"
                                onChange={(value) => updateField("discountRate", value)}
                                placeholder="0"
                                type="number"
                                value={form.discountRate}
                            />
                        </div>
                    </div>

                    <PreviewTierCard tier={previewTier} />
                </div>

                <DialogFooter>
                    <button
                        className="inline-flex h-10 items-center justify-center rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50"
                        onClick={onCancel}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className="inline-flex h-10 items-center justify-center rounded-xl bg-[#159734] px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#12862d]"
                        type="submit"
                    >
                        {editing ? "Lưu thay đổi" : "Tạo hạng"}
                    </button>
                </DialogFooter>
            </form>
        </DialogContent>
    );
}

function TextField({
    error,
    inputMode,
    label,
    onChange,
    placeholder,
    type = "text",
    value
}: {
    error?: string;
    inputMode?: "numeric";
    label: string;
    onChange: (value: string) => void;
    placeholder: string;
    type?: "number" | "text";
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-sm font-bold text-slate-700">
            <span>{label}</span>
            <input
                className={`h-11 rounded-xl border bg-white px-3 text-sm font-semibold text-slate-900 outline-none transition-colors placeholder:text-slate-400 focus:ring-2 ${
                    error
                        ? "border-red-300 focus:border-red-400 focus:ring-red-100"
                        : "border-slate-200 focus:border-emerald-500 focus:ring-emerald-100"
                }`}
                inputMode={inputMode}
                min={type === "number" ? 0 : undefined}
                onChange={(event) => onChange(event.currentTarget.value)}
                placeholder={placeholder}
                type={type}
                value={value}
            />
            {error ? <FieldError message={error} /> : null}
        </label>
    );
}

function ColorField({
    label,
    onChange,
    value
}: {
    label: string;
    onChange: (value: string) => void;
    value: string;
}) {
    return (
        <label className="grid gap-1.5 text-sm font-bold text-slate-700">
            <span>{label}</span>
            <span className="flex h-11 items-center gap-3 rounded-xl border border-slate-200 bg-white px-3">
                <input
                    aria-label={label}
                    className="size-7 cursor-pointer rounded-md border border-slate-200 bg-transparent p-0"
                    onChange={(event) => onChange(event.currentTarget.value)}
                    type="color"
                    value={value}
                />
                <input
                    className="min-w-0 flex-1 bg-transparent text-sm font-semibold uppercase text-slate-900 outline-none"
                    onChange={(event) => onChange(event.currentTarget.value)}
                    value={value}
                />
            </span>
        </label>
    );
}

function StatusField({
    onChange,
    value
}: {
    onChange: (value: CustomerTierStatus) => void;
    value: CustomerTierStatus;
}) {
    return (
        <fieldset className="grid gap-2 text-sm font-bold text-slate-700">
            <legend>Trạng thái</legend>
            <div className="grid grid-cols-2 gap-2">
                {STATUS_OPTIONS.map((option) => {
                    const active = value === option.value;

                    return (
                        <button
                            aria-pressed={active}
                            className={`h-11 rounded-xl border px-3 text-sm font-bold transition-colors ${
                                active
                                    ? "border-emerald-500 bg-emerald-50 text-emerald-700"
                                    : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                            }`}
                            key={option.value}
                            onClick={() => onChange(option.value)}
                            type="button"
                        >
                            {option.label}
                        </button>
                    );
                })}
            </div>
        </fieldset>
    );
}

function FieldError({ message }: { message: string }) {
    return (
        <span className="inline-flex items-center gap-1 text-xs font-bold text-red-600">
            <AlertCircle size={13} aria-hidden="true" />
            {message}
        </span>
    );
}

function PreviewTierCard({ tier }: { tier: CustomerTierMock }) {
    return (
        <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-black text-slate-800">
                Xem trước hạng khách hàng
            </h3>
            <article className="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <div
                    className="h-1 w-full"
                    style={{ backgroundColor: tier.colorCode }}
                />
                <div className="grid gap-3 p-4 sm:grid-cols-[1fr_auto] sm:items-center">
                    <div className="min-w-0">
                        <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                            {tier.code || "TIER_CODE"}
                        </p>
                        <h4 className="mt-1 truncate text-lg font-black text-slate-900">
                            {tier.name || "Tên hạng"}
                        </h4>
                    </div>
                    <span className="text-sm font-bold text-slate-600">
                        Từ {formatCurrency(tier.minSpending)} · Ít nhất {tier.minOrderCount.toLocaleString("vi-VN")} đơn · Giảm {tier.discountRate}%
                    </span>
                </div>
            </article>
        </section>
    );
}

const STATUS_OPTIONS: Array<{
    label: string;
    value: CustomerTierStatus;
}> = [
    { label: "Đang hoạt động", value: "ACTIVE" },
    { label: "Ngừng áp dụng", value: "INACTIVE" }
];

function toFormState(tier?: CustomerTierMock | null): FormState {
    if (!tier) return DEFAULT_FORM_STATE;

    return {
        name: tier.name,
        code: tier.code,
        colorCode: tier.colorCode,
        status: tier.status,
        minSpending: String(tier.minSpending),
        minOrderCount: String(tier.minOrderCount),
        discountRate: String(tier.discountRate)
    };
}

function toPreviewTier(form: FormState, customerCount: number): CustomerTierMock {
    return {
        id: "preview",
        name: form.name.trim(),
        code: form.code.trim().toUpperCase(),
        colorCode: form.colorCode || "#159734",
        status: form.status,
        minSpending: Number(form.minSpending) || 0,
        minOrderCount: Number(form.minOrderCount) || 0,
        discountRate: Number(form.discountRate) || 0,
        customerCount
    };
}

function validateForm(form: FormState): FormErrors {
    const errors: FormErrors = {};
    const minSpending = Number(form.minSpending);
    const minOrderCount = Number(form.minOrderCount);
    const discountRate = Number(form.discountRate);

    if (!form.name.trim()) {
        errors.name = "Tên hạng là bắt buộc.";
    }
    if (!form.code.trim()) {
        errors.code = "Mã hạng là bắt buộc.";
    }
    if (!Number.isFinite(minSpending) || minSpending < 0) {
        errors.minSpending = "Chi tiêu tối thiểu phải lớn hơn hoặc bằng 0.";
    }
    if (
        !Number.isInteger(minOrderCount) ||
        minOrderCount < 0
    ) {
        errors.minOrderCount = "Số đơn tối thiểu phải là số nguyên lớn hơn hoặc bằng 0.";
    }
    if (
        !Number.isFinite(discountRate) ||
        discountRate < 0 ||
        discountRate > 100
    ) {
        errors.discountRate = "Mức giảm giá phải nằm trong khoảng 0 đến 100.";
    }

    return errors;
}

function formatCurrency(value: number) {
    return `${value.toLocaleString("vi-VN")}đ`;
}
