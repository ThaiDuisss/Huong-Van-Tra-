import { AlertCircle, LoaderCircle } from "lucide-react";
import { type FormEvent, type ReactNode, useState } from "react";

import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_COMPACT_BUTTON,
    CUSTOMER_FIELD_CLASS_NAME
} from "../constants/customer-list.constants";
import { customerApi } from "../services/customer.api";
import type {
    CreateCustomerRequest,
    CustomerResponse,
    CustomerStatus,
    CustomerTierResponse
} from "../types/customer.types";
import { customerErrorMessage } from "../utils/customer-list.utils";

interface Props {
    customer?: CustomerResponse;
    tiers: CustomerTierResponse[];
    onCancel: () => void;
    onSaved: () => void | Promise<void>;
}

interface CustomerFormDraft {
    fullName: string;
    phone: string;
    email: string;
    dateOfBirth: string;
    tierId: string;
    creditLimit: string;
    status: CustomerStatus;
}

const EMPTY_DRAFT: CustomerFormDraft = {
    fullName: "",
    phone: "",
    email: "",
    dateOfBirth: "",
    tierId: "",
    creditLimit: "0",
    status: "ACTIVE"
};

export function CustomerCreateDialog({
    customer,
    tiers,
    onCancel,
    onSaved
}: Props) {
    const [draft, setDraft] = useState<CustomerFormDraft>(() =>
        customer
            ? {
                  fullName: customer.fullName,
                  phone: customer.phone,
                  email: customer.email ?? "",
                  dateOfBirth: customer.dateOfBirth ?? "",
                  tierId: customer.tierId ?? "",
                  creditLimit: String(customer.creditLimit),
                  status: customer.status
              }
            : EMPTY_DRAFT
    );
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    function update<K extends keyof CustomerFormDraft>(
        key: K,
        value: CustomerFormDraft[K]
    ) {
        setDraft((current) => ({ ...current, [key]: value }));
    }

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitting(true);
        setError("");

        const input: CreateCustomerRequest = {
            customerCode: customer?.customerCode ?? null,
            fullName: draft.fullName.trim(),
            phone: draft.phone.trim(),
            email: draft.email.trim() || null,
            dateOfBirth: draft.dateOfBirth || null,
            tierId: draft.tierId || null,
            creditLimit: draft.creditLimit ? Number(draft.creditLimit) : 0,
            lastPurchaseDate: customer?.lastPurchaseDate ?? null,
            totalSpent: customer?.totalSpent ?? 0,
            status: draft.status
        };

        try {
            if (customer) {
                await customerApi.update(customer.id, input);
            } else {
                await customerApi.create(input);
            }
            await onSaved();
        } catch (submitError) {
            setError(customerErrorMessage(submitError));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <DialogContent className="w-[min(760px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle>
                    {customer ? "Chỉnh sửa khách hàng" : "Thêm khách hàng"}
                </DialogTitle>
                <DialogDescription>
                    {customer
                        ? `Cập nhật hồ sơ ${customer.customerCode}.`
                        : "Tạo hồ sơ khách hàng mới. Mã khách hàng sẽ được sinh tự động."}
                </DialogDescription>
            </DialogHeader>

            <form onSubmit={(event) => void submit(event)}>
                <div className="grid max-h-[68vh] grid-cols-2 gap-x-5 gap-y-5 overflow-y-auto p-6 max-sm:grid-cols-1">
                    <FormField label="Tên khách hàng" required>
                        <input
                            autoFocus
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            maxLength={150}
                            onChange={(event) =>
                                update("fullName", event.target.value)
                            }
                            placeholder="Nguyễn Văn A"
                            required
                            value={draft.fullName}
                        />
                    </FormField>

                    <FormField label="Số điện thoại" required>
                        <input
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            maxLength={20}
                            onChange={(event) =>
                                update("phone", event.target.value)
                            }
                            placeholder="0901234567"
                            required
                            type="tel"
                            value={draft.phone}
                        />
                    </FormField>

                    <FormField label="Email">
                        <input
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            maxLength={150}
                            onChange={(event) =>
                                update("email", event.target.value)
                            }
                            placeholder="customer@example.com"
                            type="email"
                            value={draft.email}
                        />
                    </FormField>

                    <FormField label="Ngày sinh">
                        <input
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            max={new Date().toISOString().slice(0, 10)}
                            onChange={(event) =>
                                update("dateOfBirth", event.target.value)
                            }
                            type="date"
                            value={draft.dateOfBirth}
                        />
                    </FormField>

                    <FormField label="Hạng thành viên">
                        <select
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            onChange={(event) =>
                                update("tierId", event.target.value)
                            }
                            value={draft.tierId}
                        >
                            <option value="">Chưa xếp hạng</option>
                            {tiers
                                .filter((tier) => tier.status === "ACTIVE")
                                .map((tier) => (
                                    <option key={tier.id} value={tier.id}>
                                        {tier.name}
                                    </option>
                                ))}
                        </select>
                    </FormField>

                    <FormField label="Hạn mức tín dụng">
                        <input
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            min="0"
                            onChange={(event) =>
                                update("creditLimit", event.target.value)
                            }
                            step="1000"
                            type="number"
                            value={draft.creditLimit}
                        />
                    </FormField>

                    <FormField label="Trạng thái">
                        <select
                            className={CUSTOMER_FIELD_CLASS_NAME}
                            onChange={(event) =>
                                update(
                                    "status",
                                    event.target.value as CustomerStatus
                                )
                            }
                            value={draft.status}
                        >
                            <option value="ACTIVE">Hoạt động</option>
                            <option value="INACTIVE">Không hoạt động</option>
                            <option value="SUSPENDED">Tạm khóa</option>
                        </select>
                    </FormField>

                    {error && (
                        <div
                            className="col-span-full flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]"
                            role="alert"
                        >
                            <AlertCircle
                                className="mt-0.5 shrink-0"
                                size={16}
                                aria-hidden="true"
                            />
                            {error}
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON}`}
                        disabled={submitting}
                        onClick={onCancel}
                        type="button"
                    >
                        Hủy
                    </button>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white hover:bg-[#12862d] disabled:cursor-not-allowed disabled:opacity-60`}
                        disabled={submitting}
                        type="submit"
                    >
                        {submitting && (
                            <LoaderCircle
                                className="animate-spin"
                                size={16}
                                aria-hidden="true"
                            />
                        )}
                        {submitting
                            ? "Đang lưu..."
                            : customer
                              ? "Lưu thay đổi"
                              : "Tạo khách hàng"}
                    </button>
                </DialogFooter>
            </form>
        </DialogContent>
    );
}

function FormField({
    children,
    label,
    required = false
}: {
    children: ReactNode;
    label: string;
    required?: boolean;
}) {
    return (
        <label className="grid gap-2">
            <span className="text-xs font-bold text-[#536171]">
                {label}
                {required && <span className="ml-1 text-[#c4422e]">*</span>}
            </span>
            {children}
        </label>
    );
}
