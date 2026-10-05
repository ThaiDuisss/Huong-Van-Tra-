import { X } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import { couponApi } from "../../services/coupon.api";
import { promotionApi } from "../../services/promotion.api";
import type {
    CouponResponse,
    CouponSaveRequest,
    CouponScopeValue
} from "../../types/coupon.types";
import type { PromotionResponse } from "../../types/promotion.types";

interface CouponFormModalProps {
    open: boolean;
    /** Có coupon thì là sửa, không có thì là thêm mới. */
    coupon?: CouponResponse | null;
    defaultPromotionId?: string;
    onClose(): void;
    onSaved(coupon: CouponResponse, mode: "create" | "update"): void;
}

interface CouponFormState {
    promotionId: string;
    code: string;
    scope: CouponScopeValue;
    customerId: string;
    usageLimit: string;
    perCustomerLimit: string;
    expiresAt: string;
}

type CouponFormErrors = Partial<Record<keyof CouponFormState, string>>;

const CLOSED_PROMOTION_STATUSES = new Set(["CANCELLED", "EXPIRED", "ENDED"]);
const UUID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const inputClassName =
    "w-full rounded-xl border border-[#d0d5dd] bg-white px-4 py-3 text-sm font-semibold outline-none focus:border-[#009b4d] disabled:bg-[#f2f4f7] disabled:text-[#667085]";
const labelClassName = "mb-1 block text-sm font-bold text-[#344054]";
const errorClassName = "mt-1 block text-xs font-semibold text-red-600";

function toFormState(
    coupon: CouponResponse | null | undefined,
    defaultPromotionId: string
): CouponFormState {
    return {
        promotionId: coupon?.promotionId ?? defaultPromotionId,
        code: coupon?.code ?? "",
        scope: coupon?.scope ?? "PUBLIC",
        customerId: coupon?.customerId ?? "",
        usageLimit: coupon?.usageLimit ? String(coupon.usageLimit) : "",
        perCustomerLimit: coupon?.perCustomerLimit
            ? String(coupon.perCustomerLimit)
            : "",
        // input datetime-local chỉ nhận "YYYY-MM-DDTHH:mm".
        expiresAt: coupon?.expiresAt ? coupon.expiresAt.slice(0, 16) : ""
    };
}

function parsePositiveInteger(value: string) {
    const trimmed = value.trim();

    if (!trimmed) {
        return undefined;
    }

    return /^\d+$/.test(trimmed) ? Number(trimmed) : Number.NaN;
}

function validate(form: CouponFormState, isEdit: boolean): CouponFormErrors {
    const errors: CouponFormErrors = {};

    if (!isEdit && !form.promotionId) {
        errors.promotionId = "Vui lòng chọn chương trình giảm giá.";
    }

    if (!form.code.trim()) {
        errors.code = "Mã giảm giá là bắt buộc.";
    } else if (form.code.trim().length > 100) {
        errors.code = "Mã giảm giá không được vượt quá 100 ký tự.";
    }

    if (form.scope === "PERSONAL") {
        if (!form.customerId.trim()) {
            errors.customerId = "Mã cá nhân cần gán cho một khách hàng.";
        } else if (!UUID_PATTERN.test(form.customerId.trim())) {
            errors.customerId = "ID khách hàng không đúng định dạng.";
        }
    }

    const usageLimit = parsePositiveInteger(form.usageLimit);
    if (Number.isNaN(usageLimit) || usageLimit === 0) {
        errors.usageLimit = "Số lần sử dụng phải là số nguyên từ 1 trở lên.";
    }

    const perCustomerLimit = parsePositiveInteger(form.perCustomerLimit);
    if (Number.isNaN(perCustomerLimit) || perCustomerLimit === 0) {
        errors.perCustomerLimit =
            "Giới hạn mỗi khách phải là số nguyên từ 1 trở lên.";
    } else if (
        usageLimit !== undefined &&
        perCustomerLimit !== undefined &&
        !Number.isNaN(usageLimit) &&
        perCustomerLimit > usageLimit
    ) {
        errors.perCustomerLimit =
            "Giới hạn mỗi khách không được lớn hơn tổng số lần sử dụng.";
    }

    if (form.expiresAt && new Date(form.expiresAt).getTime() < Date.now()) {
        errors.expiresAt = "Thời điểm hết hạn phải ở hiện tại hoặc tương lai.";
    }

    return errors;
}

function toRequest(form: CouponFormState, isEdit: boolean): CouponSaveRequest {
    return {
        promotionId: isEdit ? undefined : form.promotionId,
        scope: form.scope,
        customerId:
            form.scope === "PERSONAL" ? form.customerId.trim() : undefined,
        code: form.code.trim(),
        issueReason: "MANUAL",
        usageLimit: parsePositiveInteger(form.usageLimit),
        perCustomerLimit: parsePositiveInteger(form.perCustomerLimit),
        // Backend nhận LocalDateTime, cần đủ phần giây.
        expiresAt: form.expiresAt ? `${form.expiresAt}:00` : undefined
    };
}

export function CouponFormModal({
    open,
    coupon,
    defaultPromotionId = "",
    onClose,
    onSaved
}: CouponFormModalProps) {
    const isEdit = Boolean(coupon);
    const [form, setForm] = useState<CouponFormState>(() =>
        toFormState(coupon, defaultPromotionId)
    );
    const [promotions, setPromotions] = useState<PromotionResponse[]>([]);
    const [fieldErrors, setFieldErrors] = useState<CouponFormErrors>({});
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!open || isEdit) {
            return;
        }

        let cancelled = false;

        async function loadPromotions() {
            try {
                const response = await promotionApi.getPromotions({
                    page: 0,
                    size: 100
                });

                if (!cancelled) {
                    setPromotions(
                        (response.content ?? []).filter(
                            (promotion) =>
                                !CLOSED_PROMOTION_STATUSES.has(promotion.status)
                        )
                    );
                }
            } catch (err) {
                console.error("Không thể tải chương trình giảm giá", err);
                if (!cancelled) {
                    setError("Không thể tải danh sách chương trình giảm giá.");
                }
            }
        }

        void loadPromotions();

        return () => {
            cancelled = true;
        };
    }, [open, isEdit]);

    if (!open) {
        return null;
    }

    function updateField<K extends keyof CouponFormState>(
        key: K,
        value: CouponFormState[K]
    ) {
        setForm((current) => ({ ...current, [key]: value }));
        setFieldErrors((current) => ({ ...current, [key]: undefined }));
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const errors = validate(form, isEdit);

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }

        try {
            setSubmitting(true);
            setError(null);

            const request = toRequest(form, isEdit);
            const saved = coupon
                ? await couponApi.update(coupon.id, request)
                : await couponApi.create(request);

            onSaved(saved, coupon ? "update" : "create");
            onClose();
        } catch (err) {
            console.error("Không thể lưu mã giảm giá", err);
            setError(
                err instanceof Error && err.message
                    ? err.message
                    : "Không thể lưu mã giảm giá. Vui lòng kiểm tra dữ liệu."
            );
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
            <section
                className="max-h-[92dvh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl"
                role="dialog"
                aria-modal="true"
                aria-label={isEdit ? "Sửa mã giảm giá" : "Thêm mã giảm giá"}
            >
                <header className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-bold text-[#17213a]">
                            {isEdit ? "Sửa mã giảm giá" : "Thêm mã giảm giá"}
                        </h2>
                        <p className="mt-1 text-sm font-medium text-[#667085]">
                            {isEdit
                                ? `Cập nhật thông tin mã ${coupon?.code ?? ""}.`
                                : "Mã mới được tạo ở trạng thái bản nháp, cần kích hoạt để sử dụng."}
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-lg p-2 hover:bg-[#f2f4f7]"
                        aria-label="Đóng form"
                    >
                        <X size={20} aria-hidden="true" />
                    </button>
                </header>

                {error ? (
                    <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                        {error}
                    </div>
                ) : null}

                <form onSubmit={handleSubmit} className="space-y-4" noValidate>
                    <label className="block">
                        <span className={labelClassName}>
                            Chương trình giảm giá
                        </span>
                        {isEdit ? (
                            <input
                                className={inputClassName}
                                value={
                                    coupon?.promotionName ??
                                    coupon?.snapshotPromotionName ??
                                    coupon?.promotionId ??
                                    ""
                                }
                                disabled
                            />
                        ) : (
                            <select
                                className={inputClassName}
                                value={form.promotionId}
                                onChange={(event) =>
                                    updateField(
                                        "promotionId",
                                        event.currentTarget.value
                                    )
                                }
                            >
                                <option value="">
                                    Chọn chương trình giảm giá
                                </option>
                                {promotions.map((promotion) => (
                                    <option
                                        key={promotion.id}
                                        value={promotion.id}
                                    >
                                        {promotion.name}
                                    </option>
                                ))}
                            </select>
                        )}
                        {fieldErrors.promotionId ? (
                            <span className={errorClassName}>
                                {fieldErrors.promotionId}
                            </span>
                        ) : null}
                    </label>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className="block">
                            <span className={labelClassName}>Mã giảm giá</span>
                            <input
                                className={inputClassName}
                                value={form.code}
                                maxLength={100}
                                placeholder="TET2027"
                                onChange={(event) =>
                                    updateField(
                                        "code",
                                        event.currentTarget.value.toUpperCase()
                                    )
                                }
                            />
                            {fieldErrors.code ? (
                                <span className={errorClassName}>
                                    {fieldErrors.code}
                                </span>
                            ) : null}
                        </label>

                        <label className="block">
                            <span className={labelClassName}>Phạm vi</span>
                            <select
                                className={inputClassName}
                                value={form.scope}
                                onChange={(event) =>
                                    updateField(
                                        "scope",
                                        event.currentTarget
                                            .value as CouponScopeValue
                                    )
                                }
                            >
                                <option value="PUBLIC">Công khai</option>
                                <option value="PERSONAL">Cá nhân</option>
                            </select>
                        </label>
                    </div>

                    {form.scope === "PERSONAL" ? (
                        <label className="block">
                            <span className={labelClassName}>
                                ID khách hàng được gán
                            </span>
                            <input
                                className={inputClassName}
                                value={form.customerId}
                                placeholder="550e8400-e29b-41d4-a716-446655440010"
                                onChange={(event) =>
                                    updateField(
                                        "customerId",
                                        event.currentTarget.value
                                    )
                                }
                            />
                            {fieldErrors.customerId ? (
                                <span className={errorClassName}>
                                    {fieldErrors.customerId}
                                </span>
                            ) : null}
                        </label>
                    ) : null}

                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className="block">
                            <span className={labelClassName}>
                                Tổng số lần sử dụng
                            </span>
                            <input
                                className={inputClassName}
                                value={form.usageLimit}
                                inputMode="numeric"
                                placeholder="Để trống nếu không giới hạn"
                                onChange={(event) =>
                                    updateField(
                                        "usageLimit",
                                        event.currentTarget.value
                                    )
                                }
                            />
                            {fieldErrors.usageLimit ? (
                                <span className={errorClassName}>
                                    {fieldErrors.usageLimit}
                                </span>
                            ) : null}
                        </label>

                        <label className="block">
                            <span className={labelClassName}>
                                Giới hạn mỗi khách hàng
                            </span>
                            <input
                                className={inputClassName}
                                value={form.perCustomerLimit}
                                inputMode="numeric"
                                placeholder="Để trống nếu không giới hạn"
                                onChange={(event) =>
                                    updateField(
                                        "perCustomerLimit",
                                        event.currentTarget.value
                                    )
                                }
                            />
                            {fieldErrors.perCustomerLimit ? (
                                <span className={errorClassName}>
                                    {fieldErrors.perCustomerLimit}
                                </span>
                            ) : null}
                        </label>
                    </div>

                    <label className="block">
                        <span className={labelClassName}>Thời điểm hết hạn</span>
                        <input
                            className={inputClassName}
                            type="datetime-local"
                            value={form.expiresAt}
                            onChange={(event) =>
                                updateField(
                                    "expiresAt",
                                    event.currentTarget.value
                                )
                            }
                        />
                        {fieldErrors.expiresAt ? (
                            <span className={errorClassName}>
                                {fieldErrors.expiresAt}
                            </span>
                        ) : null}
                    </label>

                    <footer className="flex justify-end gap-3 pt-2">
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={submitting}
                            className="rounded-xl border border-[#d0d5dd] px-4 py-2.5 text-sm font-bold text-[#344054] hover:bg-[#f9fafb] disabled:opacity-60"
                        >
                            Hủy
                        </button>
                        <button
                            type="submit"
                            disabled={submitting}
                            className="rounded-xl bg-[#009b4d] px-4 py-2.5 text-sm font-bold text-white hover:bg-[#00823f] disabled:opacity-60"
                        >
                            {submitting
                                ? "Đang lưu..."
                                : isEdit
                                  ? "Lưu thay đổi"
                                  : "Thêm mã giảm giá"}
                        </button>
                    </footer>
                </form>
            </section>
        </div>
    );
}
