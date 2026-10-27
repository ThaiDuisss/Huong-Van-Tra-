import {
    AlertTriangle,
    CheckCircle2,
    Clock3,
    RotateCcw,
    XCircle
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    StockDocumentCreateDropdown,
    StockDocumentFilterBar,
    StockDocumentSummaryCard,
    StockDocumentTable
} from "../components/StockDocumentComponents";
import { stockBusinessCreationLinks } from "../constants/stock-document.constants";
import { useStockDocumentQueryState } from "../hooks/useStockDocumentQueryState";
import { stockDocumentApi } from "../services/stockDocument.api";
import type {
    StockDocumentListResult,
    StockDocumentOptions
} from "../types/stock-document.types";

export function StockDocumentListPage() {
    const { query, resetQuery, setQuery } = useStockDocumentQueryState();
    const [result, setResult] = useState<StockDocumentListResult | null>(null);
    const [options, setOptions] = useState<StockDocumentOptions>({
        locations: [],
        users: []
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        void stockDocumentApi.getStockDocumentOptions().then(setOptions);
    }, []);

    useEffect(() => {
        let active = true;

        queueMicrotask(() => {
            if (active) {
                setLoading(true);
                setError("");
            }
        });

        void stockDocumentApi
            .listStockDocuments(query)
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
                            : "Không thể tải danh sách chứng từ kho."
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

    const documents = result?.content ?? [];

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">
                        Management / Inventory
                    </p>
                    <h1 className="text-2xl font-black text-[#172317]">
                        Chứng từ kho
                    </h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Theo dõi toàn bộ phiếu nhập, phiếu xuất và điều chỉnh
                        tồn kho
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <StockDocumentCreateDropdown
                        links={stockBusinessCreationLinks}
                    />
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                <StockDocumentSummaryCard
                    icon={<Clock3 size={18} aria-hidden="true" />}
                    label="Chờ duyệt"
                    value={result?.summary.waitingApproval ?? 0}
                />
                <StockDocumentSummaryCard
                    icon={<AlertTriangle size={18} aria-hidden="true" />}
                    label="Chờ post"
                    value={result?.summary.waitingPost ?? 0}
                />
                <StockDocumentSummaryCard
                    icon={<CheckCircle2 size={18} aria-hidden="true" />}
                    label="Đã post hôm nay"
                    value={result?.summary.postedToday ?? 0}
                />
                <StockDocumentSummaryCard
                    icon={<XCircle size={18} aria-hidden="true" />}
                    label="Bị từ chối"
                    value={result?.summary.rejected ?? 0}
                />
                <StockDocumentSummaryCard
                    icon={<RotateCcw size={18} aria-hidden="true" />}
                    label="Đã đảo trong tháng"
                    value={result?.summary.reversedThisMonth ?? 0}
                />
            </div>

            <StockDocumentFilterBar
                locations={options.locations}
                onChange={setQuery}
                onReset={resetQuery}
                query={query}
                users={options.users}
            />

            {loading ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 7 }, (_, index) => (
                        <div
                            className="h-11 animate-pulse rounded-lg bg-[#eef3ef]"
                            key={index}
                        />
                    ))}
                </div>
            ) : null}

            {!loading && error ? (
                <div className="grid place-items-center rounded-xl border border-red-100 bg-red-50 px-6 py-12 text-center">
                    <div className="grid max-w-sm gap-3 justify-items-center">
                        <strong className="text-base font-black text-red-800">
                            Không thể tải dữ liệu
                        </strong>
                        <p className="text-sm font-semibold leading-6 text-red-700">
                            {error}
                        </p>
                        <button
                            className="rounded-lg bg-red-700 px-4 py-2 text-sm font-black text-white"
                            onClick={retry}
                            type="button"
                        >
                            Thử lại
                        </button>
                    </div>
                </div>
            ) : null}

            {!loading && !error && documents.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">
                            Không có chứng từ phù hợp
                        </strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">
                            Hãy thay đổi bộ lọc hoặc xóa bộ lọc để xem toàn bộ
                            chứng từ kho.
                        </p>
                    </div>
                </div>
            ) : null}

            {!loading && !error && documents.length > 0 ? (
                <div className="grid gap-3">
                    <StockDocumentTable documents={documents} />
                    <Pagination
                        onPageChange={(page) => setQuery({ page: page - 1 })}
                        onPageSizeChange={(size) =>
                            setQuery({ page: 0, size })
                        }
                        page={query.page + 1}
                        pageCount={result?.totalPages ?? 1}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalItemsLabel="Tổng số chứng từ"
                    />
                </div>
            ) : null}
        </section>
    );
}
