import type { FormEvent, ReactNode } from "react";

import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../components/ui/select";
import {
    CUSTOMER_BUTTON_BASE,
    CUSTOMER_COMPACT_BUTTON,
    CUSTOMER_FIELD_CLASS_NAME
} from "../constants/customer-list.constants";
import type { CustomerFilterDraft } from "../types/customer-list.types";
import type {
    CustomerStatus,
    CustomerTierResponse
} from "../types/customer.types";

interface Props {
    draft: CustomerFilterDraft;
    tiers: CustomerTierResponse[];
    onChange: (draft: CustomerFilterDraft) => void;
    onClear: () => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function CustomerFilterDialog({
    draft,
    tiers,
    onChange,
    onClear,
    onSubmit
}: Props) {
    function toggleTier(tierId: string) {
        onChange({
            ...draft,
            tierIds: draft.tierIds.includes(tierId)
                ? draft.tierIds.filter((id) => id !== tierId)
                : [...draft.tierIds, tierId]
        });
    }

    return (
        <DialogContent className="w-[min(960px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle>Bộ lọc khách hàng</DialogTitle>
                <DialogDescription>
                    Thu hẹp danh sách theo thông tin, thời gian và hành vi mua
                    hàng.
                </DialogDescription>
            </DialogHeader>
            <form onSubmit={onSubmit}>
                <div className="grid max-h-[65vh] grid-cols-3 overflow-y-auto p-2 max-lg:grid-cols-1">
                    <FilterGroup title="Thông tin khách hàng">
                        <FilterField label="Trạng thái">
                            <Select
                                className="w-full"
                                onValueChange={(value) =>
                                    onChange({
                                        ...draft,
                                        status: value as CustomerStatus | ""
                                    })
                                }
                                value={draft.status}
                            >
                                <SelectTrigger className={CUSTOMER_FIELD_CLASS_NAME}>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="">Tất cả trạng thái</SelectItem>
                                    <SelectItem value="ACTIVE">Hoạt động</SelectItem>
                                    <SelectItem value="INACTIVE">Không hoạt động</SelectItem>
                                    <SelectItem value="SUSPENDED">Tạm khóa</SelectItem>
                                </SelectContent>
                            </Select>
                        </FilterField>
                        <FilterField label="Hạng thành viên">
                            <TierChecks
                                selected={draft.tierIds}
                                tiers={tiers}
                                onToggle={toggleTier}
                            />
                        </FilterField>
                    </FilterGroup>

                    <FilterGroup title="Thời gian">
                        <FilterField label="Ngày tạo từ — đến">
                            <DateRange
                                from={draft.createdFrom}
                                to={draft.createdTo}
                                type="date"
                                onFromChange={(createdFrom) =>
                                    onChange({ ...draft, createdFrom })
                                }
                                onToChange={(createdTo) =>
                                    onChange({ ...draft, createdTo })
                                }
                            />
                        </FilterField>
                        <div className="grid grid-cols-2 gap-3">
                            <FilterField label="Tháng sinh">
                                <Select
                                    className="w-full"
                                    onValueChange={(value) =>
                                        onChange({
                                            ...draft,
                                            birthMonth: value
                                        })
                                    }
                                    value={draft.birthMonth}
                                >
                                    <SelectTrigger className={CUSTOMER_FIELD_CLASS_NAME}>
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="">Tất cả tháng</SelectItem>
                                        {Array.from(
                                            { length: 12 },
                                            (_, index) => (
                                                <SelectItem
                                                    key={index + 1}
                                                    value={String(index + 1)}
                                                >
                                                    Tháng {index + 1}
                                                </SelectItem>
                                            )
                                        )}
                                    </SelectContent>
                                </Select>
                            </FilterField>
                            <FilterField label="Năm sinh">
                                <input
                                    className={CUSTOMER_FIELD_CLASS_NAME}
                                    max={new Date().getFullYear()}
                                    min="1900"
                                    onChange={(event) =>
                                        onChange({
                                            ...draft,
                                            birthYear: event.target.value
                                        })
                                    }
                                    placeholder="Tất cả năm"
                                    type="number"
                                    value={draft.birthYear}
                                />
                            </FilterField>
                        </div>
                    </FilterGroup>

                    <FilterGroup title="Hành vi mua hàng" isLast>
                        <FilterField label="Mua gần nhất từ — đến">
                            <DateRange
                                from={draft.lastPurchaseFrom}
                                to={draft.lastPurchaseTo}
                                type="date"
                                onFromChange={(lastPurchaseFrom) =>
                                    onChange({ ...draft, lastPurchaseFrom })
                                }
                                onToChange={(lastPurchaseTo) =>
                                    onChange({ ...draft, lastPurchaseTo })
                                }
                            />
                            {draft.lastPurchaseTo && (
                                <label className="mt-2 flex items-center gap-2 text-[11px] font-bold text-[#596879]">
                                    <input
                                        checked={draft.includeNeverPurchased}
                                        className="size-4 accent-[#159734]"
                                        onChange={(event) =>
                                            onChange({
                                                ...draft,
                                                includeNeverPurchased:
                                                    event.target.checked
                                            })
                                        }
                                        type="checkbox"
                                    />
                                    Gồm khách chưa từng mua trong kết quả
                                </label>
                            )}
                        </FilterField>
                        <FilterField label="Chưa từng mua hàng">
                            <Select
                                className="w-full"
                                onValueChange={(value) =>
                                    onChange({
                                        ...draft,
                                        hasPurchased:
                                            value as CustomerFilterDraft["hasPurchased"]
                                    })
                                }
                                value={draft.hasPurchased}
                            >
                                <SelectTrigger className={CUSTOMER_FIELD_CLASS_NAME}>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="">Không giới hạn</SelectItem>
                                    <SelectItem value="false">
                                        Chỉ khách chưa từng mua
                                    </SelectItem>
                                    <SelectItem value="true">
                                        Chỉ khách đã từng mua
                                    </SelectItem>
                                </SelectContent>
                            </Select>
                        </FilterField>
                    </FilterGroup>
                </div>
                <DialogFooter>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} ${CUSTOMER_COMPACT_BUTTON}`}
                        onClick={onClear}
                        type="button"
                    >
                        Đặt lại
                    </button>
                    <button
                        className={`${CUSTOMER_BUTTON_BASE} bg-[#159734] text-white hover:bg-[#12862d]`}
                        type="submit"
                    >
                        Áp dụng bộ lọc
                    </button>
                </DialogFooter>
            </form>
        </DialogContent>
    );
}

function FilterGroup({
    children,
    isLast = false,
    title
}: {
    children: ReactNode;
    isLast?: boolean;
    title: string;
}) {
    return (
        <section
            className={`min-w-0 space-y-4 rounded-xl px-4 py-4 ${
                isLast
                    ? ""
                    : "border-r border-[#e8eeeb] max-lg:border-r-0 max-lg:border-b"
            }`}
        >
            <h2 className="text-sm font-black text-[#253225]">{title}</h2>
            {children}
        </section>
    );
}

function FilterField({
    children,
    label
}: {
    children: ReactNode;
    label: string;
}) {
    return (
        <div className="grid gap-2">
            <span className="text-xs font-bold text-[#536171]">
                {label}
            </span>
            {children}
        </div>
    );
}

function TierChecks({
    selected,
    tiers,
    onToggle
}: {
    selected: string[];
    tiers: CustomerTierResponse[];
    onToggle: (tierId: string) => void;
}) {
    return (
        <div className="grid min-h-11 gap-2 rounded-lg border border-[#dce5e1] bg-white px-3 py-2.5">
            {!tiers.length && (
                <span className="text-[11px] font-bold text-[#8a96a3]">
                    Chưa có dữ liệu hạng thành viên
                </span>
            )}
            {tiers.map((tier) => (
                <label
                    className="flex items-center gap-2 text-[11px] font-bold text-[#4c5a67]"
                    key={tier.id}
                >
                    <input
                        checked={selected.includes(tier.id)}
                        className="size-3.5 accent-[#159734]"
                        onChange={() => onToggle(tier.id)}
                        type="checkbox"
                    />
                    {tier.name}
                </label>
            ))}
        </div>
    );
}

function DateRange({
    from,
    to,
    type,
    onFromChange,
    onToChange
}: {
    from: string;
    to: string;
    type: "date" | "datetime-local";
    onFromChange: (value: string) => void;
    onToChange: (value: string) => void;
}) {
    return (
        <div className="grid grid-cols-[minmax(0,1fr)_12px_minmax(0,1fr)] items-center gap-2">
            <input
                className={CUSTOMER_FIELD_CLASS_NAME}
                onChange={(event) => onFromChange(event.target.value)}
                type={type}
                value={from}
            />
            <span className="text-center text-xs font-bold text-[#8a96a3]">
                —
            </span>
            <input
                className={CUSTOMER_FIELD_CLASS_NAME}
                onChange={(event) => onToChange(event.target.value)}
                type={type}
                value={to}
            />
        </div>
    );
}
