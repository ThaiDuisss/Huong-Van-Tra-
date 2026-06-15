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
        <header className="flex flex-col items-stretch justify-between gap-4 xl:flex-row xl:items-start">
            <div className="grid gap-1">
                <h1 className="text-2xl font-bold text-black-600 sm:text-3xl">
                    {title}
                </h1>
                <p className="text-sm text-gray-500">
                    {description}
                </p>
            </div>
            <div className="flex min-w-0 flex-1 flex-wrap justify-start gap-3 xl:justify-end">
                {search}
                {actions}
            </div>
        </header>
    );
}
