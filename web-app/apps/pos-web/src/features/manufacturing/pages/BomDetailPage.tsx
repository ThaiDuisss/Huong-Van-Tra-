import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { bomApi } from "../services/bom.api";
import type { BomResponse, BomItemResponse } from "../types/bom.types";
import { ActionButton } from "../components/shared/ActionButton";

export function BomDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    
    const [bom, setBom] = useState<BomResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!id) return;
        setLoading(true);
        bomApi.detail(id)
            .then((res: BomResponse) => {
                setBom(res);
                setError(null);
            })
            .catch((err: any) => {
                console.error("Error loading BOM detail", err);
                setError("Không thể tải thông tin chi tiết BOM. Vui lòng thử lại sau.");
            })
            .finally(() => {
                setLoading(false);
            });
    }, [id]);

    if (loading) {
        return (
            <div className="flex h-[400px] w-full items-center justify-center">
                <div className="h-10 w-10 animate-spin rounded-full border-4 border-pos-primary border-t-transparent"></div>
            </div>
        );
    }

    if (error || !bom) {
        return (
            <div className="p-6">
                <div className="rounded-2xl bg-red-50 p-6 text-center">
                    <p className="text-base font-semibold text-red-600">{error || "Không tìm thấy thông tin BOM."}</p>
                    <button
                        className="mt-4 px-4 py-2 bg-white text-gray-700 font-semibold border border-gray-300 rounded-xl hover:bg-gray-50 active:scale-95 transition-all text-sm"
                        onClick={() => navigate("/system/manufacturing/boms")}
                    >
                        Quay lại danh sách
                    </button>
                </div>
            </div>
        );
    }

    // Format Date Helper
    function formatDate(dateStr: string | null | undefined) {
        if (!dateStr) return "N/A";
        try {
            const d = new Date(dateStr);
            return d.toLocaleString("vi-VN", {
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
            });
        } catch {
            return dateStr;
        }
    }

    return (
        <div className="flex flex-col gap-6 p-6">
            {/* Header section */}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate("/system/manufacturing/boms")}
                        className="grid h-10 w-10 place-items-center rounded-xl border border-pos-border bg-white text-gray-600 transition-all hover:bg-neutral-50 hover:text-gray-900 active:scale-95"
                        title="Quay lại"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="h-5 w-5">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
                        </svg>
                    </button>
                    <div>
                        <h1 className="text-2xl font-bold tracking-tight text-gray-900">
                            Chi tiết công thức sản xuất (BOM)
                        </h1>
                        <p className="text-sm font-medium text-gray-500">
                            Xem thông tin định lượng nguyên liệu và lịch sử biến động
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <ActionButton
                        variant="secondary"
                        onClick={() => navigate("/system/manufacturing/boms", { state: { editBomId: bom.id } })}
                    >
                        Chỉnh sửa BOM
                    </ActionButton>
                </div>
            </div>

            {/* Content grid */}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
                {/* Left Column: General Info */}
                <div className="flex flex-col gap-6 lg:col-span-1">
                    <div className="rounded-2xl border border-pos-border bg-white p-6 shadow-sm">
                        <h3 className="mb-4 text-base font-bold text-gray-800">Thông tin chung</h3>
                        
                        <div className="flex flex-col gap-4">
                            <div className="grid gap-1">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Mã BOM</span>
                                <span className="font-semibold text-gray-800">{bom.bomCode || "Chưa có mã"}</span>
                            </div>

                            <div className="grid gap-1">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Tên công thức</span>
                                <span className="font-medium text-gray-800">{bom.name || "Mặc định (Chưa đặt tên)"}</span>
                            </div>

                            <div className="grid gap-1">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Biến thể sản phẩm</span>
                                <span className="font-semibold text-gray-800">{bom.productName}</span>
                                <span className="text-xs text-gray-500">Variant: {bom.variantName} (SKU: {bom.variantSku})</span>
                            </div>

                            <div className="grid gap-1">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Phiên bản (Version)</span>
                                <div className="flex items-center gap-2">
                                    <span className="inline-flex items-center rounded-md bg-neutral-100 px-2 py-0.5 text-sm font-bold text-neutral-800">
                                        v{bom.version}
                                    </span>
                                    {bom.isDefault && (
                                        <span className="inline-flex items-center rounded-md bg-green-50 px-2 py-0.5 text-xs font-bold text-green-700 ring-1 ring-inset ring-green-600/20">
                                            Công thức mặc định
                                        </span>
                                    )}
                                </div>
                            </div>

                            <div className="grid gap-1">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Trạng thái</span>
                                <div>
                                    {bom.status === "ACTIVE" ? (
                                        <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-bold text-green-800">
                                            Đang hoạt động
                                        </span>
                                    ) : (
                                        <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-bold text-red-800">
                                            Ngừng hoạt động
                                        </span>
                                    )}
                                </div>
                            </div>

                            <div className="grid gap-1 border-t border-pos-border pt-4">
                                <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Mô tả chi tiết</span>
                                <p className="text-sm font-medium text-gray-600 whitespace-pre-line">
                                    {bom.description || "Không có mô tả cho công thức này."}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Right Column: Ingredient details */}
                <div className="flex flex-col gap-6 lg:col-span-2">
                    <div className="rounded-2xl border border-pos-border bg-white shadow-sm overflow-hidden">
                        <div className="border-b border-pos-border px-6 py-4">
                            <h3 className="text-base font-bold text-gray-800">Định lượng nguyên liệu</h3>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full text-left text-sm text-gray-500">
                                <thead className="bg-neutral-50 text-xs font-bold uppercase tracking-wider text-gray-400 border-b border-pos-border">
                                    <tr>
                                        <th scope="col" className="px-6 py-3">Mã nguyên liệu</th>
                                        <th scope="col" className="px-6 py-3">Tên nguyên liệu</th>
                                        <th scope="col" className="px-6 py-3 text-right">Định lượng</th>
                                        <th scope="col" className="px-6 py-3">Đơn vị</th>
                                        <th scope="col" className="px-6 py-3 text-right">Hao hụt (%)</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-pos-border">
                                    {bom.items && bom.items.length > 0 ? (
                                        bom.items.map((item: BomItemResponse) => (
                                            <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                                                <td className="px-6 py-4 font-semibold text-gray-700">{item.ingredient.ingredientCode}</td>
                                                <td className="px-6 py-4 font-medium text-gray-900">{item.ingredient.name}</td>
                                                <td className="px-6 py-4 text-right font-bold text-gray-800">{item.quantity}</td>
                                                <td className="px-6 py-4 font-medium text-gray-500">{item.unit.name} ({item.unit.symbol})</td>
                                                <td className="px-6 py-4 text-right font-semibold text-gray-600">{item.wasteRate}%</td>
                                            </tr>
                                        ))
                                    ) : (
                                        <tr>
                                            <td colSpan={5} className="px-6 py-10 text-center font-medium text-gray-400">
                                                Chưa có thông tin nguyên liệu.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

            {/* Timeline: Change History */}
            <div className="rounded-2xl border border-pos-border bg-white p-6 shadow-sm">
                <h3 className="mb-6 text-base font-bold text-gray-800">Lịch sử biến động</h3>
                <div className="flow-root">
                    <ul className="-mb-8">
                        {/* Event 1: Tạo BOM */}
                        <li>
                            <div className="relative pb-8">
                                {bom.updatedAt && bom.updatedAt !== bom.createdAt && (
                                    <span className="absolute left-4 top-4 -ml-px h-full w-0.5 bg-gray-200" aria-hidden="true" />
                                )}
                                <div className="relative flex space-x-3">
                                    <div>
                                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-green-50 ring-8 ring-white">
                                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5 text-green-600">
                                                <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11.25a.75.75 0 0 0-1.5 0v2.5h-2.5a.75.75 0 0 0 0 1.5h2.5v2.5a.75.75 0 0 0 1.5 0v-2.5h2.5a.75.75 0 0 0 0-1.5h-2.5v-2.5Z" clipRule="evenodd" />
                                            </svg>
                                        </span>
                                    </div>
                                    <div className="flex min-w-0 flex-1 justify-between space-x-4 pt-1.5">
                                        <div>
                                            <p className="text-sm font-bold text-gray-800">
                                                Khởi tạo công thức (BOM) phiên bản v{bom.version}
                                            </p>
                                            <p className="text-xs font-semibold text-gray-500">
                                                Mã hệ thống ghi nhận: <span className="font-mono text-neutral-600 bg-neutral-100 px-1 rounded">{bom.id}</span>
                                            </p>
                                        </div>
                                        <div className="whitespace-nowrap text-right text-xs font-semibold text-gray-500">
                                            <time>{formatDate(bom.createdAt)}</time>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </li>

                        {/* Event 2: Cập nhật cuối cùng (nếu updatedAt tồn tại và khác createdAt) */}
                        {bom.updatedAt && bom.updatedAt !== bom.createdAt && (
                            <li>
                                <div className="relative pb-8">
                                    <div className="relative flex space-x-3">
                                        <div>
                                            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 ring-8 ring-white">
                                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5 text-blue-600">
                                                    <path d="m2.695 14.762-1.262 3.155a.5.5 0 0 0 .65.65l3.155-1.262a4 4 0 0 0 1.343-.886L17.5 5.501a2.121 2.121 0 0 0-3-3L3.58 13.419a4 4 0 0 0-.885 1.343Z" />
                                                </svg>
                                            </span>
                                        </div>
                                        <div className="flex min-w-0 flex-1 justify-between space-x-4 pt-1.5">
                                            <div>
                                                <p className="text-sm font-bold text-gray-800">
                                                    Cập nhật thông tin công thức
                                                </p>
                                                <p className="text-xs font-semibold text-gray-500">
                                                    Hệ thống đồng bộ thay đổi định lượng nguyên vật liệu.
                                                </p>
                                            </div>
                                            <div className="whitespace-nowrap text-right text-xs font-semibold text-gray-500">
                                                <time>{formatDate(bom.updatedAt)}</time>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                        )}
                    </ul>
                </div>
            </div>
        </div>
    );
}
