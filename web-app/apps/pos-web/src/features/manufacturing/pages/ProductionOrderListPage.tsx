import { Download, Plus, RefreshCw, Upload } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import {
    productionOrderAttentionItems,
    productionOrderMetrics,
    productionOrders
} from "../mock/production-orders.mock";
import {
    defaultProductionOrderFilters,
    filterProductionOrders
} from "../utils/production-order-filters";
import { ActionButton } from "../components/shared/ActionButton";
import { BomAttentionList } from "../components/bom/BomAttentionList";
import { BomSummary } from "../components/bom/BomSummary";
import { Breadcrumbs } from "../components/shared/Breadcrumbs";
import { PageHeader } from "../components/shared/PageHeader";
import { Pagination } from "../components/shared/Pagination";
import { ProductionOrderDetailPanel } from "../components/production-orders/ProductionOrderDetailPanel";
import { ProductionOrderFilters } from "../components/production-orders/ProductionOrderFilters";
import { ProductionOrderTable } from "../components/production-orders/ProductionOrderTable";
import { SearchBox } from "../components/shared/SearchBox";
import { TableToolbar } from "../components/shared/TableToolbar";
import type {
    ProductionOrderFiltersState,
    ProductionOrderListItem
} from "../types/production-order.types";

export function ProductionOrderListPage() {
    const navigate = useNavigate();
    const [query, setQuery] = useState("");
    const [draftFilters, setDraftFilters] =
        useState<ProductionOrderFiltersState>(
            defaultProductionOrderFilters
        );
    const [appliedFilters, setAppliedFilters] =
        useState<ProductionOrderFiltersState>(
            defaultProductionOrderFilters
        );
    const [selectedOrder, setSelectedOrder] =
        useState<ProductionOrderListItem | null>(
            productionOrders[0] ?? null
        );
    const [checkedIds, setCheckedIds] = useState<string[]>([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const filteredOrders = useMemo(
        () =>
            filterProductionOrders(
                productionOrders,
                query,
                appliedFilters
            ),
        [query, appliedFilters]
    );
    const pageCount = Math.max(
        1,
        Math.ceil(filteredOrders.length / pageSize)
    );
    const safePage = Math.min(page, pageCount);
    const pagedOrders = filteredOrders.slice(
        (safePage - 1) * pageSize,
        safePage * pageSize
    );

    function toggleChecked(id: string) {
        setCheckedIds((current) =>
            current.includes(id)
                ? current.filter((item) => item !== id)
                : [...current, id]
        );
    }

    function applyFilters() {
        setAppliedFilters(draftFilters);
        setPage(1);
    }

    function resetFilters() {
        setDraftFilters(defaultProductionOrderFilters);
        setAppliedFilters(defaultProductionOrderFilters);
        setQuery("");
        setPage(1);
    }

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={["BOM & Sản xuất", "Lệnh sản xuất"]}
            />

            <PageHeader
                title="Lệnh sản xuất"
                description="Quản lý các lệnh sản xuất thành phẩm từ BOM."
                search={
                    <SearchBox
                        value={query}
                        placeholder="Tìm theo mã lệnh, thành phẩm, BOM..."
                        onChange={(value) => {
                            setQuery(value);
                            setPage(1);
                        }}
                    />
                }
                actions={
                    <div className="flex flex-wrap gap-2">
                        <ActionButton
                            variant="primary"
                            onClick={() => navigate(routePaths.productionOrderCreate)}
                        >
                            <Plus size={17} aria-hidden="true" />
                            Tạo lệnh sản xuất
                        </ActionButton>
                        <ActionButton>
                            <Upload size={16} aria-hidden="true" />
                            Import
                        </ActionButton>
                        <ActionButton>
                            <Download size={16} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                        <ActionButton>
                            <RefreshCw size={16} aria-hidden="true" />
                        </ActionButton>
                    </div>
                }
            />

            <BomSummary metrics={productionOrderMetrics} />
            <BomAttentionList items={productionOrderAttentionItems} />

            <div className="grid min-h-[680px] grid-cols-1 gap-4 xl:grid-cols-[240px_minmax(0,1fr)] min-[1800px]:grid-cols-[250px_minmax(0,1fr)_360px]">
                <div className="min-w-0">
                    <ProductionOrderFilters
                        value={draftFilters}
                        onChange={setDraftFilters}
                        onApply={applyFilters}
                        onReset={resetFilters}
                    />
                </div>

                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <TableToolbar selectedCount={checkedIds.length} />
                    <ProductionOrderTable
                        items={pagedOrders}
                        selectedOrderId={selectedOrder?.id ?? ""}
                        checkedIds={checkedIds}
                        onRowClick={setSelectedOrder}
                        onToggleCheck={toggleChecked}
                    />
                    <Pagination
                        page={safePage}
                        pageCount={pageCount}
                        totalItems={filteredOrders.length}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={(nextPageSize) => {
                            setPageSize(nextPageSize);
                            setPage(1);
                        }}
                    />
                </section>

                <div className="hidden min-h-0 min-[1800px]:block [&>aside]:h-full">
                    <ProductionOrderDetailPanel
                        order={selectedOrder}
                        onClose={() => setSelectedOrder(null)}
                    />
                </div>
            </div>
        </section>
    );
}
