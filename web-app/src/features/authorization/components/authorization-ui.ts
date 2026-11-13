/** Class dùng chung cho các màn phân quyền, cùng tông với màn Tài khoản. */
export const checkboxCard = (checked: boolean, disabled: boolean) =>
  `flex items-start gap-3 rounded-xl border px-3 py-2.5 transition-colors ${
    checked ? "border-[#159734] bg-[#f4faf5]" : "border-[#dce5e1] bg-white"
  } ${disabled ? "cursor-not-allowed opacity-55" : "cursor-pointer hover:border-[#159734]"}`;
