import {
  ArrowLeft,
  Check,
  Clock,
  Coffee,
  Layers,
  Loader2,
  Search,
  Sparkles,
  Thermometer
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "../../../components/ui/Button";
import { TextInput } from "../../../components/ui/Field";
import { bomRecommendationApi, type RecommendedBom } from "../services/bomRecommendation.api";
import {
  posCustomBundleApi,
  type BundleAvailability
} from "../../sales/services/posCustomBundle.api";

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

export interface BomBundleConfirmInput {
  item: RecommendedBom;
  scale: number;
  bundleName: string;
  note: string;
  lines: Array<{ ingredientId: string; ingredientName: string; quantity: number }>;
}

interface Props {
  customerId: string | null;
  branchId: string | null;
  personalized: boolean;
  onSelect?: (input: BomBundleConfirmInput, signal: AbortSignal) => Promise<void>;
}

const SCALE_OPTIONS = [1, 2, 3] as const;

function scaleQuantity(quantity: string | number, scale: number): number {
  return Math.round(Number(quantity) * scale * 1000) / 1000;
}

function estimateBundlePrice(item: RecommendedBom, scale = 1): number {
  const sum = item.bundlePrefill.lines.reduce((acc, line) => {
    const lineTotal = Math.round(scaleQuantity(line.quantity, scale) * Number(line.retailPricePerUnit) * 100) / 100;
    return acc + lineTotal;
  }, 0);
  return Math.round(sum);
}

const bomStatuses = {
  INSUFFICIENT_VALID_ORDERS: "Khách chưa có đủ 5 đơn hợp lệ.",
  INSUFFICIENT_INGREDIENT_SIGNAL: "Chưa đủ dữ liệu nguyên liệu để cá nhân hóa.",
  CANDIDATE_POOL_UNAVAILABLE: "Chưa có danh mục công thức đủ điều kiện để đề xuất.",
  NO_AVAILABLE_CANDIDATES: "Không có công thức đủ nguyên liệu tại kho đang chọn.",
  CATALOG_UNAVAILABLE: "Chưa đọc được công thức hiện hành từ Catalog.",
  AVAILABILITY_UNAVAILABLE: "Chưa xác minh được tồn nguyên liệu tại kho đang chọn.",
  SELECTION_STALE: "Công thức đã thay đổi. Vui lòng tải lại gợi ý.",
};

export function ProductRecommendationBomList({ customerId, branchId, personalized, onSelect }: Props) {
  const [manual, setManual] = useState(false);
  const [confirming, setConfirming] = useState<{
    item: RecommendedBom;
    scale: number;
    note: string;
  } | null>(null);
  const [scaledAvailability, setScaledAvailability] = useState<{
    key: string;
    loading: boolean;
    error: string;
    data: BundleAvailability | null;
  }>({ key: "", loading: false, error: "", data: null });
  const [pending, setPending] = useState<string | null>(null);
  const [selectionError, setSelectionError] = useState("");
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const query = useQuery({
    queryKey: ["personalized-boms", customerId, branchId],
    enabled: personalized && Boolean(customerId) && !manual,
    queryFn: ({ signal }) => bomRecommendationApi.list(customerId!, branchId, signal),
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    if (!confirming || confirming.scale === 1) {
      setScaledAvailability({ key: "", loading: false, error: "", data: null });
      return;
    }
    const key = `${confirming.item.variantId}:${confirming.scale}:${branchId ?? "default"}`;
    let active = true;
    setScaledAvailability({ key, loading: true, error: "", data: null });
    const lines = confirming.item.bundlePrefill.lines.map((line) => ({
      ingredientId: line.ingredientId,
      ingredientName: line.ingredientName,
      quantity: scaleQuantity(line.quantity, confirming.scale),
    }));
    posCustomBundleApi
      .checkAvailability({ branchId, lines })
      .then((data) => {
        if (active) setScaledAvailability({ key, loading: false, error: "", data });
      })
      .catch((error: unknown) => {
        if (active) {
          setScaledAvailability({
            key,
            loading: false,
            error: error instanceof Error ? error.message : "Không kiểm tra được tồn kho cho mức định lượng này.",
            data: null,
          });
        }
      });
    return () => {
      active = false;
    };
  }, [confirming?.item.variantId, confirming?.scale, branchId]);

  if (!personalized || !customerId) return <CatalogBomList />;

  async function confirmBundle() {
    if (!onSelect || !confirming || request.current) return;
    const { item, scale, note } = confirming;
    const controller = new AbortController();
    request.current = controller;
    setPending(item.variantId);
    setSelectionError("");
    const lines = item.bundlePrefill.lines.map((line) => ({
      ingredientId: line.ingredientId,
      ingredientName: line.ingredientName,
      quantity: scaleQuantity(line.quantity, scale),
    }));
    try {
      await onSelect(
        {
          item,
          scale,
          bundleName: item.bundlePrefill.bundleName,
          note: note.trim(),
          lines,
        },
        controller.signal,
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        setSelectionError(error instanceof Error ? error.message : "Không thể tạo Bundle từ công thức.");
      }
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setPending(null);
    }
  }

  return (
    <div className="grid gap-3">
      <div className="flex gap-2">
        <Button
          variant={manual ? "secondary" : "primary"}
          size="sm"
          onClick={() => {
            setManual(false);
            setConfirming(null);
            setSelectionError("");
          }}
          disabled={Boolean(pending)}
        >
          Gợi ý cá nhân hóa
        </Button>
        <Button
          variant={manual ? "primary" : "secondary"}
          size="sm"
          onClick={() => {
            setManual(true);
            setConfirming(null);
            setSelectionError("");
          }}
          disabled={Boolean(pending)}
        >
          Tra cứu Catalog
        </Button>
      </div>
      {manual ? <CatalogBomList /> : confirming ? (
        <div className="grid gap-3 rounded-xl border border-emerald-200 bg-emerald-50/30 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-emerald-100 pb-3">
            <button
              type="button"
              disabled={Boolean(pending)}
              onClick={() => {
                setConfirming(null);
                setSelectionError("");
              }}
              className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-700 hover:text-slate-900 disabled:opacity-50"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lại danh sách BOM
            </button>
            <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
              Tạo Bundle trực tiếp từ công thức AI
            </span>
          </div>

          <div className="grid gap-1">
            <strong className="text-base text-slate-900">
              #{confirming.item.rank} {confirming.item.bundlePrefill.bundleName}
            </strong>
            <p className="text-xs font-mono text-slate-500">
              SKU công thức gốc: {confirming.item.sku} · Tỷ lệ nguyên liệu được giữ nguyên
            </p>
            <p className="text-sm text-slate-700">{confirming.item.reason}</p>
          </div>

          <div className="grid gap-1.5">
            <span className="text-xs font-bold text-slate-700">Mức định lượng theo tỷ lệ BOM gốc:</span>
            <div className="flex flex-wrap gap-2">
              {SCALE_OPTIONS.map((opt) => (
                <Button
                  key={opt}
                  size="sm"
                  variant={confirming.scale === opt ? "primary" : "secondary"}
                  disabled={Boolean(pending)}
                  onClick={() => {
                    setSelectionError("");
                    setConfirming((cur) => (cur ? { ...cur, scale: opt } : cur));
                  }}
                >
                  {opt}x ({(Number(confirming.item.quantityBasis.ingredientInputGrams) * opt).toLocaleString("vi-VN")} g)
                </Button>
              ))}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-3">
            <div className="mb-2 text-xs font-bold text-slate-700">Thành phần nguyên liệu của Bundle</div>
            <ul className="grid gap-1.5 text-sm">
              {confirming.item.bundlePrefill.lines.map((line) => {
                const qty = scaleQuantity(line.quantity, confirming.scale);
                const linePrice = Math.round(qty * Number(line.retailPricePerUnit));
                return (
                  <li key={line.ingredientId} className="flex items-center justify-between gap-2">
                    <span className="text-slate-800">
                      {line.ingredientName} · <strong>{qty.toLocaleString("vi-VN")} {line.unitSymbol}</strong>
                    </span>
                    <span className="font-semibold text-slate-700">{formatCurrency(linePrice)}</span>
                  </li>
                );
              })}
            </ul>
          </div>

          <div className="grid gap-1 rounded-lg bg-white p-3 text-sm border border-slate-200">
            <div className="flex items-center justify-between">
              <span className="text-slate-600">Tổng nguyên liệu đầu vào:</span>
              <strong>
                {(Number(confirming.item.quantityBasis.ingredientInputGrams) * confirming.scale).toLocaleString("vi-VN")} g
              </strong>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-slate-600">Giá dự kiến của Bundle:</span>
              <strong className="text-emerald-700">
                {formatCurrency(estimateBundlePrice(confirming.item, confirming.scale))}
              </strong>
            </div>
            <div className="flex items-center justify-between pt-1">
              <span className="text-slate-600">Tồn nguyên liệu tại kho:</span>
              {confirming.scale === 1 ? (
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                  <Check className="h-3.5 w-3.5" /> Đủ nguyên liệu tại kho
                </span>
              ) : scaledAvailability.loading ? (
                <span className="text-slate-600">Đang kiểm tra tồn nguyên liệu…</span>
              ) : scaledAvailability.error ? (
                <span className="text-red-700">{scaledAvailability.error}</span>
              ) : scaledAvailability.data?.enough ? (
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                  <Check className="h-3.5 w-3.5" /> Đủ nguyên liệu cho mức {confirming.scale}x
                </span>
              ) : (
                <span className="font-semibold text-amber-700">Không đủ tồn kho cho mức {confirming.scale}x</span>
              )}
            </div>
          </div>

          <TextInput
            maxLength={1000}
            disabled={Boolean(pending)}
            onChange={(event) => {
              const val = event.currentTarget.value;
              setConfirming((cur) => (cur ? { ...cur, note: val } : cur));
            }}
            placeholder="Ghi chú đóng gói cho kho (tuỳ chọn)"
            value={confirming.note}
          />

          {selectionError && <p role="alert" className="text-sm font-semibold text-red-700">{selectionError}</p>}

          <div className="flex flex-wrap justify-end gap-2 pt-1">
            <Button
              variant="secondary"
              size="sm"
              disabled={Boolean(pending)}
              onClick={() => {
                setConfirming(null);
                setSelectionError("");
              }}
            >
              Hủy
            </Button>
            <Button
              variant="primary"
              size="sm"
              disabled={
                !onSelect
                || Boolean(pending)
                || (confirming.scale !== 1 && (scaledAvailability.loading || !scaledAvailability.data?.enough))
              }
              onClick={() => void confirmBundle()}
            >
              {pending === confirming.item.variantId
                ? "Đang tạo Bundle vào đơn…"
                : "Xác nhận tạo Bundle & Thêm vào đơn"}
            </Button>
          </div>
        </div>
      ) : (
        <div className="grid gap-3">
          {query.isFetching && <p role="status">Đang kiểm tra công thức và tồn nguyên liệu…</p>}
          {query.isError && <p role="alert">Không tải được công thức cá nhân hóa.</p>}
          {!query.isFetching && !query.isError && query.data?.status !== "OK" && query.data &&
            <p role="status">{bomStatuses[query.data.status]}</p>}
          {(query.isError || (query.data && query.data.status !== "OK")) &&
            <Button variant="secondary" size="sm" onClick={() => void query.refetch()}>Tải lại gợi ý BOM</Button>}
          {selectionError && <p role="alert" className="text-sm text-red-700">{selectionError}</p>}
          {!query.isError && !query.isFetching && query.data?.status === "OK" && <>
            <p className="text-sm text-slate-600">
              Gợi ý dựa trên BOM hiện hành của hàng đã mua còn giữ.
            </p>
            {query.data.items.map(item => <article key={item.variantId}
              className="grid gap-2 rounded-xl border border-slate-200 p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <strong>#{item.rank} {item.bundlePrefill.bundleName}</strong>
                <span className="text-sm font-bold text-emerald-700">
                  Dự kiến: {formatCurrency(estimateBundlePrice(item))}
                </span>
              </div>
              <p className="text-sm text-slate-700">{item.reason}</p>
              <p className="text-sm text-slate-600">
                {Number(item.quantityBasis.ingredientInputGrams).toLocaleString("vi-VN")} g nguyên liệu đầu vào
                {item.tasteScore === undefined ? " · Theo nguyên liệu" : " · Có đối chiếu khẩu vị"}
                {" · "}
                <span className="font-semibold text-emerald-700">Đủ nguyên liệu tại kho</span>
              </p>
              <ul className="text-sm">
                {item.bundlePrefill.lines.map(line => <li key={line.ingredientId}>
                  {line.ingredientName} · {Number(line.quantity).toLocaleString("vi-VN")} {line.unitSymbol}
                </li>)}
              </ul>
              <Button className="justify-self-start" size="sm" disabled={!onSelect || Boolean(pending)}
                onClick={() => {
                  setSelectionError("");
                  setConfirming({ item, scale: 1, note: "" });
                }}>
                Chọn công thức
              </Button>
            </article>)}
          </>}
        </div>
      )}
    </div>
  );
}

function CatalogBomList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [entries, setEntries] = useState<VariantBomEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterMode, setFilterMode] = useState<"ALL" | "HAS_BOM">("HAS_BOM");

  useEffect(() => {
    let active = true;

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
