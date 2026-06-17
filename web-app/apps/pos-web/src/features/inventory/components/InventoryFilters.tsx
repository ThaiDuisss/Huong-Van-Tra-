import type { ReactNode } from "react";

import { FilterPanel } from "../../manufacturing/components/shared/FilterPanel";

export function InventoryFilters({
    children
}: {
    children: ReactNode;
}) {
    return (
        <div className="min-w-0">
            <FilterPanel>{children}</FilterPanel>
        </div>
    );
}

export function InventorySelect({
    label,
    value,
    options,
    onChange
}: {
    label: string;
    value: string;
    options: Array<{ value: string; label: string }>;
    onChange(value: string): void;
}) {
    return (
        <label className="grid gap-2 text-sm font-bold text-gray-800">
            {label}
            <select
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
                className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"
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

export function InventoryInput({
    label,
    value,
    type = "text",
    placeholder,
    onChange
}: {
    label: string;
    value: string;
    type?: string;
    placeholder?: string;
    onChange(value: string): void;
}) {
    return (
        <label className="grid gap-2 text-sm font-bold text-gray-800">
            {label}
            <input
                type={type}
                value={value}
                placeholder={placeholder}
                onChange={(event) => onChange(event.currentTarget.value)}
                className="h-11 rounded-xl border border-pos-border px-3 font-normal outline-none focus:border-hvt-green-700"
            />
        </label>
    );
}
