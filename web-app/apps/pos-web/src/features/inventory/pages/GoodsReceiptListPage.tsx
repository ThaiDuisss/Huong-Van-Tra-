import {
    CheckCircle2,
    ClipboardList,
    Clock3,
    FileCheck2,
    Filter,
    PackageCheck,
    Truck
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    GoodsReceiptFilterBar,
    GoodsReceiptSummaryCard,
    GoodsReceiptTable
} from "../components/GoodsReceiptComponents";
import { useGoodsReceiptQueryState } from "../hooks/useGoodsReceiptQueryState";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptListResult,
    GoodsReceiptOptions,
    GoodsReceiptStatus
} from "../types/goods-receipt.types";

const statusTabs: Array<{ key: "ALL" | GoodsReceiptStatus; label: string }> = [
    { key: "ALL", label: "Tất cả" },
    { key: "DRAFT", label: "Bản nháp" },
    { key: "PENDING_APPROVAL", label: "Chờ duyệt" },
    { key: "APPROVED", label: "Đã duyệt (Chờ giao)" },
    { key: "RECEIVING", label: "Đang tiếp nhận" },
    { key: "QC_PENDING", label: "Chờ QC" },
    { key: "QC_COMPLETED", label: "Chờ ghi sổ" },
    { key: "POSTED", label: "Đã nhập kho" },
    { key: "REJECTED", label: "Từ chối" },
    { key: "CANCELLED", label: "Đã hủy" }
];

export function GoodsReceiptListPage() {
    const { query, resetQuery, setQuery } = useGoodsReceiptQueryState();
    const [showFilters, setShowFilters] = useState(false);
    const [result, setResult] = useState<GoodsReceiptListResult | null>(null);
    const [summaryCounts, setSummaryCounts] = useState<{
        draft: number;
        pendingApproval: number;
        receiving: number;
        awaitingQc: number;
        readyToPost: number;
        postedToday: number;
    }>({
        awaitingQc: 0,
        draft: 0,
        pendingApproval: 0,
        postedToday: 0,
        readyToPost: 0,
        receiving: 0
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
                console.log("[GoodsReceiptListPage] setOptions với:", res);
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

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <h1 className="text-2xl font-black text-[#172317]">
                        Phiếu nhập kho từ Nhà cung cấp
                    </h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Quản lý vòng đời phiếu nhập
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <button
                        className={`inline-flex h-10 items-center gap-2 rounded-lg border px-4 text-sm font-black transition-all ${showFilters
                            ? "border-[#159734] bg-[#f4faf5] text-[#159734]"
                            : "border-[#e8eef0] bg-white text-[#536078] hover:bg-[#f7f8fb]"
                            }`}
                        onClick={() => setShowFilters((prev) => !prev)}
                        type="button"
                    >
                        <Filter size={16} aria-hidden="true" />
                        <span>{showFilters ? "Ẩn bộ lọc" : "Hiện bộ lọc"}</span>
                        {activeFilterCount > 0 ? (
                            <span className="grid size-5 place-items-center rounded-full bg-[#159734] text-[11px] font-black text-white">
                                {activeFilterCount}
                            </span>
                        ) : null}
                    </button>
                    <Link
                        className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-5 text-sm font-black text-white hover:bg-[#12802c] shadow-sm"
                        to="/management/inventory/receipts/new"
                    >
                        + Tạo phiếu nhập mới
                    </Link>
                </div>
            </header>

            {/* THẺ CHỈ SỐ THEO VÒNG ĐỜI (TÍNH TOÁN ĐỘC LẬP VỚI BỘ LỌC) */}
            <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6">
                <GoodsReceiptSummaryCard
                    icon={<ClipboardList size={18} aria-hidden="true" />}
                    label="1. Bản nháp"
                    value={summaryCounts.draft}
                    active={query.status === "DRAFT"}
                    onClick={() => setQuery({ page: 0, status: "DRAFT" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<Clock3 size={18} aria-hidden="true" />}
                    label="2. Chờ duyệt"
                    value={summaryCounts.pendingApproval}
                    active={query.status === "PENDING_APPROVAL"}
                    onClick={() => setQuery({ page: 0, status: "PENDING_APPROVAL" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<Truck size={18} aria-hidden="true" />}
                    label="3. Đang tiếp nhận"
                    value={summaryCounts.receiving}
                    active={query.status === "RECEIVING" || query.status === "APPROVED"}
                    onClick={() => setQuery({ page: 0, status: "RECEIVING" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<FileCheck2 size={18} aria-hidden="true" />}
                    label="4. Chờ QC"
                    value={summaryCounts.awaitingQc}
                    active={query.status === "QC_PENDING" || query.status === "AWAITING_QC"}
                    onClick={() => setQuery({ page: 0, status: "QC_PENDING" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<PackageCheck size={18} aria-hidden="true" />}
                    label="5. Chờ ghi sổ"
                    value={summaryCounts.readyToPost}
                    active={query.status === "QC_COMPLETED" || query.status === "READY_TO_POST"}
                    onClick={() => setQuery({ page: 0, status: "QC_COMPLETED" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<CheckCircle2 size={18} aria-hidden="true" />}
                    label="Đã nhập kho"
                    value={summaryCounts.postedToday}
                    active={query.status === "POSTED"}
                    onClick={() => setQuery({ page: 0, status: "POSTED" })}
                />
            </div>

            {/* TAB LỌC NHANH TRẠNG THÁI */}
            <div className="flex gap-2 overflow-x-auto border-b border-[#e8eef0] pb-2">
                {statusTabs.map((tab) => (
                    <button
                        key={tab.key}
                        className={`h-9 shrink-0 rounded-lg px-4 text-xs font-black transition-all ${query.status === tab.key
                            ? "bg-[#159734] text-white shadow-sm"
                            : "bg-white text-[#536078] hover:bg-[#f4faf5] border border-[#e8eef0]"
                            }`}
                        onClick={() => setQuery({ page: 0, status: tab.key })}
                        type="button"
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* BỘ LỌC TÌM KIẾM CHI TIẾT (MẶC ĐỊNH ẨN) */}
            {showFilters ? (
                <GoodsReceiptFilterBar
                    locations={options.locations}
                    onChange={setQuery}
                    onReset={resetQuery}
                    query={query}
                    suppliers={options.suppliers}
                />
            ) : null}

            {loading ? (
                <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                    {Array.from({ length: 6 }, (_, index) => (
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
                            Không thể tải danh sách phiếu nhập
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

            {!loading && !error && receipts.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">
                            Không tìm thấy phiếu nhập phù hợp
                        </strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">
                            Thử thay đổi điều kiện lọc hoặc tạo phiếu nhập nguyên liệu mới.
                        </p>
                    </div>
                </div>
            ) : null}

            {!loading && !error && receipts.length > 0 ? (
                <div className="grid gap-3">
                    <GoodsReceiptTable receipts={receipts} />
                    <Pagination
                        onPageChange={(page) => setQuery({ page: page - 1 })}
                        onPageSizeChange={(size) =>
                            setQuery({ page: 0, size })
                        }
                        page={query.page + 1}
                        pageCount={result?.totalPages ?? 1}
                        pageSize={query.size}
                        totalItems={result?.totalElements ?? 0}
                        totalItemsLabel="Tổng số phiếu nhập"
                    />
                </div>
            ) : null}
        </section>
    );
}
