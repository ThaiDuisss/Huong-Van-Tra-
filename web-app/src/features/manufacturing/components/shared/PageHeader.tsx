import type { ReactNode } from "react";

import { ListPageHeader } from "../../../../components/shared/list/ListPageHeader";

interface PageHeaderProps {
    title: string;
    description: string;
    actions?: ReactNode;
    search?: ReactNode;
}

/** Đầu trang danh sách; kiểu dáng lấy từ `ListPageHeader` dùng chung. */
export function PageHeader({ title, description, actions, search }: PageHeaderProps) {
    return (
        <ListPageHeader
            actions={
                search || actions ? (
                    <>
                        {search}
                        {actions}
                    </>
                ) : undefined
            }
            description={description}
            title={title}
        />
    );
}
