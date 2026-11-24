import {
  Clock,
  Coffee,
  Layers,
  Loader2,
  Search,
  Sparkles,
  Thermometer
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { bomApi } from "../../manufacturing/services/bom.api";
import type { BomItemResponse } from "../../manufacturing/types/bom.types";
import { productVariantApi } from "../../product/services/productVariant.api";
import { variantTasteApi } from "../../product/services/variantTaste.api";
import type { ProductVariant } from "../../product/types/product.types";
import type { VariantTasteProfileResponse } from "../../product/types/variantTaste.types";
import { formatCurrency } from "../../sales/services/money";

interface VariantBomEntry {
  variant: ProductVariant;
  bomItems: BomItemResponse[];
  profile?: VariantTasteProfileResponse;
}

export function ProductRecommendationBomList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [entries, setEntries] = useState<VariantBomEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterMode, setFilterMode] = useState<"ALL" | "HAS_BOM">("HAS_BOM");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");

    const loadAllBoms = async () => {
      try {
        const variantsRes = await productVariantApi.list({ size: 100 });
        const variantsList = variantsRes.content || [];

        const loadedEntries = await Promise.all(
          variantsList.map(async (variant) => {
            try {
              const [boms, profilesRes] = await Promise.all([
                bomApi.listByVariant(String(variant.id)).catch(() => []),
                variantTasteApi.getVariantProfiles(String(variant.id)).catch(() => ({ content: [] }))
              ]);
              const approved = profilesRes.content?.find((p) => p.status === "APPROVED");
              const currentProfile = approved || profilesRes.content?.[0];
              return {
                variant,
                bomItems: boms || [],
                profile: currentProfile
              };
            } catch {
              return {
                variant,
                bomItems: []
              };
            }
          })
        );

        if (active) {
          setEntries(loadedEntries);
        }
      } catch (err: unknown) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không tải được danh sách công thức định mức (BOM).");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadAllBoms();

    return () => {
      active = false;
    };
  }, []);

  const filteredEntries = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    return entries.filter((item) => {
      if (filterMode === "HAS_BOM" && item.bomItems.length === 0) {
        return false;
      }
      if (!q) return true;
      const matchName = item.variant.variantName?.toLowerCase().includes(q);
      const matchSku = item.variant.sku?.toLowerCase().includes(q);
      const matchIngredient = item.bomItems.some((b) =>
        b.ingredient?.name?.toLowerCase().includes(q)
      );
      return matchName || matchSku || matchIngredient;
    });
  }, [entries, searchQuery, filterMode]);

  const totalWithBom = useMemo(() => {
    return entries.filter((e) => e.bomItems.length > 0).length;
  }, [entries]);

  if (loading) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-2.5 text-slate-500">
        <Loader2 className="h-6 w-6 animate-spin text-emerald-600" />
        <span className="text-xs font-semibold">Đang tải danh sách công thức định mức (BOM)...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-xs font-bold text-red-700">
        {error}
      </div>
    );
  }

  return (
    <div className="grid gap-3.5">
      {/* Search & Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-3">
        <div className="relative min-w-[220px] flex-1">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo tên trà, mã SKU hoặc tên nguyên liệu..."
            className="w-full rounded-lg border border-slate-200 bg-white py-1.5 pl-9 pr-3 text-xs text-slate-800 placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none focus:ring-1 focus:ring-emerald-600"
          />
        </div>

        <div className="flex items-center gap-1 rounded-lg bg-slate-100 p-1 text-xs">
          <button
            type="button"
            onClick={() => setFilterMode("HAS_BOM")}
            className={`rounded-md px-2.5 py-1 font-semibold transition ${
              filterMode === "HAS_BOM"
                ? "bg-white text-emerald-800 shadow-2xs"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            Có công thức ({totalWithBom})
          </button>
          <button
            type="button"
            onClick={() => setFilterMode("ALL")}
            className={`rounded-md px-2.5 py-1 font-semibold transition ${
              filterMode === "ALL"
                ? "bg-white text-emerald-800 shadow-2xs"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            Tất cả ({entries.length})
          </button>
        </div>
      </div>

      {/* List of Recipe / BOM cards */}
      {filteredEntries.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 p-8 text-center text-xs text-slate-500">
          <Layers className="mx-auto h-8 w-8 text-slate-300 mb-2" />
          <p className="font-semibold text-slate-700">Không tìm thấy công thức định mức phù hợp</p>
          <p className="mt-1 text-slate-400">
            {searchQuery
              ? "Hãy thử tìm kiếm với từ khóa khác"
              : "Chưa có mặt hàng nào được thiết lập định mức nguyên liệu (BOM)."}
          </p>
        </div>
      ) : (
        <div className="grid gap-3">
          {filteredEntries.map(({ variant, bomItems, profile }) => (
            <article
              key={variant.id}
              className="rounded-xl border border-[#e1e9e3] bg-white p-3.5 shadow-2xs hover:border-emerald-300 transition"
            >
              {/* Variant Header */}
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-2.5">
                <div className="flex items-center gap-2">
                  <div className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-50 text-emerald-700">
                    <Coffee className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900">{variant.variantName}</h4>
                    <div className="flex items-center gap-2 text-[11px] text-slate-500 font-mono">
                      <span>SKU: {variant.sku}</span>
                      {variant.unitName && <span>· ĐVT: {variant.unitName}</span>}
                    </div>
                  </div>
                </div>

                <div className="text-right">
                  <span className="text-xs font-black text-emerald-700">
                    {formatCurrency(variant.sellingPrice || 0)}
                  </span>
                  <div className="text-[10px] text-slate-400">
                    {bomItems.length > 0 ? (
                      <span className="font-semibold text-emerald-600">
                        {bomItems.length} thành phần
                      </span>
                    ) : (
                      <span className="italic text-slate-400">Chưa có BOM</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Ingredients List */}
              {bomItems.length > 0 ? (
                <div className="mt-3">
                  <div className="text-[11px] font-bold text-slate-600 mb-1.5 flex items-center gap-1.5">
                    <Sparkles className="h-3.5 w-3.5 text-emerald-600" />
                    <span>Định mức nguyên liệu (Công thức):</span>
                  </div>
                  <div className="grid gap-1.5 sm:grid-cols-2">
                    {bomItems.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between rounded-lg border border-slate-100 bg-[#fbfdfc] px-2.5 py-1.5 text-xs"
                      >
                        <span className="font-medium text-slate-800">
                          {item.ingredient?.name || "Nguyên liệu"}
                        </span>
                        <div className="flex items-center gap-2 font-mono font-bold text-emerald-800">
                          <span>
                            {item.quantity} {item.unit?.name || item.unit?.symbol || ""}
                          </span>
                          {Boolean(item.wasteRate) && item.wasteRate > 0 && (
                            <span className="text-[10px] text-amber-600 font-normal">
                              (+{item.wasteRate}%)
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="mt-2.5 rounded-lg border border-dashed border-slate-100 bg-slate-50/50 p-2 text-center text-xs text-slate-400 italic">
                  Chưa gán danh sách nguyên liệu cho mặt hàng này
                </div>
              )}

              {/* Preparation Protocol details if present */}
              {profile?.preparationProtocol && (
                <div className="mt-2.5 flex flex-wrap items-center gap-3 rounded-lg bg-emerald-50/60 px-3 py-1.5 text-xs text-emerald-950">
                  {profile.preparationProtocol.waterTemperatureCelsius && (
                    <span className="flex items-center gap-1 font-medium">
                      <Thermometer className="h-3.5 w-3.5 text-emerald-700" />
                      Nhiệt độ:{" "}
                      <strong>{profile.preparationProtocol.waterTemperatureCelsius}°C</strong>
                    </span>
                  )}
                  {profile.preparationProtocol.steepTimeSeconds && (
                    <span className="flex items-center gap-1 font-medium">
                      <Clock className="h-3.5 w-3.5 text-emerald-700" />
                      Hãm: <strong>{profile.preparationProtocol.steepTimeSeconds}s</strong>
                    </span>
                  )}
                  {profile.preparationProtocol.description && (
                    <span className="text-slate-600 italic">
                      Lưu ý: {profile.preparationProtocol.description}
                    </span>
                  )}
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
