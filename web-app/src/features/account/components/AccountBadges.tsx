import { StatusBadge } from "@/components/ui/StatusBadge";

import { getRoleLabel } from "../utils/account.utils";

export function AccountStatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <StatusBadge dot tone={enabled ? "success" : "danger"}>
      {enabled ? "Đang hoạt động" : "Đã khóa"}
    </StatusBadge>
  );
}

export function AccountRoleTags({ roles }: { roles: readonly string[] }) {
  if (roles.length === 0) return <span className="text-[#8793a1]">—</span>;

  return (
    <span className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <StatusBadge key={role} shape="tag" title={role} tone={role === "SUPER_ADMIN" ? "warning" : "neutral"}>
          {getRoleLabel(role)}
        </StatusBadge>
      ))}
    </span>
  );
}
