import {
  CheckCircle2,
  Clock,
  Coffee,
  Sparkles
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { toast } from "@/components/ui/Toast";
import { routePaths } from "@/routes/path";
import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import {
  CatalogResourceManagement,
  TableCell,
  type Column
} from "../components/CatalogResourceManagement";
import { usePagedApiResource } from "../hooks/useApiResource";
import { useCatalogPageState } from "../hooks/useCatalogPageState";
import { productVariantApi } from "../services/productVariant.api";
import { variantTasteApi } from "../services/variantTaste.api";
import {
  PREPARATION_CODE_OPTIONS,
  type VariantTasteProfileResponse
} from "../types/variantTaste.types";
import { formatCurrency } from "../utils/product-formatters";

export function TasteProfileListPage() {
  const navigate = useNavigate();
  const state = useCatalogPageState();

  // Load variants with standard catalog resource hook
  const loadVariants = useCallback(
    () =>
      productVariantApi.list({
        page: state.page - 1,
        size: state.pageSize,
        keyword: state.query || undefined,
        status: state.status === "ALL" ? undefined : state.status
      }),
    [state.page, state.pageSize, state.query, state.status]
  );

  const resource = usePagedApiResource(loadVariants);
  const variants = resource.data?.content ?? [];

  // Profiles cache for visible variants
  const [profilesMap, setProfilesMap] = useState<Record<string, VariantTasteProfileResponse[]>>({});

  useEffect(() => {
    let active = true;
    if (variants.length === 0) return;

    Promise.all(
      variants.map(async (v) => {
        try {
          const profs = await variantTasteApi.getVariantProfiles(String(v.id));
          return [String(v.id), profs.content || []] as [string, VariantTasteProfileResponse[]];
        } catch {
          return [String(v.id), []] as [string, VariantTasteProfileResponse[]];
        }
      })
    ).then((entries) => {
      if (active) {
        setProfilesMap((prev) => ({ ...prev, ...Object.fromEntries(entries) }));
      }
    });

    return () => {
      active = false;
    };
  }, [variants]);

  // Multi-select state for bulk actions
  const [selectedVariantIds, setSelectedVariantIds] = useState<Set<string | number>>(new Set());

  const handleToggleSelect = (id: string | number) => {
    setSelectedVariantIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const isAllSelected = variants.length > 0 && variants.every((v) => selectedVariantIds.has(v.id));

  const handleToggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedVariantIds((prev) => {
        const next = new Set(prev);
        variants.forEach((v) => next.delete(v.id));
        return next;
      });
    } else {
      setSelectedVariantIds((prev) => {
        const next = new Set(prev);
        variants.forEach((v) => next.add(v.id));
        return next;
      });
    }
  };

  const selectedVariants = useMemo(() => {
    return variants.filter((v) => selectedVariantIds.has(v.id));
  }, [variants, selectedVariantIds]);

  const columns: Column[] = [
    { key: "variant", label: "Mặt hàng / Biến thể" },
    { key: "product", label: "Sản phẩm gốc" },
    { key: "price", label: "Đơn vị & Giá" },
    { key: "preparation", label: "Cách thức / Mục đích" },
    { key: "status", label: "Trạng thái hồ sơ" }
  ];

  return (
    <>
      <CatalogResourceManagement
        title="Tiêu chuẩn khẩu vị Sản phẩm"
        description="Thiết lập tiêu chí đánh giá khẩu vị (vị, hương, nhóm trà), định mức nguyên liệu (BOM) và cách thức / mục đích sử dụng cho từng mặt hàng trà."
        searchPlaceholder="Tìm theo tên sản phẩm, tên mặt hàng, SKU..."
        totalItemsLabel="Tổng số Mặt hàng"
        columns={columns}
        items={variants}
        loading={resource.loading}
        error={resource.error}
        query={state.query}
        status={state.status}
        page={(resource.data?.page ?? 0) + 1}
        pageCount={resource.data?.totalPages ?? 1}
        totalItems={resource.data?.totalElements ?? 0}
        pageSize={state.pageSize}
        onQueryChange={state.setQuery}
        onStatusChange={state.setStatus}
        onPageChange={state.setPage}
        onPageSizeChange={state.setPageSize}
        editLabel="Gán khẩu vị"
        editIcon={<Sparkles size={14} />}
        onEdit={(variant) => {
          navigate(`/system/taste-profiles/edit/${variant.id}`, {
            state: { variant }
          });
        }}
        onRefresh={() => {
          resource.refresh();
        }}
        onSubmit={async () => {}}
        renderDetail={(variant) => {
          const profs = profilesMap[String(variant.id)] || [];
          const approved = profs.find((p) => p.status === "APPROVED");
          const draft = profs.find((p) => p.status === "DRAFT");
          const profile = approved || draft;
          const prepOption = PREPARATION_CODE_OPTIONS.find(
            (opt) => opt.value === profile?.preparationCode || opt.code === profile?.preparationCode
          );

          return (
            <div className="space-y-4 p-4 text-sm">
              <div>
                <h3 className="font-black text-base text-neutral-900">{variant.variantName}</h3>
                <div className="text-xs text-neutral-500 mt-0.5">
                  Mã SKU: <span className="font-mono text-neutral-800 font-semibold">{variant.sku}</span>
                  {variant.barcode && <span> · Mã vạch: {variant.barcode}</span>}
                </div>
                <div className="text-xs text-neutral-500 mt-0.5">
                  Sản phẩm gốc: <span className="text-neutral-800 font-medium">{variant.productName || "—"}</span>
                </div>
              </div>

              {profile ? (
                <div className="space-y-3">
                  <div className="rounded-xl border border-neutral-200 bg-white p-3.5 shadow-2xs space-y-2">
                    <div className="flex items-center justify-between border-b border-neutral-100 pb-2">
                      <span className="text-xs font-bold text-neutral-700">Trạng thái hồ sơ:</span>
                      {profile.status === "APPROVED" ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-bold text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                          <CheckCircle2 className="h-3 w-3 text-emerald-600" />
                          Đã phê duyệt (v{profile.rowVersion})
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-700 ring-1 ring-inset ring-amber-600/20">
                          <Clock className="h-3 w-3 text-amber-600" />
                          Bản nháp chờ duyệt (v{profile.rowVersion})
                        </span>
                      )}
                    </div>

                    <div className="text-xs space-y-1.5 text-neutral-700">
                      <div>
                        <strong>Cách thức thưởng thức:</strong> {prepOption?.label || profile.preparationCode}
                      </div>
                      {profile.preparationProtocol?.waterTemperatureCelsius && (
                        <div>
                          <strong>Nhiệt độ nước:</strong> {profile.preparationProtocol.waterTemperatureCelsius}°C
                        </div>
                      )}
                      {profile.preparationProtocol?.steepTimeSeconds && (
                        <div>
                          <strong>Thời gian hãm:</strong> {profile.preparationProtocol.steepTimeSeconds} giây
                        </div>
                      )}
                      {profile.preparationProtocol?.description && (
                        <div>
                          <strong>Ghi chú pha chế:</strong>{" "}
                          <span className="italic text-neutral-600">{profile.preparationProtocol.description}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  {profile.sensoryValues && Object.keys(profile.sensoryValues).length > 0 && (
                    <div className="rounded-xl border border-neutral-200 bg-white p-3.5 shadow-2xs space-y-2">
                      <span className="text-xs font-bold text-neutral-800 block">
                        Đặc tính cảm quan đã gán:
                      </span>
                      <div className="flex flex-wrap gap-1.5">
                        {Object.entries(profile.sensoryValues).map(([attr, vals]) =>
                          vals.map((v) => (
                            <span
                              key={`${attr}-${v}`}
                              className="rounded-md bg-emerald-50 px-2 py-1 text-[11px] font-semibold text-emerald-800 border border-emerald-200"
                            >
                              {attr}: <strong>{v}</strong>
                            </span>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="rounded-xl border border-dashed border-neutral-200 bg-neutral-50 p-6 text-center space-y-2">
                  <Coffee className="mx-auto h-8 w-8 text-neutral-400" />
                  <p className="text-xs font-semibold text-neutral-600">
                    Mặt hàng này chưa được thiết lập hồ sơ khẩu vị
                  </p>
                  <p className="text-[11px] text-neutral-400">
                    Bấm nút "Gán khẩu vị" ở góc dưới để bắt đầu cấu hình.
                  </p>
                </div>
              )}
            </div>
          );
        }}
        selectable={true}
        selectedIds={selectedVariantIds}
        onToggleSelect={handleToggleSelect}
        onToggleSelectAll={handleToggleSelectAll}
        isAllSelected={isAllSelected}
        extraHeaderActions={
          <>
            <ActionButton
              variant={selectedVariantIds.size > 0 ? "primary" : "secondary"}
              onClick={() => {
                if (selectedVariantIds.size === 0) {
                  toast.warning("Vui lòng tích chọn ít nhất một mặt hàng để cập nhật khẩu vị hàng loạt.");
                  return;
                }
                navigate(routePaths.tasteProfileEdit, {
                  state: {
                    variantIds: Array.from(selectedVariantIds),
                    variants: selectedVariants
                  }
                });
              }}
            >
              <Sparkles size={17} aria-hidden="true" />
              Cập nhật khẩu vị hàng loạt {selectedVariantIds.size > 0 ? `(${selectedVariantIds.size})` : ""}
            </ActionButton>
          </>
        }
        renderCells={(variant) => {
          const profs = profilesMap[String(variant.id)] || [];
          const approvedProfile = profs.find((p) => p.status === "APPROVED");
          const draftProfile = profs.find((p) => p.status === "DRAFT");
          const currentProfile = approvedProfile || draftProfile || null;

          const prepOption = PREPARATION_CODE_OPTIONS.find(
            (opt) =>
              opt.value === currentProfile?.preparationCode ||
              opt.code === currentProfile?.preparationCode
          );

          return (
            <>
              <TableCell>
                <div className="font-bold text-gray-900">{variant.variantName}</div>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="font-mono text-xs text-neutral-500">{variant.sku}</span>
                  {variant.barcode && (
                    <span className="text-xs text-neutral-400">· {variant.barcode}</span>
                  )}
                </div>
              </TableCell>

              <TableCell>
                <div className="text-sm text-neutral-800">{variant.productName || "—"}</div>
              </TableCell>

              <TableCell>
                <div className="text-sm font-medium text-neutral-900">
                  {formatCurrency(variant.sellingPrice || 0)}
                </div>
                <div className="text-xs text-neutral-500">ĐVT: {variant.unitName || "Hộp"}</div>
              </TableCell>

              <TableCell>
                {prepOption ? (
                  <div className="flex items-center gap-1.5 text-sm font-medium text-neutral-800">
                    <Coffee className="h-4 w-4 text-emerald-600" />
                    <span>{prepOption.label}</span>
                  </div>
                ) : (
                  <span className="text-xs italic text-neutral-400">Chưa chỉ định</span>
                )}
              </TableCell>

              <TableCell>
                {approvedProfile ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                    <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                    Đã phê duyệt
                  </span>
                ) : draftProfile ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/20">
                    <Clock className="h-3.5 w-3.5 text-amber-600" />
                    Bản nháp (v{draftProfile.rowVersion})
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-medium text-neutral-600 ring-1 ring-inset ring-neutral-500/20">
                    Chưa thiết lập
                  </span>
                )}
              </TableCell>
            </>
          );
        }}
      />
    </>
  );
}
