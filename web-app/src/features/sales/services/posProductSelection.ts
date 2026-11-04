import type { RequestOptions } from "@/lib/api-client";
import { posProductApi } from "./posProduct.api";
import type { SaleProductPreview } from "../types/sale.types";

export class PosProductSelectionError extends Error {}

/** Shared by manual POS selection and recommendations. Stock/backorder belongs to addProduct. */
export function posVariantSelectionError(variant: SaleProductPreview): string {
  const id = variant.productVariantId ?? variant.id;
  if (!id?.trim() || ["undefined", "null"].includes(id)) {
    return "Không xác định được biến thể bán hàng.";
  }
  if (variant.priceAvailable === false || !Number.isFinite(variant.price) || variant.price < 0) {
    return "Biến thể chưa có giá bán hợp lệ.";
  }
  return "";
}

export function initialPosVariant(variants: SaleProductPreview[]): string {
  // Multiple units require an explicit choice, including when one has no price.
  return variants.length === 1 && !posVariantSelectionError(variants[0])
    ? (variants[0].productVariantId ?? variants[0].id)
    : "";
}

export async function resolvePosVariant(
  productId: string,
  variantId: string,
  options?: RequestOptions,
) {
  const variants = await posProductApi.listProductVariants(productId, options);
  if (
    !variants.some(
      (variant) =>
        variant.productId === productId && (variant.productVariantId ?? variant.id) === variantId,
    )
  ) {
    throw new PosProductSelectionError(
      "POS không còn trả về biến thể này cho sản phẩm đã chọn. Vui lòng tải lại.",
    );
  }
  const variant = await posProductApi.getPosView(variantId, options);
  if (variant.productId !== productId || (variant.productVariantId ?? variant.id) !== variantId) {
    throw new PosProductSelectionError(
      "Thông tin sản phẩm và biến thể POS không khớp. Vui lòng tải lại.",
    );
  }
  const error = posVariantSelectionError(variant);
  if (error) throw new PosProductSelectionError(error);
  return variant;
}
