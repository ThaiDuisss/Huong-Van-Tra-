import type { ReactNode } from "react";

import { SearchInput } from "../../ui/SearchInput";

export interface ListFilterChip {
    key: string;
    label: string;
    /** `null` = đang tải (hiện "–"), `undefined` = không hiển thị số lượng. */
    count?: number | null;
    tone?: "default" | "danger";
}

interface ListFilterToolbarProps {
    chips: readonly ListFilterChip[];
    activeChip: string;
    onChipChange: (key: string) => void;
    chipsLabel?: string;
    search: {
        value: string;
        placeholder: string;
        onChange: (value: string) => void;
    };
    /** Các điều khiển phụ bên phải ô tìm kiếm (lọc thời gian, sắp xếp...). */
    children?: ReactNode;
}

export function ListFilterToolbar({
    chips,
    activeChip,
    onChipChange,
    chipsLabel = "Lọc theo trạng thái",
    search,
    children
}: ListFilterToolbarProps) {
    return (
        <div className="grid gap-3 rounded-2xl bg-white p-3 shadow-pos-card">
            <div className="flex flex-wrap items-center gap-2.5">
                <SearchInput
                    className="min-w-[240px] md:max-w-[420px]"
                    onChange={search.onChange}
                    placeholder={search.placeholder}
                    type="search"
                    value={search.value}
                />
                {children ? (
                    <div className="flex flex-wrap items-center gap-2.5">{children}</div>
                ) : null}
            </div>
            <nav aria-label={chipsLabel} className="flex min-w-0 gap-2 overflow-x-auto pb-0.5">
                {chips.map((chip) => (
                    <FilterChip
                        active={chip.key === activeChip}
                        chip={chip}
                        key={chip.key}
                        onClick={() => onChipChange(chip.key)}
                    />
                ))}
            </nav>
        </div>
    );
}

function FilterChip({
    active,
    chip,
    onClick
}: {
    active: boolean;
    chip: ListFilterChip;
    onClick: () => void;
}) {
    const danger = chip.tone === "danger";
    const toneClass = active
        ? danger
            ? "border-[#d9534f] bg-[#fdeeee] text-[#b42318]"
            : "border-[#159734] bg-[#eaf8ee] text-[#116f28] shadow-[0_1px_2px_rgba(21,151,52,0.08)]"
        : danger
            ? "border-[#f2c9c6] bg-white text-[#b42318] hover:border-[#e8a19c]"
            : "border-[#dce5e1] bg-white text-[#536171] hover:border-[#b8cac1] hover:text-[#172317]";
    const countClass = active
        ? danger ? "text-[#b42318]" : "text-[#159734]"
        : danger ? "text-[#d9534f]" : "text-[#8a96a3]";

    return (
        <button
            aria-pressed={active}
            className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-xs font-extrabold transition-colors ${toneClass}`}
            onClick={onClick}
            type="button"
        >
            <span>{chip.label}</span>
            {chip.count !== undefined ? (
                <span aria-busy={chip.count === null || undefined} className={countClass}>
                    {chip.count === null ? "–" : chip.count.toLocaleString("vi-VN")}
                </span>
            ) : null}
        </button>
    );
}
