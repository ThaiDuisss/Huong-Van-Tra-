import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from "../../../components/ui/select";
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
        <label className={styles.filterField}>
            <span className={styles.filterFieldLabel}>{label}</span>
            <Select
                className="w-full"
                value={value}
                onValueChange={onChange}
            >
                <SelectTrigger
                    aria-label={label}
                    className="h-10 w-full rounded-lg border-[#edf1f6] px-3 text-sm text-[#17213a] shadow-none hover:bg-white"
                >
                    <SelectValue />
                </SelectTrigger>
                <SelectContent className="z-[90] rounded-lg border-[#d0d5dd] p-1 shadow-[0_18px_38px_rgba(15,23,42,0.16)]">
                    {options.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                            {option.label}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </label>
    );
}
