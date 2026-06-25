import type { ReactNode } from "react";

interface IconActionProps {
    label: string;
    children: ReactNode;
    onClick(): void;
}

export function IconAction({
    label,
    children,
    onClick
}: IconActionProps) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            onClick={(event) => {
                event.stopPropagation();
                onClick();
            }}
            className="grid size-8 place-items-center rounded-lg hover:bg-white"
        >
            {children}
        </button>
    );
}
