import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Coffee, Layers, RefreshCw, Sparkles } from "lucide-react";
import { Button } from "../../../components/ui/Button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  ProductRecommendationResults,
  type RecommendationResultProps,
} from "./ProductRecommendationResults";
import { ProductRecommendationBomList, type BomBundleConfirmInput } from "./ProductRecommendationBomList";
import type { ProductRecommendationMode, RecommendationMode } from "../types/productRecommendation.types";
import { productRecommendationApi } from "../services/productRecommendation.api";
import { useGuestTasteSurvey } from "../../customer/services/guestTasteSurvey";
import { TasteSurveyRecommendations } from "./TastePreviewLauncher";

/** Parent keys this launcher by actual tab, customer, actor and invoice generation. */
export function ProductRecommendationLauncher({
  customerId,
  disabled,
  onAdd,
  guestSurveyScope,
  branchId = null,
  onSelectBom,
}: Omit<RecommendationResultProps, "mode"> & {
  guestSurveyScope?: string;
  branchId?: string | null;
  onSelectBom?: (input: BomBundleConfirmInput, signal: AbortSignal) => Promise<void>;
}) {
  const guest = useGuestTasteSurvey(customerId ? undefined : guestSurveyScope);
  const guestSurvey = guest?.criteria.hardGroups.length || guest?.criteria.softGroups.length ? guest : null;
  const [open, setOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<"products" | "bom">("products");
  const [selection, setSelection] = useState<RecommendationMode>("DISCOVERY");
  const [refresh, setRefresh] = useState(0);
  const eligibility = useQuery({
    queryKey: ["product-recommendation-eligibility", customerId],
    queryFn: ({ signal }) => customerId ? productRecommendationApi.eligibility(customerId, signal) : false,
    enabled: open && !disabled && Boolean(customerId),
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
  const eligibleForPersonalization = eligibility.data === true && !eligibility.isError;
  const recommendationMode: ProductRecommendationMode = customerId && eligibleForPersonalization
    ? (selection === "REPEAT" ? "FAMILIAR" : "EXPLORE")
    : selection;
  const trigger = useRef<HTMLButtonElement>(null);
  const body = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) body.current?.querySelector<HTMLButtonElement>("button")?.focus();
  }, [open]);

  function changeOpen(value: boolean) {
    setOpen(value);
    if (!value) trigger.current?.focus();
  }

  return (
    <section
      className="shrink-0 rounded-xl border border-[#dceade] bg-[#f4faf5] p-3"
      onKeyDown={(event) => {
        if (!open || event.key !== "Tab") return;
        const dialog = body.current?.closest('[role="dialog"]');
        const controls = dialog?.querySelectorAll<HTMLElement>(
          'button:not([disabled]),select:not([disabled]),input:not([disabled]),[tabindex="0"]',
        );
        if (!controls?.length) return;
        const first = controls[0],
          last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }}
    >
      <div className="flex items-center gap-2 text-sm font-semibold text-[#172317]">
        <Sparkles size={16} aria-hidden="true" />
        AI gợi ý sản phẩm
      </div>
      <Button
        ref={trigger}
        disabled={disabled}
        onClick={() => setOpen(true)}
        className="-ml-3 mt-1"
        size="sm"
        variant="link"
      >
        Xem sản phẩm gợi ý
      </Button>
      <Dialog open={open} onOpenChange={changeOpen}>
        {open && (
          <DialogContent className="max-w-3xl p-0 overflow-hidden">
            <div ref={body} className="flex flex-col h-[85vh] max-h-[85vh]">
              <DialogHeader className="shrink-0 p-5 border-b border-slate-100 bg-slate-50/50">
                <div className="flex flex-wrap items-center justify-between gap-3 pr-6">
                  <div>
                    <DialogTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
                      <Sparkles className="h-5 w-5 text-emerald-600" />
                      AI gợi ý sản phẩm & Công thức
                    </DialogTitle>
                    <DialogDescription className="text-xs text-slate-500">
                      {activeTab === "bom" && !eligibleForPersonalization
                        ? "Tra cứu công thức pha chế trong danh mục."
                        : customerId
                          ? "Gợi ý cá nhân hóa cho khách hàng đang chọn trên đơn."
                          : "Gợi ý sản phẩm phổ biến và danh sách công thức pha chế."}
                    </DialogDescription>
                  </div>

                  {/* 2 Tabs: Mặt hàng & Công thức */}
                  <div className="flex items-center gap-1 rounded-xl bg-slate-100 p-1 text-xs">
                    <button
                      type="button"
                      onClick={() => setActiveTab("products")}
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-bold transition ${
                        activeTab === "products"
                          ? "bg-white text-emerald-800 shadow-2xs"
                          : "text-slate-600 hover:text-slate-900"
                      }`}
                    >
                      <Coffee className="h-4 w-4" />
                      <span>Mặt hàng</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveTab("bom")}
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-bold transition ${
                        activeTab === "bom"
                          ? "bg-white text-emerald-800 shadow-2xs"
                          : "text-slate-600 hover:text-slate-900"
                      }`}
                    >
                      <Layers className="h-4 w-4" />
                      <span>Công thức (BOM)</span>
                    </button>
                  </div>
                </div>
              </DialogHeader>

              <div className="flex-1 min-h-0 overflow-y-auto p-5">
                {activeTab === "products" ? (
                  <div className="grid gap-3.5">
                    {/* Filter Mode: Chưa mua vs Đã mua */}
                    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-50 p-2.5 border border-slate-200">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-slate-700">Trạng thái mua:</span>
                        <div className="flex items-center gap-1 rounded-lg bg-white p-0.5 border border-slate-200">
                          <button
                            type="button"
                            onClick={() => {
                              setSelection("DISCOVERY");
                              setRefresh((v) => v + 1);
                            }}
                            className={`rounded-md px-3 py-1 text-xs font-bold transition ${
                              selection === "DISCOVERY"
                                ? "bg-emerald-600 text-white shadow-2xs"
                                : "text-slate-600 hover:text-slate-900"
                            }`}
                          >
                            Chưa mua (Khám phá)
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setSelection("REPEAT");
                              setRefresh((v) => v + 1);
                            }}
                            className={`rounded-md px-3 py-1 text-xs font-bold transition ${
                              selection === "REPEAT"
                                ? "bg-emerald-600 text-white shadow-2xs"
                                : "text-slate-600 hover:text-slate-900"
                            }`}
                          >
                            Đã mua (Mua lại)
                          </button>
                        </div>
                      </div>

                      <Button
                        variant="secondary"
                        disabled={disabled}
                        onClick={() => {
                          if (customerId) void eligibility.refetch();
                          setRefresh((value) => value + 1);
                        }}
                        size="sm"
                        className="h-8 text-xs"
                      >
                        <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                        Tải lại gợi ý
                      </Button>
                    </div>

                    {!customerId && guestSurvey && onAdd ? (
                      <TasteSurveyRecommendations
                        key={guestSurvey.savedAt}
                        survey={guestSurvey}
                        disabled={disabled}
                        onAdd={(product) => {
                          changeOpen(false);
                          return onAdd(product);
                        }}
                      />
                    ) : null}

                    {/* Top 5 Recommended Products */}
                    <ProductRecommendationResults
                      key={`${refresh}:${recommendationMode}`}
                      customerId={customerId}
                      mode={recommendationMode}
                      disabled={disabled}
                      onAdd={
                        onAdd
                          ? (product) => {
                              changeOpen(false);
                              return onAdd(product);
                            }
                          : undefined
                      }
                    />
                  </div>
                  ) : customerId && eligibility.isLoading ? (
                    <p role="status">Đang kiểm tra điều kiện gợi ý công thức…</p>
                  ) : (
                  /* Tab 2: Công thức (BOM List) */
                  <ProductRecommendationBomList customerId={customerId} branchId={branchId}
                      key={branchId ?? "default-warehouse"}
                    personalized={eligibleForPersonalization}
                    onSelect={onSelectBom ? async (item, signal) => {
                      await onSelectBom(item, signal);
                      if (!signal.aborted) changeOpen(false);
                    } : undefined} />
                )}
              </div>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </section>
  );
}
