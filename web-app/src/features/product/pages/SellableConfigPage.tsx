import { RefreshCw, SlidersHorizontal } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { DataTable, type DataTableColumn } from "../../../components/shared/list/DataTable";
import { Button } from "../../../components/ui/Button";
import { NativeSelect } from "../../../components/ui/Field";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { ActionLogPagination } from "../../action-log/components/ActionLogPagination";
import {
    InventoryErrorState,
    InventoryPageHeader
} from "../../inventory/components/InventoryManagementComponents";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { SellableConfigDialog } from "../components/SellableConfigDialog";
import { usePagedApiResource } from "../hooks/useApiResource";
import { productVariantApi } from "../services/productVariant.api";
import type { CatalogStatus, ProductVariant } from "../types/product.types";

const SEARCH_DEBOUNCE_MS = 350;

const quantityFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

function formatQuantity(value: number | null | undefined) {
    return quantityFormatter.format(value ?? 0);
}

function SellableLevelBadge({ variant }: { variant: ProductVariant }) {
    const sellable = variant.sellableQuantity ?? 0;
    const threshold = variant.productionThreshold ?? 0;

    if (sellable <= 0) {
        return <StatusBadge tone="danger">Hết lượng bán</StatusBadge>;
    }
    if (sellable <= threshold) {
        return <StatusBadge tone="warning">Dưới ngưỡng</StatusBadge>;
    }
    return <StatusBadge tone="success">Đủ bán</StatusBadge>;
}

export function SellableConfigPage() {
    const [keywordInput, setKeywordInput] = useState("");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState<"ALL" | CatalogStatus>("ALL");
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [editing, setEditing] = useState<ProductVariant | null>(null);
    const [notice, setNotice] = useState("");

    useEffect(() => {
        const timer = window.setTimeout(() => {
            setKeyword(keywordInput.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timer);
    }, [keywordInput]);

    const loadVariants = useCallback(
        () =>
            productVariantApi.list({
                page,
                size: pageSize,
                keyword: keyword || undefined,
                status: status === "ALL" ? undefined : status
            }),
        [keyword, page, pageSize, status]
    );
    const resource = usePagedApiResource(loadVariants);
    const rows = resource.data?.content ?? [];
    const hasFilters = keyword !== "" || status !== "ALL";

    const closeDialog = useCallback(() => setEditing(null), []);

    function handleSaved(variant: ProductVariant) {
        setEditing(null);
        setNotice(`Đã lưu cấu hình bán hàng cho ${variant.variantName}.`);
        resource.refresh();
    }

    function resetFilters() {
        setKeywordInput("");
        setStatus("ALL");
        setPage(0);
    }

    const columns: DataTableColumn<ProductVariant>[] = [
        {
            key: "variant",
            header: "Mặt hàng",
            render: (row) => (
                <div className="grid">
                    <span className="font-bold text-[#172317]">{row.variantName}</span>
                    <span className="font-mono text-[11px] text-[#8793a1]">{row.sku}</span>
                </div>
            )
        },
        { key: "status", header: "Trạng thái", render: (row) => <CatalogStatusBadge status={row.status} /> },
        {
            key: "sellable",
            header: "Có thể bán",
            align: "right",
            className: "font-bold tabular-nums text-[#172317]",
            render: (row) => formatQuantity(row.sellableQuantity)
        },
        {
            key: "threshold",
            header: "Ngưỡng sản xuất",
            align: "right",
            className: "tabular-nums text-[#172317]",
            render: (row) => formatQuantity(row.productionThreshold)
        },
        {
            key: "batch",
            header: "Mỗi mẻ sản xuất",
            align: "right",
            className: "tabular-nums text-[#172317]",
            render: (row) => formatQuantity(row.productionBatchQuantity)
        },
        { key: "level", header: "Mức bán", render: (row) => <SellableLevelBadge variant={row} /> },
        {
            key: "actions",
            header: "Thao tác",
            align: "right",
            render: (row) => (
                <Button
                    onClick={() => {
                        setNotice("");
                        setEditing(row);
                    }}
                    size="sm"
                >
                    <SlidersHorizontal aria-hidden="true" size={14} />
                    Cấu hình
                </Button>
            )
        }
    ];

    return (
        <section className="grid gap-5">
            <InventoryPageHeader
                actions={
                    <Button aria-label="Làm mới dữ liệu" disabled={resource.loading} onClick={resource.refresh}>
                        <RefreshCw
                            aria-hidden="true"
                            className={resource.loading ? "animate-spin" : ""}
                            size={16}
                        />
                        Làm mới
                    </Button>
                }
                description="Đặt số lượng có thể bán, ngưỡng yêu cầu sản xuất và số lượng mỗi mẻ cho từng Mặt hàng."
                eyebrow="Hệ thống / Cấu hình bán hàng"
                title="Cấu hình bán hàng"
            />

            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-[#e8eef0] bg-white px-4 py-3 shadow-pos-card">
                <SearchInput
                    className="min-w-[260px]"
                    controlSize="md"
                    onChange={setKeywordInput}
                    placeholder="Tìm theo tên sản phẩm, tên mặt hàng, SKU..."
                    type="search"
                    value={keywordInput}
                />
                <label className="flex items-center gap-2 text-[13px] font-[900] text-[#536078]">
                    Trạng thái
                    <span className="w-48">
                        <NativeSelect
                            onChange={(event) => {
                                setStatus(event.target.value as "ALL" | CatalogStatus);
                                setPage(0);
                            }}
                            value={status}
                        >
                            <option value="ALL">Tất cả</option>
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </NativeSelect>
                    </span>
                </label>
            </div>

            {notice ? (
                <p
                    className="rounded-xl border border-[#d8eadc] bg-[#f4faf5] px-4 py-3 text-sm font-bold text-[#07883a]"
                    role="status"
                >
                    {notice}
                </p>
            ) : null}

            {resource.error && !resource.data ? (
                <InventoryErrorState message={resource.error} onRetry={resource.refresh} />
            ) : (
                <div className="grid min-w-0 gap-3">
                    {resource.error ? (
                        <p
                            className="rounded-xl border border-red-100 bg-red-50 px-4 py-2 text-xs font-bold text-red-700"
                            role="alert"
                        >
                            {resource.error} Đang hiển thị dữ liệu của lần tải trước.
                        </p>
                    ) : null}

                    <DataTable
                        caption="Cấu hình bán hàng của các Mặt hàng"
                        columns={columns}
                        emptyAction={
                            hasFilters ? (
                                <Button onClick={resetFilters} size="sm" variant="link">
                                    Xóa bộ lọc
                                </Button>
                            ) : undefined
                        }
                        emptyTitle={
                            hasFilters
                                ? "Không có Mặt hàng nào khớp với bộ lọc hiện tại."
                                : "Chưa có Mặt hàng nào để cấu hình."
                        }
                        getRowKey={(row) => String(row.id)}
                        loading={!resource.data || resource.loading}
                        minWidth={980}
                        pagination={
                            resource.data ? (
                                <ActionLogPagination
                                    onPageChange={setPage}
                                    onPageSizeChange={(size) => {
                                        setPageSize(size);
                                        setPage(0);
                                    }}
                                    page={page}
                                    pageSize={pageSize}
                                    totalElements={resource.data.totalElements}
                                    totalPages={resource.data.totalPages}
                                />
                            ) : null
                        }
                        rows={rows}
                    />
                </div>
            )}

            {editing ? (
                <SellableConfigDialog
                    key={String(editing.id)}
                    onClose={closeDialog}
                    onSaved={handleSaved}
                    variant={editing}
                />
            ) : null}
        </section>
    );
}
