import { AlertCircle, LoaderCircle, Save, Search } from "lucide-react";
import { useEffect, useState } from "react";

import { useAuthorization } from "../../../auth/useAuthorization";
import { accountApi } from "../../account/services/account.api";
import type { AccountResponse, StaffRole } from "../../account/types/account.types";
import { STAFF_ROLE_OPTIONS, getAccountDisplayName } from "../../account/utils/account.utils";
import { DashboardListSkeleton } from "../../dashboard/components/DashboardStates";
import { authorizationApi, describeAuthorizationError } from "../services/authorization.api";
import type { UserRolesResponse } from "../types/authorization.types";
import { diffSelection, hasSelectionChanged } from "../utils/authorization.utils";
import { checkboxCard, fieldClass, primaryButton, secondaryButton } from "./authorization-ui";

const SEARCH_DEBOUNCE_MS = 350;

/** Tìm tài khoản nhân viên, xem và sửa vai trò nghiệp vụ; quyền hiệu lực được tính lại từ Keycloak. */
export function UserRoleEditor({
  canManage,
  onNotice,
}: {
  canManage: boolean;
  onNotice: (message: string) => void;
}) {
  const { keycloakSubject, refreshPermissions } = useAuthorization();
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<AccountResponse[] | null>(null);
  const [searchError, setSearchError] = useState("");
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<AccountResponse | null>(null);
  /** Kết quả tải vai trò; `userId` khác `selected.id` nghĩa là đang tải. */
  const [detailState, setDetailState] = useState<{ userId: string; detail: UserRolesResponse | null; error: string } | null>(null);
  const [draft, setDraft] = useState<StaffRole[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const needle = keyword.trim();
    const timer = window.setTimeout(() => {
      setSearching(true);
      setSearchError("");
      accountApi
        .search({ keyword: needle || undefined, page: 0, size: 10 })
        .then((page) => setResults(page.content))
        .catch((cause: unknown) => {
          setResults(null);
          setSearchError(describeAuthorizationError(cause, "Không thể tìm tài khoản."));
        })
        .finally(() => setSearching(false));
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    if (!selected) return undefined;
    let cancelled = false;
    const userId = selected.id;
    authorizationApi
      .getUserRoles(userId)
      .then((response) => {
        if (cancelled) return;
        setDetailState({ detail: response, error: "", userId });
        setDraft(response.roles.filter(isStaffRole));
        setError("");
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setDetailState({
          detail: null,
          error: describeAuthorizationError(cause, "Không thể tải vai trò của tài khoản."),
          userId,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [selected]);

  const currentDetail = selected && detailState && detailState.userId === selected.id ? detailState : null;
  const loadingDetail = Boolean(selected) && currentDetail === null;
  const detail = currentDetail?.detail ?? null;
  const detailError = currentDetail?.error ?? "";

  const isSelf = Boolean(selected && keycloakSubject && selected.id === keycloakSubject);
  const editable = canManage && (detail?.editable ?? false) && !isSelf;
  const current = detail?.roles.filter(isStaffRole) ?? [];
  const changed = detail !== null && hasSelectionChanged(current, draft);

  function toggle(role: StaffRole) {
    setDraft((value) => (value.includes(role) ? value.filter((item) => item !== role) : [...value, role]));
  }

  async function save() {
    if (!selected || !detail) return;
    setSaving(true);
    setError("");
    try {
      const diff = diffSelection(current, draft);
      const response = await authorizationApi.updateUserRoles(selected.id, draft);
      setDetailState({ detail: response, error: "", userId: selected.id });
      setDraft(response.roles.filter(isStaffRole));
      setResults((list) => list?.map((item) => (item.id === selected.id ? { ...item, roles: response.roles } : item)) ?? null);
      onNotice(`Đã cập nhật vai trò của @${response.username} (+${diff.added.length}, −${diff.removed.length}). Nhân viên cần đăng nhập lại hoặc chờ token mới để nhận quyền.`);
    } catch (cause) {
      setError(describeAuthorizationError(cause, "Không thể lưu vai trò."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
      <aside className="grid content-start gap-2">
        <label className="grid gap-2">
          <span className="text-xs font-bold text-[#536171]">Tìm tài khoản</span>
          <span className="relative block">
            <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8793a1]" size={16} aria-hidden="true" />
            <input
              className={`${fieldClass} pl-9`}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Tên đăng nhập, email, họ tên"
              value={keyword}
            />
          </span>
        </label>
        {searching && results === null ? (
          <DashboardListSkeleton rows={4} />
        ) : searchError ? (
          <p className="text-sm font-bold text-[#a43a2a]">{searchError}</p>
        ) : results && results.length === 0 ? (
          <p className="text-sm font-semibold text-[#697684]">Không có tài khoản phù hợp.</p>
        ) : (
          <ul className="grid gap-1.5">
            {(results ?? []).map((account) => {
              const active = selected?.id === account.id;
              return (
                <li key={account.id}>
                  <button
                    className={`grid w-full rounded-xl border px-3 py-2 text-left transition-colors ${
                      active ? "border-[#159734] bg-[#f4faf5]" : "border-[#dce5e1] bg-white hover:border-[#159734]"
                    }`}
                    onClick={() => setSelected(account)}
                    type="button"
                  >
                    <span className="text-sm font-bold text-[#172317]">{getAccountDisplayName(account)}</span>
                    <span className="text-[12px] text-[#697684]">
                      @{account.username} · {account.roles.join(", ") || "chưa có vai trò"}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </aside>

      <section className="grid content-start gap-3">
        {!selected ? (
          <p className="rounded-xl border border-dashed border-[#dce5e1] px-4 py-8 text-center text-sm font-semibold text-[#697684]">
            Chọn một tài khoản ở bên trái để xem và gán vai trò.
          </p>
        ) : loadingDetail ? (
          <DashboardListSkeleton rows={4} />
        ) : !detail ? (
          <div className="flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]" role="alert">
            <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
            {detailError || "Không thể tải vai trò của tài khoản."}
          </div>
        ) : (
          <>
            <div className="grid">
              <span className="text-sm font-black text-[#172317]">
                {detail.fullName || detail.username}
                <span className="ml-2 font-mono text-xs font-semibold text-[#8793a1]">@{detail.username}</span>
              </span>
              {isSelf ? (
                <span className="text-xs font-semibold text-[#b45309]">Bạn không thể thay đổi vai trò của chính mình.</span>
              ) : !detail.editable ? (
                <span className="text-xs font-semibold text-[#b45309]">Tài khoản SUPER_ADMIN không chỉnh qua màn này.</span>
              ) : !canManage ? (
                <span className="text-xs font-semibold text-[#b45309]">Bạn chỉ có quyền xem.</span>
              ) : null}
            </div>

            <fieldset className="grid gap-2">
              <legend className="mb-1 text-xs font-black uppercase tracking-wide text-[#536171]">Vai trò nghiệp vụ</legend>
              <div className="grid grid-cols-2 gap-2 max-sm:grid-cols-1">
                {STAFF_ROLE_OPTIONS.map((option) => {
                  const checked = draft.includes(option.value);
                  return (
                    <label className={checkboxCard(checked, !editable)} key={option.value}>
                      <input
                        checked={checked}
                        className="mt-0.5 size-4 accent-[#159734]"
                        disabled={!editable}
                        onChange={() => toggle(option.value)}
                        type="checkbox"
                      />
                      <span className="grid gap-0.5">
                        <span className="text-sm font-bold text-[#172317]">{option.label}</span>
                        <span className="text-xs font-semibold text-[#697684]">{option.description}</span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>

            <div className="grid gap-1 rounded-xl border border-[#edf0f4] bg-[#fafcfb] px-4 py-3">
              <span className="text-xs font-black uppercase tracking-wide text-[#536171]">
                Quyền hiệu lực ({detail.effectivePermissions.length})
              </span>
              <span className="flex flex-wrap gap-1">
                {detail.effectivePermissions.length === 0 ? (
                  <span className="text-xs font-semibold text-[#8793a1]">Chưa có quyền nào. Kiểm tra vai trò đã được gán quyền chưa.</span>
                ) : (
                  detail.effectivePermissions.map((permission) => (
                    <span className="rounded-md border border-[#d8eadc] bg-white px-1.5 py-0.5 font-mono text-[11px] font-bold text-[#1b583e]" key={permission}>
                      {permission}
                    </span>
                  ))
                )}
              </span>
            </div>

            {error ? (
              <div className="flex items-start gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] px-4 py-3 text-sm font-bold text-[#a43a2a]" role="alert">
                <AlertCircle className="mt-0.5 shrink-0" size={16} aria-hidden="true" />
                {error}
              </div>
            ) : null}

            {editable ? (
              <div className="flex flex-wrap items-center justify-end gap-2 border-t border-[#edf0f4] pt-3">
                <button className={secondaryButton} disabled={!changed || saving} onClick={() => setDraft(current)} type="button">
                  Hoàn tác
                </button>
                <button className={primaryButton} disabled={!changed || draft.length === 0 || saving} onClick={() => void save()} type="button">
                  {saving ? <LoaderCircle className="animate-spin" size={16} aria-hidden="true" /> : <Save size={16} aria-hidden="true" />}
                  {saving ? "Đang lưu..." : "Lưu vai trò"}
                </button>
              </div>
            ) : isSelf ? (
              <div className="flex justify-end border-t border-[#edf0f4] pt-3">
                <button className={secondaryButton} onClick={() => void refreshPermissions()} type="button">
                  Làm mới quyền của tôi
                </button>
              </div>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}

function isStaffRole(role: string): role is StaffRole {
  return STAFF_ROLE_OPTIONS.some((option) => option.value === role);
}
