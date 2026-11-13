import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Button, IconButton } from "../../../components/ui/Button";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { productVariantApi } from "../services/productVariant.api";
import { unitApi } from "../services/unit.api";
import { bomApi } from "../../manufacturing/services/bom.api";
import type { ProductVariant, Unit } from "../types/product.types";
import type { BomItemResponse } from "../../manufacturing/types/bom.types";
import { formatCurrency } from "../utils/product-formatters";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { ArrowLeft, Pencil, AlertCircle, Printer } from "lucide-react";
import { BarcodeSvg } from "../components/BarcodeSvg";
import { BulkBarcodePrintModal } from "../components/BulkBarcodePrintModal";

export function ProductVariantDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [variant, setVariant] = useState<ProductVariant | null>(null);
    const [unit, setUnit] = useState<Unit | null>(null);
    const [bomItems, setBomItems] = useState<BomItemResponse[]>([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showPrintModal, setShowPrintModal] = useState(false);

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

            const items = await bomApi.listByVariant(id);
            setBomItems(items);
        } catch (err) {
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
                        <Button onClick={() => navigate("/system/product-variants")}>
                            Quay lại danh sách
                        </Button>
                        <Button onClick={loadData} variant="primary">
                            Thử lại
                        </Button>
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
                    <IconButton
                        aria-label="Quay lại danh sách mặt hàng"
                        onClick={() => navigate("/system/product-variants")}
                        size="md"
                        title="Quay lại danh sách mặt hàng"
                        variant="secondary"
                    >
                        <ArrowLeft size={20} />
                    </IconButton>
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
                    <Button
                        disabled={!variant.barcode}
                        onClick={() => setShowPrintModal(true)}
                        title={variant.barcode ? "In mã vạch" : "Mặt hàng này chưa có mã vạch"}
                    >
                        <Printer size={16} />
                        In mã vạch
                    </Button>
                    <Button
                        onClick={() => navigate("/system/product-variants", { state: { editVariantId: variant.id } })}
                        variant="primary"
                    >
                        <Pencil size={16} />
                        Chỉnh sửa Mặt hàng / BOM
                    </Button>
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
                                <span className="text-sm font-medium text-gray-500">Mã vạch (Barcode)</span>
                                <span className="text-sm font-bold text-emerald-800 text-right font-mono">
                                    {variant.barcode || <span className="text-gray-400 font-normal italic">Chưa có mã vạch</span>}
                                </span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Định lượng</span>
                                <span className="text-sm font-bold text-gray-800 text-right">{variant.quantity}</span>
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
                                <span className="text-sm font-medium text-gray-500">Có thể bán</span>
                                <span className="text-sm font-semibold text-gray-700 text-right">{variant.sellableQuantity}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Ngưỡng sản xuất</span>
                                <span className="text-sm font-semibold text-gray-700 text-right">{variant.productionThreshold}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Mỗi mẻ sản xuất</span>
                                <span className="text-sm font-semibold text-gray-700 text-right">{variant.productionBatchQuantity}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-2 border-b border-neutral-50 pb-3">
                                <span className="text-sm font-medium text-gray-500">Chính sách kho</span>
                                <span className="text-sm font-semibold text-gray-800 text-right">
                                    {variant.inventoryPolicy === "TRACK_STOCK" ? "Theo dõi tồn kho" : "Không theo dõi"}
                                </span>
                            </div>
                        </div>

                        {/* Barcode Visual Card */}
                        <div className="mt-2 p-4 rounded-xl border border-neutral-200 bg-neutral-50/70 flex flex-col items-center justify-center">
                            <span className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Hình ảnh mã vạch</span>
                            <div className="bg-white p-3 rounded-lg border border-gray-200 shadow-2xs flex items-center justify-center min-w-[200px]">
                                <BarcodeSvg
                                    value={variant.barcode}
                                    width={1.6}
                                    height={44}
                                    displayValue={true}
                                    fontSize={12}
                                />
                            </div>
                            {variant.barcode ? (
                                <Button className="mt-3 px-0" onClick={() => setShowPrintModal(true)} size="xs" variant="link">
                                    <Printer size={14} />
                                    In mã vạch này
                                </Button>
                            ) : (
                                <span className="mt-3 text-xs text-amber-600 font-medium">
                                    Chưa có mã vạch để in
                                </span>
                            )}
                        </div>
                    </div>
                </div>

                {/* Right column - BOM items list */}
                <div className="flex flex-col lg:col-span-2 rounded-2xl bg-white border border-neutral-100 shadow-pos-card overflow-hidden">
                    <div className="bg-[#eaf7ec] px-5 py-4 border-b border-[#edf0f4] flex items-center justify-between">
                        <h2 className="text-base font-bold text-gray-900">Định mức nguyên liệu (BOM)</h2>
                        <StatusBadge tone="success">
                            {bomItems.length} nguyên liệu
                        </StatusBadge>
                    </div>

                    <div className="flex-1 p-5 min-h-[300px]">
                        {bomItems.length === 0 ? (
                            <div className="flex flex-col items-center justify-center text-center p-8 h-full min-h-[250px]">
                                <div className="rounded-full bg-emerald-50 p-4 text-emerald-600 mb-3 border border-emerald-100">
                                    <Pencil size={30} />
                                </div>
                                <h3 className="text-lg font-bold text-gray-700">Chưa có định mức nguyên liệu</h3>
                                <p className="text-sm text-gray-500 mt-1 max-w-sm mb-5">
                                    Mặt hàng này hiện chưa được khai báo định mức nguyên liệu (BOM). Bạn cần khai báo để thực hiện sản xuất.
                                </p>
                                <Button
                                    onClick={() => navigate("/system/product-variants", { state: { editVariantId: variant.id } })}
                                    variant="primary"
                                >
                                    <Pencil size={16} />
                                    Khai báo ngay
                                </Button>
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableHeaderCell>Nguyên liệu</TableHeaderCell>
                                            <TableHeaderCell className="text-right">Định lượng</TableHeaderCell>
                                            <TableHeaderCell>Đơn vị</TableHeaderCell>
                                            <TableHeaderCell className="text-right">Hao hụt</TableHeaderCell>
                                        </TableRow>
                                    </TableHead>
                                    <tbody className="font-medium text-gray-700">
                                        {bomItems.map((item) => (
                                            <TableRow key={item.id} className="hover:bg-neutral-50/50 transition-colors">
                                                <TableCell className="font-bold text-gray-800">{item.ingredient.name}</TableCell>
                                                <TableCell className="text-right font-mono">{item.quantity}</TableCell>
                                                <TableCell>{item.unit.symbol}</TableCell>
                                                <TableCell className="text-right">{item.wasteRate}%</TableCell>
                                            </TableRow>
                                        ))}
                                    </tbody>
                                </Table>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <BulkBarcodePrintModal
                isOpen={showPrintModal}
                onClose={() => setShowPrintModal(false)}
                variants={variant ? [variant] : []}
            />
        </div>
    );
}
