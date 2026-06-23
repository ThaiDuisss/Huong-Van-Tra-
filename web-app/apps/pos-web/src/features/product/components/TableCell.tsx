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
            className={`border-b border-pos-border px-4 py-4 ${className}`}
        >
            {children}
        </td>
    );
}
