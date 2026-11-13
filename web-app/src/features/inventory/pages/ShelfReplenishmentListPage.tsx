import { Eye, Pencil, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../components/InventoryResourceManagement";
import { ShelfReplenishmentStatusBadge } from "../components/ShelfReplenishmentStatusBadge";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { shelfReplenishmentApi } from "../services/shelfReplenishment.api";
import type {
    ShelfReplenishment,
    ShelfReplenishmentSummary
} from "../types/shelfReplenishment.types";
import {
    formatReplenishmentDateTime,
    formatReplenishmentQuantity,
    shelfReplenishmentDetailPath,
    shelfReplenishmentEditPath,
    shelfReplenishmentStatusTabs,
    statusFilterToQuery,
    type ShelfReplenishmentStatusFilter
} from "../utils/shelfReplenishment.utils";

const SEARCH_DEBOUNCE_MS = 350;

type ReplenishmentStatusFilterOption = Exclude<ShelfReplenishmentStatusFilter, "ALL">;

const columns: Column[] = [
    { key: "documentCode", label: "Mã phiếu" },
    { key: "location", label: "Kho → Kệ" },
    { align: "right", key: "lineCount", label: "Số mặt hàng" },
    { align: "right", key: "totalQuantity", label: "Tổng số lượng" },
    { key: "status", label: "Trạng thái" },
    { key: "createdAt", label: "Tạo lúc" }
];

export function ShelfReplenishmentListPage() {
    const navigate = useNavigate();
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState<ShelfReplenishmentStatusFilter>("ALL");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    const [summary, setSummary] = useState<ShelfReplenishmentSummary | null>(null);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setKeyword(keywordInput.trim());
            setPage(1);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timer);
    }, [keywordInput]);

    const load = useCallback(
        () =>
            shelfReplenishmentApi.list({
                page: Math.max(0, page - 1),
                size: pageSize,
                query: keyword || undefined,
                ...statusFilterToQuery(status)
            }),
        [keyword, page, pageSize, status]
    );

    const resource = useInventoryPage<ShelfReplenishment>(load);
    const rows = resource.data?.content ?? [];

    useEffect(() => {
        let active = true;
        shelfReplenishmentApi
            .summary()
            .then((result: ShelfReplenishmentSummary) => {
                if (active) setSummary(result);
            })
            .catch((error: unknown) => console.error("Error loading shelf replenishment summary", error));
        return () => {
            active = false;
        };
    }, [resource.data]);

    const statusOptions = useMemo<Array<{ label: string; value: ShelfReplenishmentStatusFilter }>>(() => {
        return shelfReplenishmentStatusTabs.map((tab) => {
            const count = tab.count && summary ? summary[tab.count] : null;
            return {
                label: count !== null ? `${tab.label} (${count})` : tab.label,
                value: tab.value
            };
        });
    }, [summary]);

    return (
        <InventoryResourceManagement<ShelfReplenishment, ReplenishmentStatusFilterOption>
            columns={columns}
            description="Chuyển hàng từ kho lên kệ bán. Phiếu do POS tạo khi kệ thiếu hàng cũng nằm ở đây."
            detailPath={(item) => shelfReplenishmentDetailPath(item.id)}
            error={resource.error}
            extraHeaderActions={
                <div className="flex flex-wrap items-center gap-2">
                    <ActionButton
                        onClick={() => navigate(routePaths.shelfReplenishmentCreate)}
                        variant="primary"
                    >
                        <Plus aria-hidden="true" size={16} />
                        Tạo phiếu nhập kệ
                    </ActionButton>
                </div>
            }
            items={rows}
            loading={resource.loading}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(1);
            }}
            onQueryChange={setKeywordInput}
            onRefresh={resource.refresh}
            onStatusChange={(val) => {
                setStatus(val);
                setPage(1);
            }}
            page={page}
            pageCount={resource.data?.totalPages ?? 1}
            pageSize={pageSize}
            query={keywordInput}
            renderCells={(item) => (
                <>
                    <TableCell strong className="font-mono text-[#07883a]">
                        <Link
                            className="hover:underline"
                            onClick={(event) => event.stopPropagation()}
                            to={shelfReplenishmentDetailPath(item.id)}
                        >
                            {item.documentCode}
                        </Link>
                    </TableCell>
                    <TableCell>
                        <span className="font-semibold text-[#172317]">
                            {item.warehouseName ?? "—"} → {item.shelfName ?? "—"}
                        </span>
                    </TableCell>
                    <TableCell className="text-right pr-[18px]">
                        <span className="tabular-nums text-[#172317]">{item.lineCount}</span>
                    </TableCell>
                    <TableCell className="text-right pr-[18px]">
                        <span className="font-bold tabular-nums text-[#172317]">
                            {formatReplenishmentQuantity(item.totalQuantity)}
                        </span>
                    </TableCell>
                    <TableCell>
                        <ShelfReplenishmentStatusBadge
                            pendingReview={item.pendingReview}
                            status={item.status}
                        />
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                        <span className="tabular-nums text-[#536078]">
                            {formatReplenishmentDateTime(item.createdAt)}
                        </span>
                    </TableCell>
                </>
            )}
            renderDetail={(item) => (
                <dl className="space-y-3 text-sm">
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Mã phiếu</dt>
                        <dd className="text-gray-900 font-mono font-bold text-[#07883a]">{item.documentCode}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Kho → Kệ</dt>
                        <dd className="text-gray-900 font-semibold">{item.warehouseName ?? "—"} → {item.shelfName ?? "—"}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Số mặt hàng</dt>
                        <dd className="text-gray-900 font-semibold">{item.lineCount}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Tổng số lượng</dt>
                        <dd className="text-gray-900 font-bold">{formatReplenishmentQuantity(item.totalQuantity)}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Trạng thái</dt>
                        <dd><ShelfReplenishmentStatusBadge pendingReview={item.pendingReview} status={item.status} /></dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Tạo lúc</dt>
                        <dd className="text-gray-900 font-semibold">{formatReplenishmentDateTime(item.createdAt)}</dd>
                    </div>
                    {item.note && (
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Ghi chú</dt>
                            <dd className="text-gray-900 font-medium">{item.note}</dd>
                        </div>
                    )}
                </dl>
            )}
            getExtraRowActions={(item) => [
                {
                    icon: <Eye size={14} />,
                    label: "Xem chi tiết",
                    onSelect: () => navigate(shelfReplenishmentDetailPath(item.id))
                },
                ...(item.status === "DRAFT"
                    ? [{
                        icon: <Pencil size={14} />,
                        label: "Chỉnh sửa",
                        onSelect: () => navigate(shelfReplenishmentEditPath(item.id))
                    }]
                    : [])
            ]}
            searchPlaceholder="Tìm theo mã phiếu, mã đơn tham chiếu, ghi chú..."
            status={status}
            statusOptions={statusOptions}
            title="Phiếu nhập kệ"
            totalItems={resource.data?.totalElements ?? 0}
            totalItemsLabel="Tổng số phiếu nhập kệ"
        />
    );
}
