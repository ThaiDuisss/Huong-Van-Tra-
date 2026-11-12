import { X } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import { Button, IconButton } from "../../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../../components/ui/Field";
import { ModalShell } from "../../../../components/ui/Overlay";
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
        <ModalShell
            className="w-full max-w-2xl p-6"
            closeOnBackdrop={false}
            closeOnEscape={false}
            labelledBy="coupon-form-title"
            onClose={onClose}
        >
            <header className="mb-5 flex items-start justify-between gap-4">
                <div>
                    <h2
                        className="text-xl font-bold text-[#17213a]"
                        id="coupon-form-title"
                    >
                        {isEdit ? "Sửa mã giảm giá" : "Thêm mã giảm giá"}
                    </h2>
                    <p className="mt-1 text-sm font-medium text-[#667085]">
                        {isEdit
                            ? `Cập nhật thông tin mã ${coupon?.code ?? ""}.`
                            : "Mã mới được tạo ở trạng thái bản nháp, cần kích hoạt để sử dụng."}
                    </p>
                </div>
                <IconButton aria-label="Đóng form" onClick={onClose}>
                    <X size={20} aria-hidden="true" />
                </IconButton>
            </header>

            {error ? (
                <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                    {error}
                </div>
            ) : null}

            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
                <Field
                    label="Chương trình giảm giá"
                    error={fieldErrors.promotionId}
                >
                    {isEdit ? (
                        <TextInput
                            value={
                                coupon?.promotionName ??
                                coupon?.snapshotPromotionName ??
                                coupon?.promotionId ??
                                ""
                            }
                            disabled
                        />
                    ) : (
                        <NativeSelect
                            value={form.promotionId}
                            onChange={(event) =>
                                updateField(
                                    "promotionId",
                                    event.currentTarget.value
                                )
                            }
                        >
                            <option value="">Chọn chương trình giảm giá</option>
                            {promotions.map((promotion) => (
                                <option key={promotion.id} value={promotion.id}>
                                    {promotion.name}
                                </option>
                            ))}
                        </NativeSelect>
                    )}
                </Field>

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Mã giảm giá" error={fieldErrors.code}>
                        <TextInput
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
                    </Field>

                    <Field label="Phạm vi">
                        <NativeSelect
                            value={form.scope}
                            onChange={(event) =>
                                updateField(
                                    "scope",
                                    event.currentTarget.value as CouponScopeValue
                                )
                            }
                        >
                            <option value="PUBLIC">Công khai</option>
                            <option value="PERSONAL">Cá nhân</option>
                        </NativeSelect>
                    </Field>
                </div>

                {form.scope === "PERSONAL" ? (
                    <Field
                        label="ID khách hàng được gán"
                        error={fieldErrors.customerId}
                    >
                        <TextInput
                            value={form.customerId}
                            placeholder="550e8400-e29b-41d4-a716-446655440010"
                            onChange={(event) =>
                                updateField(
                                    "customerId",
                                    event.currentTarget.value
                                )
                            }
                        />
                    </Field>
                ) : null}

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field
                        label="Tổng số lần sử dụng"
                        error={fieldErrors.usageLimit}
                    >
                        <TextInput
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
                    </Field>

                    <Field
                        label="Giới hạn mỗi khách hàng"
                        error={fieldErrors.perCustomerLimit}
                    >
                        <TextInput
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
                    </Field>
                </div>

                <Field label="Thời điểm hết hạn" error={fieldErrors.expiresAt}>
                    <TextInput
                        type="datetime-local"
                        value={form.expiresAt}
                        onChange={(event) =>
                            updateField("expiresAt", event.currentTarget.value)
                        }
                    />
                </Field>

                <footer className="flex justify-end gap-3 pt-2">
                    <Button onClick={onClose} disabled={submitting}>
                        Hủy
                    </Button>
                    <Button
                        type="submit"
                        variant="primary"
                        disabled={submitting}
                    >
                        {submitting
                            ? "Đang lưu..."
                            : isEdit
                              ? "Lưu thay đổi"
                              : "Thêm mã giảm giá"}
                    </Button>
                </footer>
            </form>
        </ModalShell>
    );
}
