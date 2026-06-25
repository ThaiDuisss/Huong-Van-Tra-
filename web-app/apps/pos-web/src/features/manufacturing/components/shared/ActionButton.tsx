import type { ReactNode } from "react";

interface ActionButtonProps {
    children: ReactNode;
    variant?: "primary" | "secondary";
    onClick?(): void;
}

export function ActionButton({
    children,
    variant = "secondary",
    onClick
}: ActionButtonProps) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`inline-flex h-11 items-center justify-center gap-2 rounded-xl px-4 text-sm font-bold shadow-sm ${
                variant === "primary"
                    ? "bg-hvt-green-900 text-white"
                    : "border border-pos-border bg-white text-gray-500"
            }`}
        >
            {children}
        </button>
    );
}
