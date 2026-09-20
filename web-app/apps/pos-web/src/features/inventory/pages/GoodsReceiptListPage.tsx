import { Download, Filter, Plus, Upload } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { routePaths } from "../../../routes/path";
import {
    GoodsReceiptFilterBar,
    GoodsReceiptStatusBadge,
    QcBadge
} from "../components/GoodsReceiptComponents";
import {
    InventoryResourceManagement,
    TableCell,
    type Column,
    type InventoryFilter
} from "../components/InventoryResourceManagement";
import { useGoodsReceiptQueryState } from "../hooks/useGoodsReceiptQueryState";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptDashboardSummary,
    GoodsReceiptListResult,
    GoodsReceiptOptions,
    GoodsReceiptStatus,
    GoodsReceiptSummary
} from "../types/goods-receipt.types";
import {
    formatVietnamDateTime,
    formatVietnamNumber
} from "../utils/inventory-format";
import { exportGoodsReceiptsToExcel } from "../utils/goodsReceiptExcel";

const columns: Column[] = [
    // { key: "code", label: "Mã phiếu" },
    { key: "supplier", label: "Nhà cung cấp" },
    // { key: "po", label: "PO" },
    { key: "location", label: "Kho nhận" },
    { align: "right", key: "totalReceivedQuantity", label: "Tổng nhận" },
    // { key: "qcResult", label: "QC" },
    // { key: "costStatus", label: "Giá" },
    { key: "status", label: "Trạng thái" },
    { key: "receivedAt", label: "Ngày nhận" },
    // { key: "createdBy", label: "Người tạo" }
];

export function GoodsReceiptListPage() {
    const { query, resetQuery, setQuery } = useGoodsReceiptQueryState();
    const navigate = useNavigate();
    const [showFilters, setShowFilters] = useState(false);
    const [result, setResult] = useState<GoodsReceiptListResult | null>(null);
    const [summaryCounts, setSummaryCounts] = useState<GoodsReceiptDashboardSummary>({
        awaitingCost: 0,
        awaitingQc: 0,
        draft: 0,
        pendingApproval: 0,
        postedToday: 0,
        readyToPost: 0,
        receiving: 0,
        total: 0
    });
    const [options, setOptions] = useState<GoodsReceiptOptions>({
        locations: [],
        materials: [],
        suppliers: []
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        void goodsReceiptApi
            .getGoodsReceiptOptions()
            .then((res) => {
                setOptions(res);
            })
            .catch((err: unknown) => {
                console.error("[GoodsReceiptListPage] Lỗi tải danh mục (nhà cung cấp/kho):", err);
            });
    }, []);

    useEffect(() => {
        void goodsReceiptApi
            .getGoodsReceiptSummaryCounts()
            .then((counts) => {
                setSummaryCounts(counts);
            })
            .catch((err: unknown) => {
                console.error("[GoodsReceiptListPage] Lỗi tải tổng số đếm phiếu nhập:", err);
            });
    }, [refreshKey]);

    useEffect(() => {
        let active = true;

        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void goodsReceiptApi
            .listGoodsReceipts(query)
            .then((response) => {
                if (active) {
                    setResult(response);
                }
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(
                        loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải danh sách phiếu nhập."
                    );
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [query, refreshKey]);

    const retry = useCallback(() => {
        setRefreshKey((value) => value + 1);
    }, []);

    const receipts = result?.content ?? [];
    const activeFilterCount = [
        Boolean(query.receiptCode),
        query.supplierId !== "ALL",
        query.receivingLocationId !== "ALL",
        Boolean(query.purchaseOrderCode),
        query.qcResult !== "ALL",
        query.costStatus !== "ALL",
        query.hasVariance !== "ALL",
        Boolean(query.receivedFrom),
        Boolean(query.receivedTo)
    ].filter(Boolean).length;

    const statusOptions: Array<{ label: string; value: "ALL" | GoodsReceiptStatus }> = [
        { label: `Tất cả (${summaryCounts.total ?? 0})`, value: "ALL" },
        { label: `Bản nháp (${summaryCounts.draft ?? 0})`, value: "DRAFT" },
        { label: `Chờ duyệt (${summaryCounts.pendingApproval ?? 0})`, value: "PENDING_APPROVAL" },
        { label: "Đã duyệt (Chờ giao) (0)", value: "APPROVED" },
        { label: `Đang tiếp nhận (${summaryCounts.receiving ?? 0})`, value: "RECEIVING" },
        { label: `Chờ QC (${summaryCounts.awaitingQc ?? 0})`, value: "QC_PENDING" },
        { label: `Chờ ghi sổ (${summaryCounts.readyToPost ?? 0})`, value: "QC_COMPLETED" },
        { label: `Đã nhập kho (${summaryCounts.postedToday ?? 0})`, value: "POSTED" },
        { label: "Từ chối (0)", value: "REJECTED" },
        { label: "Đã hủy (0)", value: "CANCELLED" }
    ];

    const handleExportExcel = () => {
        try {
            if (receipts.length === 0) {
                alert("Không có dữ liệu phiếu nhập để xuất Excel!");
                return;
            }
            exportGoodsReceiptsToExcel(receipts);
        } catch (err) {
            console.error("Lỗi xuất Excel phiếu nhập kho:", err);
            alert("Có lỗi xảy ra khi xuất file Excel!");
        }
    };

    const filters: InventoryFilter[] | undefined = showFilters
        ? [
            {
                label: "Bộ lọc chi tiết",
                onChange: () => { },
                options: [],
                render: (
                    <div className="w-full pt-1">
                        <GoodsReceiptFilterBar
                            locations={options.locations}
                            onChange={setQuery}
                            onReset={resetQuery}
                            query={query}
                            suppliers={options.suppliers}
                        />
                    </div>
                ),
                value: ""
            }
        ]
        : undefined;

    return (
        <>
            <InventoryResourceManagement<GoodsReceiptSummary, GoodsReceiptStatus>
                columns={columns}
                description="Quản lý vòng đời phiếu nhập kho từ nhà cung cấp, kiểm định QC và theo dõi nguyên vật liệu."
                detailPath={(item) => `/management/inventory/receipts/${item.id}`}
                error={error}
                extraHeaderActions={
                    <div className="flex flex-wrap items-center gap-2">
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.goodsReceiptImportCreate)}>
                            <Upload size={17} aria-hidden="true" />
                            Nhập Excel
                        </ActionButton>
                        <ActionButton variant="secondary" onClick={handleExportExcel}>
                            <Download size={17} aria-hidden="true" />
                            Xuất Excel
                        </ActionButton>
                        <button
                            className={`inline-flex h-12 items-center justify-center gap-[9px] rounded-lg px-[18px] text-sm font-black transition-all cursor-pointer ${showFilters
                                    ? "border border-[#07883a] bg-[#f4faf5] text-[#07883a]"
                                    : "border border-[#dfe6ee] bg-white text-[#172317] hover:bg-neutral-50"
                                }`}
                            onClick={() => setShowFilters((prev) => !prev)}
                            type="button"
                        >
                            <Filter size={17} aria-hidden="true" />
                            <span>{showFilters ? "Ẩn bộ lọc" : "Hiện bộ lọc"}</span>
                            {activeFilterCount > 0 ? (
                                <span className="grid size-5 place-items-center rounded-full bg-[#07883a] text-[11px] font-black text-white">
                                    {activeFilterCount}
                                </span>
                            ) : null}
                        </button>
                        <Link
                            className="inline-flex h-12 items-center justify-center gap-[9px] rounded-lg border-0 bg-[#07883a] px-[22px] text-sm font-black text-white shadow-[0_10px_24px_rgba(7,136,58,0.24)] hover:opacity-90 transition-all cursor-pointer"
                            to="/management/inventory/receipts/new"
                        >
                            <Plus size={17} aria-hidden="true" />
                            <span>Tạo phiếu nhập mới</span>
                        </Link>
                    </div>
                }
                filters={filters}
                items={receipts}
                loading={loading}
                onPageChange={(page) => setQuery({ page: page - 1 })}
                onPageSizeChange={(size) => setQuery({ page: 0, size })}
                onQueryChange={(val) => setQuery({ page: 0, receiptCode: val })}
                onRefresh={retry}
                onStatusChange={(status) => setQuery({ page: 0, status })}
                page={query.page + 1}
                pageCount={result?.totalPages ?? 1}
                pageSize={query.size}
                query={query.receiptCode}
                renderCells={(item) => (
                    <>
                        <TableCell>{item.supplier.name}</TableCell>
                        <TableCell>{item.receivingLocation.name}</TableCell>
                        <TableCell className="text-right pr-[18px]">
                            {formatVietnamNumber(item.totalReceivedQuantity)}
                        </TableCell>
                        <TableCell>
                            <GoodsReceiptStatusBadge status={item.status} />
                        </TableCell>
                        <TableCell>{formatVietnamDateTime(item.receivedAt)}</TableCell>
                    </>
                )}
                renderDetail={(item) => (
                    <dl className="space-y-3 text-sm">
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Mã phiếu</dt>
                            <dd className="text-gray-900 font-semibold">{item.code}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Nhà cung cấp</dt>
                            <dd className="text-gray-900 font-semibold">{item.supplier.name}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Đơn đặt hàng (PO)</dt>
                            <dd className="text-gray-900 font-semibold">{item.purchaseOrderCode || "Ngoài PO"}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Kho nhận</dt>
                            <dd className="text-gray-900 font-semibold">{item.receivingLocation.name}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Số dòng nguyên liệu</dt>
                            <dd className="text-gray-900 font-semibold">{formatVietnamNumber(item.totalLines)} dòng</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Tổng lượng nhận</dt>
                            <dd className="text-gray-900 font-semibold">{formatVietnamNumber(item.totalReceivedQuantity)}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Trạng thái</dt>
                            <dd><GoodsReceiptStatusBadge status={item.status} /></dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Kết quả QC</dt>
                            <dd><QcBadge result={item.qcResult} /></dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Ngày nhận</dt>
                            <dd className="text-gray-900 font-semibold">{formatVietnamDateTime(item.receivedAt)}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Người tạo</dt>
                            <dd className="text-gray-900 font-semibold">{item.createdBy.name}</dd>
                        </div>
                    </dl>
                )}
                searchPlaceholder="Tìm kiếm theo mã phiếu nhập..."
                status={query.status}
                statusOptions={statusOptions}
                title="Phiếu nhập kho từ Nhà cung cấp"
                totalItems={result?.totalElements ?? 0}
                totalItemsLabel="Tổng số phiếu nhập"
            />
        </>
    );
}

