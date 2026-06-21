import { ArrowLeft, Pencil } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { Breadcrumbs } from "../../manufacturing/components/shared/Breadcrumbs";
import { ApiErrorState, ApiLoadingState } from "../components/ApiState";
import { CatalogStatusBadge } from "../components/CatalogStatusBadge";
import { DetailList } from "../components/CatalogResourceManagement";
import { useApiResource } from "../hooks/useApiResource";
import { productVariantApi } from "../services/productVariant.api";
import { routePaths } from "../../../routes/path";
import { formatCurrency } from "../utils/product-formatters";

export function ProductVariantDetailPage() {
    const { id = "" } = useParams();
    const loadVariant = useCallback(
        () => productVariantApi.detail(id),
        [id]
    );
    const resource = useApiResource(loadVariant);

    if (resource.loading) {
        return <ApiLoadingState />;
    }

    if (resource.error || !resource.data) {
        return (
            <ApiErrorState
                message={
                    resource.error || "Không tìm thấy biến thể sản phẩm."
                }
            />
        );
    }

    const variant = resource.data;

    return (
        <section className="grid gap-5">
            <Breadcrumbs
                items={[
                    "Product Management",
                    "Biến thể sản phẩm",
                    variant.sku
                ]}
            />
            <header className="flex flex-col items-stretch justify-between gap-4 lg:flex-row lg:items-start">
                <div>
                    <Link
                        to={routePaths.productVariants}
                        className="mb-3 inline-flex items-center gap-2 text-sm font-bold text-hvt-green-800"
                    >
                        <ArrowLeft size={16} />
                        Quay lại danh sách
                    </Link>
                    <h1 className="text-2xl font-bold sm:text-3xl">
                        {variant.variantName}
                    </h1>
                    <p className="mt-1 text-pos-muted">{variant.sku}</p>
                </div>
                <ActionButton variant="primary">
                    <Pencil size={16} />
                    Chỉnh sửa biến thể
                </ActionButton>
            </header>
            <article className="w-full max-w-3xl rounded-2xl bg-white p-4 shadow-pos-card sm:p-6">
                <DetailList
                    items={[
                        { label: "Sản phẩm", value: variant.productName },
                        { label: "SKU", value: variant.sku },
                        {
                            label: "Tên biến thể",
                            value: variant.variantName
                        },
                        {
                            label: "Giá bán",
                            value: formatCurrency(variant.sellingPrice)
                        },
                        {
                            label: "Giá vốn",
                            value: formatCurrency(variant.costPrice)
                        },
                        {
                            label: "Chính sách tồn kho",
                            value:
                                variant.inventoryPolicy === "TRACK_STOCK"
                                    ? "Theo dõi tồn kho"
                                    : "Không theo dõi"
                        },
                        {
                            label: "Trạng thái",
                            value: (
                                <CatalogStatusBadge
                                    status={variant.status}
                                />
                            )
                        }
                    ]}
                />
            </article>
        </section>
    );
}
