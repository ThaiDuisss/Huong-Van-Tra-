import { useEffect, useRef, useState } from "react";
import { formatCurrency } from "../../sales/services/money";
import { PosVariantSelector } from "../../sales/components/product/PosVariantSelector";
import {
  initialPosVariant,
  posVariantSelectionError,
  PosProductSelectionError,
} from "../../sales/services/posProductSelection";
import type { SaleProductPreview } from "../../sales/types/sale.types";
import { useProductRecommendations } from "../hooks/useProductRecommendations";
import {
  loadRecommendedProduct,
  resolveRecommendedVariant,
} from "../services/productRecommendation.api";
import type {
  RecommendationMode,
  RecommendedProductView,
} from "../types/productRecommendation.types";

export interface RecommendationResultProps {
  customerId: string | null;
  mode: RecommendationMode;
  disabled?: boolean;
  onAdd?(product: SaleProductPreview): Promise<boolean>;
}

export function ProductRecommendationResults({
  customerId,
  mode,
  disabled,
  onAdd,
}: RecommendationResultProps) {
  const { loading, result, products, error } = useProductRecommendations(customerId, mode);
  const [pending, setPending] = useState<string | null>(null);
  if (loading)
    return (
      <p role="status" className="p-4 text-sm text-slate-600">
        Đang tải gợi ý và thông tin bán hàng...
      </p>
    );
  if (error)
    return (
      <p role="alert" className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
        {error}
      </p>
    );
  const label =
    result?.source === "FALLBACK"
      ? "Gợi ý dự phòng"
      : result?.source === "POPULARITY" || result?.modelType === "popularity"
        ? "Sản phẩm phổ biến"
        : "Gợi ý cá nhân hóa";
  return (
    <div className="grid gap-3">
      <p className="text-sm font-semibold text-[#159734]">{label}</p>
      {!products.length && (
        <p role="status" className="p-4 text-sm text-slate-600">
          Chưa có sản phẩm phù hợp với điều kiện gợi ý hiện tại.
        </p>
      )}
      {products.map((product) => (
        <RecommendedProductCard
          key={product.recommendation.productId}
          product={product}
          onAdd={onAdd}
          disabled={disabled || pending !== null}
          onPending={setPending}
        />
      ))}
    </div>
  );
}

function RecommendedProductCard({
  product,
  onAdd,
  disabled,
  onPending,
}: {
  product: RecommendedProductView;
  onAdd?: RecommendationResultProps["onAdd"];
  disabled?: boolean;
  onPending(value: string | null): void;
}) {
  const [details, setDetails] = useState(product);
  const { recommendation, variants } = details;
  const [variantId, setVariantId] = useState(() => initialPosVariant(product.variants));
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [added, setAdded] = useState(false);
  const live = useRef(true);
  const addRef = useRef(onAdd);
  useEffect(() => {
    addRef.current = onAdd;
  }, [onAdd]);
  const controller = useRef<AbortController | null>(null);
  const busy = useRef(false);
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      controller.current?.abort();
    };
  }, []);
  const selected = variants.find(
    (variant) => (variant.productVariantId ?? variant.id) === variantId,
  );
  const image = selected?.imageUrl ?? variants[0]?.imageUrl;
  async function reloadMetadata() {
    if (disabled || busy.current) return;
    busy.current = true;
    const request = new AbortController();
    controller.current = request;
    onPending(recommendation.productId);
    setRefreshing(true);
    setError("");
    setAdded(false);
    try {
      const next = await loadRecommendedProduct(recommendation, request.signal);
      if (!live.current || request.signal.aborted) return;
      setDetails(next);
      setVariantId(initialPosVariant(next.variants));
    } finally {
      busy.current = false;
      if (live.current) {
        setRefreshing(false);
        onPending(null);
      }
    }
  }
  async function add() {
    if (!selected || disabled || busy.current || !onAdd || posVariantSelectionError(selected))
      return;
    busy.current = true;
    const request = new AbortController();
    controller.current = request;
    onPending(recommendation.productId);
    setError("");
    setAdded(false);
    try {
      const current = await resolveRecommendedVariant(
        recommendation.productId,
        variantId,
        request.signal,
      );
      if (!live.current || request.signal.aborted) return;
      const success = (await addRef.current?.(current)) ?? false;
      if (live.current) {
        setAdded(success);
        if (!success)
          setError("Chưa thêm được sản phẩm. Kiểm tra hóa đơn, tồn kho hoặc thông báo bán hàng.");
      }
    } catch (failure) {
      if (live.current)
        setError(
          failure instanceof PosProductSelectionError
            ? failure.message
            : "Không thể tải thông tin biến thể hiện tại. Vui lòng tải lại gợi ý.",
        );
    } finally {
      busy.current = false;
      if (live.current) onPending(null);
    }
  }
  return (
    <article className="flex gap-3 rounded-xl border border-[#e1e9e3] p-3">
      <span className="text-sm font-bold text-[#159734]">#{recommendation.rank}</span>
      {image ? (
        <img src={image} alt="" className="size-16 shrink-0 rounded-lg object-cover" />
      ) : (
        <span className="grid size-16 shrink-0 place-items-center rounded-lg bg-slate-100 text-xs text-slate-500">
          Chưa có ảnh
        </span>
      )}
      <div className="grid min-w-0 flex-1 gap-2">
        <strong className="text-sm text-[#172317]">{recommendation.name}</strong>
        <span className="text-xs text-slate-500">{recommendation.productCode}</span>
        {variants.length > 0 && (
          <PosVariantSelector
            variants={variants}
            selectedId={variantId}
            disabled={disabled}
            formatCurrency={formatCurrency}
            onSelect={(id) => {
              setVariantId(id);
              setAdded(false);
              setError("");
            }}
          />
        )}
        {selected && <span className="text-sm font-bold">{formatCurrency(selected.price)}</span>}
        <span className="text-xs text-slate-600">
          {details.error ||
            (selected
              ? posVariantSelectionError(selected) ||
                (selected.sellableQuantity <= 0
                  ? "Tồn khả dụng hiện tại bằng 0. POS sẽ kiểm tra phương án đặt chờ khi thêm."
                  : "POS sẽ kiểm tra lại tồn kho khi thêm vào đơn.")
              : "Cần chọn biến thể / đơn vị bán.")}
        </span>
        {(details.error || error) && (
          <button
            type="button"
            disabled={disabled || refreshing}
            onClick={() => void reloadMetadata()}
            className="justify-self-start text-sm font-semibold text-[#159734] disabled:opacity-50"
          >
            {refreshing ? "Đang tải lại..." : "Tải lại thông tin bán hàng"}
          </button>
        )}
        {onAdd && (
          <button
            type="button"
            disabled={disabled || !selected || Boolean(posVariantSelectionError(selected))}
            onClick={() => void add()}
            className="justify-self-start rounded-lg bg-[#159734] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            Thêm vào đơn
          </button>
        )}
        {added && (
          <span role="status" className="text-xs text-[#159734]">
            Đã thêm vào hóa đơn.
          </span>
        )}
        {error && (
          <span role="alert" className="text-xs text-red-700">
            {error}
          </span>
        )}
      </div>
    </article>
  );
}
