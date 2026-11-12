import { useRef, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { Field, TextArea } from "../../../components/ui/Field";
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
            <Field error={validationError} label={options.inputLabel ?? "Lý do"}>
              <TextArea
                autoFocus
                onChange={(event) => {
                  setValue(event.currentTarget.value);
                  setValidationError("");
                }}
                placeholder={options.inputPlaceholder}
                value={value}
              />
            </Field>
          </div>
        ) : null}
        <DialogFooter>
          <Button onClick={() => close(null)}>Quay lại</Button>
          <Button onClick={confirm} variant={options?.tone === "danger" ? "danger" : "primary"}>
            {options?.confirmLabel ?? "Xác nhận"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  return { ask, dialog };
}
