import { ApiError } from "@hvt/api-client";
import { Download, Eye, Filter, Pencil, Plus, Trash2, Upload } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuthorization } from "../../../auth/useAuthorization";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { routePaths } from "../../../routes/path";
import {
    GoodsReceiptFilterBar,
    GoodsReceiptStatusBadge
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
    GoodsReceiptListItem,
    GoodsReceiptListResult,
    GoodsReceiptStatus
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

const receiptDetailPath = (item: GoodsReceiptListItem) => `/management/inventory/receipts/${item.id}`;

function getDeleteErrorMessage(error: unknown): string {
    if (error instanceof ApiError) {
        if (error.status === 403) {
            return "Bạn không có quyền xóa phiếu nhập kho này. Chỉ ADMIN hoặc người tạo phiếu mới được xóa.";
        }
        if (error.status === 404) {
            return "Phiếu nhập kho không tồn tại hoặc đã bị xóa.";
        }
        if (error.status === 400) {
            return error.message || "Chỉ có thể xóa phiếu nhập kho ở trạng thái Bản nháp.";
        }
    }
    return "Không thể xóa phiếu nhập kho. Vui lòng thử lại sau.";
}

export function GoodsReceiptListPage() {
    const { query, resetQuery, setQuery } = useGoodsReceiptQueryState();
    const navigate = useNavigate();
    const { isAdmin, keycloakSubject } = useAuthorization();
    const [deleteTarget, setDeleteTarget] = useState<GoodsReceiptListItem | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");
    const [toast, setToast] = useState("");
    const [exporting, setExporting] = useState(false);
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
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

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

    // createdBy lưu Keycloak subject (JWT sub) nên so sánh với keycloakSubject, không phải user.id
    const currentUserSubject = keycloakSubject;
    const isAdminOrCreator = (item: GoodsReceiptListItem) =>
        isAdmin() || (Boolean(currentUserSubject) && item.createdById === currentUserSubject);
    const canEditReceipt = (item: GoodsReceiptListItem) =>
        (item.status === "DRAFT" || item.status === "REJECTED") && isAdminOrCreator(item);
    const canDeleteReceipt = (item: GoodsReceiptListItem) =>
        item.status === "DRAFT" && isAdminOrCreator(item);

    const confirmDelete = async () => {
        if (!deleteTarget || deleting) return;
        setDeleting(true);
        setDeleteError("");
        try {
            await goodsReceiptApi.deleteGoodsReceipt(deleteTarget.id);
            setDeleteTarget(null);
            setToast(`Đã xóa phiếu nhập kho ${deleteTarget.code}.`);
            window.setTimeout(() => setToast(""), 2500);
            retry();
        } catch (err: unknown) {
            setDeleteError(getDeleteErrorMessage(err));
        } finally {
            setDeleting(false);
        }
    };

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
        { label: `QC hoàn thành (${summaryCounts.readyToPost ?? 0})`, value: "QC_COMPLETED" },
        { label: `Đã nhập kho (${summaryCounts.postedToday ?? 0})`, value: "POSTED" },
        { label: "Từ chối (0)", value: "REJECTED" },
        { label: "Đã hủy (0)", value: "CANCELLED" }
    ];

    // Xuất toàn bộ phiếu khớp bộ lọc hiện tại qua API riêng, không chỉ trang đang xem
    const handleExportExcel = async () => {
        if (exporting) return;
        setExporting(true);
        try {
            const rows = await goodsReceiptApi.exportGoodsReceipts(query);
            if (rows.length === 0) {
                alert("Không có dữ liệu phiếu nhập để xuất Excel!");
                return;
            }
            exportGoodsReceiptsToExcel(rows);
        } catch (err) {
            console.error("Lỗi xuất Excel phiếu nhập kho:", err);
            alert("Có lỗi xảy ra khi xuất file Excel!");
        } finally {
            setExporting(false);
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
                            onChange={setQuery}
                            onReset={resetQuery}
                            query={query}
                        />
                    </div>
                ),
                value: ""
            }
        ]
        : undefined;

    return (
        <>
            <InventoryResourceManagement<GoodsReceiptListItem, GoodsReceiptStatus>
                columns={columns}
                description="Quản lý vòng đời phiếu nhập kho từ nhà cung cấp, kiểm định QC và theo dõi nguyên vật liệu."
                detailPath={receiptDetailPath}
                error={error}
                extraHeaderActions={
                    <div className="flex flex-wrap items-center gap-2 w-full justify-start sm:w-auto sm:justify-end">
                        <ActionButton variant="secondary" onClick={() => navigate(routePaths.goodsReceiptImportCreate)}>
                            <Upload size={17} aria-hidden="true" />
                            Nhập Excel
                        </ActionButton>
                        <ActionButton disabled={exporting} variant="secondary" onClick={() => void handleExportExcel()}>
                            <Download size={17} aria-hidden="true" />
                            {exporting ? "Đang xuất..." : "Xuất Excel"}
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
                        <TableCell>{item.supplierName}</TableCell>
                        <TableCell>{item.receivingLocationName}</TableCell>
                        <TableCell className="text-right pr-[18px]">
                            {formatVietnamNumber(item.totalReceivedQuantity)}
                        </TableCell>
                        <TableCell>
                            <GoodsReceiptStatusBadge status={item.status} />
                        </TableCell>
                        <TableCell>{item.receivedAt ? formatVietnamDateTime(item.receivedAt) : "—"}</TableCell>
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
                            <dd className="text-gray-900 font-semibold">{item.supplierName}</dd>
                        </div>
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Kho nhận</dt>
                            <dd className="text-gray-900 font-semibold">{item.receivingLocationName}</dd>
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
                            <dt className="text-gray-500 font-bold uppercase text-xs">Ngày nhận</dt>
                            <dd className="text-gray-900 font-semibold">{item.receivedAt ? formatVietnamDateTime(item.receivedAt) : "—"}</dd>
                        </div>
                    </dl>
                )}
                renderExtraRowActions={(item, closeMenu) => (
                    <>
                        <button
                            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#159734] font-medium transition-colors cursor-pointer"
                            onClick={() => {
                                closeMenu();
                                navigate(receiptDetailPath(item));
                            }}
                            type="button"
                        >
                            <Eye size={14} />
                            Xem chi tiết
                        </button>
                        {canEditReceipt(item) ? (
                            <button
                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#159734] font-medium transition-colors cursor-pointer"
                                onClick={() => {
                                    closeMenu();
                                    navigate(`/management/inventory/receipts/${item.id}/edit`);
                                }}
                                type="button"
                            >
                                <Pencil size={14} />
                                Chỉnh sửa
                            </button>
                        ) : null}
                        {canDeleteReceipt(item) ? (
                            <button
                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-red-600 hover:bg-red-50 font-medium transition-colors cursor-pointer"
                                onClick={() => {
                                    closeMenu();
                                    setDeleteError("");
                                    setDeleteTarget(item);
                                }}
                                type="button"
                            >
                                <Trash2 size={14} />
                                Xóa
                            </button>
                        ) : null}
                    </>
                )}
                searchPlaceholder="Tìm kiếm theo mã phiếu nhập..."
                status={query.status}
                statusOptions={statusOptions}
                title="Phiếu nhập kho từ Nhà cung cấp"
                totalItems={result?.totalElements ?? 0}
                totalItemsLabel="Tổng số phiếu nhập"
            />

            {deleteTarget ? (
                <ConfirmDialog
                    confirmLabel="Xóa phiếu"
                    confirmVariant="danger"
                    description={`Bạn có chắc chắn muốn xóa phiếu nhập kho ${deleteTarget.code} không?`}
                    error={deleteError}
                    onCancel={() => setDeleteTarget(null)}
                    onConfirm={() => void confirmDelete()}
                    submitting={deleting}
                    title="Xóa phiếu nhập kho"
                />
            ) : null}

            {toast ? (
                <div className="fixed bottom-5 right-5 z-50 flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white shadow-xl">
                    <span>{toast}</span>
                </div>
            ) : null}
        </>
    );
}

