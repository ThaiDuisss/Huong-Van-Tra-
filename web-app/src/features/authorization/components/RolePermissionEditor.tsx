import { AlertCircle, LoaderCircle, Save } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import { InventoryErrorState } from "../../inventory/components/InventoryManagementComponents";
import { authorizationApi, describeAuthorizationError } from "../services/authorization.api";
import type { BusinessRoleResponse, PermissionResponse } from "../types/authorization.types";
import { diffSelection, groupPermissionsByModule, hasSelectionChanged } from "../utils/authorization.utils";
import { checkboxCard, fieldClass, primaryButton, secondaryButton } from "./authorization-ui";

/** Chọn vai trò, tick quyền, lưu. Backend chỉ thêm/gỡ phần chênh lệch. */
export function RolePermissionEditor({
  canManage,
  onNotice,
  permissions,
}: {
  canManage: boolean;
  onNotice: (message: string) => void;
  permissions: PermissionResponse[];
}) {
  const [roles, setRoles] = useState<BusinessRoleResponse[] | null>(null);
  const [rolesError, setRolesError] = useState<string | null>(null);
  const [rolesReload, setRolesReload] = useState(0);
  const [selectedRole, setSelectedRole] = useState<string>("");
  /** Kết quả tải quyền của vai trò; `role` khác `selectedRole` nghĩa là đang tải. */
  const [assignedState, setAssignedState] = useState<{ role: string; permissions: string[] | null; error: string } | null>(null);
  const [assignedReload, setAssignedReload] = useState(0);
  const [draft, setDraft] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    let cancelled = false;
    authorizationApi
      .listRoles()
      .then((list) => {
        if (cancelled) return;
        setRoles(list);
        setRolesError(null);
        setSelectedRole((current) => current || list[0]?.name || "");
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setRolesError(describeAuthorizationError(cause, "Không thể tải danh sách vai trò."));
      });
    return () => {
      cancelled = true;
    };
  }, [rolesReload]);

  const loadRoles = useCallback(() => {
    setRoles(null);
    setRolesError(null);
    setRolesReload((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!selectedRole) return undefined;
    let cancelled = false;
    authorizationApi
      .getRolePermissions(selectedRole)
      .then((response) => {
        if (cancelled) return;
        setAssignedState({ error: "", permissions: response.permissions, role: selectedRole });
        setDraft(response.permissions);
        setError("");
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setAssignedState({
          error: describeAuthorizationError(cause, "Không thể tải quyền của vai trò."),
          permissions: null,
          role: selectedRole,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [assignedReload, selectedRole]);

  const loadingAssigned = Boolean(selectedRole) && assignedState?.role !== selectedRole;
  const assigned = assignedState?.role === selectedRole ? assignedState.permissions : null;
  const assignedError = assignedState?.role === selectedRole ? assignedState.error : "";

  const role = roles?.find((item) => item.name === selectedRole);
  const editable = canManage && (role?.editable ?? false);
  const groups = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    const visible = needle
      ? permissions.filter(
          (permission) =>
            permission.name.toLowerCase().includes(needle) ||
            permission.displayName.toLowerCase().includes(needle),
        )
      : permissions;
    return groupPermissionsByModule(visible);
  }, [filter, permissions]);
  const changed = assigned !== null && hasSelectionChanged(assigned, draft);
  const diff = assigned === null ? { added: [], removed: [] } : diffSelection(assigned, draft);

  function toggle(name: string) {
    setDraft((current) => (current.includes(name) ? current.filter((item) => item !== name) : [...current, name]));
  }

  async function save() {
    if (!selectedRole || assigned === null) return;
    setSaving(true);
    setError("");
    try {
      const response = await authorizationApi.updateRolePermissions(selectedRole, draft);
      setAssignedState({ error: "", permissions: response.permissions, role: selectedRole });
      setDraft(response.permissions);
      setRoles((current) =>
        current?.map((item) =>
          item.name === selectedRole ? { ...item, permissionCount: response.permissions.length } : item,
        ) ?? null,
      );
      onNotice(`Đã cập nhật quyền của vai trò ${selectedRole} (+${diff.added.length}, −${diff.removed.length}).`);
    } catch (cause) {
      setError(describeAuthorizationError(cause, "Không thể lưu quyền của vai trò."));
    } finally {
      setSaving(false);
    }
  }

  if (rolesError) {
    return <InventoryErrorState message={rolesError} onRetry={loadRoles} />;
  }
  if (roles === null) {
    return <DashboardListSkeleton rows={4} />;
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
      <aside className="grid content-start gap-2">
        <span className="text-xs font-bold text-[#536171]">Vai trò nghiệp vụ</span>
        {roles.map((item) => {
          const active = item.name === selectedRole;
          return (
            <button
              className={`flex items-center justify-between rounded-xl border px-3 py-2.5 text-left transition-colors ${
                active ? "border-[#159734] bg-[#f4faf5]" : "border-[#dce5e1] bg-white hover:border-[#159734]"
              }`}
              key={item.name}
              onClick={() => setSelectedRole(item.name)}
              type="button"
            >
              <span className="grid">
                <span className="text-sm font-bold text-[#172317]">{item.label}</span>
                <span className="font-mono text-[11px] text-[#8793a1]">{item.name}</span>
              </span>
              <span className="rounded-md bg-[#eaf7ec] px-2 py-0.5 text-[11px] font-black text-[#1b583e]">
                {item.permissionCount}
              </span>
            </button>
          );
        })}
      </aside>

      <section className="grid content-start gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="grid">
            <span className="text-sm font-black text-[#172317]">
              Quyền của {role?.label ?? selectedRole}
              {assigned ? <span className="ml-2 text-xs font-bold text-[#697684]">{draft.length} đã chọn</span> : null}
            </span>
            {!editable && role ? (
              <span className="text-xs font-semibold text-[#b45309]">
                {canManage
                  ? "Bạn đang giữ vai trò này nên không thể tự thay đổi quyền của nó."
                  : "Bạn chỉ có quyền xem."}
              </span>
            ) : null}
          </div>
          <input
            className={`${fieldClass} h-10 w-56`}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Lọc quyền..."
            value={filter}
          />
        </div>

        {loadingAssigned ? (
          <DashboardListSkeleton rows={5} />
        ) : assigned === null ? (
          <InventoryErrorState
            message={assignedError || "Không thể tải quyền của vai trò."}
            onRetry={() => setAssignedReload((value) => value + 1)}
          />
        ) : (
          <div className="grid gap-4">
            {groups.map((group) => (
              <fieldset className="grid gap-2" key={group.module}>
                <legend className="mb-1 text-xs font-black uppercase tracking-wide text-[#536171]">{group.label}</legend>
                <div className="grid grid-cols-2 gap-2 max-sm:grid-cols-1 xl:grid-cols-3">
                  {group.items.map((permission) => {
                    const checked = draft.includes(permission.name);
                    return (
                      <label className={checkboxCard(checked, !editable)} key={permission.name}>
                        <input
                          checked={checked}
                          className="mt-0.5 size-4 accent-[#159734]"
                          disabled={!editable}
                          onChange={() => toggle(permission.name)}
                          type="checkbox"
                        />
                        <span className="grid gap-0.5">
                          <span className="text-sm font-bold text-[#172317]">{permission.displayName}</span>
                          <span className="font-mono text-[11px] text-[#697684]">{permission.name}</span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>
            ))}
          </div>
        )}

        {error && assigned !== null ? (
          <div className="flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]" role="alert">
            <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
            {error}
          </div>
        ) : null}

        {editable && assigned !== null ? (
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-[#edf0f4] pt-3">
            {changed ? (
              <span className="mr-auto text-xs font-semibold text-[#536171]">
                Thêm {diff.added.length}, gỡ {diff.removed.length}
              </span>
            ) : null}
            <button className={secondaryButton} disabled={!changed || saving} onClick={() => setDraft(assigned)} type="button">
              Hoàn tác
            </button>
            <button className={primaryButton} disabled={!changed || saving} onClick={() => void save()} type="button">
              {saving ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : <Save size={16} aria-hidden="true" />}
              {saving ? "Đang lưu..." : "Lưu thay đổi"}
            </button>
          </div>
        ) : null}
      </section>
    </div>
  );
}
