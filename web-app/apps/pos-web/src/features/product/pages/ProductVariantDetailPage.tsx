import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { productVariantApi } from "../services/productVariant.api";
import { unitApi } from "../services/unit.api";
import { bomApi } from "../../manufacturing/services/bom.api";
import type { ProductVariant, Unit } from "../types/product.types";
import type { BomResponse } from "../../manufacturing/types/bom.types";
import { formatCurrency } from "../utils/product-formatters";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { ArrowLeft, Plus, Eye, Check, AlertCircle } from "lucide-react";

export function ProductVariantDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [variant, setVariant] = useState<ProductVariant | null>(null);
    const [unit, setUnit] = useState<Unit | null>(null);
    const [boms, setBoms] = useState<BomResponse[]>([]);
    
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const loadData = async () => {
        if (!id) return;
        setLoading(true);
        setError(null);
        try {
            const variantData = await productVariantApi.detail(id);
            setVariant(variantData);

            if (variantData.unitId) {
                try {
                    const unitData = await unitApi.detail(String(variantData.unitId));
                    setUnit(unitData);
                } catch (unitErr) {
                    console.error("Error loading unit details", unitErr);
                }
            }

            const bomPage = await bomApi.list({ productVariantId: id, size: 100 });
            setBoms(bomPage.content ?? []);
        } catch (err: any) {
            console.error("Error loading product variant details", err);
            setError("Không thể tải thông tin chi tiết Mặt hàng. Vui lòng thử lại sau.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, [id]);

    if (loading) {
        return (
            <div className="flex h-[450px] w-full items-center justify-center">
                <div className="flex flex-col items-center gap-3">
                    <div className="h-10 w-10 animate-spin rounded-full border-4 border-pos-primary border-t-transparent"></div>
                    <p className="text-sm font-medium text-gray-500">Đang tải thông tin mặt hàng...</p>
                </div>
            </div>
        );
    }

    if (error || !variant) {
        return (
            <div className="p-6">
                <div className="rounded-2xl bg-red-50 p-6 text-center border border-red-200 shadow-sm max-w-xl mx-auto">
                    <AlertCircle className="mx-auto mb-3 text-red-500" size={36} />
                    <h2 className="text-lg font-bold text-red-800 mb-2">Đã xảy ra lỗi</h2>
                    <p className="text-sm font-medium text-red-600 mb-4">{error || "Không tìm thấy mặt hàng."}</p>
                    <div className="flex gap-3 justify-center">
                        <button
                            className="px-4 py-2 bg-white text-gray-700 font-bold border border-gray-300 rounded-xl hover:bg-gray-50 active:scale-95 transition-all text-sm shadow-sm"
                            onClick={() => navigate("/system/product-variants")}
                        >
                            Quay lại danh sách
                        </button>
                        <button
                            className="px-4 py-2 bg-pos-primary text-white font-bold rounded-xl hover:bg-hvt-green-700 active:scale-95 transition-all text-sm shadow-sm"
                            onClick={loadData}
                        >
                            Thử lại
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-6 p-6">
            {/* Header section */}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-neutral-100 pb-5">
                <div className="flex items-center gap-3.5">
                    <button
                        onClick={() => navigate("/system/product-variants")}
                        className="grid h-10 w-10 place-items-center rounded-xl border border-pos-border bg-white text-gray-600 transition-all hover:bg-neutral-50 hover:text-gray-900 active:scale-95 shadow-sm"
                        title="Quay lại danh sách mặt hàng"
                    >
                        <ArrowLeft size={20} />
                    </button>
                    <div>
                        <div className="flex items-center gap-2.5">
                            <h1 className="text-2xl font-bold tracking-tight text-gray-900">
                                {variant.variantName}
                            </h1>
                            <CatalogStatusBadge status={variant.status} />
                        </div>
                        <p className="text-sm font-medium text-gray-500 mt-0.5">
                            Mã sản phẩm (SKU): <span className="font-bold text-gray-700">{variant.sku}</span> | Sản phẩm: <span className="text-gray-700 font-semibold">{variant.productName}</span>
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    <Link
                        to={`/system/manufacturing/boms?create=true&variantId=${variant.id}`}
                        className="flex items-center gap-1.5 rounded-xl bg-[#07883a] px-4.5 py-2.5 text-sm font-bold text-white shadow-md hover:bg-hvt-green-700 active:scale-95 transition-all cursor-pointer"
                    >
                        <Plus size={16} />
                        Tạo công thức sản xuất
                    </Link>
                </div>
            </div>

            {/* Content grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left column - variant details */}
                <div className="flex flex-col rounded-2xl bg-white border border-neutral-100 shadow-pos-card overflow-hidden">
                    <div className="bg-[#eaf7ec] px-5 py-4 border-b border-[#edf0f4]">
                        <h2 className="text-base font-bold text-gray-900">Thông tin chi tiết Mặt hàng</h2>
                    </div>
                    <div className="p-5 flex-1 flex flex-col justify-between gap-5">
                        <div className="grid gap-4.5">
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Tên Mặt hàng</span>
                                <span className="text-sm font-bold text-gray-800 text-right">{variant.variantName}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Sản phẩm gốc</span>
                                <span className="text-sm font-semibold text-gray-800 text-right">{variant.productName}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Mã SKU</span>
                                <span className="text-sm font-bold text-gray-800 text-right font-mono">{variant.sku}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Đơn vị bán</span>
                                <span className="text-sm font-semibold text-gray-800 text-right">
                                    {unit ? `${unit.name} (${unit.symbol})` : "N/A"}
                                </span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Giá bán</span>
                                <span className="text-sm font-bold text-emerald-700 text-right">{formatCurrency(variant.sellingPrice)}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Giá vốn</span>
                                <span className="text-sm font-semibold text-gray-700 text-right">{formatCurrency(variant.costPrice)}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Chính sách kho</span>
                                <span className="text-sm font-semibold text-gray-800 text-right">
                                    {variant.inventoryPolicy === "TRACK_STOCK" ? "Theo dõi tồn kho" : "Không theo dõi"}
                                </span>
                            </div>
                        </div>

                        {/* Profit margin summary card */}
                        {/* <div className="rounded-xl bg-neutral-50 p-4 border border-neutral-200/60 mt-2">
                            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Ước tính lợi nhuận</h3>
                            <div className="grid grid-cols-2 gap-y-1 text-sm font-medium text-gray-700">
                                <div>Lợi nhuận:</div>
                                <div className="text-right font-bold text-gray-900">{formatCurrency(profit)}</div>
                                <div>Tỷ suất biên:</div>
                                <div className={`text-right font-bold ${profitMargin >= 30 ? 'text-emerald-600' : profitMargin > 0 ? 'text-pos-gold' : 'text-red-500'}`}>
                                    {profitMargin.toFixed(1)}%
                                </div>
                            </div>
                        </div> */}
                    </div>
                </div>

                {/* Right column - BOMs list */}
                <div className="flex flex-col lg:col-span-2 rounded-2xl bg-white border border-neutral-100 shadow-pos-card overflow-hidden">
                    <div className="bg-[#eaf7ec] px-5 py-4 border-b border-[#edf0f4] flex items-center justify-between">
                        <h2 className="text-base font-bold text-gray-900">Công thức sản xuất (BOM)</h2>
                        <span className="text-xs font-bold bg-[#07883a]/10 text-[#07883a] px-2 py-0.5 rounded-full">
                            Có {boms.length} công thức
                        </span>
                    </div>

                    <div className="flex-1 p-5 min-h-[300px]">
                        {boms.length === 0 ? (
                            <div className="flex flex-col items-center justify-center text-center p-8 h-full min-h-[250px]">
                                <div className="rounded-full bg-emerald-50 p-4 text-emerald-600 mb-3 border border-emerald-100">
                                    <Plus size={30} />
                                </div>
                                <h3 className="text-lg font-bold text-gray-700">Chưa có công thức sản xuất</h3>
                                <p className="text-sm text-gray-500 mt-1 max-w-sm mb-5">
                                    Mặt hàng này hiện tại chưa được khai báo công thức định lượng (BOM). Bạn cần tạo công thức để thực hiện sản xuất.
                                </p>
                                <Link
                                    to={`/system/manufacturing/boms?create=true&variantId=${variant.id}`}
                                    className="flex items-center gap-1.5 rounded-xl bg-[#07883a] px-5 py-2.5 text-sm font-bold text-white shadow-sm hover:bg-hvt-green-700 active:scale-95 transition-all cursor-pointer"
                                >
                                    <Plus size={16} />
                                    Tạo công thức ngay
                                </Link>
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
                                    <thead className="bg-neutral-50 text-xs font-bold text-gray-500 uppercase tracking-wider">
                                        <tr>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80">Mã BOM</th>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80">Tên công thức</th>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80">Phiên bản</th>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80">Mặc định</th>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80">Trạng thái</th>
                                            <th className="h-10 align-middle px-4 py-0 border-b border-neutral-200/80 text-right">Hành động</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-neutral-100 font-medium text-gray-700">
                                        {boms.map((bom) => (
                                            <tr key={bom.id} className="hover:bg-neutral-50/50 transition-colors">
                                                <td className="h-12 align-middle px-4 py-0 font-bold text-gray-800">{bom.bomCode}</td>
                                                <td className="h-12 align-middle px-4 py-0">{bom.name || "N/A"}</td>
                                                <td className="h-12 align-middle px-4 py-0 text-center font-mono">v{bom.version}</td>
                                                <td className="h-12 align-middle px-4 py-0">
                                                    {bom.isDefault ? (
                                                        <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                                                            <Check size={10} /> Mặc định
                                                        </span>
                                                    ) : (
                                                        <span className="text-gray-400 text-xs">-</span>
                                                    )}
                                                </td>
                                                <td className="h-12 align-middle px-4 py-0">
                                                    <span className={`inline-block px-2 py-0.5 text-xs font-bold rounded-full border ${
                                                        bom.status === "ACTIVE"
                                                            ? "bg-emerald-50 border-emerald-200 text-emerald-700"
                                                            : bom.status === "INACTIVE"
                                                            ? "bg-neutral-100 border-neutral-200 text-neutral-600"
                                                            : "bg-amber-50 border-amber-200 text-amber-700"
                                                    }`}>
                                                        {bom.status === "ACTIVE" ? "Đang hoạt động" : bom.status === "INACTIVE" ? "Ngừng hoạt động" : bom.status}
                                                    </span>
                                                </td>
                                                <td className="h-12 align-middle px-4 py-0 text-right">
                                                    <Link
                                                        to={`/system/manufacturing/boms/${bom.id}`}
                                                        className="inline-flex items-center gap-1 text-xs text-[#07883a] hover:underline font-bold"
                                                    >
                                                        <Eye size={12} />
                                                        Chi tiết
                                                    </Link>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
