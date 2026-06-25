import { Download, Plus, RefreshCw, Upload } from "lucide-react";
import { useMemo, useState } from "react";

import {
    bomAttentionItems,
    bomItems,
    bomSummaryMetrics
} from "../mock/bom.mock";
import {
    defaultBomFilters,
    filterBoms
} from "../utils/bom-filters";
import { ActionButton } from "../components/shared/ActionButton";
import { BomAttentionList } from "../components/bom/BomAttentionList";
import { BomDetailPanel } from "../components/bom/BomDetailPanel";
import { BomFilters } from "../components/bom/BomFilters";
import { BomSummary } from "../components/bom/BomSummary";
import { BomTable } from "../components/bom/BomTable";
import { Breadcrumbs } from "../components/shared/Breadcrumbs";
import { PageHeader } from "../components/shared/PageHeader";
import { Pagination } from "../components/shared/Pagination";
import { SearchBox } from "../components/shared/SearchBox";
import { TableToolbar } from "../components/shared/TableToolbar";
import type {
    BomFiltersState,
    BomListItem
} from "../types/bom.types";

export function BomListPage() {
    const [query, setQuery] = useState("");
    const [draftFilters, setDraftFilters] =
        useState<BomFiltersState>(defaultBomFilters);
    const [appliedFilters, setAppliedFilters] =
        useState<BomFiltersState>(defaultBomFilters);
    const [selectedBom, setSelectedBom] =
        useState<BomListItem | null>(bomItems[0] ?? null);
    const [checkedIds, setCheckedIds] = useState<string[]>([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const filteredBoms = useMemo(
        () => filterBoms(bomItems, query, appliedFilters),
        [query, appliedFilters]
    );

    const pageCount = Math.max(
        1,
        Math.ceil(filteredBoms.length / pageSize)
    );
    const safePage = Math.min(page, pageCount);
    const pagedBoms = filteredBoms.slice(
        (safePage - 1) * pageSize,
        safePage * pageSize
    );

    function applyFilters() {
        setAppliedFilters(draftFilters);
        setPage(1);
    }

    function resetFilters() {
        setDraftFilters(defaultBomFilters);
        setAppliedFilters(defaultBomFilters);
        setQuery("");
        setPage(1);
    }

    function toggleChecked(id: string) {
        setCheckedIds((current) =>
            current.includes(id)
                ? current.filter((item) => item !== id)
                : [...current, id]
        );
    }

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={["BOM & Sản xuất", "Danh sách BOM"]}
            />

            <PageHeader
                title="Danh sách BOM"
                description="Quản lý công thức sản xuất và định mức nguyên liệu."
                search={
                    <SearchBox
                        value={query}
                        placeholder="Tìm theo tên BOM, SKU, thành phẩm..."
                        onChange={(value) => {
                            setQuery(value);
                            setPage(1);
                        }}
                    />
                }
                actions={
                    <div className="flex flex-wrap gap-2">
                        <ActionButton variant="primary">
                            <Plus size={17} aria-hidden="true" />
                            Tạo BOM
                        </ActionButton>
                        <ActionButton>
                            <Upload size={16} aria-hidden="true" />
                            Import Excel
                        </ActionButton>
                        <ActionButton>
                            <Download size={16} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                        <ActionButton>
                            <RefreshCw
                                size={16}
                                aria-hidden="true"
                            />
                        </ActionButton>
                    </div>
                }
            />

            <BomSummary metrics={bomSummaryMetrics} />
            <BomAttentionList items={bomAttentionItems} />

            <div className="grid min-h-[680px] grid-cols-1 gap-4 xl:grid-cols-[240px_minmax(0,1fr)] min-[1800px]:grid-cols-[250px_minmax(0,1fr)_360px]">
                <div className="min-w-0">
                    <BomFilters
                        value={draftFilters}
                        onChange={setDraftFilters}
                        onApply={applyFilters}
                        onReset={resetFilters}
                    />
                </div>

                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    <TableToolbar
                        selectedCount={checkedIds.length}
                    />
                    <BomTable
                        items={pagedBoms}
                        selectedBomId={selectedBom?.id ?? ""}
                        checkedIds={checkedIds}
                        onRowClick={setSelectedBom}
                        onToggleCheck={toggleChecked}
                    />
                    <Pagination
                        page={safePage}
                        pageCount={pageCount}
                        totalItems={filteredBoms.length}
                        pageSize={pageSize}
                        onPageChange={setPage}
                        onPageSizeChange={(nextPageSize) => {
                            setPageSize(nextPageSize);
                            setPage(1);
                        }}
                    />
                </section>

                <div className="hidden min-h-0 min-[1800px]:block [&>aside]:h-full">
                    <BomDetailPanel
                        bom={selectedBom}
                        onClose={() => setSelectedBom(null)}
                    />
                </div>
            </div>
        </section>
    );
}
