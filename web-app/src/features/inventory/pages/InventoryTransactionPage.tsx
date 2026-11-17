import { useCallback, useState } from "react";

import {
    Table,
    TableCell as UiTableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
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

function isUuid(val?: unknown): boolean {
    return typeof val === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);
}

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
            renderDetail={(item: any) => {
                const hasValidRefCode = item.referenceId && !isUuid(item.referenceId);
                const branchDisplay = item.branchName || (item.branchId && !isUuid(item.branchId) ? `#${item.branchId}` : null);
                const creatorDisplay = item.createdByName || (item.createdBy && !isUuid(item.createdBy) ? item.createdBy : "-");

                return (
                    <div className="grid gap-5 text-sm">
                        <dl className="grid gap-3">
                            <DetailItem label="Mã giao dịch" value={item.transactionCode} />
                            <DetailItem label="Loại giao dịch" value={transactionTypeLabels[item.type as keyof typeof transactionTypeLabels]} />
                            <DetailItem label="Loại tham chiếu" value={formatReferenceType(item.referenceType)} />
                            {hasValidRefCode && (
                                <DetailItem label="Mã tham chiếu" value={`#${item.referenceId}`} />
                            )}
                            {branchDisplay && (
                                <DetailItem label="Chi nhánh" value={branchDisplay} />
                            )}
                            <DetailItem label="Người tạo" value={creatorDisplay} />
                            <DetailItem label="Thời gian tạo" value={formatInventoryDate(item.createdAt)} />
                        </dl>
                        <div className="overflow-x-auto rounded-xl border border-pos-border">
                            <Table className="min-w-[620px]">
                                <TableHead>
                                    <TableRow>
                                        <TableHeaderCell>Mặt hàng</TableHeaderCell>
                                        <TableHeaderCell>Vị trí</TableHeaderCell>
                                        <TableHeaderCell>Số lượng thay đổi</TableHeaderCell>
                                        <TableHeaderCell>Tồn trước</TableHeaderCell>
                                        <TableHeaderCell>Tồn sau</TableHeaderCell>
                                    </TableRow>
                                </TableHead>
                                <tbody>
                                    {item.lines.map((line: any) => {
                                        const itemName = line.itemName;
                                        const itemCode = line.itemCode && !isUuid(line.itemCode) ? line.itemCode : null;
                                        const locationName = line.locationName;
                                        const locationCode = line.locationCode && !isUuid(line.locationCode) ? line.locationCode : null;

                                        return (
                                            <TableRow key={line.id}>
                                                <UiTableCell>
                                                    {itemName ? (
                                                        <div>
                                                            <div className="font-semibold text-gray-900">{itemName}</div>
                                                            {itemCode && (
                                                                <div className="text-[11px] text-gray-500 font-mono">#{itemCode}</div>
                                                            )}
                                                        </div>
                                                    ) : itemCode ? (
                                                        <div className="font-mono text-gray-900">#{itemCode}</div>
                                                    ) : (
                                                        <span className="text-gray-400">-</span>
                                                    )}
                                                </UiTableCell>
                                                <UiTableCell>
                                                    {locationName ? (
                                                        <div>
                                                            <div className="font-medium text-gray-900">{locationName}</div>
                                                            {locationCode && (
                                                                <div className="text-[11px] text-gray-500 font-mono">{locationCode}</div>
                                                            )}
                                                        </div>
                                                    ) : locationCode ? (
                                                        <div className="font-medium text-gray-900">{locationCode}</div>
                                                    ) : (
                                                        <span className="text-gray-400">-</span>
                                                    )}
                                                </UiTableCell>
                                                <UiTableCell>
                                                    <span className={line.quantity > 0 ? "font-bold text-emerald-600" : line.quantity < 0 ? "font-bold text-rose-600" : "text-gray-700"}>
                                                        {line.quantity > 0 ? `+${formatQuantity(line.quantity)}` : formatQuantity(line.quantity)}
                                                    </span>
                                                </UiTableCell>
                                                <UiTableCell>{formatQuantity(line.beforeQuantity)}</UiTableCell>
                                                <UiTableCell>{formatQuantity(line.afterQuantity)}</UiTableCell>
                                            </TableRow>
                                        );
                                    })}
                                </tbody>
                            </Table>
                        </div>
                    </div>
                );
            }}
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
