import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { routePaths } from "../../../routes/path";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import { productVariantApi } from "../../product/services/productVariant.api";
import { bomApi } from "../services/bom.api";
import { productionOrderApi } from "../services/production-order.api";
import { SearchableDropdown } from "../components/shared/SearchableDropdown";
import type { LookupItem } from "../components/shared/SearchableDropdown";
import type { BomItemResponse } from "../types/bom.types";

export function ProductionOrderCreatePage() {
    const navigate = useNavigate();

    const [variantId, setVariantId] = useState("");
    const [variantOptions, setVariantOptions] = useState<LookupItem[]>([]);
    const [variantPage, setVariantPage] = useState(0);
    const [variantHasMore, setVariantHasMore] = useState(true);
    const [variantLoading, setVariantLoading] = useState(false);
    const variantSearchRef = useRef("");

    const [locationId, setLocationId] = useState("");
    const [locationOptions, setLocationOptions] = useState<LookupItem[]>([]);
    const [locationPage, setLocationPage] = useState(0);
    const [locationHasMore, setLocationHasMore] = useState(true);
    const [locationLoading, setLocationLoading] = useState(false);
    const locationSearchRef = useRef("");

    const [plannedQuantity, setPlannedQuantity] = useState(1);
    const [note, setNote] = useState("");

    const [bomItems, setBomItems] = useState<BomItemResponse[] | null>(null);
    const [bomItemsLoading, setBomItemsLoading] = useState(false);
    // Tách riêng lỗi gọi API (mạng/401/500...) khỏi trường hợp BOM thật sự rỗng — nếu gộp chung thì
    // 1 lỗi mạng thoáng qua sẽ hiển thị y hệt "sản phẩm chưa có BOM" dù dữ liệu BOM vẫn tồn tại.
    const [bomFetchError, setBomFetchError] = useState<string | null>(null);
    const bomItemsRequestRef = useRef(0);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    const loadVariantOptions = useCallback(async (search: string, page: number) => {
        try {
            setVariantLoading(true);
            const res = await productVariantApi.lookup(search, page, 20);
            setVariantOptions((prev) => (page === 0 ? (res.content ?? []) : [...prev, ...(res.content ?? [])]));
            setVariantHasMore(!res.last);
        } catch (err) {
            console.error("Lỗi tải danh sách sản phẩm", err);
            setVariantHasMore(false);
        } finally {
            setVariantLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadVariantOptions("", 0);
    }, [loadVariantOptions]);

    const handleVariantSearch = useCallback(
        (term: string) => {
            variantSearchRef.current = term;
            setVariantPage(0);
            void loadVariantOptions(term, 0);
        },
        [loadVariantOptions]
    );

    const handleLoadMoreVariants = useCallback(() => {
        if (!variantLoading && variantHasMore) {
            const nextPage = variantPage + 1;
            setVariantPage(nextPage);
            void loadVariantOptions(variantSearchRef.current, nextPage);
        }
    }, [variantLoading, variantHasMore, variantPage, loadVariantOptions]);

    const loadLocationOptions = useCallback(async (search: string, page: number) => {
        try {
            setLocationLoading(true);
            const res = await inventoryLocationApi.lookupByType("WAREHOUSE", search, page, 20);
            const options = (res.content ?? []).map((loc) => ({
                id: String(loc.id),
                name: `${loc.name} (${loc.code})`
            }));
            setLocationOptions((prev) => (page === 0 ? options : [...prev, ...options]));
            setLocationHasMore(!res.last);
        } catch (err) {
            console.error("Lỗi tải danh sách kho sản xuất", err);
            setLocationHasMore(false);
        } finally {
            setLocationLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadLocationOptions("", 0);
    }, [loadLocationOptions]);

    const handleLocationSearch = useCallback(
        (term: string) => {
            locationSearchRef.current = term;
            setLocationPage(0);
            void loadLocationOptions(term, 0);
        },
        [loadLocationOptions]
    );

    const handleLoadMoreLocations = useCallback(() => {
        if (!locationLoading && locationHasMore) {
            const nextPage = locationPage + 1;
            setLocationPage(nextPage);
            void loadLocationOptions(locationSearchRef.current, nextPage);
        }
    }, [locationLoading, locationHasMore, locationPage, loadLocationOptions]);

    function fetchBomItems(forVariantId: string) {
        const requestId = ++bomItemsRequestRef.current;
        setBomItemsLoading(true);
        setBomFetchError(null);
        bomApi
            .listByVariant(forVariantId)
            .then((items) => {
                if (bomItemsRequestRef.current !== requestId) return;
                setBomItems(items);
            })
            .catch((err) => {
                if (bomItemsRequestRef.current !== requestId) return;
                // Lỗi gọi API (mạng/401/500...) khác với "sản phẩm chưa có BOM" — giữ bomItems ở null
                // (chưa xác định) thay vì ép về [] để không hiển thị nhầm là BOM thật sự rỗng.
                setBomFetchError(
                    err instanceof Error ? err.message : "Không thể tải định mức nguyên liệu của sản phẩm này."
                );
                setBomItems(null);
            })
            .finally(() => {
                if (bomItemsRequestRef.current === requestId) setBomItemsLoading(false);
            });
    }

    // Khi chọn sản phẩm: tải luôn danh sách nguyên liệu (BOM) của sản phẩm đó để xem trước
    function handleVariantChange(newVariantId: string) {
        setVariantId(newVariantId);
        setBomFetchError(null);

        if (!newVariantId) {
            ++bomItemsRequestRef.current;
            setBomItems(null);
            return;
        }

        fetchBomItems(newVariantId);
    }

    const noBomAvailable = bomItems !== null && bomItems.length === 0;

    const materialPreviewRows = useMemo(() => {
        if (!bomItems) return [];
        return bomItems.map((item) => {
            const wasteRate = item.wasteRate ?? 0;
            const requiredQuantity = item.quantity * plannedQuantity * (1 + wasteRate);
            return {
                id: item.id,
                name: item.ingredient.name,
                unit: item.unit.symbol,
                requiredQuantity
            };
        });
    }, [bomItems, plannedQuantity]);

    const canSubmit =
        Boolean(variantId) &&
        Boolean(locationId) &&
        Boolean(bomItems && bomItems.length > 0) &&
        plannedQuantity > 0 &&
        !submitting;

    async function handleSave(submitAfterCreate: boolean) {
        if (!variantId) {
            setError("Vui lòng chọn sản phẩm cần sản xuất.");
            return;
        }
        if (!locationId) {
            setError("Vui lòng chọn kho/vị trí sản xuất.");
            return;
        }
        if (bomFetchError) {
            setError(bomFetchError);
            return;
        }
        if (!bomItems || bomItems.length === 0) {
            setError("Sản phẩm này chưa có định mức nguyên liệu (BOM) — vui lòng cập nhật BOM trước khi tạo lệnh sản xuất.");
            return;
        }
        if (plannedQuantity <= 0) {
            setError("Số lượng kế hoạch phải lớn hơn 0.");
            return;
        }

        setError("");
        setSubmitting(true);
        try {
            const created = await productionOrderApi.create({
                productVariantId: variantId,
                plannedQuantity,
                locationId,
                note: note.trim() || undefined
            });

            if (submitAfterCreate) {
                await productionOrderApi.submit(created.id);
            }

            navigate(routePaths.productionOrderDetail.replace(":id", created.id));
        } catch (err) {
            setError(err instanceof Error ? err.message : "Tạo lệnh sản xuất thất bại.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <Link className="text-sm font-black text-[#159734] hover:underline" to={routePaths.productionOrders}>
                    ← Quay lại danh sách lệnh sản xuất
                </Link>
            </div>

            <header className="grid gap-1 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <h1 className="text-2xl font-black text-[#172317]">Tạo lệnh sản xuất</h1>
                <p className="text-sm font-semibold text-[#536078]">
                    Chọn sản phẩm cần sản xuất — hệ thống sẽ tự động lấy định mức nguyên liệu (BOM) đã khai báo cho sản phẩm đó.
                </p>
            </header>

            {error ? (
                <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-700">
                    {error}
                </div>
            ) : null}

            <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <h2 className="text-base font-black text-[#172317]">1. Thông tin chung</h2>
                <div className="grid gap-4 md:grid-cols-2">
                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Sản phẩm cần sản xuất</label>
                        <SearchableDropdown
                            value={variantId}
                            onChange={handleVariantChange}
                            options={variantOptions}
                            onSearch={handleVariantSearch}
                            onLoadMore={handleLoadMoreVariants}
                            hasMore={variantHasMore}
                            loading={variantLoading}
                            placeholder="Tìm theo tên hoặc SKU..."
                            emptyText="Không tìm thấy sản phẩm"
                        />
                    </div>
                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Kho / vị trí sản xuất</label>
                        <SearchableDropdown
                            value={locationId}
                            onChange={setLocationId}
                            options={locationOptions}
                            onSearch={handleLocationSearch}
                            onLoadMore={handleLoadMoreLocations}
                            hasMore={locationHasMore}
                            loading={locationLoading}
                            placeholder="Tìm theo tên kho..."
                            emptyText="Không tìm thấy kho/vị trí"
                        />
                    </div>
                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Số lượng kế hoạch</label>
                        <input
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600 focus:ring-1 focus:ring-hvt-green-600"
                            min={0.01}
                            step="0.01"
                            type="number"
                            value={plannedQuantity}
                            onChange={(e) => setPlannedQuantity(Number(e.target.value))}
                        />
                    </div>
                    <div className="grid gap-1.5">
                        <label className="text-xs font-bold text-[#536078]">Ghi chú</label>
                        <input
                            className="h-10 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-600 focus:ring-1 focus:ring-hvt-green-600"
                            placeholder="Không bắt buộc"
                            type="text"
                            value={note}
                            onChange={(e) => setNote(e.target.value)}
                        />
                    </div>
                </div>
            </div>

            {variantId ? (
                <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                    <h2 className="text-base font-black text-[#172317]">2. Định mức nguyên liệu (BOM)</h2>

                    {bomItemsLoading ? (
                        <div className="text-sm font-semibold text-[#697684]">Đang tải định mức nguyên liệu...</div>
                    ) : bomFetchError ? (
                        <div className="grid gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-bold text-red-700">
                            <span>Không thể tải định mức nguyên liệu: {bomFetchError}</span>
                            <button
                                className="w-fit rounded-lg border border-red-300 bg-white px-3 py-1.5 text-xs font-black text-red-700 hover:bg-red-100"
                                onClick={() => fetchBomItems(variantId)}
                                type="button"
                            >
                                Thử lại
                            </button>
                        </div>
                    ) : noBomAvailable ? (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm font-bold text-amber-700">
                            Sản phẩm này chưa có định mức nguyên liệu (BOM). Vui lòng cập nhật BOM cho mặt hàng trước khi sản xuất.
                        </div>
                    ) : (
                        <div className="grid gap-2">
                            <h3 className="text-sm font-black text-[#172317]">Nguyên liệu dự kiến tiêu thụ</h3>
                            <div className="overflow-hidden rounded-xl border border-[#e8eef0]">
                                <table className="w-full text-sm">
                                    <thead className="bg-neutral-50 text-xs font-bold uppercase text-[#697684]">
                                        <tr>
                                            <th className="px-3 py-2 text-left">Nguyên liệu</th>
                                            <th className="px-3 py-2 text-right">Số lượng cần</th>
                                            <th className="px-3 py-2 text-left">Đơn vị</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {materialPreviewRows.map((row) => (
                                            <tr key={row.id} className="border-t border-[#e8eef0]">
                                                <td className="px-3 py-2 font-semibold text-[#172317]">{row.name}</td>
                                                <td className="px-3 py-2 text-right font-mono">
                                                    {row.requiredQuantity.toLocaleString("vi-VN", { maximumFractionDigits: 4 })}
                                                </td>
                                                <td className="px-3 py-2 text-[#536078]">{row.unit}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                            <p className="text-xs font-semibold italic text-[#697684]">
                                Số liệu chỉ để xem trước — số lượng chính thức sẽ được hệ thống tính lại khi tạo lệnh.
                            </p>
                        </div>
                    )}
                </div>
            ) : null}

            <div className="flex flex-wrap items-center justify-end gap-2">
                <button
                    className="inline-flex h-10 items-center rounded-lg border border-pos-border bg-white px-4 text-sm font-black text-[#536078] hover:bg-neutral-50 disabled:opacity-50"
                    disabled={!canSubmit}
                    onClick={() => void handleSave(false)}
                    type="button"
                >
                    Lưu bản nháp
                </button>
                <button
                    className="inline-flex h-10 items-center rounded-lg bg-[#159734] px-4 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-50"
                    disabled={!canSubmit}
                    onClick={() => void handleSave(true)}
                    type="button"
                >
                    {submitting ? "Đang xử lý..." : "Lưu & Gửi duyệt"}
                </button>
            </div>
        </section>
    );
}
