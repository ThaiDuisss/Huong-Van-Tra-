import type { HTMLAttributes, TableHTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from "react";

/**
 * Bộ thẻ bảng có kiểu dáng chung cho những bảng không dùng được `DataTable`
 * (bảng nhập liệu theo dòng, bảng trong popup, bảng có ô gộp…).
 * Danh sách thông thường (tải dữ liệu, phân trang, trạng thái rỗng) thì dùng `DataTable`.
 */
export function Table({ className = "", ...rest }: TableHTMLAttributes<HTMLTableElement>) {
    return <table className={`w-full border-collapse text-left text-sm text-[#172317] ${className}`} {...rest} />;
}

export function TableHead({ className = "", ...rest }: HTMLAttributes<HTMLTableSectionElement>) {
    return <thead className={`bg-[#f7faf8] ${className}`} {...rest} />;
}

export function TableRow({
    className = "",
    interactive = false,
    striped = false,
    ...rest
}: HTMLAttributes<HTMLTableRowElement> & {
    /** Dòng bấm được: đổi nền khi rê chuột. */
    interactive?: boolean;
    /** Nền xen kẽ cho dòng chẵn. */
    striped?: boolean;
}) {
    return (
        <tr
            className={`border-b border-[#eef2f0] last:border-b-0 ${interactive ? "cursor-pointer hover:bg-[#f7fbf8]" : ""} ${
                striped ? "even:bg-[#fafcfb]" : ""
            } ${className}`}
            {...rest}
        />
    );
}

export function TableHeaderCell({ className = "", ...rest }: ThHTMLAttributes<HTMLTableCellElement>) {
    return (
        <th
            className={`h-11 whitespace-nowrap px-4 text-xs font-black uppercase tracking-wide text-[#657282] ${className}`}
            scope="col"
            {...rest}
        />
    );
}

export function TableCell({ className = "", ...rest }: TdHTMLAttributes<HTMLTableCellElement>) {
    return <td className={`px-4 py-3 align-middle ${className}`} {...rest} />;
}
