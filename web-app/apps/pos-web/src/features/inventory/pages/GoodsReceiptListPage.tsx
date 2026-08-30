import {
    CheckCircle2,
    ClipboardList,
    Clock3,
    FileCheck2,
    PackageCheck,
    Truck
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Pagination } from "../../manufacturing/components/shared/Pagination";
import {
    GoodsReceiptFilterBar,
    GoodsReceiptRoleSwitch,
    GoodsReceiptSummaryCard,
    GoodsReceiptTable
} from "../components/GoodsReceiptComponents";
import { useGoodsReceiptQueryState } from "../hooks/useGoodsReceiptQueryState";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptListResult,
    GoodsReceiptOptions,
    GoodsReceiptRole,
    GoodsReceiptStatus
} from "../types/goods-receipt.types";

function readRole(): GoodsReceiptRole {
    const stored = window.localStorage.getItem("hvt.goodsReceipt.role");
    if (
        stored &&
        ["ADMIN", "WAREHOUSE", "QC_STAFF", "ACCOUNTANT", "BRANCH_MANAGER", "DIRECTOR"].includes(
            stored
        )
    ) {
        return stored as GoodsReceiptRole;
    }
    return "WAREHOUSE";
}

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
    const [role, setRole] = useState<GoodsReceiptRole>(readRole);
    const [result, setResult] = useState<GoodsReceiptListResult | null>(null);
    const [options, setOptions] = useState<GoodsReceiptOptions>({
        locations: [],
        materials: [],
        suppliers: []
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        window.localStorage.setItem("hvt.goodsReceipt.role", role);
    }, [role]);

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

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid gap-1">
                    <p className="text-xs font-black uppercase tracking-wider text-[#159734]">
                        Quản lý Kho vận › Quy trình nhập nguyên liệu
                    </p>
                    <h1 className="text-2xl font-black text-[#172317]">
                        Phiếu nhập kho từ Nhà cung cấp
                    </h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Quản lý toàn bộ 5 giai đoạn vòng đời: Lập kế hoạch (Draft) → Phê duyệt → Tiếp nhận kiểm đếm → Kiểm định QC & Lô → Ghi sổ nhập kho khả dụng.
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <GoodsReceiptRoleSwitch onChange={setRole} value={role} />
                    <Link
                        className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-5 text-sm font-black text-white hover:bg-[#12802c] shadow-sm"
                        to="/management/inventory/receipts/new"
                    >
                        + Tạo phiếu nhập mới
                    </Link>
                </div>
            </header>

            {/* THẺ CHỈ SỐ THEO VÒNG ĐỜI */}
            <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6">
                <GoodsReceiptSummaryCard
                    icon={<ClipboardList size={18} aria-hidden="true" />}
                    label="1. Bản nháp"
                    value={result?.summary.draft ?? 0}
                    active={query.status === "DRAFT"}
                    onClick={() => setQuery({ page: 0, status: "DRAFT" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<Clock3 size={18} aria-hidden="true" />}
                    label="2. Chờ duyệt"
                    value={receipts.filter((r) => r.status === "PENDING_APPROVAL").length}
                    active={query.status === "PENDING_APPROVAL"}
                    onClick={() => setQuery({ page: 0, status: "PENDING_APPROVAL" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<Truck size={18} aria-hidden="true" />}
                    label="3. Đang tiếp nhận"
                    value={receipts.filter((r) => r.status === "RECEIVING" || r.status === "APPROVED").length}
                    active={query.status === "RECEIVING"}
                    onClick={() => setQuery({ page: 0, status: "RECEIVING" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<FileCheck2 size={18} aria-hidden="true" />}
                    label="4. Chờ QC"
                    value={result?.summary.awaitingQc ?? 0}
                    active={query.status === "QC_PENDING"}
                    onClick={() => setQuery({ page: 0, status: "QC_PENDING" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<PackageCheck size={18} aria-hidden="true" />}
                    label="5. Sẵn sàng post"
                    value={result?.summary.readyToPost ?? 0}
                    active={query.status === "QC_COMPLETED"}
                    onClick={() => setQuery({ page: 0, status: "QC_COMPLETED" })}
                />
                <GoodsReceiptSummaryCard
                    icon={<CheckCircle2 size={18} aria-hidden="true" />}
                    label="Đã nhập kho"
                    value={result?.summary.postedToday ?? 0}
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

            {/* BỘ LỌC TÌM KIẾM */}
            <GoodsReceiptFilterBar
                locations={options.locations}
                onChange={setQuery}
                onReset={resetQuery}
                query={query}
                suppliers={options.suppliers}
            />

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
