import type {
    ProductionOrderFiltersState,
    ProductionOrderStatus,
    ProductionWorkshop
} from "../../types/production-order.types";
import { FilterPanel } from "../shared/FilterPanel";

interface ProductionOrderFiltersProps {
    value: ProductionOrderFiltersState;
    onChange(value: ProductionOrderFiltersState): void;
    onApply(): void;
    onReset(): void;
}

const statusOptions: Array<{
    value: ProductionOrderStatus;
    label: string;
}> = [
    { value: "DRAFT", label: "Mới tạo" },
    { value: "IN_PROGRESS", label: "Đang sản xuất" },
    { value: "WAITING_MATERIAL", label: "Chờ nguyên liệu" },
    { value: "COMPLETED", label: "Hoàn thành" },
    { value: "CANCELLED", label: "Hủy" }
];

const workshopOptions: Array<{
    value: ProductionWorkshop;
    label: string;
}> = [
    { value: "PACKAGING", label: "Xưởng đóng gói" },
    { value: "MIXING", label: "Xưởng pha trộn" },
    { value: "GIFT_ASSEMBLY", label: "Xưởng quà tặng" }
];

export function ProductionOrderFilters({
    value,
    onChange,
    onApply,
    onReset
}: ProductionOrderFiltersProps) {
    return (
        <FilterPanel>
            <CheckboxGroup
                title="Trạng thái"
                options={statusOptions}
                selected={value.statuses}
                onToggle={(status) =>
                    onChange({
                        ...value,
                        statuses: toggle(value.statuses, status)
                    })
                }
            />
            <CheckboxGroup
                title="Xưởng"
                options={workshopOptions}
                selected={value.workshops}
                onToggle={(workshop) =>
                    onChange({
                        ...value,
                        workshops: toggle(value.workshops, workshop)
                    })
                }
            />
            <fieldset className="grid gap-2">
                <legend className="mb-1 text-sm font-bold text-gray-500">
                    Thời gian
                </legend>
                {[
                    ["TODAY", "Hôm nay"],
                    ["LAST_7_DAYS", "7 ngày"],
                    ["LAST_30_DAYS", "30 ngày"],
                    ["CUSTOM", "Tùy chỉnh"]
                ].map(([range, label]) => (
                    <label
                        key={range}
                        className="flex items-center gap-2 text-sm text-pos-muted"
                    >
                        <input
                            type="radio"
                            name="production-date-range"
                            checked={value.dateRange === range}
                            onChange={() =>
                                onChange({
                                    ...value,
                                    dateRange:
                                        range as ProductionOrderFiltersState["dateRange"]
                                })
                            }
                        />
                        {label}
                    </label>
                ))}
            </fieldset>
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
                    className="h-11 rounded-xl border border-pos-border bg-white text-sm font-bold text-gray-500"
                >
                    Đặt lại
                </button>
            </div>
        </FilterPanel>
    );
}

function toggle<T>(values: T[], value: T) {
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
            <legend className="mb-1 text-sm font-bold text-gray-500">
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
