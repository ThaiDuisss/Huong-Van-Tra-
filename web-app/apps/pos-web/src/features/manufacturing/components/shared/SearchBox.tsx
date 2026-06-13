import { Search } from "lucide-react";

interface SearchBoxProps {
    value: string;
    placeholder: string;
    onChange(value: string): void;
}

export function SearchBox({
    value,
    placeholder,
    onChange
}: SearchBoxProps) {
    return (
        <label className="flex h-11 min-w-[360px] items-center gap-3 rounded-xl border border-pos-border bg-white px-4 text-sm shadow-sm focus-within:border-hvt-green-700">
            <Search
                size={18}
                className="text-pos-muted"
                aria-hidden="true"
            />
            <span className="sr-only">{placeholder}</span>
            <input
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-pos-muted"
                value={value}
                placeholder={placeholder}
                onChange={(event) =>
                    onChange(event.currentTarget.value)
                }
            />
        </label>
    );
}
