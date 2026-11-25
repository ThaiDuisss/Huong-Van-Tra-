import { useEffect, useRef, useState } from "react";
import { Sparkles } from "lucide-react";
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
import { productRecommendationApi } from "../services/productRecommendation.api";
import type { PersonalizedMode } from "../types/productRecommendation.types";

/** Parent keys this launcher by actual tab, customer, actor and invoice generation. */
export function ProductRecommendationLauncher({
  customerId,
  disabled,
  onAdd,
}: Omit<RecommendationResultProps, "mode">) {
  const [open, setOpen] = useState(false);
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
          <DialogContent className="max-w-2xl">
            <div ref={body}>
              <DialogHeader>
                <DialogTitle>AI gợi ý sản phẩm</DialogTitle>
                <DialogDescription>
                  {customerId
                    ? "Gợi ý cho khách hàng đang chọn trên hóa đơn."
                    : "Khách lẻ — sản phẩm phổ biến."}
                </DialogDescription>
              </DialogHeader>
              <RecommendationChoices
                customerId={customerId}
                disabled={disabled}
                onAdd={onAdd ? (product) => {
                  // Keep the POS stock and backorder prompts visible.
                  changeOpen(false);
                  return onAdd(product);
                } : undefined}
              />
            </div>
          </DialogContent>
        )}
      </Dialog>
    </section>
  );
}

export function RecommendationChoices({ customerId, disabled, onAdd }: Omit<RecommendationResultProps, "mode">) {
  const [eligibility, setEligibility] = useState<"loading" | "level1" | "level2" | "error">(
    customerId ? "loading" : "level1",
  );
  const [mode, setMode] = useState<PersonalizedMode | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!customerId) return;
    const request = new AbortController();
    void productRecommendationApi.eligibility(customerId, request.signal).then(
      (eligible) => { if (!request.signal.aborted) setEligibility(eligible ? "level2" : "level1"); },
      () => { if (!request.signal.aborted) setEligibility("error"); },
    );
    return () => request.abort();
  }, [customerId, retry]);

  const selected = eligibility === "level1" ? "DISCOVERY" : eligibility === "level2" ? mode : null;
  return (
    <div className="grid max-h-[70vh] gap-3 overflow-y-auto px-5 pb-5">
      {eligibility === "loading" && <p role="status">Đang kiểm tra điều kiện gợi ý...</p>}
      {eligibility === "error" && (
        <div>
          <p role="alert">Không kiểm tra được điều kiện gợi ý của khách hàng.</p>
          <Button size="sm" disabled={disabled} onClick={() => {
            setEligibility("loading");
            setRetry((value) => value + 1);
          }}>Thử lại</Button>
        </div>
      )}
      {eligibility === "level2" && (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Chọn cách gợi ý">
          <Button aria-pressed={mode === "EXPLORE"} disabled={disabled}
            variant={mode === "EXPLORE" ? "primary" : "secondary"}
            onClick={() => setMode("EXPLORE")}>Muốn đổi vị</Button>
          <Button aria-pressed={mode === "FAMILIAR"} disabled={disabled}
            variant={mode === "FAMILIAR" ? "primary" : "secondary"}
            onClick={() => setMode("FAMILIAR")}>Không muốn đổi vị</Button>
        </div>
      )}
      {selected && (
        <>
          <Button className="justify-self-start" size="sm" disabled={disabled}
            onClick={() => setRefresh((value) => value + 1)}>Tải lại gợi ý</Button>
          <ProductRecommendationResults key={`${selected}:${refresh}`} customerId={customerId}
            mode={selected} disabled={disabled} onAdd={onAdd} />
        </>
      )}
    </div>
  );
}
