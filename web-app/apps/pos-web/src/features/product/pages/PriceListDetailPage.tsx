import { ArrowLeft, Pencil } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { ApiErrorState, ApiLoadingState } from "../components/ApiState";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { useApiResource } from "../hooks/useApiResource";
import { priceListApi } from "../services/priceList.api";
import { routePaths } from "../../../routes/path";
import {
    formatCurrency,
    formatDate
} from "../utils/product-formatters";

export function PriceListDetailPage() {
    const { id = "" } = useParams();
    const loadPriceList = useCallback(
        () => priceListApi.detail(id),
        [id]
    );
    const resource = useApiResource(loadPriceList);

    if (resource.loading) {
        return <ApiLoadingState />;
    }

    if (resource.error || !resource.data) {
        return (
            <ApiErrorState
                message={resource.error || "Không tìm thấy bảng giá."}
            />
        );
    }

    const priceList = resource.data;

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={[
                    "Product Management",
                    "Bảng giá",
                    priceList.name
                ]}
            />
            <header className="flex flex-col items-stretch justify-between gap-4 lg:flex-row lg:items-start">
                <div>
                    <Link
                        to={routePaths.priceLists}
                        className="mb-3 inline-flex items-center gap-2 text-sm font-bold text-hvt-green-800"
                    >
                        <ArrowLeft size={16} />
                        Quay lại danh sách
                    </Link>
                    <h1 className="text-2xl font-bold sm:text-3xl">
                        {priceList.name}
                    </h1>
                </div>
                <ActionButton>
                    <Pencil size={16} />
                    Sửa bảng giá
                </ActionButton>
            </header>

            <article className="grid grid-cols-1 gap-4 rounded-2xl bg-white p-4 shadow-pos-card sm:grid-cols-2 sm:p-6 xl:grid-cols-4">
                <Info
                    label="Chi nhánh"
                    value={`#${priceList.branchId}`}
                />
                <Info
                    label="Hiệu lực từ"
                    value={formatDate(priceList.effectiveFrom)}
                />
                <Info
                    label="Hiệu lực đến"
                    value={formatDate(priceList.effectiveTo)}
                />
                <Info
                    label="Trạng thái"
                    value={<CatalogStatusBadge status={priceList.status} />}
                />
            </article>

            <section className="overflow-hidden rounded-2xl bg-white shadow-pos-card">
                <header className="border-b border-pos-border px-5 py-4">
                    <h2 className="text-xl font-bold">
                        Danh sách giá ({priceList.items.length})
                    </h2>
                </header>
                <div className="overflow-x-auto">
                    <table className="min-w-[640px] w-full text-left text-sm">
                        <thead className="bg-neutral-50 text-xs text-pos-muted">
                            <tr>
                                <th className="px-5 py-3">SKU</th>
                                <th className="px-5 py-3">Sản phẩm</th>
                                <th className="px-5 py-3">Biến thể</th>
                                <th className="px-5 py-3">Giá bán</th>
                            </tr>
                        </thead>
                        <tbody>
                            {priceList.items.map((item) => (
                                <tr key={item.id}>
                                    <td className="border-t border-pos-border px-5 py-4 font-bold">
                                        {item.productVariant.sku}
                                    </td>
                                    <td className="border-t border-pos-border px-5 py-4">
                                        {item.productVariant.productName}
                                    </td>
                                    <td className="border-t border-pos-border px-5 py-4">
                                        {item.productVariant.variantName}
                                    </td>
                                    <td className="border-t border-pos-border px-5 py-4">
                                        {formatCurrency(item.sellingPrice)}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                {priceList.items.length === 0 ? (
                    <p className="p-8 text-center text-sm text-pos-muted">
                        Bảng giá chưa có sản phẩm.
                    </p>
                ) : null}
            </section>
        </section>
    );
}

function Info({
    label,
    value
}: {
    label: string;
    value: React.ReactNode;
}) {
    return (
        <div>
            <p className="text-sm text-pos-muted">{label}</p>
            <div className="mt-1 font-bold">{value}</div>
        </div>
    );
}
