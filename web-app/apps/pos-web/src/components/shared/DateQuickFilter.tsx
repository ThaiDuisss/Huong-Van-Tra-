import { DATE_FILTERS, type DateFilterKey } from "./dateFilters";
import styles from "./DateQuickFilter.module.css";

interface DateQuickFilterProps {
    label?: string;
    value: DateFilterKey;
    onChange: (value: DateFilterKey) => void;
}

export function DateQuickFilter({
    label = "Thời gian:",
    value,
    onChange
}: DateQuickFilterProps) {
    return (
        <div className={styles.group}>
            <span className={styles.label}>{label}</span>
            <div className={styles.chips}>
                {DATE_FILTERS.map((filter) => (
                    <button
                        className={`${styles.chip} ${
                            value === filter.key ? styles.activeChip : ""
                        }`}
                        key={filter.key}
                        onClick={() => onChange(filter.key)}
                        type="button"
                    >
                        {filter.label}
                    </button>
                ))}
            </div>
        </div>
    );
}
