import { Search } from "lucide-react";
import type { InputHTMLAttributes } from "react";

interface SearchInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
    value: string;
    placeholder: string;
    onChange: (value: string) => void;
    /** Class của khung ngoài (độ rộng). */
    className?: string;
    /** `md`: cao 40px để đứng cùng hàng với `TextInput` / `Button` cỡ md. Mặc định 44px. */
    controlSize?: "md" | "lg";
}

/** Ô tìm kiếm dùng chung cho thanh lọc và đầu trang danh sách. */
export function SearchInput({
    value,
    placeholder,
    onChange,
    className = "",
    controlSize = "lg",
    ...rest
}: SearchInputProps) {
    return (
        <label
            className={`flex ${controlSize === "md" ? "h-10" : "h-11"} min-w-0 flex-1 items-center gap-3 rounded-xl border border-pos-border bg-white px-4 text-sm shadow-sm focus-within:border-hvt-green-700 ${className}`}
        >
            <Search aria-hidden="true" className="shrink-0 text-gray-500" size={18} />
            <span className="sr-only">{placeholder}</span>
            <input
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-gray-500"
                onChange={(event) => onChange(event.currentTarget.value)}
                placeholder={placeholder}
                value={value}
                {...rest}
            />
        </label>
    );
}
