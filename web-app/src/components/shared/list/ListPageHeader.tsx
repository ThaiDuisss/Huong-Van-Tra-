import type { ReactNode } from "react";

interface ListPageHeaderProps {
    title: string;
    description?: string;
    actions?: ReactNode;
    /** Dòng chữ nhỏ phía trên tiêu đề (tên phân hệ). */
    eyebrow?: ReactNode;
    /** Đường dẫn / nút quay lại phía trên tiêu đề. */
    breadcrumb?: ReactNode;
}

export function ListPageHeader({ title, description, actions, eyebrow, breadcrumb }: ListPageHeaderProps) {
    return (
        <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div className="grid min-w-[240px] gap-1">
                {breadcrumb}
                {eyebrow ? <p className="text-xs font-black uppercase tracking-wide text-[#159734]">{eyebrow}</p> : null}
                <h1 className="text-2xl font-bold text-gray-800 sm:text-3xl">{title}</h1>
                {description ? <p className="text-sm text-gray-500">{description}</p> : null}
            </div>
            {actions ? (
                <div className="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-2.5">
                    {actions}
                </div>
            ) : null}
        </header>
    );
}
