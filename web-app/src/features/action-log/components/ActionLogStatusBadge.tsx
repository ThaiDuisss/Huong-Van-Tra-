import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

export function ActionLogStatusBadge({ status }: { status: string }) {
  const isSuccess = status === "SUCCESS";

  return (
    <StatusBadge dot tone={isSuccess ? "success" : "danger"}>
      {isSuccess ? "Thành công" : "Thất bại"}
    </StatusBadge>
  );
}

const methodTones: Record<string, BadgeTone> = {
  DELETE: "danger",
  PATCH: "warning",
  POST: "success",
  PUT: "info",
};

export function HttpMethodTag({ method }: { method: string }) {
  return (
    <StatusBadge className="shrink-0 font-mono" shape="tag" tone={methodTones[method] ?? "neutral"}>
      {method}
    </StatusBadge>
  );
}
