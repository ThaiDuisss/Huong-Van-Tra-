interface SelectFieldOption {
    label: string;
    value: string;
}

interface SelectFieldProps {
    label: string;
    value: string;
    options: SelectFieldOption[];
    onChange(value: string): void;
}

export function SelectField({
    label,
    value,
    options,
    onChange
}: SelectFieldProps) {
    return (
        <label className="grid gap-2 text-sm font-bold text-gray-600">
            {label}
            <select
                value={value}
                onChange={(event) => onChange(event.currentTarget.value)}
                className="h-11 rounded-xl border border-pos-border bg-white px-3 font-medium"
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
