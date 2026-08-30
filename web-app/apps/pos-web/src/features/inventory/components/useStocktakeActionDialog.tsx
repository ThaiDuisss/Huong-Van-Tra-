import { useRef, useState } from "react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";

type DialogOptions = {
  confirmLabel?: string;
  description: string;
  inputLabel?: string;
  inputPlaceholder?: string;
  requireInput?: boolean;
  tone?: "default" | "danger";
  title: string;
};

export function useStocktakeActionDialog() {
  const [options, setOptions] = useState<DialogOptions | null>(null);
  const [value, setValue] = useState("");
  const [validationError, setValidationError] = useState("");
  const resolver = useRef<((value: string | null) => void) | null>(null);

  function ask(nextOptions: DialogOptions): Promise<string | null> {
    resolver.current?.(null);
    setOptions(nextOptions);
    setValue("");
    setValidationError("");
    return new Promise((resolve) => {
      resolver.current = resolve;
    });
  }

  function close(result: string | null) {
    resolver.current?.(result);
    resolver.current = null;
    setOptions(null);
    setValue("");
    setValidationError("");
  }

  function confirm() {
    if (options?.requireInput && !value.trim()) {
      setValidationError("Vui lòng nhập nội dung trước khi tiếp tục.");
      return;
    }
    close(options?.requireInput ? value.trim() : "confirmed");
  }

  const dialog = (
    <Dialog onOpenChange={(open) => !open && close(null)} open={Boolean(options)}>
      <DialogContent className="w-[min(520px,calc(100vw-32px))]">
        <DialogHeader>
          <DialogTitle>{options?.title}</DialogTitle>
          <DialogDescription>{options?.description}</DialogDescription>
        </DialogHeader>
        {options?.requireInput ? (
          <div className="grid gap-2 px-6 py-5">
            <label className="grid gap-1.5 text-sm font-bold">
              {options.inputLabel ?? "Lý do"}
              <textarea
                autoFocus
                className="min-h-24 rounded-xl border border-[#dce5e1] px-3 py-2 text-sm font-semibold outline-none focus:border-[#159734]"
                onChange={(event) => {
                  setValue(event.currentTarget.value);
                  setValidationError("");
                }}
                placeholder={options.inputPlaceholder}
                value={value}
              />
            </label>
            {validationError ? (
              <p className="text-xs font-bold text-red-700">{validationError}</p>
            ) : null}
          </div>
        ) : null}
        <DialogFooter>
          <button
            className="h-10 rounded-xl border border-[#dce5e1] px-4 text-sm font-bold text-[#536171]"
            onClick={() => close(null)}
            type="button"
          >
            Quay lại
          </button>
          <button
            className={`h-10 rounded-xl px-4 text-sm font-bold text-white ${options?.tone === "danger" ? "bg-red-600 hover:bg-red-700" : "bg-[#159734] hover:bg-[#12862d]"}`}
            onClick={confirm}
            type="button"
          >
            {options?.confirmLabel ?? "Xác nhận"}
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  return { ask, dialog };
}
