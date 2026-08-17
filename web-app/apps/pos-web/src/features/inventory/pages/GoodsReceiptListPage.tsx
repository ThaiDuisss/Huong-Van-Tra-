import {
    CheckCircle2,
    ClipboardList,
    Clock3,
    PackageCheck,
    Scale
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
    GoodsReceiptRole
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
        void goodsReceiptApi.getGoodsReceiptOptions().then(setOptions);
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
                    <p className="text-xs font-black uppercase tracking-normal text-[#159734]">
                        Management / Inventory
                    </p>
                    <h1 className="text-2xl font-black text-[#172317]">
                        Nhập nguyên liệu
                    </h1>
                    <p className="max-w-3xl text-sm font-semibold leading-6 text-[#697684]">
                        Tiếp nhận nguyên liệu, kiểm tra QC, xác nhận giá nhập
                        và post batch vào tồn kho khả dụng.
                    </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                    <GoodsReceiptRoleSwitch
                        onChange={setRole}
                        value={role}
                    />
                    <Link
                        className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white shadow-pos-card"
                        to="/management/inventory/receipts/new"
                    >
                        Tạo phiếu nhập
                    </Link>
                </div>
            </header>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
                <GoodsReceiptSummaryCard
                    icon={<ClipboardList size={18} aria-hidden="true" />}
                    label="Draft"
                    value={result?.summary.draft ?? 0}
                />
                <GoodsReceiptSummaryCard
                    icon={<Clock3 size={18} aria-hidden="true" />}
                    label="Chờ QC"
                    value={result?.summary.awaitingQc ?? 0}
                />
                <GoodsReceiptSummaryCard
                    icon={<Scale size={18} aria-hidden="true" />}
                    label="Chờ xác nhận giá"
                    value={result?.summary.awaitingCost ?? 0}
                />
                <GoodsReceiptSummaryCard
                    icon={<PackageCheck size={18} aria-hidden="true" />}
                    label="Sẵn sàng post"
                    value={result?.summary.readyToPost ?? 0}
                />
                <GoodsReceiptSummaryCard
                    icon={<CheckCircle2 size={18} aria-hidden="true" />}
                    label="Đã post hôm nay"
                    value={result?.summary.postedToday ?? 0}
                />
            </div>

            <GoodsReceiptFilterBar
                locations={options.locations}
                onChange={setQuery}
                onReset={resetQuery}
                query={query}
                suppliers={options.suppliers}
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

            {!loading && !error && receipts.length === 0 ? (
                <div className="grid place-items-center rounded-xl border border-dashed border-[#cfdad2] bg-white px-6 py-14 text-center">
                    <div className="grid max-w-sm gap-2">
                        <strong className="text-base font-black text-[#172317]">
                            Không có phiếu nhập phù hợp
                        </strong>
                        <p className="text-sm font-semibold leading-6 text-[#697684]">
                            Hãy đổi bộ lọc hoặc tạo phiếu nhập nguyên liệu mới.
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
