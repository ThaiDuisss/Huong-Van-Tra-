import { CheckCircle2, Plus, RefreshCw, ShieldCheck, Sparkles, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useAuthorization } from "../../../auth/useAuthorization";
import { Button, IconButton } from "../../../components/ui/Button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "../../../components/ui/dialog";
import { SearchInput } from "../../../components/ui/SearchInput";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
  InventoryEmptyState,
  InventoryErrorState,
  InventoryPageHeader,
} from "../../inventory/components/InventoryManagementComponents";
import { PermissionFormDialog } from "../components/PermissionFormDialog";
import { PermissionTable } from "../components/PermissionTable";
import { RolePermissionEditor } from "../components/RolePermissionEditor";
import { UserRoleEditor } from "../components/UserRoleEditor";
import { authorizationApi, describeAuthorizationError } from "../services/authorization.api";
import type { AuthorizationTab, PermissionResponse } from "../types/authorization.types";
import { canUse, filterPermissions } from "../utils/authorization.utils";
import { ROLES } from "../../../auth/roles";

const NOTICE_TIMEOUT_MS = 6000;
const ADMIN_FALLBACK_ROLES = [ROLES.SUPER_ADMIN];

/**
 * Phân quyền: danh mục quyền, quyền theo vai trò, vai trò theo người dùng.
 * FE chỉ ẩn/hiện theo quyền hiệu lực; Identity-Service kiểm tra lại mọi thao tác.
 */
export function AuthorizationPage() {
  const auth = useAuthorization();
  const access = { permissions: auth.permissions, roles: auth.roles };
  const canManagePermissions = canUse(access, ["permission:manage"], ADMIN_FALLBACK_ROLES);
  const canManageRoles = canUse(access, ["role:manage"], ADMIN_FALLBACK_ROLES);
  const canManageUsers = canUse(access, ["user:manage"], ADMIN_FALLBACK_ROLES);
  const canViewPermissions = canUse(access, ["permission:manage", "role:manage"], ADMIN_FALLBACK_ROLES);
  const canBootstrap = auth.hasAnyRole([ROLES.SUPER_ADMIN]);

  const tabs = useMemo(
    () =>
      (
        [
          { key: "permissions", label: "Danh mục quyền", visible: canViewPermissions },
          { key: "roles", label: "Quyền theo vai trò", visible: canViewPermissions },
          { key: "users", label: "Vai trò của người dùng", visible: canManageUsers || canManageRoles },
        ] as Array<{ key: AuthorizationTab; label: string; visible: boolean }>
      ).filter((tab) => tab.visible),
    [canManageRoles, canManageUsers, canViewPermissions],
  );
  const [tab, setTab] = useState<AuthorizationTab>(() => tabs[0]?.key ?? "permissions");

  const [permissions, setPermissions] = useState<PermissionResponse[] | null>(null);
  const [permissionsError, setPermissionsError] = useState<string | null>(null);
  const [permissionsReload, setPermissionsReload] = useState(0);
  const loading = canViewPermissions && permissions === null && permissionsError === null;
  const [keyword, setKeyword] = useState("");
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<PermissionResponse | null>(null);
  const [deleting, setDeleting] = useState<PermissionResponse | null>(null);
  const [deleteError, setDeleteError] = useState("");
  const [deletingBusy, setDeletingBusy] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [noticeTone, setNoticeTone] = useState<"ok" | "error">("ok");

  const showNotice = useCallback((message: string, tone: "ok" | "error" = "ok") => {
    setNoticeTone(tone);
    setNotice(message);
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    if (!canViewPermissions) return undefined;
    let cancelled = false;
    authorizationApi
      .listPermissions()
      .then((list) => {
        if (cancelled) return;
        setPermissions(list);
        setPermissionsError(null);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setPermissions(null);
        setPermissionsError(describeAuthorizationError(cause, "Không thể tải danh mục quyền."));
      });
    return () => {
      cancelled = true;
    };
  }, [canViewPermissions, permissionsReload]);

  /** Chỉ gọi từ thao tác người dùng (không gọi trong effect). */
  const loadPermissions = useCallback(() => {
    setPermissions(null);
    setPermissionsError(null);
    setPermissionsReload((value) => value + 1);
  }, []);

  const visiblePermissions = useMemo(() => filterPermissions(permissions ?? [], keyword), [keyword, permissions]);

  async function confirmDelete() {
    if (!deleting) return;
    setDeletingBusy(true);
    setDeleteError("");
    try {
      await authorizationApi.deletePermission(deleting.name);
      setPermissions((list) => list?.filter((item) => item.name !== deleting.name) ?? null);
      showNotice(`Đã xóa quyền ${deleting.name}.`);
      setDeleting(null);
    } catch (cause) {
      setDeleteError(describeAuthorizationError(cause, "Không thể xóa quyền."));
    } finally {
      setDeletingBusy(false);
    }
  }

  async function bootstrap() {
    setBootstrapping(true);
    try {
      const result = await authorizationApi.bootstrap();
      showNotice(
        `Khởi tạo xong: thêm ${result.createdPermissions.length} quyền, cập nhật ${result.updatedRoles.length} vai trò (${result.totalPermissions} quyền trong danh mục).`,
      );
      loadPermissions();
      await auth.refreshPermissions();
    } catch (cause) {
      showNotice(describeAuthorizationError(cause, "Không thể khởi tạo danh mục quyền."), "error");
    } finally {
      setBootstrapping(false);
    }
  }

  return (
    <div className="grid gap-5">
      <InventoryPageHeader
        actions={
          <div className="flex flex-wrap gap-2">
            {canBootstrap ? (
              <Button disabled={bootstrapping} onClick={() => void bootstrap()}>
                <Sparkles size={16} aria-hidden="true" />
                {bootstrapping ? "Đang khởi tạo..." : "Khởi tạo quyền mặc định"}
              </Button>
            ) : null}
            <Button onClick={loadPermissions}>
              <RefreshCw size={16} aria-hidden="true" />
              Tải lại
            </Button>
            {tab === "permissions" && canManagePermissions ? (
              <Button onClick={() => setCreating(true)} variant="primary">
                <Plus size={16} aria-hidden="true" />
                Tạo quyền
              </Button>
            ) : null}
          </div>
        }
        description="Quyền là client role trên Keycloak, vai trò nghiệp vụ là composite role. Mọi thay đổi có hiệu lực khi người dùng nhận token mới."
        eyebrow="Cài đặt"
        title="Phân quyền"
      />

      {notice ? (
        <div
          className={`flex items-start justify-between gap-3 rounded-xl border px-4 py-3 text-sm font-bold ${
            noticeTone === "ok"
              ? "border-[#cfe8d6] bg-[#f1f8f4] text-[#1b583e]"
              : "border-[#f1c8c1] bg-[#fff5f3] text-[#a43a2a]"
          }`}
          role="status"
        >
          <span className="flex items-start gap-2">
            <CheckCircle2 className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
            {notice}
          </span>
          <IconButton aria-label="Đóng thông báo" onClick={() => setNotice(null)}>
            <X size={16} aria-hidden="true" />
          </IconButton>
        </div>
      ) : null}

      {auth.permissionsLoaded && !Array.isArray(auth.permissions) ? (
        <p className="rounded-xl border border-[#f6e0b5] bg-[#fff9ec] px-4 py-3 text-xs font-semibold text-[#8a5a00]">
          Chưa tải được danh sách quyền hiệu lực của bạn (backend chưa cập nhật hoặc Keycloak chưa cấu hình). Màn hình tạm dùng vai trò để hiển thị; backend vẫn kiểm tra khi lưu.
        </p>
      ) : null}

      <div className="flex gap-1 border-b border-[#dce8df]">
        {tabs.map((item) => (
          <button
            className={`-mb-px border-b-2 px-4 py-2.5 text-sm font-bold transition-colors ${
              tab === item.key ? "border-[#159734] text-[#159734]" : "border-transparent text-[#536171] hover:text-[#172317]"
            }`}
            key={item.key}
            onClick={() => setTab(item.key)}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>

      {tabs.length === 0 ? (
        <InventoryEmptyState message="Tài khoản của bạn không có quyền quản lý phân quyền." />
      ) : tab === "permissions" ? (
        <section className="grid gap-3 rounded-2xl border border-[#e4ebe6] bg-white p-4">
          <SearchInput
            className="max-w-sm"
            onChange={setKeyword}
            placeholder="Tìm theo định danh, tên hiển thị, mô tả"
            value={keyword}
          />
          {loading ? (
            <DashboardListSkeleton rows={6} />
          ) : permissionsError ? (
            <InventoryErrorState message={permissionsError} onRetry={loadPermissions} />
          ) : visiblePermissions.length === 0 ? (
            <InventoryEmptyState
              action={
                canBootstrap && (permissions?.length ?? 0) === 0 ? (
                  <Button disabled={bootstrapping} onClick={() => void bootstrap()} variant="primary">
                    <ShieldCheck size={16} aria-hidden="true" />
                    Khởi tạo quyền mặc định
                  </Button>
                ) : undefined
              }
              message={keyword ? "Không có quyền nào khớp từ khóa." : "Chưa có quyền nào trên Keycloak."}
            />
          ) : (
            <PermissionTable
              canManage={canManagePermissions}
              onDelete={(row) => {
                setDeleteError("");
                setDeleting(row);
              }}
              onEdit={setEditing}
              rows={visiblePermissions}
            />
          )}
        </section>
      ) : tab === "roles" ? (
        <section className="rounded-2xl border border-[#e4ebe6] bg-white p-4">
          {loading ? (
            <DashboardListSkeleton rows={6} />
          ) : permissionsError ? (
            <InventoryErrorState message={permissionsError} onRetry={loadPermissions} />
          ) : (
            <RolePermissionEditor canManage={canManageRoles} onNotice={showNotice} permissions={permissions ?? []} />
          )}
        </section>
      ) : (
        <section className="rounded-2xl border border-[#e4ebe6] bg-white p-4">
          <UserRoleEditor canManage={canManageUsers} onNotice={showNotice} />
        </section>
      )}

      <Dialog onOpenChange={(open) => { if (!open) { setCreating(false); setEditing(null); } }} open={creating || editing !== null}>
        {creating || editing ? (
          <PermissionFormDialog
            onCancel={() => { setCreating(false); setEditing(null); }}
            onSaved={(saved, created) => {
              setPermissions((list) =>
                created ? [...(list ?? []), saved] : (list ?? []).map((item) => (item.name === saved.name ? saved : item)),
              );
              setCreating(false);
              setEditing(null);
              showNotice(created ? `Đã tạo quyền ${saved.name}.` : `Đã cập nhật quyền ${saved.name}.`);
            }}
            permission={editing ?? undefined}
          />
        ) : null}
      </Dialog>

      <Dialog onOpenChange={(open) => { if (!open) setDeleting(null); }} open={deleting !== null}>
        {deleting ? (
          <DialogContent className="w-[min(480px,calc(100vw-32px))]">
            <DialogHeader>
              <DialogTitle>Xóa quyền {deleting.name}?</DialogTitle>
              <DialogDescription>
                Client role sẽ bị xóa khỏi Keycloak. Backend từ chối nếu quyền còn nằm trong vai trò hoặc gán trực tiếp cho tài khoản.
              </DialogDescription>
            </DialogHeader>
            {deleteError ? (
              <p className="px-6 pt-4 text-sm font-bold text-[#a43a2a]" role="alert">{deleteError}</p>
            ) : null}
            <DialogFooter>
              <Button disabled={deletingBusy} onClick={() => setDeleting(null)}>Hủy</Button>
              <Button disabled={deletingBusy} onClick={() => void confirmDelete()} variant="danger">
                {deletingBusy ? "Đang xóa..." : "Xóa quyền"}
              </Button>
            </DialogFooter>
          </DialogContent>
        ) : null}
      </Dialog>
    </div>
  );
}
