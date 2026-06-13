import { ChevronDown } from "lucide-react";

interface TableToolbarProps {
    selectedCount: number;
}

export function TableToolbar({
    selectedCount
}: TableToolbarProps) {
    return (
        <div className="flex items-center justify-between border-b border-pos-border px-4 py-3">
            <label className="flex items-center gap-3 text-sm font-semibold text-pos-muted">
                <input type="checkbox" className="size-4" readOnly />
                {selectedCount > 0
                    ? `${selectedCount} đã chọn`
                    : "Chọn BOM"}
            </label>
            <button
                type="button"
                className="inline-flex h-9 items-center gap-2 rounded-lg border border-pos-border bg-white px-3 text-sm font-semibold text-pos-ink"
            >
                Hành động (0)
                <ChevronDown size={14} aria-hidden="true" />
            </button>
        </div>
    );
}
