import type { CatalogStatus } from "../types/product.types";

export function CatalogStatusBadge({
    status
}: {
    status: CatalogStatus;
}) {
    return (
        <span
            className={`inline-flex h-7 items-center rounded-lg px-2.5 text-xs font-bold ${
                status === "ACTIVE"
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-neutral-100 text-neutral-600"
            }`}
        >
            {status === "ACTIVE" ? "Đang hoạt động" : "Ngừng hoạt động"}
        </span>
    );
}
