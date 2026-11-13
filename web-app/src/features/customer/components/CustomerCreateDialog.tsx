import { AlertCircle, LoaderCircle } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "../../../components/ui/Button";
import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { Field, TextInput } from "../../../components/ui/Field";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../components/ui/select";
import { customerApi } from "../services/customer.api";
import type {
    CreateCustomerRequest,
    CustomerResponse,
    CustomerStatus,
    CustomerTierResponse,
    CustomerType
} from "../types/customer.types";
import { customerErrorMessage } from "../utils/customer-list.utils";

interface Props {
    customer?: CustomerResponse;
    tiers: CustomerTierResponse[];
    onCancel: () => void;
    onSaved: (customer: CustomerResponse) => void | Promise<void>;
}

interface CustomerFormDraft {
    fullName: string;
    phone: string;
    email: string;
    dateOfBirth: string;
    customerType: CustomerType;
    tierId: string;
    status: CustomerStatus;
}

const EMPTY_DRAFT: CustomerFormDraft = {
    fullName: "",
    phone: "",
    email: "",
    dateOfBirth: "",
    customerType: "INDIVIDUAL",
    tierId: "",
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
                  customerType: customer.customerType ?? "INDIVIDUAL",
                  tierId: customer.tierId ?? "",
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
            customerType: draft.customerType,
            tierId: draft.tierId || null,
            lastPurchaseDate: customer?.lastPurchaseDate ?? null,
            totalSpent: customer?.totalSpent ?? 0,
            status: draft.status
        };

        try {
            const saved = customer
                ? await customerApi.update(customer.id, input)
                : await customerApi.create(input);
            await onSaved(saved);
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
                    <Field label="Tên khách hàng" required>
                        <TextInput
                            autoFocus
                                                        maxLength={150}
                            onChange={(event) =>
                                update("fullName", event.target.value)
                            }
                            placeholder="Nguyễn Văn A"
                            required
                            value={draft.fullName}
                        />
                    </Field>

                    <Field label="Số điện thoại" required>
                        <TextInput
                                                        maxLength={20}
                            onChange={(event) =>
                                update("phone", event.target.value)
                            }
                            placeholder="0901234567"
                            required
                            type="tel"
                            value={draft.phone}
                        />
                    </Field>

                    <Field label="Email">
                        <TextInput
                                                        maxLength={150}
                            onChange={(event) =>
                                update("email", event.target.value)
                            }
                            placeholder="customer@example.com"
                            type="email"
                            value={draft.email}
                        />
                    </Field>

                    <Field label="Ngày sinh">
                        <TextInput
                                                        max={new Date().toISOString().slice(0, 10)}
                            onChange={(event) =>
                                update("dateOfBirth", event.target.value)
                            }
                            type="date"
                            value={draft.dateOfBirth}
                        />
                    </Field>

                    <Field label="Loại khách hàng">
                        <Select
                            className="w-full"
                            onValueChange={(value) =>
                                update("customerType", value as CustomerType)
                            }
                            value={draft.customerType}
                        >
                            <SelectTrigger className="w-full rounded-lg font-normal shadow-none">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="INDIVIDUAL">Cá nhân</SelectItem>
                                <SelectItem value="BUSINESS">Doanh nghiệp</SelectItem>
                            </SelectContent>
                        </Select>
                    </Field>

                    <Field label="Hạng thành viên">
                        <Select
                            className="w-full"
                            onValueChange={(value) => update("tierId", value)}
                            value={draft.tierId}
                        >
                            <SelectTrigger className="w-full rounded-lg font-normal shadow-none">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="">Chưa xếp hạng</SelectItem>
                                {tiers
                                    .filter((tier) => tier.status === "ACTIVE")
                                    .map((tier) => (
                                        <SelectItem key={tier.id} value={tier.id}>
                                            {tier.name}
                                        </SelectItem>
                                    ))}
                            </SelectContent>
                        </Select>
                    </Field>

                    <Field label="Trạng thái">
                        <Select
                            className="w-full"
                            onValueChange={(value) =>
                                update("status", value as CustomerStatus)
                            }
                            value={draft.status}
                        >
                            <SelectTrigger className="w-full rounded-lg font-normal shadow-none">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ACTIVE">Hoạt động</SelectItem>
                                <SelectItem value="INACTIVE">Không hoạt động</SelectItem>
                                <SelectItem value="SUSPENDED">Tạm khóa</SelectItem>
                            </SelectContent>
                        </Select>
                    </Field>

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
                    <Button disabled={submitting} onClick={onCancel}>
                        Hủy
                    </Button>
                    <Button
                        variant="primary"
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
                    </Button>
                </DialogFooter>
            </form>
        </DialogContent>
    );
}
