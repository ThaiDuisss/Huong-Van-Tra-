import { ChevronDown } from "lucide-react";

import styles from "./discount-programs/DiscountProgramPage.module.css";

interface MarketingSelectFieldProps {
    label: string;
    value: string;
    options: Array<{ label: string; value: string }>;
    onChange(value: string): void;
}

export function MarketingSelectField({
    label,
    value,
    options,
    onChange
}: MarketingSelectFieldProps) {
    return (
        <label className={styles.selectWrap}>
            <span className="sr-only">{label}</span>
            <select
                className={styles.select}
                value={value}
                onChange={(event) =>
                    onChange(event.currentTarget.value)
                }
            >
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
            <ChevronDown size={16} aria-hidden="true" />
        </label>
    );
}
