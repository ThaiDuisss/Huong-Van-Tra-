import { SearchInput } from "../../../../components/ui/SearchInput";

interface SearchBoxProps {
    value: string;
    placeholder: string;
    onChange(value: string): void;
    className?: string;
}

/** Ô tìm kiếm ở đầu trang danh sách; kiểu dáng lấy từ `SearchInput` dùng chung. */
export function SearchBox({ value, placeholder, onChange, className = "xl:max-w-[420px]" }: SearchBoxProps) {
    return (
        <SearchInput
            className={`sm:min-w-[280px] ${className}`}
            onChange={onChange}
            placeholder={placeholder}
            value={value}
        />
    );
}
