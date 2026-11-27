import { Check, Plus, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { formatCurrency } from "../../sales/services/money";
import { bundleLimits } from "../../sales/services/posCustomBundle.api";
import { formatKgPriceMeta } from "../../sales/components/custom-bundle/flexibleWeight";
import type {
  IngredientRecommendations,
  IngredientRecommendationStatus,
  RecommendedIngredient,
} from "../types/ingredientRecommendation.types";

interface IngredientRecommendationPanelProps {
  data: IngredientRecommendations | null;
  loading: boolean;
  error: string;
  selectedIngredientIds: string[];
  onRefresh?(): void;
  onAdd(item: RecommendedIngredient): void;
  onAddAll(items: RecommendedIngredient[]): void;
}

function statusNotice(status: IngredientRecommendationStatus, validOrderCount: number): string | null {
  switch (status) {
    case "INSUFFICIENT_VALID_ORDERS":
      return `Khách hàng hiện có ${validOrderCount}/5 đơn hợp lệ để bật AI gợi ý nguyên liệu cá nhân hóa.`;
    case "NO_INGREDIENT_EVIDENCE":
      return "Chưa đủ dữ liệu thành phần nguyên liệu từ lịch sử mua của khách.";
    case "NO_AVAILABLE_INGREDIENTS":
      return "Các nguyên liệu phù hợp hiện tạm hết hàng tại kho.";
    case "UPSTREAM_UNAVAILABLE":
      return "Chưa lấy được dữ liệu danh mục hoặc tồn kho nguyên liệu. Vui lòng thử lại sau.";
    case "FALLBACK_SIMILAR_RETAIL":
    case "OK":
      return null;
  }
}

function formatStock(quantity: number): string {
  return quantity.toLocaleString("vi-VN", { maximumFractionDigits: 3 });
}

/** Vùng trái: hiển thị tối đa 5 nguyên liệu AI đề xuất, nhân viên bấm Thêm để nạp vào CustomBundleBuilder. */
export function IngredientRecommendationPanel({
  data,
  loading,
  error,
  selectedIngredientIds,
  onRefresh,
  onAdd,
  onAddAll,
}: IngredientRecommendationPanelProps) {
  const selectedSet = new Set(selectedIngredientIds);
  const visibleItems = (data?.items ?? []).slice(0, 5);
  const unselectedItems = visibleItems.filter((item) => !selectedSet.has(item.ingredientId));
  const atLimit = selectedSet.size >= bundleLimits.maxIngredients;
  const canAddAll =
    unselectedItems.length > 0 &&
    selectedSet.size + unselectedItems.length <= bundleLimits.maxIngredients;
  const notice = data ? statusNotice(data.status, data.validOrderCount) : null;

  return (
    <div className="grid min-w-0 gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50/30 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-emerald-100 pb-2">
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1.5">
            <Sparkles className="h-4 w-4 shrink-0 text-emerald-700" aria-hidden="true" />
            <span className="text-xs font-black uppercase tracking-wide text-slate-900">
              AI gợi ý nguyên liệu
            </span>
          </div>
          {!error && visibleItems.length > 0 ? (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-800">
              Top {visibleItems.length} phù hợp
            </span>
          ) : null}
          {loading ? (
            <span
              role="status"
              className="rounded-full border border-emerald-200 bg-white px-2 py-0.5 text-[11px] font-semibold text-emerald-700"
            >
              Đang cập nhật…
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-1.5">
          {canAddAll ? (
            <Button
              size="xs"
              variant="secondary"
              onClick={() => onAddAll(unselectedItems)}
            >
              <Plus className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
              Thêm tất cả ({unselectedItems.length})
            </Button>
          ) : null}
        </div>
      </div>

      {error ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-semibold text-red-700"
        >
          <span>{error}</span>
          {onRefresh ? (
            <Button size="xs" variant="secondary" onClick={onRefresh}>
              Thử lại
            </Button>
          ) : null}
        </div>
      ) : null}

      {!error && notice ? (
        <p
          role="status"
          className={`rounded-lg border px-3 py-2 text-xs font-semibold ${
            data?.status === "UPSTREAM_UNAVAILABLE"
              ? "border-amber-200 bg-amber-50 text-amber-800"
              : "border-emerald-200 bg-white text-slate-600"
          }`}
        >
          {notice}
        </p>
      ) : null}

      {!error && loading && !data ? (
        <p role="status" className="rounded-lg border border-emerald-100 bg-white px-3 py-4 text-xs font-semibold text-slate-600">
          Đang phân tích lịch sử mua để gợi ý nguyên liệu…
        </p>
      ) : null}

      {!error && visibleItems.length > 0 ? (
        <div className="grid grid-cols-1 gap-2">
          {visibleItems.map((item, idx) => {
            const isSelected = selectedSet.has(item.ingredientId);
            const addDisabled = !isSelected && atLimit;
            return (
              <article
                key={item.ingredientId}
                className={`flex min-w-0 items-center justify-between gap-3 rounded-xl border bg-white px-3 py-2.5 transition ${
                  isSelected
                    ? "border-emerald-400 bg-emerald-50/40"
                    : "border-slate-200 hover:border-emerald-300"
                }`}
              >
                <div className="min-w-0 flex-1 grid gap-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="inline-flex h-5 min-w-5 shrink-0 items-center justify-center rounded bg-emerald-100 px-1.5 text-[11px] font-black text-emerald-800">
                      #{idx + 1}
                    </span>
                    <span className="text-sm font-bold leading-tight text-slate-900 break-words">
                      {item.ingredientName}
                    </span>
                    <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-bold text-slate-600">
                      {item.unitSymbol}
                    </span>
                    {isSelected ? (
                      <span className="inline-flex shrink-0 items-center gap-0.5 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-800">
                        <Check className="h-3 w-3" aria-hidden="true" />
                        Đã chọn
                      </span>
                    ) : null}
                  </div>
                  <p className="text-xs leading-snug text-slate-600">{item.reason}</p>
                  <div className="flex flex-wrap items-center gap-x-2 text-xs font-semibold">
                    {(() => {
                      const priceMeta = formatKgPriceMeta(item.retailPricePerUnit, item.unitSymbol);
                      return (
                        <span className="text-emerald-700 font-bold">
                          {formatCurrency(priceMeta.price)} / {priceMeta.unitLabel}
                        </span>
                      );
                    })()}
                    <span className="text-slate-300">·</span>
                    <span className="text-slate-600">
                      Tồn: {formatStock(item.availableQuantity)} {item.unitSymbol}
                    </span>
                  </div>
                </div>

                <Button
                  size="sm"
                  variant={isSelected ? "secondary" : "primary"}
                  disabled={addDisabled}
                  onClick={() => onAdd(item)}
                  className="shrink-0 whitespace-nowrap"
                >
                  <Plus className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                  {isSelected ? "Thêm nữa" : "Thêm"}
                </Button>
              </article>
            );
          })}
        </div>
      ) : null}

      {atLimit ? (
        <p className="text-xs font-semibold text-amber-700">
          Đã đạt giới hạn tối đa {bundleLimits.maxIngredients} nguyên liệu trong một bundle.
        </p>
      ) : null}
    </div>
  );
}
