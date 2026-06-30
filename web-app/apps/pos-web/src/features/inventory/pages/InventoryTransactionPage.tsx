import { Eye, Search, MoreHorizontal } from "lucide-react";
import { useCallback, useState } from "react";

import { DetailPanel } from "../../manufacturing/components/shared/DetailPanel";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { Pagination } from "../../manufacturing/components/shared/Pagination";
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
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [selected, setSelected] =
        useState<InventoryTransactionResponse | null>(null);
    const [activeActionMenuId, setActiveActionMenuId] = useState<string | null>(null);
    const load = useCallback(
        () =>
            inventoryTransactionApi.storeList({
                page: page - 1,
                size: pageSize,
                transactionCode: transactionCode || undefined,
                type: type === "ALL" ? undefined : type,
                referenceType:
                    referenceType === "ALL" ? undefined : referenceType,
                branchId: branchId ? branchId : undefined,
                fromDate: fromDate ? `${fromDate}T00:00:00` : undefined,
                toDate: toDate ? `${toDate}T23:59:59` : undefined
            }),
        [
            branchId,
            fromDate,
            page,
            pageSize,
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
            {/* <Breadcrumbs
                items={["Quản lý tồn kho", "Lịch sử biến động tồn kho"]}
            /> */}
            <PageHeader
                title="Lịch sử biến động tồn kho"
                description="Tra cứu các giao dịch nhập, bán, hoàn và điều chỉnh tồn kho."
            />
            {/* Config & Filters Row */}
            <div className="flex flex-col rounded-2xl bg-white p-4 shadow-pos-card border border-neutral-100">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    {/* Search */}
                    <div className="relative w-full sm:w-[320px] md:w-[400px]">
                        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                        <input
                            type="text"
                            placeholder="Tìm theo mã giao dịch, mã tham chiếu..."
                            value={transactionCode}
                            onChange={(e) => resetPage(setTransactionCode, e.target.value)}
                            className="h-10 w-full rounded-xl border border-pos-border pl-9 pr-3 text-sm outline-none focus:border-hvt-green-700 bg-white"
                        />
                    </div>
                    {/* Time Filters Group */}
                    <div className="flex items-center gap-3">
                        {/* Filter: Từ ngày */}
                        <div className="flex items-center gap-1.5 min-w-[150px]">
                            <span className="text-xs font-bold text-[#536078] shrink-0">Từ:</span>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={(e) => resetPage(setFromDate, e.target.value)}
                                className="h-10 w-full rounded-xl border border-pos-border px-2 text-xs outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                            />
                        </div>
                        {/* Filter: Đến ngày */}
                        <div className="flex items-center gap-1.5 min-w-[150px]">
                            <span className="text-xs font-bold text-[#536078] shrink-0">Đến:</span>
                            <input
                                type="date"
                                value={toDate}
                                onChange={(e) => resetPage(setToDate, e.target.value)}
                                className="h-10 w-full rounded-xl border border-pos-border px-2 text-xs outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                            />
                        </div>
                    </div>
                </div>

                {/* Filters chips (giữ nguyên như cũ) */}
                <div className="flex flex-col gap-2 pt-3 border-t border-[#f3f5f7] mt-3">
                    {/* Loại giao dịch */}
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0 w-28">
                            Loại giao dịch:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => { setType("ALL"); setPage(1); }}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    type === "ALL"
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {Object.entries(transactionTypeLabels).map(([value, label]) => {
                                const isActive = type === value;
                                return (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => { setType(value as any); setPage(1); }}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Loại tham chiếu */}
                    <div className="flex flex-wrap items-center gap-2 pt-1">
                        <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0 w-28">
                            Loại tham chiếu:
                        </span>
                        <div className="flex flex-wrap gap-2 items-center">
                            <button
                                type="button"
                                onClick={() => { setReferenceType("ALL"); setPage(1); }}
                                className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                    referenceType === "ALL"
                                        ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                        : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                }`}
                            >
                                Tất cả
                            </button>
                            {Object.entries(referenceTypeLabels).map(([value, label]) => {
                                const isActive = referenceType === value;
                                return (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => { setReferenceType(value as any); setPage(1); }}
                                        className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                            isActive
                                                ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                                : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                        }`}
                                    >
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>

            <div className="grid gap-4 grid-cols-1">
                <section className="min-w-0 overflow-hidden rounded-2xl bg-white shadow-pos-card">
                    {resource.loading ? <InventoryLoading /> : null}
                    {!resource.loading && resource.error ? <InventoryError message={resource.error} onRetry={resource.refresh} /> : null}
                    {!resource.loading && !resource.error && transactions.length === 0 ? <InventoryEmpty /> : null}
                    {!resource.loading && !resource.error && transactions.length > 0 ? (
                        <div className="overflow-x-auto min-h-[280px]">
                            <table className="min-w-[960px] w-full text-left text-sm border-separate border-spacing-0">
                                <thead className="bg-[#eaf7ec] text-[13px] font-[950] text-gray-900 border-b border-[#edf0f4]">
                                    <tr>
                                        {/* <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] first:pl-[18px]">Mã giao dịch</th> */}
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Loại</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Loại tham chiếu</th>
                                        {/* <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Mã tham chiếu</th> */}
                                        {/* <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Chi nhánh</th> */}
                                        {/* <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Người tạo</th> */}
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4]">Thời gian tạo</th>
                                        <th className="h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px]">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {transactions.map((item, idx) => {
                                        const isSelected = selected?.id === item.id;
                                        const isEven = idx % 2 === 1;
                                        const rowBgClass = isSelected
                                            ? "bg-[#f0fbef] shadow-[inset_4px_0_0_#07883a]"
                                            : isEven
                                                ? "bg-[#f2faf1]/50"
                                                : "bg-white";

                                        return (
                                            <tr
                                                key={item.id}
                                                onClick={() => setSelected(item)}
                                                className={`cursor-pointer hover:bg-neutral-50 transition-all ${rowBgClass}`}
                                            >
                                                {/* <Cell strong>{item.transactionCode}</Cell> */}
                                                <Cell><TransactionTypeBadge type={item.type} /></Cell>
                                                <Cell>{referenceTypeLabels[item.referenceType]}</Cell>
                                                {/* <Cell>{item.referenceId ? `#${item.referenceId}` : "-"}</Cell> */}
                                                {/* <Cell>#{item.branchId}</Cell> */}
                                                {/* <Cell>#{item.createdBy}</Cell> */}
                                                <Cell>{formatInventoryDate(item.createdAt)}</Cell>
                                                <td className={`h-[52px] align-middle px-4 py-0 border-b border-[#edf0f4] text-right pr-[18px] relative ${activeActionMenuId === String(item.id) ? "z-30" : "z-20"}`} onClick={(e) => e.stopPropagation()}>
                                                    <div className="flex justify-end relative">
                                                        <button
                                                            type="button"
                                                            onClick={() => setActiveActionMenuId(activeActionMenuId === String(item.id) ? null : String(item.id))}
                                                            className="grid size-8 place-items-center rounded-lg hover:bg-neutral-100 text-gray-500 hover:text-gray-700 cursor-pointer"
                                                        >
                                                            <MoreHorizontal size={16} />
                                                        </button>

                                                        {activeActionMenuId === String(item.id) && (
                                                            <>
                                                                <div className="fixed inset-0 z-30" onClick={() => setActiveActionMenuId(null)} />
                                                                <div className={`absolute right-0 ${idx >= 2 && idx >= transactions.length - 2 ? "bottom-8 mb-1" : "top-8 mt-1"} w-36 rounded-xl border border-pos-border bg-white p-1.5 shadow-lg z-40 text-left`}>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => { setSelected(item); setActiveActionMenuId(null); }}
                                                                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#07883a] font-medium transition-colors cursor-pointer"
                                                                    >
                                                                        <Eye size={14} />
                                                                        Xem chi tiết
                                                                    </button>
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    ) : null}
                    {!resource.loading && !resource.error ? <Pagination page={(resource.data?.page ?? 0) + 1} pageCount={resource.data?.totalPages ?? 1} totalItems={resource.data?.totalElements ?? 0} pageSize={pageSize} onPageChange={setPage} onPageSizeChange={(value) => { setPageSize(value); setPage(1); }} /> : null}
                </section>
                {selected ? (
                    <div
                        className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-5"
                        onClick={() => setSelected(null)}
                        role="presentation"
                    >
                        <div
                            aria-modal="true"
                            className="w-full max-w-2xl max-h-[85vh] overflow-y-auto"
                            onClick={(event) => event.stopPropagation()}
                            role="dialog"
                        >
                            <DetailPanel title="Chi tiết giao dịch" onClose={() => setSelected(null)}>
                                <div className="grid gap-5 text-sm">
                                    <dl className="grid gap-3"><Detail label="Mã giao dịch" value={selected.transactionCode} /><Detail label="Loại giao dịch" value={transactionTypeLabels[selected.type]} /><Detail label="Loại tham chiếu" value={referenceTypeLabels[selected.referenceType]} /><Detail label="Mã tham chiếu" value={selected.referenceId ? `#${selected.referenceId}` : "-"} /><Detail label="Chi nhánh" value={`#${selected.branchId}`} /><Detail label="Người tạo" value={`#${selected.createdBy}`} /><Detail label="Thời gian tạo" value={formatInventoryDate(selected.createdAt)} /></dl>
                                    <div className="overflow-x-auto rounded-xl border border-pos-border"><table className="min-w-[620px] w-full text-left text-xs"><thead className="bg-neutral-50"><tr><th className="p-3">Mã hàng tồn kho</th><th className="p-3">Vị trí</th><th className="p-3">Số lượng thay đổi</th><th className="p-3">Tồn trước</th><th className="p-3">Tồn sau</th></tr></thead><tbody>{selected.lines.map((line) => <tr key={line.id}><td className="border-t p-3">#{line.inventoryItemId}</td><td className="border-t p-3">#{line.locationId}</td><td className="border-t p-3">{formatQuantity(line.quantity)}</td><td className="border-t p-3">{formatQuantity(line.beforeQuantity)}</td><td className="border-t p-3">{formatQuantity(line.afterQuantity)}</td></tr>)}</tbody></table></div>
                                </div>
                            </DetailPanel>
                        </div>
                    </div>
                ) : null}
            </div>
        </section>
    );
}

function Cell({ children, strong = false }: { children: React.ReactNode; strong?: boolean }) {
    return <td className={`border-b border-[#edf0f4] h-[52px] align-middle px-4 py-0 first:pl-[18px] ${strong ? "font-black text-[#172317]" : ""}`}>{children}</td>;
}

function Detail({ label, value }: { label: string; value: string }) {
    return <div className="border-b border-pos-border pb-2"><dt className="text-xs font-bold uppercase text-gray-500">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>;
}
