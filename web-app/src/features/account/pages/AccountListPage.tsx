import { CheckCircle2, RefreshCw, UserPlus, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { useAuthorization } from "../../../auth/useAuthorization";
import { Dialog } from "../../../components/ui/dialog";
import { ActionLogPagination } from "../../action-log/components/ActionLogPagination";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import {
  InventoryEmptyState,
  InventoryErrorState,
  InventoryPageHeader,
} from "../../inventory/components/InventoryManagementComponents";
import { AccountDetailDrawer } from "../components/AccountDetailDrawer";
import { AccountFilterBar } from "../components/AccountFilterBar";
import { AccountFormDialog } from "../components/AccountFormDialog";
import { AccountResetPasswordDialog } from "../components/AccountResetPasswordDialog";
import { AccountStatusDialog } from "../components/AccountStatusDialog";
import { AccountTable } from "../components/AccountTable";
import { useAccounts } from "../hooks/useAccounts";
import { accountApi } from "../services/account.api";
import type { AccountFilters, AccountResponse, StaffRole } from "../types/account.types";
import { DEFAULT_ACCOUNT_FILTERS, getAccountDisplayName, isDefaultAccountFilters } from "../utils/account.utils";

const NOTICE_TIMEOUT_MS = 5000;

/** Quản lý tài khoản nhân viên (chỉ SUPER_ADMIN): danh sách, tìm kiếm/lọc, tạo, sửa, khóa/mở khóa. */
export function AccountListPage() {
  const { keycloakSubject } = useAuthorization();
  const [filters, setFilters] = useState<AccountFilters>(DEFAULT_ACCOUNT_FILTERS);
  const [selected, setSelected] = useState<AccountResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<AccountResponse | null>(null);
  const [statusTarget, setStatusTarget] = useState<AccountResponse | null>(null);
  const [resetTarget, setResetTarget] = useState<AccountResponse | null>(null);
  const [availableRoles, setAvailableRoles] = useState<StaffRole[] | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const accounts = useAccounts(filters);

  useEffect(() => {
    let cancelled = false;
    accountApi
      .assignableRoles()
      .then((roles) => {
        if (!cancelled) setAvailableRoles(roles);
      })
      .catch(() => {
        // Không tải được thì form cho chọn mọi vai trò; backend vẫn kiểm tra lại khi lưu.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const changeFilters = useCallback((patch: Partial<AccountFilters>) => {
    setFilters((current) => ({ ...current, ...patch }));
  }, []);
  const resetFilters = useCallback(() => setFilters(DEFAULT_ACCOUNT_FILTERS), []);
  const closeDetail = useCallback(() => setSelected(null), []);

  const { refresh } = accounts;
  const applySaved = useCallback(
    (saved: AccountResponse, message: string) => {
      setNotice(message);
      // Cập nhật drawer nếu đang mở đúng tài khoản vừa đổi, rồi tải lại danh sách.
      setSelected((current) => (current?.id === saved.id ? saved : current));
      refresh();
    },
    [refresh],
  );

  const rows = accounts.data?.content ?? [];
  const defaultFilters = isDefaultAccountFilters(filters);
  const dialogOpen = creating || editing !== null || statusTarget !== null || resetTarget !== null;

  return (
    <section className="grid gap-5">
      <InventoryPageHeader
        actions={
          <>
            <button
              aria-label="Làm mới dữ liệu"
              className="inline-flex h-10 items-center gap-2 rounded-lg border border-[#d8eadc] bg-white px-4 text-sm font-black text-[#159734] transition hover:bg-[#f4faf5] disabled:opacity-60"
              disabled={accounts.isLoading}
              onClick={accounts.refresh}
              type="button"
            >
              <RefreshCw className={accounts.isLoading ? "animate-spin" : ""} size={16} aria-hidden="true" />
              Làm mới
            </button>
            <button
              className="inline-flex h-10 items-center gap-2 rounded-lg bg-[#159734] px-4 text-sm font-black text-white transition hover:bg-[#12862d]"
              onClick={() => setCreating(true)}
              type="button"
            >
              <UserPlus size={16} aria-hidden="true" />
              Tạo tài khoản
            </button>
          </>
        }
        description="Tạo, phân quyền và khóa/mở khóa tài khoản đăng nhập của nhân viên."
        eyebrow="Hệ thống / Tài khoản"
        title="Quản lý tài khoản"
      />

      {notice ? (
        <div
          className="flex items-center gap-2 rounded-xl border border-[#cfe8d5] bg-[#f1f8f4] px-4 py-2.5 text-sm font-bold text-[#1b6b35]"
          role="status"
        >
          <CheckCircle2 className="shrink-0" size={16} aria-hidden="true" />
          <span className="min-w-0 flex-1">{notice}</span>
          <button
            aria-label="Đóng thông báo"
            className="grid size-6 place-items-center rounded text-[#1b6b35] hover:bg-[#dff0e4]"
            onClick={() => setNotice(null)}
            type="button"
          >
            <X size={14} aria-hidden="true" />
          </button>
        </div>
      ) : null}

      <AccountFilterBar filters={filters} onChange={changeFilters} onReset={resetFilters} />

      {accounts.error && !accounts.data ? (
        <InventoryErrorState message={accounts.error} onRetry={accounts.refresh} />
      ) : (
        <div className="min-w-0 overflow-hidden rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
          {accounts.error ? (
            <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-xs font-bold text-red-700" role="alert">
              {accounts.error} Đang hiển thị dữ liệu của lần tải trước.
            </p>
          ) : null}

          {!accounts.data ? (
            <div className="p-4">
              <DashboardListSkeleton rows={8} />
            </div>
          ) : rows.length === 0 ? (
            <div className="p-4">
              <InventoryEmptyState
                action={
                  defaultFilters ? (
                    <button
                      className="text-sm font-black text-[#159734] hover:underline"
                      onClick={() => setCreating(true)}
                      type="button"
                    >
                      Tạo tài khoản đầu tiên
                    </button>
                  ) : (
                    <button
                      className="text-sm font-black text-[#159734] hover:underline"
                      onClick={resetFilters}
                      type="button"
                    >
                      Xóa bộ lọc
                    </button>
                  )
                }
                message={
                  defaultFilters
                    ? "Chưa có tài khoản nhân viên nào."
                    : "Không có tài khoản nào khớp với bộ lọc hiện tại."
                }
              />
            </div>
          ) : (
            <div
              aria-busy={accounts.isLoading}
              className={accounts.isLoading ? "opacity-60 transition-opacity" : "transition-opacity"}
            >
              <AccountTable
                currentUserId={keycloakSubject}
                onEdit={setEditing}
                onSelect={setSelected}
                onResetPassword={setResetTarget}
        onToggleStatus={setStatusTarget}
                rows={rows}
                selectedId={selected?.id ?? null}
              />
            </div>
          )}

          {accounts.data ? (
            <ActionLogPagination
              onPageChange={accounts.setPage}
              onPageSizeChange={accounts.setPageSize}
              page={accounts.page}
              pageSize={accounts.pageSize}
              totalElements={accounts.data.totalElements}
              totalPages={accounts.data.totalPages}
            />
          ) : null}
        </div>
      )}

      <AccountDetailDrawer
        account={selected}
        currentUserId={keycloakSubject}
        escapeDisabled={dialogOpen}
        onClose={closeDetail}
        onEdit={setEditing}
        onResetPassword={setResetTarget}
        onToggleStatus={setStatusTarget}
      />

      <Dialog onOpenChange={setCreating} open={creating}>
        {creating ? (
          <AccountFormDialog
            availableRoles={availableRoles}
            onCancel={() => setCreating(false)}
            onSaved={(saved) => {
              setCreating(false);
              applySaved(saved, `Đã tạo tài khoản ${getAccountDisplayName(saved)} (@${saved.username}).`);
            }}
          />
        ) : null}
      </Dialog>

      <Dialog
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
        open={editing !== null}
      >
        {editing ? (
          <AccountFormDialog
            account={editing}
            availableRoles={availableRoles}
            key={editing.id}
            onCancel={() => setEditing(null)}
            onSaved={(saved) => {
              setEditing(null);
              applySaved(saved, `Đã cập nhật tài khoản @${saved.username}.`);
            }}
          />
        ) : null}
      </Dialog>

      <Dialog
        onOpenChange={(open) => {
          if (!open) setStatusTarget(null);
        }}
        open={statusTarget !== null}
      >
        {statusTarget ? (
          <AccountStatusDialog
            account={statusTarget}
            key={statusTarget.id}
            onCancel={() => setStatusTarget(null)}
            onChanged={(updated) => {
              setStatusTarget(null);
              applySaved(
                updated,
                updated.enabled
                  ? `Đã mở khóa tài khoản @${updated.username}.`
                  : `Đã khóa tài khoản @${updated.username}.`,
              );
            }}
          />
        ) : null}
      </Dialog>

      <Dialog
        onOpenChange={(open) => {
          if (!open) setResetTarget(null);
        }}
        open={resetTarget !== null}
      >
        {resetTarget ? (
          <AccountResetPasswordDialog
            account={resetTarget}
            key={resetTarget.id}
            onCancel={() => setResetTarget(null)}
            onReset={(updated) => {
              setResetTarget(null);
              applySaved(
                updated,
                `Đã đặt lại mật khẩu cho @${updated.username}. Nhân viên phải đổi mật khẩu ở lần đăng nhập kế tiếp.`,
              );
            }}
          />
        ) : null}
      </Dialog>
    </section>
  );
}
