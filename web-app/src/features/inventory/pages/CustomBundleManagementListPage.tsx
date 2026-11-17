import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { DataTable, type DataTableColumn } from "../../../components/shared/list/DataTable";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    customBundlePackingStatusLabels,
    customBundlePackingStatusTones,
    customBundlePackingTabs
} from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type {
    CustomBundlePacking,
    CustomBundlePackingPage,
    CustomBundlePackingQuery,
    CustomBundlePackingStatus
} from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Không tải được danh sách đóng gói.";
}

const columns: DataTableColumn<CustomBundlePacking>[] = [
    {
        key: "packingCode",
        header: "Mã đóng gói",
        className: "font-black",
        render: (packing) => (
            <Link className="text-[#159734] hover:underline" to={`/management/inventory/custom-bundles/${packing.id}`}>
                {packing.packingCode}
            </Link>
        )
    },
    { key: "orderCode", header: "Mã đơn", render: (packing) => packing.orderCode ?? "-" },
    { key: "bundleName", header: "Tên bundle", render: (packing) => packing.bundleName ?? "-" },
    {
        key: "expectedTotalWeight",
        header: "Khối lượng",
        render: (packing) => formatVietnamNumber(packing.expectedTotalWeight)
    },
    {
        key: "status",
        header: "Trạng thái",
        render: (packing) => (
            <StatusBadge tone={customBundlePackingStatusTones[packing.status]}>
                {customBundlePackingStatusLabels[packing.status]}
            </StatusBadge>
        )
    },
    {
        key: "expectedAvailableAt",
        header: "Dự kiến đủ nguyên liệu",
        render: (packing) => (packing.expectedAvailableAt ? formatVietnamDateTime(packing.expectedAvailableAt) : "-")
    },
    {
        key: "createdAt",
        header: "Tạo lúc",
        render: (packing) => (packing.createdAt ? formatVietnamDateTime(packing.createdAt) : "-")
    }
];

/** Hàng đợi đóng trà theo yêu cầu của kho: chờ đóng gói, chờ nguyên liệu, đã đóng gói. */
export function CustomBundleManagementListPage() {
    const [query, setQuery] = useState<CustomBundlePackingQuery & { status: CustomBundlePackingStatus }>({
        status: "AWAITING_PACKING",
        search: "",
        page: 0,
        size: 20
    });
    const [result, setResult] = useState<CustomBundlePackingPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        const timeoutId = window.setTimeout(() => {
            setLoading(true);
            setError("");
            customBundleApi
                .listPackings(query)
                .then((response) => {
                    if (active) setResult(response);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        }, 250);
        return () => {
            active = false;
            window.clearTimeout(timeoutId);
        };
    }, [query]);

    const packings = result?.content ?? [];
    const totalPages = result?.totalPages ?? 1;

    return (
        <section className="grid gap-4">
            <header className="grid gap-1">
                <p className="text-xs font-black uppercase text-[#159734]">Quản lý / Kho</p>
                <h1 className="text-2xl font-black text-[#172317]">Đóng trà theo yêu cầu</h1>
                <p className="text-sm font-semibold text-[#697684]">
                    Bundle khách tự chọn nguyên liệu đã thu tiền. Xác nhận đóng gói để trừ nguyên liệu đã giữ và báo quầy giao hàng.
                </p>
            </header>

            <nav className="flex gap-2 overflow-x-auto">
                {customBundlePackingTabs.map((status) => (
                    <button
                        className={`h-10 shrink-0 rounded-lg px-4 text-sm font-black ${
                            query.status === status ? "bg-[#159734] text-white" : "bg-white text-[#536078]"
                        }`}
                        key={status}
                        onClick={() => setQuery({ ...query, status, page: 0 })}
                        type="button"
                    >
                        {customBundlePackingStatusLabels[status]}
                    </button>
                ))}
            </nav>

            <SearchInput
                className="max-w-md"
                onChange={(search) => setQuery({ ...query, search, page: 0 })}
                placeholder="Tìm mã đóng gói, mã đơn, tên bundle"
                value={query.search ?? ""}
            />

            <DataTable
                caption="Danh sách đóng trà theo yêu cầu"
                columns={columns}
                emptyTitle="Không có bundle ở trạng thái này."
                error={error || null}
                getRowKey={(packing) => packing.id}
                loading={loading}
                minWidth={900}
                pagination={totalPages > 1 ? (
                    <TablePagination
                        itemLabel="bundle"
                        onPageChange={(page) => setQuery({ ...query, page })}
                        page={query.page}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalPages={totalPages}
                    />
                ) : null}
                rows={error ? [] : packings}
            />
        </section>
    );
}
