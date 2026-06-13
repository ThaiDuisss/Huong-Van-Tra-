import type { ReactNode } from "react";

interface PageHeaderProps {
    title: string;
    description: string;
    actions?: ReactNode;
    search?: ReactNode;
}

export function PageHeader({
    title,
    description,
    actions,
    search
}: PageHeaderProps) {
    return (
        <header className="flex flex-wrap items-start justify-between gap-4">
            <div className="grid gap-1">
                <h1 className="text-3xl font-bold text-pos-ink">
                    {title}
                </h1>
                <p className="text-sm text-pos-muted">
                    {description}
                </p>
            </div>
            <div className="flex min-w-[520px] flex-1 justify-end gap-3">
                {search}
                {actions}
            </div>
        </header>
    );
}
