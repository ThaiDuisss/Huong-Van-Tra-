import type { ReactNode } from "react";

interface TableCellProps {
    children: ReactNode;
    className?: string;
}

export function TableCell({
    children,
    className = ""
}: TableCellProps) {
    return (
        <td
            className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 first:pl-[18px] ${className}`}
        >
            {children}
        </td>
    );
}
