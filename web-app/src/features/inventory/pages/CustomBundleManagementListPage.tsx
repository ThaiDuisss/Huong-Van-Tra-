import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
    customBundlePackingStatusLabels,
    customBundlePackingStatusTones,
    customBundlePackingTabs
} from "../constants/custom-bundle.constants";
import { customBundleApi } from "../services/customBundle.api";
import type {
    CustomBundlePackingPage,
    CustomBundlePackingQuery,
    CustomBundlePackingStatus
} from "../types/custom-bundle.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Không tải được danh sách đóng gói.";
}

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

            <input
                className="h-10 max-w-md rounded-lg border border-pos-border bg-white px-3 text-sm font-bold"
                onChange={(event) => setQuery({ ...query, search: event.currentTarget.value, page: 0 })}
                placeholder="Tìm mã đóng gói, mã đơn, tên bundle"
                value={query.search}
            />

            {error ? <div className="rounded-xl bg-red-50 p-4 text-sm font-bold text-red-700">{error}</div> : null}
            {loading ? <div className="rounded-xl bg-white p-6 text-sm font-bold">Đang tải...</div> : null}
            {!loading && !error && packings.length === 0 ? (
                <div className="rounded-xl border border-dashed border-[#cfdad2] bg-white p-10 text-center text-sm font-bold text-[#697684]">
                    Không có bundle ở trạng thái này.
                </div>
            ) : null}

            {packings.length > 0 ? (
                <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                    <table className="w-full min-w-[900px] text-left text-sm">
                        <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
                            <tr>
                                <th className="px-4 py-3">Mã đóng gói</th>
                                <th>Mã đơn</th>
                                <th>Tên bundle</th>
                                <th>Khối lượng</th>
                                <th>Trạng thái</th>
                                <th>Dự kiến đủ nguyên liệu</th>
                                <th>Tạo lúc</th>
                            </tr>
                        </thead>
                        <tbody>
                            {packings.map((packing) => (
                                <tr className="hover:bg-[#f8fbf8]" key={packing.id}>
                                    <td className="border-t border-[#edf0f4] px-4 py-3 font-black">
                                        <Link
                                            className="text-[#159734] hover:underline"
                                            to={`/management/inventory/custom-bundles/${packing.id}`}
                                        >
                                            {packing.packingCode}
                                        </Link>
                                    </td>
                                    <td className="border-t border-[#edf0f4]">{packing.orderCode ?? "-"}</td>
                                    <td className="border-t border-[#edf0f4]">{packing.bundleName ?? "-"}</td>
                                    <td className="border-t border-[#edf0f4]">
                                        {formatVietnamNumber(packing.expectedTotalWeight)}
                                    </td>
                                    <td className="border-t border-[#edf0f4]">
                                        <span
                                            className={`rounded-full px-2 py-1 text-xs font-black ${customBundlePackingStatusTones[packing.status]}`}
                                        >
                                            {customBundlePackingStatusLabels[packing.status]}
                                        </span>
                                    </td>
                                    <td className="border-t border-[#edf0f4]">
                                        {packing.expectedAvailableAt ? formatVietnamDateTime(packing.expectedAvailableAt) : "-"}
                                    </td>
                                    <td className="border-t border-[#edf0f4]">
                                        {packing.createdAt ? formatVietnamDateTime(packing.createdAt) : "-"}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            ) : null}

            {totalPages > 1 ? (
                <div className="flex items-center justify-end gap-2 text-sm font-bold">
                    <button
                        className="h-9 rounded-lg border border-[#dce5e1] bg-white px-3 disabled:opacity-50"
                        disabled={query.page === 0}
                        onClick={() => setQuery({ ...query, page: query.page - 1 })}
                        type="button"
                    >
                        Trước
                    </button>
                    <span>
                        Trang {query.page + 1}/{totalPages}
                    </span>
                    <button
                        className="h-9 rounded-lg border border-[#dce5e1] bg-white px-3 disabled:opacity-50"
                        disabled={query.page + 1 >= totalPages}
                        onClick={() => setQuery({ ...query, page: query.page + 1 })}
                        type="button"
                    >
                        Sau
                    </button>
                </div>
            ) : null}
        </section>
    );
}
