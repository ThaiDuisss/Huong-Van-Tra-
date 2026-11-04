import { useEffect, useRef, useState } from "react";
import { Sparkles } from "lucide-react";
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

/** Parent keys this launcher by actual tab, customer, actor and invoice generation. */
export function ProductRecommendationLauncher({
  customerId,
  disabled,
  onAdd,
}: Omit<RecommendationResultProps, "mode">) {
  const [open, setOpen] = useState(false);
  const [refresh, setRefresh] = useState(0);
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
      <button
        ref={trigger}
        type="button"
        disabled={disabled}
        onClick={() => setOpen(true)}
        className="mt-2 text-sm font-semibold text-[#159734] disabled:opacity-50"
      >
        Xem sản phẩm gợi ý
      </button>
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
              <div className="grid max-h-[70vh] gap-3 overflow-y-auto px-5 pb-5">
                <button
                  type="button"
                  disabled={disabled}
                  className="justify-self-start text-sm font-semibold text-[#159734] disabled:opacity-50"
                  onClick={() => setRefresh((value) => value + 1)}
                >
                  Tải lại gợi ý
                </button>
                <ProductRecommendationResults
                  key={refresh}
                  customerId={customerId}
                  mode="DISCOVERY"
                  disabled={disabled}
                  onAdd={
                    onAdd
                      ? (product) => {
                          // Hand off to POS, whose backorder/stock dialogs and errors must stay visible.
                          changeOpen(false);
                          return onAdd(product);
                        }
                      : undefined
                  }
                />
              </div>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </section>
  );
}
