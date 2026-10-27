/** Class dùng chung cho các màn phân quyền, cùng tông với màn Tài khoản. */
export const fieldClass =
  "h-11 w-full rounded-lg border border-[#dce5e1] bg-white px-3 text-sm font-semibold text-[#273a2b] outline-none focus:border-[#159734] focus:ring-2 focus:ring-[#159734]/10 disabled:bg-[#f4f6f5] disabled:text-[#697684]";
export const buttonBase =
  "inline-flex h-11 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-4 text-sm font-bold shadow-sm transition-colors";
export const primaryButton = `${buttonBase} bg-[#159734] text-white hover:bg-[#12862d] disabled:cursor-not-allowed disabled:opacity-60`;
export const secondaryButton = `${buttonBase} border border-[#dce5e1] bg-white text-[#273a2b] hover:bg-[#f7fbf8] disabled:cursor-not-allowed disabled:opacity-60`;
export const dangerButton = `${buttonBase} bg-[#c4422e] text-white hover:bg-[#a43a2a] disabled:cursor-not-allowed disabled:opacity-60`;
export const checkboxCard = (checked: boolean, disabled: boolean) =>
  `flex items-start gap-3 rounded-xl border px-3 py-2.5 transition-colors ${
    checked ? "border-[#159734] bg-[#f4faf5]" : "border-[#dce5e1] bg-white"
  } ${disabled ? "cursor-not-allowed opacity-55" : "cursor-pointer hover:border-[#159734]"}`;
