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
            className={`inline-flex h-12 items-center justify-center gap-[9px] rounded-lg text-sm font-black transition-all cursor-pointer ${
                variant === "primary"
                    ? "border-0 bg-[#07883a] text-white px-[22px] shadow-[0_10px_24px_rgba(7,136,58,0.24)] hover:opacity-90"
                    : "border border-[#dfe6ee] bg-white text-[#172317] px-[18px] hover:bg-neutral-50"
            }`}
        >
            {children}
        </button>
    );
}
