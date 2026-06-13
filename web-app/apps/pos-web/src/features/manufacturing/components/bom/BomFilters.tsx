import type {
    BomFiltersState,
    BomProductType,
    BomStatus,
    BomUpdatedRange
} from "../../types/bom.types";
import { FilterPanel } from "../shared/FilterPanel";

interface BomFiltersProps {
    value: BomFiltersState;
    onChange(value: BomFiltersState): void;
    onApply(): void;
    onReset(): void;
}

const productTypeOptions: Array<{
    value: BomProductType;
    label: string;
}> = [
    { value: "PACKAGED_TEA", label: "Trà đóng gói" },
    { value: "GIFT_SET", label: "Combo quà" },
    { value: "ACCESSORY", label: "Phụ kiện" }
];

const statusOptions: Array<{
    value: BomStatus;
    label: string;
}> = [
    { value: "ACTIVE", label: "Đang áp dụng" },
    { value: "PENDING_APPROVAL", label: "Chờ duyệt" },
    { value: "INACTIVE", label: "Ngừng sử dụng" },
    { value: "MATERIAL_SHORTAGE", label: "Thiếu nguyên liệu" }
];

const versionOptions = ["v1", "v2", "v3"];

const updatedOptions: Array<{
    value: BomUpdatedRange;
    label: string;
}> = [
    { value: "TODAY", label: "Hôm nay" },
    { value: "LAST_7_DAYS", label: "7 ngày" },
    { value: "LAST_30_DAYS", label: "30 ngày" },
    { value: "CUSTOM", label: "Tùy chỉnh" }
];

export function BomFilters({
    value,
    onChange,
    onApply,
    onReset
}: BomFiltersProps) {
    return (
        <FilterPanel>
            <CheckboxGroup
                title="Loại thành phẩm"
                options={productTypeOptions}
                selected={value.productTypes}
                onToggle={(option) =>
                    onChange({
                        ...value,
                        productTypes: toggleValue(
                            value.productTypes,
                            option
                        )
                    })
                }
            />
            <CheckboxGroup
                title="Trạng thái"
                options={statusOptions}
                selected={value.statuses}
                onToggle={(option) =>
                    onChange({
                        ...value,
                        statuses: toggleValue(
                            value.statuses,
                            option
                        )
                    })
                }
            />
            <CheckboxGroup
                title="Phiên bản"
                options={versionOptions.map((version) => ({
                    value: version,
                    label: version
                }))}
                selected={value.versions}
                onToggle={(option) =>
                    onChange({
                        ...value,
                        versions: toggleValue(
                            value.versions,
                            option
                        )
                    })
                }
            />
            <RadioGroup
                title="Ngày cập nhật"
                options={updatedOptions}
                selected={value.updatedRange}
                onChange={(updatedRange) =>
                    onChange({
                        ...value,
                        updatedRange
                    })
                }
            />
            <div className="grid gap-2">
                <button
                    type="button"
                    onClick={onApply}
                    className="h-11 rounded-xl bg-hvt-green-900 text-sm font-bold text-white"
                >
                    Áp dụng bộ lọc
                </button>
                <button
                    type="button"
                    onClick={onReset}
                    className="h-11 rounded-xl border border-pos-border bg-white text-sm font-bold text-pos-ink"
                >
                    Đặt lại
                </button>
            </div>
        </FilterPanel>
    );
}

function toggleValue<T>(values: T[], value: T) {
    return values.includes(value)
        ? values.filter((item) => item !== value)
        : [...values, value];
}

interface CheckboxGroupProps<T extends string> {
    title: string;
    options: Array<{
        value: T;
        label: string;
    }>;
    selected: T[];
    onToggle(value: T): void;
}

function CheckboxGroup<T extends string>({
    title,
    options,
    selected,
    onToggle
}: CheckboxGroupProps<T>) {
    return (
        <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-bold text-pos-ink">
                {title}
            </legend>
            {options.map((option) => (
                <label
                    key={option.value}
                    className="flex items-center gap-2 text-sm text-pos-muted"
                >
                    <input
                        type="checkbox"
                        checked={selected.includes(option.value)}
                        onChange={() => onToggle(option.value)}
                    />
                    {option.label}
                </label>
            ))}
        </fieldset>
    );
}

interface RadioGroupProps<T extends string> {
    title: string;
    options: Array<{
        value: T;
        label: string;
    }>;
    selected: T;
    onChange(value: T): void;
}

function RadioGroup<T extends string>({
    title,
    options,
    selected,
    onChange
}: RadioGroupProps<T>) {
    return (
        <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-bold text-pos-ink">
                {title}
            </legend>
            {options.map((option) => (
                <label
                    key={option.value}
                    className="flex items-center gap-2 text-sm text-pos-muted"
                >
                    <input
                        type="radio"
                        name="bom-updated-range"
                        checked={selected === option.value}
                        onChange={() => onChange(option.value)}
                    />
                    {option.label}
                </label>
            ))}
        </fieldset>
    );
}
