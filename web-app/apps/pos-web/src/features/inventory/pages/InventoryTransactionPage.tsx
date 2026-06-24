import { Eye } from "lucide-react";
import { useCallback, useState } from "react";

import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    InventoryFilters,
    InventoryInput,
    InventorySelect
} from "../components/InventoryFilters";
import { TransactionTypeBadge } from "../components/InventoryBadges";
import {
    InventoryEmpty,
    InventoryError,
    InventoryLoading
} from "../components/InventoryState";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";
import type {
    InventoryReferenceType,
    InventoryTransactionResponse,
    InventoryTransactionType
} from "../types/inventory.types";
import {
    formatInventoryDate,
    formatQuantity,
    referenceTypeLabels,
    transactionTypeLabels
} from "../utils/inventory-labels";

export function InventoryTransactionPage() {
    const [transactionCode, setTransactionCode] = useState("");
    const [type, setType] =
        useState<"ALL" | InventoryTransactionType>("ALL");
    const [referenceType, setReferenceType] =
        useState<"ALL" | InventoryReferenceType>("ALL");
    const [branchId, _setBranchId] = useState("");
    const [createdBy, setCreatedBy] = useState("");
    const [referenceId, setReferenceId] = useState("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selected, setSelected] =
        useState<InventoryTransactionResponse | null>(null);
    const load = useCallback(
        () =>
            inventoryTransactionApi.list({
                page: page - 1,
                size: pageSize,
                transactionCode: transactionCode || undefined,
                type: type === "ALL" ? undefined : type,
                referenceType:
                    referenceType === "ALL" ? undefined : referenceType,
                branchId: branchId ? branchId : undefined,
                createdBy: createdBy ? createdBy : undefined,
                referenceId: referenceId ? referenceId : undefined,
                fromDate: fromDate ? `${fromDate}T00:00:00` : undefined,
                toDate: toDate ? `${toDate}T23:59:59` : undefined
            }),
        [
            branchId,
            createdBy,
            fromDate,
            page,
            pageSize,
            referenceId,
            referenceType,
            toDate,
            transactionCode,
            type
        ]
    );
    const resource = useInventoryPage(load);
    const transactions = resource.data?.content ?? [];

    function resetPage(valueSetter: (value: string) => void, value: string) {
        valueSetter(value);
        setPage(1);
    }

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={["Quản lý tồn kho", "Lịch sử biến động tồn kho"]}
            />
            <PageHeader
                title="Lịch sử biến động tồn kho"
                description="Tra cứu các giao dịch nhập, bán, hoàn và điều chỉnh tồn kho."
            />
            <div className="grid min-h-[640px] grid-cols-1 gap-4 xl:grid-cols-[250px_minmax(0,1fr)] min-[1800px]:grid-cols-[250px_minmax(0,1fr)_380px]">
                <InventoryFilters>
                    <InventoryInput label="Mã giao dịch" value={transactionCode} onChange={(value: string) => resetPage(setTransactionCode, value)} />
                    <InventorySelect label="Loại giao dịch" value={type} options={[{ value: "ALL", label: "Tất cả" }, ...Object.entries(transactionTypeLabels).map(([value, label]) => ({ value, label }))]} onChange={(value) => { setType(value as "ALL" | InventoryTransactionType); setPage(1); }} />
                    <InventorySelect label="Loại tham chiếu" value={referenceType} options={[{ value: "ALL", label: "Tất cả" }, ...Object.entries(referenceTypeLabels).map(([value, label]) => ({ value, label }))]} onChange={(value) => { setReferenceType(value as "ALL" | InventoryReferenceType); setPage(1); }} />
                    <InventoryInput label="Mã tham chiếu" value={referenceId} onChange={(value: string) => resetPage(setReferenceId, value)} />
                    {/* <InventoryInput label="Chi nhánh" value={branchId} type="number" onChange={(value: string) => resetPage(setBranchId, value)} /> */}
                    <InventoryInput label="Người tạo" value={createdBy} onChange={(value: string) => resetPage(setCreatedBy, value)} />
                    <InventoryInput label="Từ ngày" value={fromDate} type="date" onChange={(value: string) => resetPage(setFromDate, value)} />
                    <InventoryInput label="Đến ngày" value={toDate} type="date" onChange={(value: string) => resetPage(setToDate, value)} />
                    {/* <button type="button" onClick={resource.refresh} className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-pos-border text-sm font-bold"><RefreshCw size={15} />Tải lại dữ liệu</button> */}
                </InventoryFilters>
                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {resource.loading ? <InventoryLoading /> : null}
                    {!resource.loading && resource.error ? <InventoryError message={resource.error} onRetry={resource.refresh} /> : null}
                    {!resource.loading && !resource.error && transactions.length === 0 ? <InventoryEmpty /> : null}
                    {!resource.loading && !resource.error && transactions.length > 0 ? (
                        <div className="overflow-x-auto">
                            <table className="min-w-[960px] w-full text-left text-sm">
                                <thead className="bg-neutral-50 text-xs text-gray-600"><tr><th className="px-4 py-3">Mã giao dịch</th><th className="px-4 py-3">Loại</th><th className="px-4 py-3">Loại tham chiếu</th><th className="px-4 py-3">Mã tham chiếu</th><th className="px-4 py-3">Chi nhánh</th><th className="px-4 py-3">Người tạo</th><th className="px-4 py-3">Thời gian tạo</th><th className="px-4 py-3 text-right">Thao tác</th></tr></thead>
                                <tbody>{transactions.map((item) => <tr key={item.id} onClick={() => setSelected(item)} className="cursor-pointer hover:bg-neutral-50"><Cell strong>{item.transactionCode}</Cell><Cell><TransactionTypeBadge type={item.type} /></Cell><Cell>{referenceTypeLabels[item.referenceType]}</Cell><Cell>{item.referenceId ? `#${item.referenceId}` : "-"}</Cell><Cell>#{item.branchId}</Cell><Cell>#{item.createdBy}</Cell><Cell>{formatInventoryDate(item.createdAt)}</Cell><Cell><button type="button" onClick={(event) => { event.stopPropagation(); setSelected(item); }} title="Xem chi tiết" className="ml-auto grid size-8 place-items-center rounded-lg hover:bg-neutral-100"><Eye size={16} /></button></Cell></tr>)}</tbody>
                            </table>
                        </div>
                    ) : null}
                    {!resource.loading && !resource.error ? <Pagination page={(resource.data?.page ?? 0) + 1} pageCount={resource.data?.totalPages ?? 1} totalItems={resource.data?.totalElements ?? 0} pageSize={pageSize} onPageChange={setPage} onPageSizeChange={(value) => { setPageSize(value); setPage(1); }} /> : null}
                </section>
                <div className="hidden min-h-0 min-[1800px]:block [&>aside]:h-full">
                    {selected ? (
                        <DetailPanel title="Chi tiết giao dịch" onClose={() => setSelected(null)}>
                            <div className="grid gap-5 text-sm">
                                <dl className="grid gap-3"><Detail label="Mã giao dịch" value={selected.transactionCode} /><Detail label="Loại giao dịch" value={transactionTypeLabels[selected.type]} /><Detail label="Loại tham chiếu" value={referenceTypeLabels[selected.referenceType]} /><Detail label="Mã tham chiếu" value={selected.referenceId ? `#${selected.referenceId}` : "-"} /><Detail label="Chi nhánh" value={`#${selected.branchId}`} /><Detail label="Người tạo" value={`#${selected.createdBy}`} /><Detail label="Thời gian tạo" value={formatInventoryDate(selected.createdAt)} /></dl>
                                <div className="overflow-x-auto rounded-xl border border-pos-border"><table className="min-w-[620px] w-full text-left text-xs"><thead className="bg-neutral-50"><tr><th className="p-3">Mã hàng tồn kho</th><th className="p-3">Vị trí</th><th className="p-3">Số lượng thay đổi</th><th className="p-3">Tồn trước</th><th className="p-3">Tồn sau</th></tr></thead><tbody>{selected.lines.map((line) => <tr key={line.id}><td className="border-t p-3">#{line.inventoryItemId}</td><td className="border-t p-3">#{line.locationId}</td><td className="border-t p-3">{formatQuantity(line.quantity)}</td><td className="border-t p-3">{formatQuantity(line.beforeQuantity)}</td><td className="border-t p-3">{formatQuantity(line.afterQuantity)}</td></tr>)}</tbody></table></div>
                            </div>
                        </DetailPanel>
                    ) : null}
                </div>
            </div>
        </section>
    );
}

function Cell({ children, strong = false }: { children: React.ReactNode; strong?: boolean }) {
    return <td className={`border-t border-pos-border px-4 py-4 ${strong ? "font-bold" : ""}`}>{children}</td>;
}

function Detail({ label, value }: { label: string; value: string }) {
    return <div className="border-b border-pos-border pb-2"><dt className="text-xs font-bold uppercase text-gray-500">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>;
}
