import { useCallback, useState } from "react";

import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../components/InventoryResourceManagement";
import { TransactionTypeBadge } from "../components/InventoryBadges";
import { useInventoryPage } from "../hooks/useInventoryResource";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";
import type {
    InventoryReferenceType,
    InventoryTransactionType
} from "../types/inventory.types";
import {
    formatInventoryDate,
    formatQuantity,
    formatReferenceType,
    referenceTypeLabels,
    transactionTypeLabels
} from "../utils/inventory-labels";

export function InventoryTransactionPage() {
    const [transactionCode, setTransactionCode] = useState("");
    const [type, setType] = useState<"ALL" | InventoryTransactionType>("ALL");
    const [referenceType, setReferenceType] = useState<"ALL" | InventoryReferenceType>("ALL");
    const [branchId] = useState("");
    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    const load = useCallback(
        () =>
            inventoryTransactionApi.storeList({
                page: page - 1,
                size: pageSize,
                transactionCode: transactionCode || undefined,
                type: type === "ALL" ? undefined : type,
                referenceType: referenceType === "ALL" ? undefined : referenceType,
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

    const columns: Column[] = [
        { key: "type", label: "Loại" },
        { key: "referenceType", label: "Loại tham chiếu" },
        { key: "createdAt", label: "Thời gian tạo" }
    ];

    return (
        <InventoryResourceManagement
            title="Lịch sử biến động tồn kho"
            description="Tra cứu các giao dịch nhập, bán, hoàn và điều chỉnh tồn kho."
            searchPlaceholder="Tìm theo mã giao dịch, mã tham chiếu..."
            totalItemsLabel="Tổng số giao dịch"
            items={transactions}
            loading={resource.loading}
            error={resource.error || ""}
            query={transactionCode}
            status="ALL"
            page={page}
            pageCount={resource.data?.totalPages ?? 1}
            totalItems={resource.data?.totalElements ?? 0}
            pageSize={pageSize}
            onQueryChange={(val) => {
                setTransactionCode(val);
                setPage(1);
            }}
            onPageChange={setPage}
            onPageSizeChange={(val) => {
                setPageSize(val);
                setPage(1);
            }}
            onRefresh={resource.refresh}
            columns={columns}
            filters={[
                {
                    label: "Loại tham chiếu",
                    isTop: true,
                    value: referenceType,
                    options: [
                        { label: "Tất cả loại tham chiếu", value: "ALL" },
                        ...Object.entries(referenceTypeLabels).map(([val, label]) => ({
                            label,
                            value: val
                        }))
                    ],
                    onChange: () => {},
                    render: (
                        <select
                            value={referenceType}
                            onChange={(e) => {
                                setReferenceType(e.target.value as any);
                                setPage(1);
                            }}
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer min-w-[170px]"
                        >
                            <option value="ALL">Tất cả loại tham chiếu</option>
                            {Object.entries(referenceTypeLabels).map(([val, label]) => (
                                <option key={val} value={val}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    )
                },
                {
                    label: "Khoảng thời gian",
                    isTop: true,
                    value: "ALL",
                    options: [],
                    onChange: () => {},
                    render: (
                        <div className="flex items-center gap-2">
                            <div className="flex items-center gap-1.5 min-w-[140px]">
                                <span className="text-xs font-bold text-[#536078] shrink-0">Từ:</span>
                                <input
                                    type="date"
                                    value={fromDate}
                                    onChange={(e) => {
                                        setFromDate(e.target.value);
                                        setPage(1);
                                    }}
                                    className="h-10 w-full rounded-xl border border-pos-border px-2 text-xs outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                />
                            </div>
                            <div className="flex items-center gap-1.5 min-w-[140px]">
                                <span className="text-xs font-bold text-[#536078] shrink-0">Đến:</span>
                                <input
                                    type="date"
                                    value={toDate}
                                    onChange={(e) => {
                                        setToDate(e.target.value);
                                        setPage(1);
                                    }}
                                    className="h-10 w-full rounded-xl border border-pos-border px-2 text-xs outline-none focus:border-hvt-green-700 bg-white text-[#172317] cursor-pointer"
                                />
                            </div>
                        </div>
                    )
                },
                {
                    label: "Loại giao dịch",
                    value: type,
                    options: [],
                    onChange: () => {},
                    render: (
                        <div className="flex flex-wrap items-center gap-2 pt-1">
                            <span className="text-[13px] font-[900] text-[#536078] mr-2 shrink-0">
                                Loại giao dịch:
                            </span>
                            <div className="flex flex-wrap gap-2 items-center">
                                <button
                                    type="button"
                                    onClick={() => {
                                        setType("ALL");
                                        setPage(1);
                                    }}
                                    className={`h-[30px] px-3 rounded-full text-[13px] font-[850] border transition-all cursor-pointer ${
                                        type === "ALL"
                                            ? "bg-[#07883a] border-[#07883a] text-white shadow-sm"
                                            : "bg-white border-[#dfe6ee] text-[#536078] hover:bg-neutral-50"
                                    }`}
                                >
                                    Tất cả
                                </button>
                                {Object.entries(transactionTypeLabels).map(([val, label]) => {
                                    const isActive = type === val;
                                    return (
                                        <button
                                            key={val}
                                            type="button"
                                            onClick={() => {
                                                setType(val as any);
                                                setPage(1);
                                            }}
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
                    )
                }
            ]}
            renderCells={(item: any) => (
                <>
                    <TableCell>
                        <TransactionTypeBadge type={item.type} />
                    </TableCell>
                    <TableCell>{formatReferenceType(item.referenceType)}</TableCell>
                    <TableCell>{formatInventoryDate(item.createdAt)}</TableCell>
                </>
            )}
            renderDetail={(item: any) => (
                <div className="grid gap-5 text-sm">
                    <dl className="grid gap-3">
                        <DetailItem label="Mã giao dịch" value={item.transactionCode} />
                        <DetailItem label="Loại giao dịch" value={transactionTypeLabels[item.type as keyof typeof transactionTypeLabels]} />
                        <DetailItem label="Loại tham chiếu" value={formatReferenceType(item.referenceType)} />
                        <DetailItem label="Mã tham chiếu" value={item.referenceId ? `#${item.referenceId}` : "-"} />
                        <DetailItem label="Chi nhánh" value={`#${item.branchId}`} />
                        <DetailItem label="Người tạo" value={`#${item.createdBy}`} />
                        <DetailItem label="Thời gian tạo" value={formatInventoryDate(item.createdAt)} />
                    </dl>
                    <div className="overflow-x-auto rounded-xl border border-pos-border">
                        <table className="min-w-[620px] w-full text-left text-xs">
                            <thead className="bg-neutral-50">
                                <tr>
                                    <th className="p-3">Mã hàng tồn kho</th>
                                    <th className="p-3">Vị trí</th>
                                    <th className="p-3">Số lượng thay đổi</th>
                                    <th className="p-3">Tồn trước</th>
                                    <th className="p-3">Tồn sau</th>
                                </tr>
                            </thead>
                            <tbody>
                                {item.lines.map((line: any) => (
                                    <tr key={line.id}>
                                        <td className="border-t p-3">#{line.inventoryItemId}</td>
                                        <td className="border-t p-3">#{line.locationId}</td>
                                        <td className="border-t p-3">{formatQuantity(line.quantity)}</td>
                                        <td className="border-t p-3">{formatQuantity(line.beforeQuantity)}</td>
                                        <td className="border-t p-3">{formatQuantity(line.afterQuantity)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        />
    );
}

function DetailItem({ label, value }: { label: string; value: string }) {
    return (
        <div className="border-b border-pos-border pb-2">
            <dt className="text-xs font-bold uppercase text-gray-500">{label}</dt>
            <dd className="mt-1 font-semibold text-gray-700">{value}</dd>
        </div>
    );
}
