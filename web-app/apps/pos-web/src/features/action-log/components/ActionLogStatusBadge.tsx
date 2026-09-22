export function ActionLogStatusBadge({ status }: { status: string }) {
  const isSuccess = status === "SUCCESS";

  return (
    <span
      className={`inline-flex h-6 items-center gap-1.5 whitespace-nowrap rounded-full border px-2 text-[11px] font-black ${
        isSuccess ? "border-[#cfe8d5] bg-[#f1f8f4] text-[#1b6b35]" : "border-red-200 bg-red-50 text-red-700"
      }`}
    >
      <span className={`size-1.5 rounded-full ${isSuccess ? "bg-[#159734]" : "bg-red-600"}`} aria-hidden="true" />
      {isSuccess ? "Thành công" : "Thất bại"}
    </span>
  );
}

const methodTones: Record<string, string> = {
  DELETE: "bg-red-50 text-red-700",
  PATCH: "bg-amber-50 text-amber-700",
  POST: "bg-[#eaf7ec] text-[#1b6b35]",
  PUT: "bg-blue-50 text-blue-700",
};

export function HttpMethodTag({ method }: { method: string }) {
  return (
    <span
      className={`inline-flex h-5 shrink-0 items-center rounded px-1.5 font-mono text-[11px] font-bold ${
        methodTones[method] ?? "bg-slate-100 text-slate-600"
      }`}
    >
      {method}
    </span>
  );
}
