import { getRoleLabel } from "../utils/account.utils";

export function AccountStatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <span
      className={`inline-flex h-6 items-center gap-1.5 whitespace-nowrap rounded-full border px-2 text-[11px] font-black ${
        enabled ? "border-[#cfe8d5] bg-[#f1f8f4] text-[#1b6b35]" : "border-red-200 bg-red-50 text-red-700"
      }`}
    >
      <span className={`size-1.5 rounded-full ${enabled ? "bg-[#159734]" : "bg-red-600"}`} aria-hidden="true" />
      {enabled ? "Đang hoạt động" : "Đã khóa"}
    </span>
  );
}

export function AccountRoleTags({ roles }: { roles: readonly string[] }) {
  if (roles.length === 0) return <span className="text-[#8793a1]">—</span>;

  return (
    <span className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <span
          className={`inline-flex h-6 items-center whitespace-nowrap rounded-md px-2 text-[11px] font-bold ${
            role === "SUPER_ADMIN" ? "bg-amber-50 text-amber-700" : "bg-[#eef3f8] text-[#3d4b63]"
          }`}
          key={role}
          title={role}
        >
          {getRoleLabel(role)}
        </span>
      ))}
    </span>
  );
}
