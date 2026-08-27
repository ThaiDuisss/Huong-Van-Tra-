import { Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptCreateInput,
    GoodsReceiptMaterialOption,
    GoodsReceiptOptions
} from "../types/goods-receipt.types";
import { formatVietnamNumber } from "../utils/inventory-format";
import { SearchableDropdown } from "../../manufacturing/components/shared/SearchableDropdown";
import type { LookupItem } from "../../manufacturing/components/shared/SearchableDropdown";

interface CreateLine {
    id: string;
    materialId: string;
    note: string;
    orderedQuantity: number;
}

function emptyLine(materialId = ""): CreateLine {
    return {
        id: `line-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
        materialId,
        note: "",
        orderedQuantity: 10
    };
}

export function GoodsReceiptCreatePage() {
    const navigate = useNavigate();
    const [options, setOptions] = useState<GoodsReceiptOptions>({
        locations: [],
        materials: [],
        suppliers: []
    });
    const [loadingOptions, setLoadingOptions] = useState(true);
    const [loadingMaterials, setLoadingMaterials] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    const [supplierSearch, setSupplierSearch] = useState("");
    const [locationSearch, setLocationSearch] = useState("");
    const [materialSearch, setMaterialSearch] = useState("");

    const [form, setForm] = useState({
        expectedDate: new Date().toISOString().split("T")[0],
        note: "",
        purchaseOrderCode: "",
        receivingLocationId: "",
        supplierId: ""
    });

    const [lines, setLines] = useState<CreateLine[]>([emptyLine()]);

    const supplierLookupOptions = useMemo<LookupItem[]>(() => {
        const q = supplierSearch.toLowerCase().trim();
        return (options.suppliers || [])
            .filter((s) => Boolean(s && s.id))
            .filter((s) => !q || (s.name || "").toLowerCase().includes(q) || ((s.code || "").toLowerCase().includes(q)))
            .map((s) => ({
                id: String(s.id),
                name: s.code ? `${s.name} (${s.code})` : s.name
            }));
    }, [options.suppliers, supplierSearch]);

    const locationLookupOptions = useMemo<LookupItem[]>(() => {
        const q = locationSearch.toLowerCase().trim();
        return (options.locations || [])
            .filter((l) => Boolean(l && l.id))
            .filter((l) => !q || (l.name || "").toLowerCase().includes(q) || ((l.code || "").toLowerCase().includes(q)))
            .map((l) => ({
                id: String(l.id),
                name: l.code ? `${l.name} (${l.code})` : l.name
            }));
    }, [options.locations, locationSearch]);

    const materialLookupOptions = useMemo<LookupItem[]>(() => {
        const q = materialSearch.toLowerCase().trim();
        return (options.materials || [])
            .filter((m) => Boolean(m && m.id))
            .filter((m) => !q || (m.name || "").toLowerCase().includes(q) || ((m.skuCode || "").toLowerCase().includes(q)))
            .map((m) => ({
                id: String(m.id),
                name: m.skuCode ? `${m.name} (${m.skuCode})` : m.name
            }));
    }, [options.materials, materialSearch]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (active) {
                setLoadingOptions(true);
            }
        });

        void goodsReceiptApi
            .getGoodsReceiptOptions()
            .then((response) => {
                if (!active) return;
                setOptions(response);
                setForm((cur) => ({
                    ...cur,
                    receivingLocationId:
                        cur.receivingLocationId ||
                        response.locations[0]?.id ||
                        "",
                    supplierId: ""
                }));
                setLines([emptyLine("")]);
            })
            .catch((err: unknown) => {
                console.error("[GoodsReceiptCreatePage] Lỗi tải danh mục:", err);
                if (active) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : "Không thể tải danh sách nhà cung cấp/kho."
                    );
                }
            })
            .finally(() => {
                if (active) setLoadingOptions(false);
            });

        return () => {
            active = false;
        };
    }, []);

    const handleSupplierChange = useCallback((supplierId: string) => {
        setForm((cur) => ({ ...cur, supplierId }));
        setError("");

        if (!supplierId) {
            setOptions((cur) => ({
                ...cur,
                materials: []
            }));
            setLines([emptyLine("")]);
            return;
        }

        setLoadingMaterials(true);
        void goodsReceiptApi
            .getGoodsReceiptOptions(supplierId)
            .then((response) => {
                setOptions((cur) => ({
                    ...cur,
                    materials: response.materials
                }));
                const validIds = new Set(response.materials.map((m) => m.id));
                setLines((cur) =>
                    cur.map((l) => ({
                        ...l,
                        materialId: validIds.has(l.materialId)
                            ? l.materialId
                            : response.materials[0]?.id || ""
                    }))
                );
            })
            .catch((err: unknown) => {
                console.error("[GoodsReceiptCreatePage] Lỗi tải nguyên liệu của NCC:", err);
                setError(
                    err instanceof Error
                        ? err.message
                        : "Không thể tải danh sách nguyên liệu của nhà cung cấp này."
                );
            })
            .finally(() => {
                setLoadingMaterials(false);
            });
    }, []);

    const totals = useMemo(() => {
        const totalOrderedQuantity = lines.reduce(
            (sum, l) => sum + (Number(l.orderedQuantity) || 0),
            0
        );
        return {
            totalLines: lines.length,
            totalOrderedQuantity
        };
    }, [lines]);

    function getMaterial(materialId: string): GoodsReceiptMaterialOption | undefined {
        return options.materials.find((m) => m.id === materialId);
    }

    function addLine() {
        if (!form.supplierId) {
            setError("Vui lòng chọn Nhà cung cấp trước khi thêm nguyên liệu.");
            return;
        }
        if (options.materials.length === 0) {
            setError("Nhà cung cấp đã chọn chưa có nguyên liệu nào để thêm.");
            return;
        }
        setLines((cur) => [
            ...cur,
            emptyLine(options.materials[0]?.id || "")
        ]);
    }

    function removeLine(id: string) {
        if (lines.length <= 1) return;
        setLines((cur) => cur.filter((l) => l.id !== id));
    }

    function updateLine(id: string, patch: Partial<CreateLine>) {
        setLines((cur) =>
            cur.map((l) => (l.id === id ? { ...l, ...patch } : l))
        );
    }

    async function handleSave(submit: boolean) {
        setError("");

        if (!form.supplierId) {
            setError("Vui lòng chọn Nhà cung cấp.");
            return;
        }

        if (!form.receivingLocationId) {
            setError("Vui lòng chọn Kho nhận hàng.");
            return;
        }

        if (lines.length === 0) {
            setError("Vui lòng thêm ít nhất một nguyên liệu cần nhập.");
            return;
        }

        for (const line of lines) {
            if (!line.materialId) {
                setError("Vui lòng chọn nguyên liệu cho tất cả các dòng.");
                return;
            }
            if (!line.orderedQuantity || line.orderedQuantity <= 0) {
                setError("Số lượng đặt của nguyên liệu phải lớn hơn 0.");
                return;
            }
        }

        const payload: GoodsReceiptCreateInput = {
            deliveryPerson: "",
            evidence: [],
            lines: lines.map((l) => ({
                countryOfOrigin: "Việt Nam",
                expiryDate: "",
                initialDamagedQuantity: 0,
                manufacturedAt: "",
                materialId: l.materialId,
                orderedQuantity: l.orderedQuantity,
                receivedQuantity: l.orderedQuantity,
                supplierLot: l.note
            })),
            note: form.note || undefined,
            outsidePo: !form.purchaseOrderCode,
            purchaseOrderCode: form.purchaseOrderCode || undefined,
            receivedAt: form.expectedDate,
            receivingLocationId: form.receivingLocationId,
            submit,
            supplierId: form.supplierId
        };

        try {
            setSubmitting(true);
            const created = await goodsReceiptApi.createGoodsReceipt(payload);
            navigate(`/management/inventory/receipts/${created.id}`);
        } catch (err) {
            setError(
                err instanceof Error ? err.message : "Không thể tạo phiếu nhập nguyên liệu."
            );
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="grid gap-4">
            <header className="flex flex-col gap-2">
                <div className="text-xs font-bold text-[#697684]">
                    Kho vận › Phiếu nhập nguyên liệu từ Nhà cung cấp › Tạo mới
                </div>
                <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                        <h1 className="text-2xl font-black text-[#172317]">
                            Tạo phiếu nhập nguyên liệu (DRAFT)
                        </h1>
                        <p className="text-sm font-semibold text-[#697684]">
                            Giai đoạn 1: Lập kế hoạch tiếp nhận nguyên liệu từ Nhà cung cấp. Thông tin thực nhận, Lô và QC sẽ được ghi nhận khi hàng về kho.
                        </p>
                    </div>
                    <Link
                        className="inline-flex h-9 items-center justify-center rounded-lg border border-[#dce5e1] px-4 text-xs font-bold text-[#536078] hover:bg-[#f7f8fb]"
                        to="/management/inventory/receipts"
                    >
                        Hủy & Quay lại
                    </Link>
                </div>
            </header>

            {error ? (
                <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">
                    {error}
                </div>
            ) : null}

            {/* THÔNG TIN CHUNG */}
            <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <h2 className="text-base font-black text-[#172317] border-b border-[#eef2f0] pb-2">
                    1. Thông tin phiếu nhập
                </h2>
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    <div className="grid gap-1.5 text-xs font-black text-[#536078]">
                        <span>Nhà cung cấp <span className="text-red-500">*</span></span>
                        <SearchableDropdown
                            value={form.supplierId}
                            onChange={handleSupplierChange}
                            options={supplierLookupOptions}
                            onSearch={setSupplierSearch}
                            onLoadMore={() => {}}
                            hasMore={false}
                            loading={loadingOptions}
                            placeholder="Chọn nhà cung cấp..."
                            emptyText="Không tìm thấy nhà cung cấp"
                            disabled={loadingOptions}
                        />
                    </div>

                    <div className="grid gap-1.5 text-xs font-black text-[#536078]">
                        <span>Kho nhận hàng <span className="text-red-500">*</span></span>
                        <SearchableDropdown
                            value={form.receivingLocationId}
                            onChange={(val) =>
                                setForm((cur) => ({
                                    ...cur,
                                    receivingLocationId: val
                                }))
                            }
                            options={locationLookupOptions}
                            onSearch={setLocationSearch}
                            onLoadMore={() => {}}
                            hasMore={false}
                            loading={loadingOptions}
                            placeholder="Chọn kho nhận hàng..."
                            emptyText="Không tìm thấy kho hàng"
                            disabled={loadingOptions}
                        />
                    </div>

                    <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                        Ngày dự kiến giao
                        <input
                            className="h-10 rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-[#172317] outline-none focus:border-[#159734]"
                            onChange={(e) =>
                                setForm((cur) => ({
                                    ...cur,
                                    expectedDate: e.target.value
                                }))
                            }
                            type="date"
                            value={form.expectedDate}
                        />
                    </label>

                    <label className="grid gap-1.5 text-xs font-black text-[#536078]">
                        Mã đơn đặt hàng (PO)
                        <input
                            className="h-10 rounded-lg border border-[#dce5e1] px-3 text-sm font-bold text-[#172317] outline-none focus:border-[#159734]"
                            placeholder="Ví dụ: PO-2026-001 (Nếu có)"
                            onChange={(e) =>
                                setForm((cur) => ({
                                    ...cur,
                                    purchaseOrderCode: e.target.value
                                }))
                            }
                            value={form.purchaseOrderCode}
                        />
                    </label>

                    <label className="grid gap-1.5 text-xs font-black text-[#536078] md:col-span-2 lg:col-span-4">
                        Ghi chú phiếu
                        <input
                            className="h-10 rounded-lg border border-[#dce5e1] px-3 text-sm font-semibold text-[#172317] outline-none focus:border-[#159734]"
                            placeholder="Ghi chú về lô hàng, phương thức vận chuyển hoặc điều kiện đặc biệt..."
                            onChange={(e) =>
                                setForm((cur) => ({
                                    ...cur,
                                    note: e.target.value
                                }))
                            }
                            value={form.note}
                        />
                    </label>
                </div>
            </div>

            {/* DANH SÁCH NGUYÊN LIỆU */}
            <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
                <div className="flex items-center justify-between border-b border-[#eef2f0] pb-2">
                    <div>
                        <h2 className="text-base font-black text-[#172317]">
                            2. Danh sách nguyên liệu dự kiến nhập
                        </h2>
                        <p className="text-xs font-semibold text-[#697684]">
                            Chỉ chọn các nguyên liệu do nhà cung cấp đã chọn cung cấp.
                        </p>
                    </div>
                    <button
                        className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-[#edf8ef] px-3 text-xs font-black text-[#159734] hover:bg-[#e2f4e5] disabled:cursor-not-allowed disabled:opacity-40 cursor-pointer"
                        disabled={!form.supplierId || loadingMaterials}
                        onClick={addLine}
                        type="button"
                    >
                        <Plus size={15} />
                        Thêm dòng
                    </button>
                </div>

                {!form.supplierId ? (
                    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-800">
                        ⚠️ Vui lòng chọn <strong>Nhà cung cấp</strong> ở mục 1 trước để tải và chọn danh sách nguyên liệu.
                    </div>
                ) : loadingMaterials ? (
                    <div className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs font-bold text-blue-800">
                        ⏳ Đang tải danh sách nguyên liệu từ nhà cung cấp...
                    </div>
                ) : options.materials.length === 0 ? (
                    <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs font-bold text-red-700">
                        ⚠️ Nhà cung cấp này hiện chưa có nguyên liệu nào được liên kết trong hệ thống.
                    </div>
                ) : null}

                <div className="overflow-x-auto">
                    <table className="w-full min-w-[750px] text-left text-sm">
                        <thead className="bg-[#f4faf5] text-xs font-black text-[#315f3c]">
                            <tr>
                                <th className="px-3 py-2.5 w-12">#</th>
                                <th className="px-3 py-2.5 min-w-[280px]">Nguyên liệu <span className="text-red-500">*</span></th>
                                <th className="px-3 py-2.5 w-20">ĐVT</th>
                                <th className="px-3 py-2.5 text-right w-44">Số lượng đặt <span className="text-red-500">*</span></th>
                                <th className="px-3 py-2.5">Ghi chú dòng</th>
                                <th className="px-3 py-2.5 text-center w-16">Xóa</th>
                            </tr>
                        </thead>
                        <tbody>
                            {lines.map((line, index) => {
                                const material = getMaterial(line.materialId);
                                const isMaterialDisabled = !form.supplierId || loadingMaterials || options.materials.length === 0;
                                return (
                                    <tr key={line.id} className="border-b border-[#edf0f4]">
                                        <td className="px-3 py-2.5 text-xs font-bold text-[#697684]">
                                            {index + 1}
                                        </td>
                                        <td className="px-3 py-2.5 min-w-[280px]">
                                            <SearchableDropdown
                                                value={line.materialId}
                                                onChange={(val) =>
                                                    updateLine(line.id, {
                                                        materialId: val
                                                    })
                                                }
                                                options={materialLookupOptions}
                                                onSearch={setMaterialSearch}
                                                onLoadMore={() => {}}
                                                hasMore={false}
                                                loading={loadingMaterials}
                                                placeholder={
                                                    !form.supplierId
                                                        ? "Vui lòng chọn NCC trước"
                                                        : loadingMaterials
                                                        ? "Đang tải nguyên liệu..."
                                                        : options.materials.length === 0
                                                        ? "Không có nguyên liệu"
                                                        : "Chọn nguyên liệu..."
                                                }
                                                emptyText="Không tìm thấy nguyên liệu"
                                                disabled={isMaterialDisabled}
                                            />
                                        </td>
                                        <td className="px-3 py-2.5 text-sm font-bold text-[#536078]">
                                            {material?.unit ?? "kg"}
                                        </td>
                                        <td className="px-3 py-2.5 text-right">
                                            <input
                                                className="h-9 w-full rounded-lg border border-[#dce5e1] px-2.5 text-right text-sm font-bold text-[#172317] outline-none focus:border-[#159734] disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed"
                                                disabled={isMaterialDisabled || !line.materialId}
                                                min="0.01"
                                                step="any"
                                                type="number"
                                                onChange={(e) =>
                                                    updateLine(line.id, {
                                                        orderedQuantity:
                                                            Number(e.target.value) || 0
                                                    })
                                                }
                                                value={line.orderedQuantity || ""}
                                            />
                                        </td>
                                        <td className="px-3 py-2.5">
                                            <input
                                                className="h-9 w-full rounded-lg border border-[#dce5e1] px-2.5 text-sm font-medium text-[#172317] outline-none focus:border-[#159734] disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed"
                                                disabled={isMaterialDisabled || !line.materialId}
                                                placeholder="Quy cách bao gói, yêu cầu..."
                                                onChange={(e) =>
                                                    updateLine(line.id, {
                                                        note: e.target.value
                                                    })
                                                }
                                                value={line.note}
                                            />
                                        </td>
                                        <td className="px-3 py-2.5 text-center">
                                            <button
                                                className="inline-flex size-8 items-center justify-center rounded-lg text-red-500 hover:bg-red-50 disabled:opacity-30"
                                                disabled={lines.length <= 1}
                                                onClick={() => removeLine(line.id)}
                                                title="Xóa dòng"
                                                type="button"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>

                {/* TỔNG KẾT DÒNG */}
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-[#f8fbf8] p-3 border border-[#e8f2e9]">
                    <span className="text-xs font-bold text-[#536078]">
                        Tổng số dòng nguyên liệu: <strong className="text-[#172317]">{totals.totalLines}</strong>
                    </span>
                    <span className="text-sm font-bold text-[#315f3c]">
                        Tổng số lượng đặt hàng: <strong className="text-base font-black text-[#159734]">{formatVietnamNumber(totals.totalOrderedQuantity)}</strong>
                    </span>
                </div>
            </div>

            {/* HÀNH ĐỘNG CUỐI TRANG */}
            <footer className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
                <Link
                    className="h-10 rounded-lg border border-[#dce5e1] px-4 text-sm font-black text-[#536078] hover:bg-[#f7f8fb] inline-flex items-center"
                    to="/management/inventory/receipts"
                >
                    Hủy bỏ
                </Link>
                <div className="flex flex-wrap gap-2">
                    <button
                        className="h-10 rounded-lg border border-[#d8eadc] bg-white px-5 text-sm font-black text-[#159734] hover:bg-[#f4faf5] disabled:opacity-60"
                        disabled={submitting}
                        onClick={() => void handleSave(false)}
                        type="button"
                    >
                        {submitting ? "Đang lưu..." : "Lưu bản nháp (DRAFT)"}
                    </button>
                    <button
                        className="h-10 rounded-lg bg-[#159734] px-6 text-sm font-black text-white hover:bg-[#12802c] shadow-sm disabled:opacity-60"
                        disabled={submitting}
                        onClick={() => void handleSave(true)}
                        type="button"
                    >
                        {submitting ? "Đang xử lý..." : "Lưu & Gửi duyệt (Submit)"}
                    </button>
                </div>
            </footer>
        </section>
    );
}
