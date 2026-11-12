import { StatusBadge } from "@/components/ui/StatusBadge";

import type { CatalogStatus } from "../types/product.types";

export function CatalogStatusBadge({ status }: { status: CatalogStatus }) {
    const active = status === "ACTIVE";

    return (
        <StatusBadge dot tone={active ? "success" : "neutral"}>
            {active ? "Đang hoạt động" : "Ngừng hoạt động"}
        </StatusBadge>
    );
}
