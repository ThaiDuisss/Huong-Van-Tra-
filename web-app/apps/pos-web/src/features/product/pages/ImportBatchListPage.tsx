import { Eye } from "lucide-react";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { routePaths } from "../../../routes/path";
import {
    InventoryResourceManagement,
    TableCell,
    type Column
} from "../../inventory/components/InventoryResourceManagement";
import { StatusPill } from "../components/ImportBatchPanel";
import { usePagedApiResource } from "../hooks/useApiResource";
import { importBatchApi as defaultImportBatchApi, type ImportBatchApi } from "../services/importBatch.api";
import type { ImportBatch, ImportBatchStatus, ImportType } from "../types/import-batch.types";

const DEFAULT_IMPORT_TYPE_LABEL: Record<string, string> = {
    PRODUCT: "Sản phẩm",
    PRODUCT_VARIANT: "Mặt hàng"
};

// Mỗi service tự quản lý bảng import_batches riêng (Catalog-Service, Inventory-Service...) — trang này (mặc định)
// chỉ gọi API của Catalog-Service nên không có lựa chọn "Tất cả" (sẽ phải gộp nhiều API khác nhau, không hợp lý).
// Sản phẩm và Mặt hàng đều cùng Catalog-Service nên chuyển qua lại vẫn chỉ 1 API call, không phải gộp service khác.
// Service khác (vd Inventory) truyền `importTypeOptions` riêng (1 phần tử) để tái dùng trang này mà không cần bộ lọc.
const DEFAULT_IMPORT_TYPE_OPTIONS: Array<{ label: string; value: ImportType }> = [
    { label: "Sản phẩm", value: "PRODUCT" },
    { label: "Mặt hàng", value: "PRODUCT_VARIANT" }
];

const columns: Column[] = [
    // { key: "batchCode", label: "Mã phiếu" },
    { key: "importType", label: "Loại dữ liệu" },
    { key: "sourceFileName", label: "File nguồn" },
    { key: "status", label: "Trạng thái" },
    { key: "createdAt", label: "Ngày tạo" }
];

const statusOptions: Array<{ label: string; value: "ALL" | ImportBatchStatus }> = [
    { label: "Tất cả", value: "ALL" },
    { label: "Bản nháp", value: "DRAFT" },
    { label: "Chờ duyệt", value: "PENDING_APPROVAL" },
    { label: "Đã duyệt", value: "APPROVED" },
    { label: "Bị từ chối", value: "REJECTED" }
];

function formatDateTime(value: string | null): string {
    if (!value) return "—";
    return new Date(value).toLocaleString("vi-VN");
}

interface ImportBatchListPageProps {
    /** API import-batch dùng cho trang này — mặc định trỏ Catalog, service khác truyền bộ riêng qua createImportBatchApi(). */
    api?: ImportBatchApi;
    /** Các loại import hiển thị được ở trang này — mặc định 2 loại của Catalog. Chỉ 1 phần tử thì ẩn luôn bộ lọc "Loại dữ liệu". */
    importTypeOptions?: Array<{ label: string; value: ImportType }>;
    /** Nhãn hiển thị cho từng loại import. */
    typeLabels?: Record<string, string>;
    /** Route pattern có ":batchId" để điều hướng sang trang chi tiết. */
    detailRoute?: string;
    title?: string;
}

export function ImportBatchListPage({
    api = defaultImportBatchApi,
    importTypeOptions = DEFAULT_IMPORT_TYPE_OPTIONS,
    typeLabels = DEFAULT_IMPORT_TYPE_LABEL,
    detailRoute = routePaths.importExcelDetail,
    title = "Yêu cầu nhập Excel"
}: ImportBatchListPageProps = {}) {
    const navigate = useNavigate();
    const { userId } = useAuth();
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [status, setStatus] = useState<"ALL" | ImportBatchStatus>("ALL");
    const [importType, setImportType] = useState<ImportType>(importTypeOptions[0].value);
    const [searchTerm, setSearchTerm] = useState("");

    const loadBatches = useCallback(
        () =>
            api.list({
                page: page - 1,
                size: pageSize,
                status: status === "ALL" ? undefined : status,
                type: importType
            }),
        [api, page, pageSize, status, importType]
    );
    const resource = usePagedApiResource(loadBatches);

    const allBatches = resource.data?.content ?? [];
    const batches = searchTerm.trim()
        ? allBatches.filter((b) => b.batchCode.toLowerCase().includes(searchTerm.trim().toLowerCase()))
        : allBatches;

    async function handleDelete(item: ImportBatch) {
        const isEditable = item.status === "DRAFT" || item.status === "REJECTED";
        const isOwner = Boolean(userId) && item.createdBy === userId;
        if (!isEditable || !isOwner) {
            throw new Error(
                "Bạn chỉ có thể xóa phiếu import do chính mình tạo và đang ở trạng thái Nháp hoặc Bị từ chối."
            );
        }
        await api.deleteBatch(String(item.id));
    }

    return (
        <InventoryResourceManagement<ImportBatch, ImportBatchStatus>
            columns={columns}
            description="Theo dõi toàn bộ phiếu nhập dữ liệu từ Excel — bản nháp, đang chờ duyệt, đã duyệt và bị từ chối."
            detailPath={(item) => detailRoute.replace(":batchId", String(item.id))}
            error={resource.error}
            secondaryFilter={
                importTypeOptions.length > 1
                    ? {
                          label: "Loại dữ liệu",
                          value: importType,
                          options: importTypeOptions,
                          onChange: (value) => {
                              setImportType(value as ImportType);
                              setPage(1);
                          }
                      }
                    : undefined
            }
            items={batches}
            loading={resource.loading}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(1);
            }}
            onDelete={handleDelete}
            onQueryChange={setSearchTerm}
            onRefresh={resource.refresh}
            onStatusChange={(value) => {
                setStatus(value);
                setPage(1);
            }}
            page={(resource.data?.page ?? 0) + 1}
            pageCount={resource.data?.totalPages ?? 1}
            pageSize={pageSize}
            query={searchTerm}
            renderExtraRowActions={(item, closeMenu) => (
                <button
                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-[#172317] hover:bg-[#f0fbef] hover:text-[#159734] font-medium transition-colors cursor-pointer"
                    onClick={() => {
                        closeMenu();
                        navigate(detailRoute.replace(":batchId", String(item.id)));
                    }}
                    type="button"
                >
                    <Eye size={14} aria-hidden="true" />
                    Xem chi tiết
                </button>
            )}
            renderCells={(item) => (
                <>
                    {/* <TableCell strong>{item.batchCode}</TableCell> */}
                    <TableCell>{typeLabels[item.importType] ?? item.importType}</TableCell>
                    <TableCell>{item.sourceFileName ?? "—"}</TableCell>
                    <TableCell>
                        <StatusPill status={item.status} />
                    </TableCell>
                    <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                </>
            )}
            renderDetail={(item) => (
                <dl className="space-y-3 text-sm">
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Mã phiếu</dt>
                        <dd className="text-gray-900 font-semibold font-mono">{item.batchCode}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Loại dữ liệu</dt>
                        <dd className="text-gray-900 font-semibold">{typeLabels[item.importType] ?? item.importType}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">File nguồn</dt>
                        <dd className="text-gray-900 font-semibold">{item.sourceFileName ?? "—"}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Trạng thái</dt>
                        <dd><StatusPill status={item.status} /></dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Ngày tạo</dt>
                        <dd className="text-gray-900 font-semibold">{formatDateTime(item.createdAt)}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Gửi duyệt lúc</dt>
                        <dd className="text-gray-900 font-semibold">{formatDateTime(item.submittedAt)}</dd>
                    </div>
                    <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                        <dt className="text-gray-500 font-bold uppercase text-xs">Duyệt lúc</dt>
                        <dd className="text-gray-900 font-semibold">{formatDateTime(item.approvedAt)}</dd>
                    </div>
                    {item.status === "REJECTED" ? (
                        <div className="flex justify-between py-2 border-b border-dashed border-neutral-200">
                            <dt className="text-gray-500 font-bold uppercase text-xs">Lý do từ chối</dt>
                            <dd className="text-gray-900 font-semibold">{item.rejectionReason ?? "—"}</dd>
                        </div>
                    ) : null}
                </dl>
            )}
            searchPlaceholder="Tìm theo mã phiếu..."
            status={status}
            statusOptions={statusOptions}
            title={title}
            totalItems={resource.data?.totalElements ?? 0}
            totalItemsLabel="Tổng số phiếu import"
        />
    );
}
