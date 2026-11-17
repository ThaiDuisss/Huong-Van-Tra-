import type { ReactNode } from "react";

import { Button } from "../../../../components/ui/Button";

interface ActionButtonProps {
    children: ReactNode;
    variant?: "primary" | "secondary";
    disabled?: boolean;
    onClick?(): void;
}

/** Nút hành động ở đầu trang danh sách; kiểu dáng lấy từ `Button` dùng chung. */
export function ActionButton({ children, variant = "secondary", disabled = false, onClick }: ActionButtonProps) {
    return (
        <Button disabled={disabled} onClick={onClick} size="lg" variant={variant}>
            {children}
        </Button>
    );
}
