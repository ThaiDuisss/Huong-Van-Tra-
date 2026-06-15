import { Eye, History, RefreshCw } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    InventoryFilters,
    InventoryInput
} from "../components/InventoryFilters";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { routePaths } from "../../../routes/path";
import { formatInventoryDate, formatQuantity } from "../utils/inventory-labels";

export function InventoryBalancePage() {
    const [inventoryItemId, setInventoryItemId] = useState("");
    const [locationId, setLocationId] = useState("");
    const [branchId, setBranchId] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const load = useCallback(
        () =>
            inventoryBalanceApi.list({
                page: page - 1,
                size: pageSize,
                inventoryItemId: inventoryItemId
                    ? Number(inventoryItemId)
                    : undefined,
                locationId: locationId ? Number(locationId) : undefined,
                branchId: branchId ? Number(branchId) : undefined
            }),
        [branchId, inventoryItemId, locationId, page, pageSize]
    );
    const resource = useInventoryPage(load);
    const balances = useMemo(
        () => resource.data?.content ?? [],
        [resource.data?.content]
    );
    const metrics = useMemo(
        () => ({
            items: resource.data?.totalElements ?? 0,
            onHand: balances.reduce(
                (total, item) => total + item.quantityOnHand,
                0
            ),
            reserved: balances.reduce(
                (total, item) => total + item.quantityReserved,
                0
            ),
            low: balances.filter((item) => item.availableQuantity <= 0)
                .length
        }),
        [balances, resource.data?.totalElements]
    );

    return (
        <section className="grid gap-5">
            <Breadcrumbs items={["Quản lý tồn kho", "Tồn kho hiện tại"]} />
            <PageHeader
                title="Tồn kho hiện tại"
                description="Theo dõi số lượng tồn thực tế, giữ chỗ và có thể bán theo vị trí."
            />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 2xl:grid-cols-4">
                <Metric label="Tổng mặt hàng" value={formatQuantity(metrics.items)} />
                <Metric label="Tổng tồn thực tế" value={formatQuantity(metrics.onHand)} />
                <Metric label="Tổng tồn giữ chỗ" value={formatQuantity(metrics.reserved)} />
                <Metric label="Mặt hàng sắp hết" value={formatQuantity(metrics.low)} danger={metrics.low > 0} />
            </div>
            <div className="grid min-h-[620px] grid-cols-1 gap-4 xl:grid-cols-[240px_minmax(0,1fr)]">
                <InventoryFilters>
                    <InventoryInput label="Mã hàng tồn kho" type="number" value={inventoryItemId} onChange={(value) => { setInventoryItemId(value); setPage(1); }} />
                    <InventoryInput label="Mã vị trí" type="number" value={locationId} onChange={(value) => { setLocationId(value); setPage(1); }} />
                    <InventoryInput label="Mã chi nhánh" type="number" value={branchId} onChange={(value) => { setBranchId(value); setPage(1); }} />
                    <button type="button" onClick={resource.refresh} className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-sm font-bold"><RefreshCw size={15} />Tải lại dữ liệu</button>
                </InventoryFilters>
                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {resource.loading ? <InventoryLoading /> : null}
                    {!resource.loading && resource.error ? <InventoryError message={resource.error} onRetry={resource.refresh} /> : null}
                    {!resource.loading && !resource.error && balances.length === 0 ? <InventoryEmpty /> : null}
                    {!resource.loading && !resource.error && balances.length > 0 ? (
                        <div className="overflow-x-auto">
                            <table className="min-w-[1000px] w-full text-left text-sm">
                                <thead className="bg-neutral-50 text-xs text-gray-600"><tr><th className="px-4 py-3">Mã hàng tồn kho</th><th className="px-4 py-3">Mã vị trí</th><th className="px-4 py-3">Chi nhánh</th><th className="px-4 py-3">Tồn thực tế</th><th className="px-4 py-3">Tồn giữ chỗ</th><th className="px-4 py-3">Có thể bán</th><th className="px-4 py-3">Ngày cập nhật</th><th className="px-4 py-3 text-right">Thao tác</th></tr></thead>
                                <tbody>{balances.map((item) => <tr key={item.id}><Cell strong>#{item.inventoryItemId}</Cell><Cell>#{item.locationId}</Cell><Cell>#{item.branchId}</Cell><Cell>{formatQuantity(item.quantityOnHand)}</Cell><Cell>{item.quantityReserved > 0 ? <span className="rounded-lg bg-amber-50 px-2 py-1 font-bold text-amber-700">{formatQuantity(item.quantityReserved)}</span> : "0"}</Cell><Cell><strong className={item.availableQuantity <= 0 ? "text-red-600" : "text-emerald-700"}>{formatQuantity(item.availableQuantity)}</strong></Cell><Cell>{formatInventoryDate(item.updatedAt)}</Cell><Cell><div className="flex justify-end gap-1"><button type="button" title="Xem chi tiết" className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"><Eye size={16} /></button><Link to={routePaths.inventoryTransactions} title="Xem lịch sử biến động" className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100"><History size={16} /></Link></div></Cell></tr>)}</tbody>
                            </table>
                        </div>
                    ) : null}
                    {!resource.loading && !resource.error ? <Pagination page={(resource.data?.page ?? 0) + 1} pageCount={resource.data?.totalPages ?? 1} totalItems={resource.data?.totalElements ?? 0} pageSize={pageSize} onPageChange={setPage} onPageSizeChange={(value) => { setPageSize(value); setPage(1); }} /> : null}
                </section>
            </div>
        </section>
    );
}

function Metric({ label, value, danger = false }: { label: string; value: string; danger?: boolean }) {
    return <article className="rounded-2xl bg-white p-5 shadow-pos-card"><p className="text-sm font-semibold text-gray-600">{label}</p><strong className={`mt-2 block text-3xl ${danger ? "text-red-600" : "text-gray-900"}`}>{value}</strong></article>;
}

function Cell({ children, strong = false }: { children: React.ReactNode; strong?: boolean }) {
    return <td className={`border-t border-pos-border px-4 py-4 ${strong ? "font-bold" : ""}`}>{children}</td>;
}
