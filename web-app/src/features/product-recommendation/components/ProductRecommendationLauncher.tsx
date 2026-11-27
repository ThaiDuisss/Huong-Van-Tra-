import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Coffee, Leaf, RefreshCw, Sparkles } from "lucide-react";
import { Button } from "../../../components/ui/Button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  CustomBundleBuilder,
  type CustomBundleInput,
} from "../../sales/components/custom-bundle/CustomBundleBuilder";
import {
  ProductRecommendationResults,
  type RecommendationResultProps,
} from "./ProductRecommendationResults";
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
  customBundleSubmitting = false,
  customBundleError = "",
  onAddCustomBundle,
  onClearCustomBundleError,
}: Omit<RecommendationResultProps, "mode"> & {
  guestSurveyScope?: string;
  branchId?: string | null;
  customBundleSubmitting?: boolean;
  customBundleError?: string;
  onAddCustomBundle?: (input: CustomBundleInput) => Promise<boolean>;
  onClearCustomBundleError?: () => void;
}) {
  const guest = useGuestTasteSurvey(customerId ? undefined : guestSurveyScope);
  const guestSurvey = guest?.criteria.hardGroups.length || guest?.criteria.softGroups.length ? guest : null;
  const [open, setOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<"products" | "custom-tea">("products");
  const [customTeaMounted, setCustomTeaMounted] = useState(false);
  const [hasCustomTeaDraft, setHasCustomTeaDraft] = useState(false);
  const [discardPromptOpen, setDiscardPromptOpen] = useState(false);
  const [selection, setSelection] = useState<RecommendationMode>("DISCOVERY");
  const queryClient = useQueryClient();
  const eligibility = useQuery({
    queryKey: ["product-recommendation-eligibility", customerId],
    queryFn: ({ signal }) => customerId ? productRecommendationApi.eligibility(customerId, signal) : false,
    enabled: open && !disabled && Boolean(customerId),
    retry: false,
    staleTime: 120_000,
    gcTime: 5 * 60_000,
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

  function closeImmediately() {
    if (customBundleSubmitting) return;
    setOpen(false);
    setCustomTeaMounted(false);
    setHasCustomTeaDraft(false);
    setDiscardPromptOpen(false);
    onClearCustomBundleError?.();
    trigger.current?.focus();
  }

  function changeOpen(value: boolean) {
    if (!value && customBundleSubmitting) return;
    if (!value && hasCustomTeaDraft) {
      setActiveTab("custom-tea");
      setDiscardPromptOpen(true);
      return;
    }
    if (!value) {
      closeImmediately();
      return;
    }
    onClearCustomBundleError?.();
    setOpen(true);
  }

  return (
    <section
      className="shrink-0 rounded-xl border border-[#dceade] bg-[#f4faf5] p-3"
      onKeyDown={(event) => {
        if (!open || event.key !== "Tab") return;
        const dialog = body.current?.closest('[role="dialog"]');
        const controls = dialog?.querySelectorAll<HTMLElement>(
          'button:not([disabled]),select:not([disabled]),input:not([disabled]),textarea:not([disabled]),[tabindex="0"]',
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
        onClick={() => {
          onClearCustomBundleError?.();
          setOpen(true);
        }}
        className="-ml-3 mt-1"
        size="sm"
        variant="link"
      >
        Xem sản phẩm gợi ý
      </Button>
      <Dialog open={open} onOpenChange={changeOpen}>
        {open && (
          <DialogContent
            className={`p-0 overflow-hidden ${
              activeTab === "custom-tea"
                ? "w-[min(1080px,calc(100vw-24px))]"
                : "w-[min(920px,calc(100vw-24px))]"
            }`}
          >
            <div ref={body} className="flex flex-col h-[min(86vh,700px)] max-h-[90vh]">
              <DialogHeader className="shrink-0 px-5 py-3.5 pr-14 border-b border-slate-100 bg-slate-50/60">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#172317]">
                      <Sparkles className="h-5 w-5 text-[#159734]" />
                      AI gợi ý sản phẩm & Nguyên liệu
                    </DialogTitle>
                    <DialogDescription className="text-xs text-slate-500">
                      Gợi ý mặt hàng và nguyên liệu pha trà cá nhân hóa cho khách hàng đang chọn
                    </DialogDescription>
                  </div>

                  {/* 2 Tabs: Mặt hàng & Gợi ý nguyên liệu */}
                  <div className="flex items-center gap-1 rounded-xl bg-slate-100 p-1 text-xs">
                    <button
                      type="button"
                      onClick={() => setActiveTab("products")}
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-bold transition ${
                        activeTab === "products"
                          ? "bg-white text-[#159734] shadow-2xs"
                          : "text-slate-600 hover:text-slate-900"
                      }`}
                    >
                      <Coffee className="h-4 w-4" />
                      <span>Mặt hàng</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setActiveTab("custom-tea");
                        setCustomTeaMounted(true);
                      }}
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-bold transition ${
                        activeTab === "custom-tea"
                          ? "bg-white text-[#159734] shadow-2xs"
                          : "text-slate-600 hover:text-slate-900"
                      }`}
                    >
                      <Leaf className="h-4 w-4" />
                      <span>Gợi ý nguyên liệu</span>
                      {hasCustomTeaDraft ? (
                        <span className="ml-0.5 inline-block size-2 rounded-full bg-[#159734]" aria-hidden="true" />
                      ) : null}
                    </button>
                  </div>
                </div>
              </DialogHeader>

              <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
                {activeTab === "products" ? (
                  <div className="flex-1 min-h-0 overflow-y-auto p-5">
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
                              }}
                              className={`rounded-md px-3 py-1 text-xs font-bold transition ${
                                selection === "DISCOVERY"
                                  ? "bg-[#159734] text-white shadow-2xs"
                                  : "text-slate-600 hover:text-slate-900"
                              }`}
                            >
                              Chưa mua (Khám phá)
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setSelection("REPEAT");
                              }}
                              className={`rounded-md px-3 py-1 text-xs font-bold transition ${
                                selection === "REPEAT"
                                  ? "bg-[#159734] text-white shadow-2xs"
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
                            void queryClient.resetQueries({
                              queryKey: ["product-recommendations", customerId ?? "guest"],
                            });
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
                            closeImmediately();
                            return onAdd(product);
                          }}
                        />
                      ) : null}

                      {/* Top 5 Recommended Products */}
                      {customerId && eligibility.isLoading ? (
                        <p role="status" className="p-4 text-sm text-slate-600">
                          Đang kiểm tra điều kiện gợi ý cá nhân hóa...
                        </p>
                      ) : (
                        <ProductRecommendationResults
                          customerId={customerId}
                          mode={recommendationMode}
                          disabled={disabled}
                          onAdd={
                            onAdd
                              ? (product) => {
                                  closeImmediately();
                                  return onAdd(product);
                                }
                              : undefined
                          }
                        />
                      )}
                    </div>
                  </div>
                ) : null}

                {customTeaMounted ? (
                  <div className={activeTab === "custom-tea" ? "flex min-h-0 flex-1 flex-col gap-2.5 overflow-hidden p-3" : "hidden"}>
                    {discardPromptOpen ? (
                      <div
                        role="alert"
                        className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-2.5 text-xs font-semibold text-amber-900"
                      >
                        <span>Gói trà đang soạn chưa được thêm vào đơn hàng. Bạn có muốn hủy không?</span>
                        <div className="flex items-center gap-2">
                          <Button size="xs" variant="secondary" onClick={() => setDiscardPromptOpen(false)}>
                            Tiếp tục soạn
                          </Button>
                          <Button size="xs" variant="danger" onClick={closeImmediately}>
                            Hủy & đóng
                          </Button>
                        </div>
                      </div>
                    ) : null}

                    <CustomBundleBuilder
                      key={branchId ?? "default-warehouse"}
                      branchId={branchId}
                      customerId={customerId}
                      mode="AI_RECOMMENDATION"
                      submitLoading={customBundleSubmitting}
                      submitError={customBundleError}
                      onDirtyChange={setHasCustomTeaDraft}
                      onCancel={() => changeOpen(false)}
                      onSubmit={async (input) => {
                        if (!onAddCustomBundle) return;
                        const added = await onAddCustomBundle(input);
                        if (added) closeImmediately();
                      }}
                    />
                  </div>
                ) : null}
              </div>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </section>
  );
}
