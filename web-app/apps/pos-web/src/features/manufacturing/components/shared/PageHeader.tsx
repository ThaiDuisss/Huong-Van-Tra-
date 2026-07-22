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
        <header className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
            <div className="grid gap-1">
                <h1 className="text-2xl font-bold text-black-600 sm:text-3xl">
                    {title}
                </h1>
                <p className="text-sm text-gray-500">
                    {description}
                </p>
            </div>
            <div className="flex min-w-0 flex-wrap justify-start gap-3 sm:justify-end shrink-0">
                {search}
                {actions}
            </div>
        </header>
    );
}
