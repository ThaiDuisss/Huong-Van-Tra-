import { ArrowLeft, Package, Pencil, Plus } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { ApiErrorState, ApiLoadingState } from "../components/ApiState";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { useApiResource } from "../hooks/useApiResource";
import { productApi } from "../services/product.api";
import { routePaths } from "../../../routes/path";
import { formatCurrency } from "../utils/product-formatters";

export function ProductDetailPage() {
    const { id = "" } = useParams();
    const loadProduct = useCallback(() => productApi.detail(id), [id]);
    const resource = useApiResource(loadProduct);

    if (resource.loading) {
        return <ApiLoadingState />;
    }

    if (resource.error || !resource.data) {
        return (
            <ApiErrorState
                message={resource.error || "Không tìm thấy sản phẩm."}
            />
        );
    }

    const product = resource.data;

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={["Product Management", "Sản phẩm", product.name]}
            />
            <header className="flex items-start justify-between gap-4">
                <div>
                    <Link
                        to={routePaths.products}
                        className="mb-3 inline-flex items-center gap-2 text-sm font-bold text-hvt-green-800"
                    >
                        <ArrowLeft size={16} />
                        Quay lại danh sách
                    </Link>
                    <h1 className="text-3xl font-bold text-pos-ink">
                        {product.name}
                    </h1>
                </div>
                <div className="flex gap-2">
                    <ActionButton>
                        <Pencil size={16} />
                        Sửa sản phẩm
                    </ActionButton>
                    <Link
                        to={routePaths.productVariantsByProduct.replace(
                            ":productId",
                            String(product.id)
                        )}
                    >
                        <ActionButton variant="primary">
                            <Plus size={16} />
                            Thêm biến thể
                        </ActionButton>
                    </Link>
                </div>
            </header>

            <article className="grid grid-cols-[220px_minmax(0,1fr)] gap-6 rounded-2xl bg-white p-6 shadow-pos-card">
                {product.imageUrl ? (
                    <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-52 w-full rounded-2xl object-cover"
                    />
                ) : (
                    <div className="grid h-52 place-items-center rounded-2xl bg-neutral-100 text-pos-muted">
                        <Package size={42} />
                    </div>
                )}
                <div className="grid content-start gap-4">
                    <CatalogStatusBadge status={product.status} />
                    <div>
                        <p className="text-sm text-pos-muted">Danh mục</p>
                        <strong>{product.category.name}</strong>
                    </div>
                    <div>
                        <p className="text-sm text-pos-muted">Mô tả</p>
                        <p className="mt-1">
                            {product.description || "Chưa có mô tả."}
                        </p>
                    </div>
                </div>
            </article>

            <section className="overflow-hidden rounded-2xl bg-white shadow-pos-card">
                <header className="border-b border-pos-border px-5 py-4">
                    <h2 className="text-xl font-bold">
                        Danh sách biến thể ({product.variants.length})
                    </h2>
                </header>
                <table className="w-full text-left text-sm">
                    <thead className="bg-neutral-50 text-xs text-pos-muted">
                        <tr>
                            <th className="px-5 py-3">SKU</th>
                            <th className="px-5 py-3">Tên biến thể</th>
                            <th className="px-5 py-3">Giá bán</th>
                            <th className="px-5 py-3">Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody>
                        {product.variants.map((variant) => (
                            <tr key={variant.id}>
                                <td className="border-t border-pos-border px-5 py-4 font-bold">
                                    {variant.sku}
                                </td>
                                <td className="border-t border-pos-border px-5 py-4">
                                    {variant.variantName}
                                </td>
                                <td className="border-t border-pos-border px-5 py-4">
                                    {formatCurrency(variant.sellingPrice)}
                                </td>
                                <td className="border-t border-pos-border px-5 py-4">
                                    <CatalogStatusBadge
                                        status={variant.status}
                                    />
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {product.variants.length === 0 ? (
                    <p className="p-8 text-center text-sm text-pos-muted">
                        Sản phẩm chưa có biến thể.
                    </p>
                ) : null}
            </section>
        </section>
    );
}
