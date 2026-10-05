import { Plus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../../inventory/components/InventoryResourceManagement";
import { productionOrderApi } from "../services/production-order.api";
import type { ProductionOrder, ProductionOrderApiStatus } from "../types/production-order.types";

const columns: Column[] = [
    { key: "orderCode", label: "Mã lệnh sản xuất" },
    { align: "right", key: "plannedQuantity", label: "Số lượng kế hoạch" },
    { key: "status", label: "Trạng thái" },
    { key: "createdAt", label: "Ngày tạo" }
];

const STATUS_LABEL_MAP: Record<string, string> = {
    DRAFT: "Mới tạo",
    PENDING_APPROVAL: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    REJECTED: "Từ chối",
    CANCELLED: "Đã hủy"
};

const STATUS_BADGE_CLASS: Record<string, string> = {
    DRAFT: "bg-neutral-100 text-neutral-700",
    PENDING_APPROVAL: "bg-purple-50 text-purple-700",
    APPROVED: "bg-blue-50 text-blue-700",
    REJECTED: "bg-red-50 text-red-700",
    CANCELLED: "bg-red-100 text-red-700"
};

function createStatusOptions(
    statuses: string[]
): Array<{ label: string; value: "ALL" | ProductionOrderApiStatus }> {
    return [
        { label: "Tất cả trạng thái", value: "ALL" as const },
        ...statuses.map((s) => ({
            label: STATUS_LABEL_MAP[s as ProductionOrderApiStatus] || s,
            value: s as ProductionOrderApiStatus
        }))
    ] as Array<{ label: string; value: "ALL" | ProductionOrderApiStatus }>;
}

function StatusPill({ status }: { status: ProductionOrderApiStatus }) {
    return (
        <span className={`inline-flex h-7 items-center rounded-full px-2.5 text-xs font-bold ${STATUS_BADGE_CLASS[status] || 'bg-gray-50 text-gray-700'}`}>
            {STATUS_LABEL_MAP[status] || status}
        </span>
    );
}

function formatDateTime(value: string) {
    return new Date(value).toLocaleString("vi-VN");
}

export function ProductionOrderListPage() {
    const navigate = useNavigate();

    const [search, setSearch] = useState("");
    const [status, setStatus] = useState<"ALL" | ProductionOrderApiStatus>("ALL");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [refreshKey, setRefreshKey] = useState(0);

    const [orders, setOrders] = useState<ProductionOrder[]>([]);
    const [totalItems, setTotalItems] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [statusOptions, setStatusOptions] = useState<Array<{ label: string; value: "ALL" | ProductionOrderApiStatus }>>([
        { label: "Tất cả trạng thái", value: "ALL" }
    ]);

    useEffect(() => {
        productionOrderApi
            .getStatuses()
            .then((statuses) => {
                setStatusOptions(createStatusOptions(statuses));
            })
            .catch(() => {
                console.error("Failed to load statuses from API");
            });
    }, []);

    const loadOrders = useCallback(() => {
        let active = true;
        setLoading(true);
        setError("");
        productionOrderApi
            .list({
                page: page - 1,
                size: pageSize,
                search: search || undefined,
                status: status === "ALL" ? undefined : status
            })
            .then((res) => {
                if (!active) return;
                setOrders(res.content ?? []);
                setTotalItems(res.totalElements ?? 0);
                setTotalPages(res.totalPages ?? 1);
            })
            .catch((err: unknown) => {
                if (!active) return;
                setError(err instanceof Error ? err.message : "Không thể tải danh sách lệnh sản xuất.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [page, pageSize, search, status, refreshKey]);

    useEffect(() => loadOrders(), [loadOrders]);

    return (
        <InventoryResourceManagement<ProductionOrder, ProductionOrderApiStatus>
            title="Lệnh sản xuất"
            description="Quản lý các lệnh sản xuất thành phẩm từ định mức nguyên liệu (BOM)."
            searchPlaceholder="Tìm theo mã lệnh sản xuất..."
            columns={columns}
            items={orders}
            loading={loading}
            error={error}
            query={search}
            status={status}
            statusOptions={statusOptions}
            page={page}
            pageCount={totalPages}
            totalItems={totalItems}
            totalItemsLabel="Tổng số lệnh sản xuất"
            pageSize={pageSize}
            onQueryChange={(value) => {
                setSearch(value);
                setPage(1);
            }}
            onStatusChange={(value) => {
                setStatus(value);
                setPage(1);
            }}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(1);
            }}
            onRefresh={() => setRefreshKey((v) => v + 1)}
            detailPath={(item) => routePaths.productionOrderDetail.replace(":id", String(item.id))}
            renderCells={(item) => (
                <>
                    <TableCell strong>{item.orderCode}</TableCell>
                    <TableCell className="text-right pr-[18px]">{item.plannedQuantity}</TableCell>
                    <TableCell>
                        <StatusPill status={item.status} />
                    </TableCell>
                    <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                </>
            )}
            renderDetail={() => null}
            extraHeaderActions={
                <button
                    className="inline-flex h-12 items-center justify-center gap-[9px] rounded-lg border-0 bg-[#07883a] px-[22px] text-sm font-black text-white shadow-[0_10px_24px_rgba(7,136,58,0.24)] hover:opacity-90 transition-all cursor-pointer"
                    onClick={() => navigate(routePaths.productionOrderCreate)}
                    type="button"
                >
                    <Plus size={17} aria-hidden="true" />
                    Tạo lệnh sản xuất
                </button>
            }
        />
    );
}
