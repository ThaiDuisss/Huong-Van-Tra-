import {
  AlertCircle,
  Check,
  CheckCircle2,
  Clock,
  Coffee,
  Loader2,
  Sparkles
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { useAuthorization } from "@/auth/useAuthorization";
import { Button } from "@/components/ui/Button";
import { buttonClassName } from "@/components/ui/button-styles";
import { Field, TextArea, TextInput } from "@/components/ui/Field";
import { toast } from "@/components/ui/Toast";
import { routePaths } from "@/routes/path";
import type {
  TasteAttribute,
  TasteTaxonomy
} from "../../customer/services/tasteSurvey.api";
import { productVariantApi } from "../services/productVariant.api";
import {
  type BomItemDetail,
  variantTasteApi
} from "../services/variantTaste.api";
import type { ProductVariant } from "../types/product.types";
import {
  PREPARATION_CODE_OPTIONS,
  type AssessmentPayload,
  type VariantTasteProfileResponse
} from "../types/variantTaste.types";
import { formatCurrency } from "../utils/product-formatters";

interface LocationState {
  variant?: ProductVariant;
  variants?: ProductVariant[];
  variantIds?: (string | number)[];
}

function editableProfile(profiles: VariantTasteProfileResponse[], taxonomy: TasteTaxonomy) {
  const current = profiles.filter((profile) => profile.taxonomyVersion === taxonomy.versionCode &&
    profile.taxonomySha256 === taxonomy.manifestSha256);
  return current.find((profile) => profile.status === "DRAFT") ??
    current.find((profile) => profile.status === "APPROVED") ?? null;
}

function saveDraft(
  variantId: string,
  taxonomy: TasteTaxonomy,
  existing: VariantTasteProfileResponse | null,
  assessment: AssessmentPayload
) {
  if (existing?.status === "DRAFT") {
    return variantTasteApi.updateProfile(existing.id, existing.rowVersion, assessment);
  }
  const draft = { taxonomyVersion: taxonomy.versionCode, assessment };
  if (existing) {
    return variantTasteApi.createRevision(existing.id, existing.rowVersion, draft);
  }
  return variantTasteApi.createProfile(variantId, draft);
}

export function TasteProfileEditPage() {
  const navigate = useNavigate();
  const params = useParams<{ variantId?: string }>();
  const location = useLocation();
  const state = (location.state as LocationState) || {};

  const { isQc, isManager, isSuperAdmin, hasPermission } = useAuthorization();
  const isManagerOrAdmin = isManager() || isSuperAdmin();
  const canAssess = isManagerOrAdmin || hasPermission("catalog:taste:assess");
  const isQcOnly = isQc() && !isManagerOrAdmin;

  // Detect mode: Single product or Bulk update
  const isSingleMode = useMemo(() => {
    if (params.variantId) return true;
    if (state.variant) return true;
    if (state.variants && state.variants.length === 1) return true;
    if (state.variantIds && state.variantIds.length === 1) return true;
    return false;
  }, [params.variantId, state.variant, state.variants, state.variantIds]);

  const targetSingleId = useMemo(() => {
    if (params.variantId) return params.variantId;
    if (state.variant) return String(state.variant.id);
    if (state.variants && state.variants.length === 1) return String(state.variants[0].id);
    if (state.variantIds && state.variantIds.length === 1) return String(state.variantIds[0]);
    return null;
  }, [params.variantId, state.variant, state.variants, state.variantIds]);

  // Taxonomy
  const [taxonomy, setTaxonomy] = useState<TasteTaxonomy | null>(null);
  const [loadingTaxonomy, setLoadingTaxonomy] = useState(true);

  // Single mode data
  const [singleVariant, setSingleVariant] = useState<ProductVariant | null>(state.variant || null);
  const [existingProfile, setExistingProfile] = useState<VariantTasteProfileResponse | null>(null);
  const [bomItems, setBomItems] = useState<BomItemDetail[]>([]);
  const [loadingSingleData, setLoadingSingleData] = useState(false);

  // Bulk mode data
  const [bulkVariants, setBulkVariants] = useState<ProductVariant[]>(state.variants || []);
  const [loadingBulkVariants, setLoadingBulkVariants] = useState(false);

  // Form states - Single Mode
  const [singlePrepCode, setSinglePrepCode] = useState<string>("HOT_BREW");
  const [singleSensoryValues, setSingleSensoryValues] = useState<Record<string, string[]>>({});
  const [singleWaterTemp, setSingleWaterTemp] = useState<number | undefined>(undefined);
  const [singleSteepTime, setSingleSteepTime] = useState<number | undefined>(undefined);
  const [singleDescription, setSingleDescription] = useState<string>("");

  // Form states - Bulk Mode (DEFAULT EMPTY / UNTOUCHED)
  const [bulkUpdatePrepCode, setBulkUpdatePrepCode] = useState(false);
  const [bulkPrepCode, setBulkPrepCode] = useState<string>("");

  const [bulkEnabledAttrs, setBulkEnabledAttrs] = useState<Record<string, boolean>>({});
  const [bulkSensoryValues, setBulkSensoryValues] = useState<Record<string, string[]>>({});

  const [bulkUpdateWaterTemp, setBulkUpdateWaterTemp] = useState(false);
  const [bulkWaterTemp, setBulkWaterTemp] = useState<number | undefined>(undefined);

  const [bulkUpdateSteepTime, setBulkUpdateSteepTime] = useState(false);
  const [bulkSteepTime, setBulkSteepTime] = useState<number | undefined>(undefined);

  const [bulkUpdateDescription, setBulkUpdateDescription] = useState(false);
  const [bulkDescription, setBulkDescription] = useState<string>("");

  // Processing state
  const [saving, setSaving] = useState(false);
  const [approvingExisting, setApprovingExisting] = useState(false);

  // Load taxonomy
  useEffect(() => {
    let active = true;
    setLoadingTaxonomy(true);
    variantTasteApi
      .currentTaxonomy()
      .then((data) => {
        if (active) setTaxonomy(data);
      })
      .catch((err: unknown) => {
        toast.error(err instanceof Error ? err.message : "Không tải được bộ tiêu chí khẩu vị");
      })
      .finally(() => {
        if (active) setLoadingTaxonomy(false);
      });
    return () => {
      active = false;
    };
  }, []);

  // Load single product details & existing profile
  useEffect(() => {
    if (!isSingleMode || !targetSingleId || !taxonomy) return;

    let active = true;
    setLoadingSingleData(true);

    const loadDetail = async () => {
      try {
        let v = state.variant;
        if (!v || String(v.id) !== targetSingleId) {
          v = await productVariantApi.detail(targetSingleId);
        }
        if (active && v) setSingleVariant(v);

        // BOM items
        variantTasteApi
          .getVariantBom(targetSingleId)
          .then((items) => {
            if (active) setBomItems(items);
          })
          .catch(() => {});

        // Existing profiles
        const profilesPage = await variantTasteApi.getVariantProfiles(targetSingleId);
        const current = editableProfile(profilesPage.content, taxonomy);

        if (active && current) {
          setExistingProfile(current);
          setSinglePrepCode(current.preparationCode || "HOT_BREW");
          setSingleSensoryValues(current.sensoryValues || {});
          setSingleWaterTemp(current.preparationProtocol?.waterTemperatureCelsius);
          setSingleSteepTime(current.preparationProtocol?.steepTimeSeconds);
          setSingleDescription(current.preparationProtocol?.description || "");
        } else if (active) {
          setExistingProfile(null);
          setSinglePrepCode("HOT_BREW");
          setSingleSensoryValues({});
          setSingleWaterTemp(undefined);
          setSingleSteepTime(undefined);
          setSingleDescription("");
        }
      } catch (err: unknown) {
        if (active) {
          setSingleVariant(null);
          setExistingProfile(null);
          toast.error(err instanceof Error ? err.message : "Lỗi khi tải thông tin sản phẩm");
        }
      } finally {
        if (active) setLoadingSingleData(false);
      }
    };

    void loadDetail();

    return () => {
      active = false;
    };
  }, [isSingleMode, targetSingleId, state.variant, taxonomy]);

  // Load bulk variants if only IDs were passed
  useEffect(() => {
    if (isSingleMode) return;
    if (bulkVariants.length > 0) return;

    const ids = state.variantIds || [];
    if (ids.length === 0) return;

    let active = true;
    setLoadingBulkVariants(true);

    Promise.all(
      ids.map(async (id) => {
        try {
          return await productVariantApi.detail(String(id));
        } catch {
          return null;
        }
      })
    )
      .then((items) => {
        if (active) {
          setBulkVariants(items.filter((item): item is ProductVariant => Boolean(item)));
        }
      })
      .finally(() => {
        if (active) setLoadingBulkVariants(false);
      });

    return () => {
      active = false;
    };
  }, [isSingleMode, bulkVariants.length, state.variantIds]);

  // Attributes list sorted by displayOrder
  const attributes = useMemo(() => {
    if (!taxonomy?.manifest.attributes) return [];
    return [...taxonomy.manifest.attributes].sort((a, b) => a.displayOrder - b.displayOrder);
  }, [taxonomy]);

  // Single mode toggle sensory value (NOT REQUIRED: click again to unselect)
  const toggleSingleSensoryValue = (attr: TasteAttribute, valueCode: string) => {
    const isMulti = attr.selectionKind === "ENUM_MULTI";
    const limit = Math.min(attr.maxSelections, 32);

    setSingleSensoryValues((prev) => {
      const current = prev[attr.attributeCode] || [];
      if (isMulti) {
        if (current.includes(valueCode)) {
          return { ...prev, [attr.attributeCode]: current.filter((v) => v !== valueCode) };
        }
        if (current.length >= limit) {
          toast.warning(`Chỉ được chọn tối đa ${limit} giá trị cho tiêu chí này.`);
          return prev;
        }
        return { ...prev, [attr.attributeCode]: [...current, valueCode] };
      }
      if (current.includes(valueCode)) {
        return { ...prev, [attr.attributeCode]: [] };
      }
      return { ...prev, [attr.attributeCode]: [valueCode] };
    });
  };

  // Bulk mode toggle: clicking a value enables the attribute and toggles the value
  const toggleBulkSensoryValue = (attr: TasteAttribute, valueCode: string) => {
    const isMulti = attr.selectionKind === "ENUM_MULTI";
    const limit = Math.min(attr.maxSelections, 32);

    setBulkEnabledAttrs((prev) => ({ ...prev, [attr.attributeCode]: true }));

    setBulkSensoryValues((prev) => {
      const current = prev[attr.attributeCode] || [];
      if (isMulti) {
        if (current.includes(valueCode)) {
          const next = current.filter((v) => v !== valueCode);
          return { ...prev, [attr.attributeCode]: next };
        }
        if (current.length >= limit) {
          toast.warning(`Chỉ được chọn tối đa ${limit} giá trị cho tiêu chí này.`);
          return prev;
        }
        return { ...prev, [attr.attributeCode]: [...current, valueCode] };
      }
      if (current.includes(valueCode)) {
        return { ...prev, [attr.attributeCode]: [] };
      }
      return { ...prev, [attr.attributeCode]: [valueCode] };
    });
  };

  const toggleBulkAttributeEnable = (attrCode: string, enabled: boolean) => {
    setBulkEnabledAttrs((prev) => ({ ...prev, [attrCode]: enabled }));
    if (!enabled) {
      setBulkSensoryValues((prev) => {
        const next = { ...prev };
        delete next[attrCode];
        return next;
      });
    }
  };

  // Count active fields in bulk mode
  const bulkEnabledAttrCount = Object.values(bulkEnabledAttrs).filter(Boolean).length;
  const anyBulkFieldEnabled =
    bulkUpdatePrepCode ||
    bulkEnabledAttrCount > 0 ||
    bulkUpdateWaterTemp ||
    bulkUpdateSteepTime ||
    bulkUpdateDescription;

  const handleSingleSave = async () => {
    if (!canAssess || !singleVariant || !taxonomy) return;

    setSaving(true);
    try {
      const payload = {
        preparationCode: singlePrepCode || "HOT_BREW",
        preparationProtocol: {
          ...existingProfile?.preparationProtocol,
          description: singleDescription || undefined,
          steepTimeSeconds: singleSteepTime ? Number(singleSteepTime) : undefined,
          waterTemperatureCelsius: singleWaterTemp ? Number(singleWaterTemp) : undefined
        },
        sensoryValues: singleSensoryValues,
        sourceMethod: isQcOnly ? "EXPERT_TASTING" : "INTERNAL_PANEL"
      };

      await saveDraft(String(singleVariant.id), taxonomy, existingProfile, payload);
      toast.success("Đã lưu bản nháp hồ sơ khẩu vị.");

      navigate(routePaths.tasteProfiles);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Không thể lưu Tiêu chuẩn khẩu vị");
    } finally {
      setSaving(false);
    }
  };

  // Quick approve for managers reviewing existing DRAFT
  const handleApproveExisting = async () => {
    if (!isManagerOrAdmin || existingProfile?.status !== "DRAFT") return;
    setApprovingExisting(true);
    try {
      await variantTasteApi.approveProfile(existingProfile.id, existingProfile.rowVersion);
      toast.success("Đã phê duyệt Tiêu chuẩn khẩu vị thành công!");
      navigate(routePaths.tasteProfiles);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Không thể phê duyệt hồ sơ");
    } finally {
      setApprovingExisting(false);
    }
  };

  const handleBulkSave = async () => {
    if (!canAssess) return;
    if (!anyBulkFieldEnabled) {
      toast.warning("Vui lòng chọn ít nhất một tiêu chí bạn muốn cập nhật!");
      return;
    }
    if (bulkVariants.length === 0 || !taxonomy) {
      toast.warning("Không có mặt hàng nào được chọn để cập nhật!");
      return;
    }

    setSaving(true);
    try {
      const enabledAttributes = Object.entries(bulkEnabledAttrs)
        .filter(([, enabled]) => enabled)
        .map(([code]) => code);

      const response = await variantTasteApi.bulkDraft({
        variantIds: bulkVariants.map((v) => String(v.id)),
        taxonomyVersion: taxonomy.versionCode,
        preparationCode: bulkUpdatePrepCode && bulkPrepCode ? bulkPrepCode : undefined,
        updatePreparationCode: bulkUpdatePrepCode && Boolean(bulkPrepCode),
        sensoryValues: bulkSensoryValues,
        enabledAttributes,
        waterTemperatureCelsius: bulkWaterTemp,
        updateWaterTemperatureCelsius: bulkUpdateWaterTemp,
        steepTimeSeconds: bulkSteepTime,
        updateSteepTimeSeconds: bulkUpdateSteepTime,
        description: bulkDescription,
        updateDescription: bulkUpdateDescription,
        sourceMethod: isQcOnly ? "EXPERT_TASTING" : "INTERNAL_PANEL"
      });

      if (response.errorCount === 0) {
        toast.success(`Đã lưu bản nháp cho ${response.successCount} mặt hàng.`);
      } else {
        toast.warning(
          `Hoàn tất: ${response.successCount} thành công, ${response.errorCount} lỗi. Vui lòng kiểm tra lại.`
        );
      }
      navigate(routePaths.tasteProfiles);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Không thể cập nhật khẩu vị hàng loạt");
    } finally {
      setSaving(false);
    }
  };

  if (loadingTaxonomy || (isSingleMode && loadingSingleData) || (!isSingleMode && loadingBulkVariants)) {
    return (
      <div className="flex h-96 flex-col items-center justify-center gap-3 text-[#697684]">
        <Loader2 className="h-8 w-8 animate-spin text-[#159734]" />
        <p className="text-sm font-semibold">Đang tải dữ liệu Tiêu chuẩn khẩu vị...</p>
      </div>
    );
  }

  if (isSingleMode && !singleVariant) {
    return (
      <section className="grid gap-4">
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
          <AlertCircle className="mx-auto h-10 w-10 text-red-600" />
          <h2 className="mt-3 text-base font-black text-red-800">Không tìm thấy mặt hàng</h2>
          <p className="mt-1 text-sm text-red-600">
            Mặt hàng bạn yêu cầu gán khẩu vị không tồn tại hoặc đã bị xóa.
          </p>
          <div className="mt-4">
            <Link className={buttonClassName({ size: "sm" })} to="/system/taste-profiles">
              Quay lại danh sách
            </Link>
          </div>
        </div>
      </section>
    );
  }

  if (!isSingleMode && bulkVariants.length === 0) {
    return (
      <section className="grid gap-4">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-center">
          <AlertCircle className="mx-auto h-10 w-10 text-amber-600" />
          <h2 className="mt-3 text-base font-black text-amber-800">Chưa chọn mặt hàng nào</h2>
          <p className="mt-1 text-sm text-amber-700">
            Vui lòng tích chọn ít nhất một mặt hàng từ danh sách để cập nhật khẩu vị hàng loạt.
          </p>
          <div className="mt-4">
            <Link className={buttonClassName({ size: "sm" })} to="/system/taste-profiles">
              Quay lại danh sách
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="grid gap-4 pb-12">
      {/* HEADER PHONG CÁCH RECEIPTS/NEW */}
      <header className="flex flex-col gap-2">
        <div className="text-xs font-bold text-[#697684]">
          Hồ sơ sản phẩm › Tiêu chuẩn khẩu vị sản phẩm › {isSingleMode ? "Gán khẩu vị" : "Cập nhật hàng loạt"}
        </div>
        <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-2xl font-black text-[#172317]">
              {isSingleMode
                ? `Tiêu chuẩn khẩu vị: ${singleVariant?.variantName ?? ""}`
                : `Cập nhật khẩu vị hàng loạt (${bulkVariants.length} mặt hàng)`}
            </h1>
          </div>
          <Link
            className={buttonClassName({ size: "sm" })}
            to="/system/taste-profiles"
          >
            Hủy & Quay lại
          </Link>
        </div>
      </header>

      {/* THÔNG TIN CHUNG (CARD 1) */}
      {isSingleMode && singleVariant ? (
        <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
          <div className="flex items-center justify-between border-b border-[#eef2f0] pb-2">
            <h2 className="text-base font-black text-[#172317]">
              1. Thông tin mặt hàng & Hồ sơ hiện tại
            </h2>
            <div className="flex items-center gap-2">
              {existingProfile?.status === "APPROVED" ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                  Đã phê duyệt (Phiên bản v{existingProfile.rowVersion})
                </span>
              ) : existingProfile?.status === "DRAFT" ? (
                <>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-700 ring-1 ring-inset ring-amber-600/20">
                    <Clock className="h-3.5 w-3.5 text-amber-600" />
                    Bản nháp chờ duyệt (v{existingProfile.rowVersion})
                  </span>
                  {isManagerOrAdmin && (
                    <Button
                      size="sm"
                      variant="primary"
                      disabled={approvingExisting || saving}
                      onClick={() => void handleApproveExisting()}
                    >
                      {approvingExisting ? (
                        <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
                      )}
                      Phê duyệt ngay
                    </Button>
                  )}
                </>
              ) : (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-neutral-100 px-3 py-1 text-xs font-semibold text-neutral-600 ring-1 ring-inset ring-neutral-500/20">
                  Chưa thiết lập hồ sơ
                </span>
              )}
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <div className="grid gap-1 text-xs font-black text-[#536078]">
              <span>Tên mặt hàng (Biến thể)</span>
              <div className="text-sm font-black text-[#172317]">{singleVariant.variantName}</div>
            </div>
            <div className="grid gap-1 text-xs font-black text-[#536078]">
              <span>Mã SKU / Barcode</span>
              <div className="font-mono text-sm font-bold text-[#172317]">
                {singleVariant.sku} {singleVariant.barcode ? `· ${singleVariant.barcode}` : ""}
              </div>
            </div>
            <div className="grid gap-1 text-xs font-black text-[#536078]">
              <span>Sản phẩm gốc</span>
              <div className="text-sm font-semibold text-[#172317]">
                {singleVariant.productName || "—"}
              </div>
            </div>
            <div className="grid gap-1 text-xs font-black text-[#536078]">
              <span>Giá bán & Đơn vị tính</span>
              <div className="text-sm font-black text-[#159734]">
                {formatCurrency(singleVariant.sellingPrice || 0)}{" "}
                <span className="text-xs font-semibold text-[#697684]">
                  / {singleVariant.unitName || "Hộp"}
                </span>
              </div>
            </div>

            {bomItems.length > 0 && (
              <div className="md:col-span-2 lg:col-span-4 rounded-lg bg-[#f8fbf8] p-3 border border-[#e8f2e9] text-xs">
                <span className="font-black text-[#315f3c]">Định mức nguyên liệu (BOM): </span>
                <span className="font-semibold text-[#172317]">
                  {bomItems
                    .map(
                      (b) =>
                        `${b.ingredient?.name || b.ingredient?.ingredientCode} (${b.quantity}${
                          b.unit?.name || b.ingredient?.unitName || ""
                        })`
                    )
                    .join(", ")}
                </span>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
          <div className="flex items-center justify-between border-b border-[#eef2f0] pb-2">
            <h2 className="text-base font-black text-[#172317]">
              1. Danh sách mặt hàng áp dụng ({bulkVariants.length} mặt hàng)
            </h2>
          </div>
          <div className="flex flex-wrap gap-2 max-h-36 overflow-y-auto rounded-lg bg-[#f8faf9] p-3 border border-[#e8eef0]">
            {bulkVariants.map((v) => (
              <span
                key={v.id}
                className="inline-flex items-center gap-1.5 rounded-lg border border-[#e8eef0] bg-white px-3 py-1.5 text-xs font-bold text-[#172317] shadow-2xs"
              >
                <span>{v.variantName}</span>
                <span className="font-mono text-[10px] text-[#697684]">({v.sku})</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {/* CÁCH THỨC / MỤC ĐÍCH SỬ DỤNG (CARD 2) */}
      <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="flex items-center justify-between border-b border-[#eef2f0] pb-2">
          <div className="flex items-center gap-2">
            <Coffee className="h-4 w-4 text-[#159734]" />
            <h2 className="text-base font-black text-[#172317]">
              2. Cách thức / Mục đích sử dụng
            </h2>
          </div>
          {!isSingleMode && (
            <label className="flex cursor-pointer items-center gap-2 text-xs font-black text-[#159734]">
              <input
                type="checkbox"
                checked={bulkUpdatePrepCode}
                onChange={(e) => {
                  setBulkUpdatePrepCode(e.target.checked);
                  if (e.target.checked && !bulkPrepCode) {
                    setBulkPrepCode("DAILY");
                  }
                }}
                className="rounded border-gray-300 text-[#159734] focus:ring-[#159734]"
              />
              Áp dụng thay đổi cách thức
            </label>
          )}
        </div>

        <div>
          {isSingleMode ? (
            <div className="grid gap-3 sm:grid-cols-3">
              {PREPARATION_CODE_OPTIONS.map((opt) => {
                const isSelected = singlePrepCode === opt.value || singlePrepCode === opt.code;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setSinglePrepCode(isSelected ? "" : opt.value)}
                    className={`flex flex-col items-start rounded-xl border p-4 text-left transition-all ${
                      isSelected
                        ? "border-[#159734] bg-[#f0fbef] text-[#172317] shadow-2xs ring-1 ring-[#159734]"
                        : "border-[#e8eef0] bg-white text-[#536078] hover:border-[#cfdad5] hover:bg-[#f8faf9]"
                    }`}
                  >
                    <div className="flex w-full items-center justify-between">
                      <span className="text-sm font-black">{opt.label}</span>
                      {isSelected && <Check className="h-4 w-4 text-[#159734]" />}
                    </div>
                    <p className="mt-1 text-xs text-[#697684] leading-relaxed">{opt.description}</p>
                  </button>
                );
              })}
            </div>
          ) : (
            <div>
              {!bulkUpdatePrepCode ? (
                <div className="rounded-lg border border-dashed border-[#dce5e1] bg-[#f8faf9] p-4 text-xs font-semibold text-[#697684] italic">
                  Đang giữ nguyên cách thức sử dụng cũ của từng mặt hàng. Tích vào ô &quot;Áp dụng thay đổi cách thức&quot; ở trên nếu muốn đồng bộ lại.
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-3">
                  {PREPARATION_CODE_OPTIONS.map((opt) => {
                    const isSelected = bulkPrepCode === opt.value || bulkPrepCode === opt.code;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        onClick={() => setBulkPrepCode(opt.value)}
                        className={`flex flex-col items-start rounded-xl border p-4 text-left transition-all ${
                          isSelected
                            ? "border-[#159734] bg-[#f0fbef] text-[#172317] shadow-2xs ring-1 ring-[#159734]"
                            : "border-[#e8eef0] bg-white text-[#536078] hover:border-[#cfdad5] hover:bg-[#f8faf9]"
                        }`}
                      >
                        <div className="flex w-full items-center justify-between">
                          <span className="text-sm font-black">{opt.label}</span>
                          {isSelected && <Check className="h-4 w-4 text-[#159734]" />}
                        </div>
                        <p className="mt-1 text-xs text-[#697684] leading-relaxed">{opt.description}</p>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* TIÊU CHÍ CẢM QUAN (CARD 3) */}
      <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="border-b border-[#eef2f0] pb-2">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-[#159734]" />
            <h2 className="text-base font-black text-[#172317]">
              3. Tiêu chí đánh giá cảm quan (Vị đắng, Vị ngọt, Nốt hương...)
            </h2>
          </div>
          <p className="mt-1 text-xs font-semibold text-[#697684]">
            {isSingleMode
              ? "Chọn mức độ cảm quan phù hợp. Không bắt buộc nhập hết tất cả các tiêu chí — có thể để trống hoặc bỏ chọn."
              : "Mặc định tất cả tiêu chí đều TRỐNG. Bạn chỉ cần tích chọn và đổi tiêu chí mong muốn — các tiêu chí khác giữ nguyên vẹn của từng sản phẩm."}
          </p>
        </div>

        <div className="space-y-4">
          {attributes.map((attr) => {
            const isMulti = attr.selectionKind === "ENUM_MULTI";

            if (isSingleMode) {
              const selectedVals = singleSensoryValues[attr.attributeCode] || [];
              return (
                <div
                  key={attr.attributeCode}
                  className="rounded-xl border border-[#e8eef0] bg-[#f8faf9] p-4"
                >
                  <div className="mb-2 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-black text-[#172317]">{attr.label}</span>
                      {isMulti && (
                        <span className="rounded bg-white px-2 py-0.5 text-[10px] font-bold text-[#697684] border border-[#e8eef0]">
                          Chọn tối đa {attr.maxSelections}
                        </span>
                      )}
                    </div>
                    {selectedVals.length > 0 && (
                      <button
                        type="button"
                        onClick={() =>
                          setSingleSensoryValues((prev) => ({
                            ...prev,
                            [attr.attributeCode]: []
                          }))
                        }
                        className="text-xs font-bold text-[#697684] hover:text-[#172317]"
                      >
                        Bỏ chọn
                      </button>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {attr.values.map((val) => {
                      const isChosen = selectedVals.includes(val.valueCode);
                      return (
                        <button
                          key={val.valueCode}
                          type="button"
                          onClick={() => toggleSingleSensoryValue(attr, val.valueCode)}
                          className={`flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-bold transition-all ${
                            isChosen
                              ? "border-[#159734] bg-[#159734] text-white shadow-2xs"
                              : "border-[#dce5e1] bg-white text-[#172317] hover:border-[#159734] hover:bg-[#f0fbef]"
                          }`}
                        >
                          {isChosen && <Check className="h-3.5 w-3.5" />}
                          <span>{val.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            }

            // Bulk Mode
            const isAttrEnabled = Boolean(bulkEnabledAttrs[attr.attributeCode]);
            const selectedVals = bulkSensoryValues[attr.attributeCode] || [];

            return (
              <div
                key={attr.attributeCode}
                className={`rounded-xl border transition-all p-4 ${
                  isAttrEnabled
                    ? "border-[#159734] bg-[#f0fbef]/50 shadow-2xs"
                    : "border-[#e8eef0] bg-[#f8faf9]/50"
                }`}
              >
                <div className="mb-2.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <label className="flex cursor-pointer items-center gap-2">
                      <input
                        type="checkbox"
                        checked={isAttrEnabled}
                        onChange={(e) =>
                          toggleBulkAttributeEnable(attr.attributeCode, e.target.checked)
                        }
                        className="rounded border-gray-300 text-[#159734] focus:ring-[#159734]"
                      />
                      <span className="text-sm font-black text-[#172317]">{attr.label}</span>
                    </label>
                    {isMulti && isAttrEnabled && (
                      <span className="rounded bg-white px-2 py-0.5 text-[10px] font-bold text-[#697684] border border-[#e8eef0]">
                        Chọn tối đa {attr.maxSelections}
                      </span>
                    )}
                  </div>

                  {isAttrEnabled ? (
                    <span className="text-xs font-black text-[#159734]">
                      {selectedVals.length > 0
                        ? `Sẽ cập nhật (${selectedVals.length} lựa chọn)`
                        : "Sẽ xóa tiêu chí này"}
                    </span>
                  ) : (
                    <span className="text-xs font-semibold text-[#697684] italic">
                      Giữ nguyên giá trị cũ của từng mặt hàng
                    </span>
                  )}
                </div>

                {isAttrEnabled ? (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {attr.values.map((val) => {
                      const isChosen = selectedVals.includes(val.valueCode);
                      return (
                        <button
                          key={val.valueCode}
                          type="button"
                          onClick={() => toggleBulkSensoryValue(attr, val.valueCode)}
                          className={`flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-bold transition-all ${
                            isChosen
                              ? "border-[#159734] bg-[#159734] text-white shadow-2xs"
                              : "border-[#dce5e1] bg-white text-[#172317] hover:border-[#159734] hover:bg-[#f0fbef]"
                          }`}
                        >
                          {isChosen && <Check className="h-3.5 w-3.5" />}
                          <span>{val.label}</span>
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="text-xs font-semibold text-[#697684] italic pl-6">
                    (Tích vào ô phía trước nếu bạn muốn đồng bộ tiêu chí này cho các sản phẩm đã chọn)
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* THÔNG SỐ KỸ THUẬT PHA CHẾ (CARD 4) */}
      <div className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card">
        <div className="border-b border-[#eef2f0] pb-2">
          <h2 className="text-base font-black text-[#172317]">
            4. Thông số kỹ thuật pha chế & Hướng dẫn (Không bắt buộc)
          </h2>
          <p className="mt-1 text-xs font-semibold text-[#697684]">
            Nhiệt độ nước hãm trà, thời gian ủ và các lưu ý kỹ thuật pha chế.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          {isSingleMode ? (
            <>
              <Field label="Nhiệt độ nước (°C)">
                <TextInput
                  type="number"
                  min={40}
                  max={100}
                  placeholder="Ví dụ: 85"
                  value={singleWaterTemp !== undefined ? String(singleWaterTemp) : ""}
                  onChange={(e) =>
                    setSingleWaterTemp(e.target.value ? Number(e.target.value) : undefined)
                  }
                />
              </Field>

              <Field label="Thời gian hãm (Giây)">
                <TextInput
                  type="number"
                  min={5}
                  max={600}
                  placeholder="Ví dụ: 45"
                  value={singleSteepTime !== undefined ? String(singleSteepTime) : ""}
                  onChange={(e) =>
                    setSingleSteepTime(e.target.value ? Number(e.target.value) : undefined)
                  }
                />
              </Field>

              <Field className="md:col-span-2" label="Ghi chú quy trình pha chế">
                <TextArea
                  rows={3}
                  placeholder="Ví dụ: Đánh thức trà nhanh trong 5s, hãm tuần trà đầu ở 85°C..."
                  value={singleDescription}
                  onChange={(e) => setSingleDescription(e.target.value)}
                />
              </Field>
            </>
          ) : (
            <>
              <div className="rounded-xl border border-[#e8eef0] bg-[#f8faf9] p-3.5">
                <div className="flex items-center justify-between mb-2">
                  <label className="flex cursor-pointer items-center gap-2 text-xs font-black text-[#172317]">
                    <input
                      type="checkbox"
                      checked={bulkUpdateWaterTemp}
                      onChange={(e) => setBulkUpdateWaterTemp(e.target.checked)}
                      className="rounded border-gray-300 text-[#159734] focus:ring-[#159734]"
                    />
                    Đổi nhiệt độ nước (°C)
                  </label>
                  {!bulkUpdateWaterTemp && (
                    <span className="text-[11px] font-semibold text-[#697684] italic">Giữ nguyên</span>
                  )}
                </div>
                {bulkUpdateWaterTemp && (
                  <TextInput
                    type="number"
                    min={40}
                    max={100}
                    placeholder="Ví dụ: 85"
                    value={bulkWaterTemp !== undefined ? String(bulkWaterTemp) : ""}
                    onChange={(e) =>
                      setBulkWaterTemp(e.target.value ? Number(e.target.value) : undefined)
                    }
                  />
                )}
              </div>

              <div className="rounded-xl border border-[#e8eef0] bg-[#f8faf9] p-3.5">
                <div className="flex items-center justify-between mb-2">
                  <label className="flex cursor-pointer items-center gap-2 text-xs font-black text-[#172317]">
                    <input
                      type="checkbox"
                      checked={bulkUpdateSteepTime}
                      onChange={(e) => setBulkUpdateSteepTime(e.target.checked)}
                      className="rounded border-gray-300 text-[#159734] focus:ring-[#159734]"
                    />
                    Đổi thời gian hãm (Giây)
                  </label>
                  {!bulkUpdateSteepTime && (
                    <span className="text-[11px] font-semibold text-[#697684] italic">Giữ nguyên</span>
                  )}
                </div>
                {bulkUpdateSteepTime && (
                  <TextInput
                    type="number"
                    min={5}
                    max={600}
                    placeholder="Ví dụ: 45"
                    value={bulkSteepTime !== undefined ? String(bulkSteepTime) : ""}
                    onChange={(e) =>
                      setBulkSteepTime(e.target.value ? Number(e.target.value) : undefined)
                    }
                  />
                )}
              </div>

              <div className="md:col-span-2 rounded-xl border border-[#e8eef0] bg-[#f8faf9] p-3.5">
                <div className="flex items-center justify-between mb-2">
                  <label className="flex cursor-pointer items-center gap-2 text-xs font-black text-[#172317]">
                    <input
                      type="checkbox"
                      checked={bulkUpdateDescription}
                      onChange={(e) => setBulkUpdateDescription(e.target.checked)}
                      className="rounded border-gray-300 text-[#159734] focus:ring-[#159734]"
                    />
                    Đổi ghi chú quy trình pha chế
                  </label>
                  {!bulkUpdateDescription && (
                    <span className="text-[11px] font-semibold text-[#697684] italic">Giữ nguyên</span>
                  )}
                </div>
                {bulkUpdateDescription && (
                  <TextArea
                    rows={3}
                    placeholder="Ví dụ: Đánh thức trà nhanh trong 5s, hãm tuần trà đầu ở 85°C..."
                    value={bulkDescription}
                    onChange={(e) => setBulkDescription(e.target.value)}
                  />
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* HÀNH ĐỘNG CUỐI TRANG PHONG CÁCH RECEIPTS/NEW */}
      <footer className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card">
        <Link
          className={buttonClassName()}
          to="/system/taste-profiles"
        >
          Hủy bỏ
        </Link>

        {canAssess && (isSingleMode ? (
          <Button disabled={saving || approvingExisting} onClick={() => void handleSingleSave()}>
            {saving ? "Đang lưu..." : "Lưu bản nháp"}
          </Button>
        ) : (
          <Button disabled={saving || !anyBulkFieldEnabled} onClick={() => void handleBulkSave()}>
            {saving ? "Đang xử lý..." : "Lưu bản nháp hàng loạt"}
          </Button>
        ))}
      </footer>
    </section>
  );
}
