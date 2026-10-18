import { KeyRound, Lock, LockOpen, Pencil } from "lucide-react";

import type { AccountResponse } from "../types/account.types";
import { formatAccountDate, getAccountDisplayName, shortAccountId } from "../utils/account.utils";
import { AccountRoleTags, AccountStatusBadge } from "./AccountBadges";

const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-middle";
const actionButtonClass =
  "inline-flex size-8 items-center justify-center rounded-lg border border-pos-border bg-white text-[#536078] transition hover:border-[#159734] hover:text-[#159734] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-pos-border disabled:hover:text-[#536078]";

/** Bảng theo class của `ActionLogTable`; cả dòng bấm được (và focus bằng bàn phím) để mở chi tiết. */
export function AccountTable({
  currentUserId,
  onEdit,
  onSelect,
  onResetPassword,
  onToggleStatus,
  rows,
  selectedId,
}: {
  /** Keycloak subject của người đang đăng nhập: không cho tự khóa chính mình. */
  currentUserId?: string;
  onEdit: (row: AccountResponse) => void;
  onSelect: (row: AccountResponse) => void;
  onResetPassword: (row: AccountResponse) => void;
  onToggleStatus: (row: AccountResponse) => void;
  rows: AccountResponse[];
  selectedId: string | null;
}) {
  return (
    <div className="min-w-0 overflow-x-auto">
      <table className="w-full min-w-[1040px] text-left text-sm">
        <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
          <tr>
            <th className={headClass} scope="col">Tài khoản</th>
            <th className={headClass} scope="col">Email</th>
            <th className={headClass} scope="col">Vai trò</th>
            <th className={headClass} scope="col">Trạng thái</th>
            <th className={headClass} scope="col">Ngày tạo</th>
            <th className={`${headClass} text-right`} scope="col">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const selected = row.id === selectedId;
            const isSelf = Boolean(currentUserId) && row.id === currentUserId;
            const statusLocked = !row.editable || (isSelf && row.enabled);
            const statusLabel = row.enabled ? "Khóa tài khoản" : "Mở khóa tài khoản";

            return (
              <tr
                className={`cursor-pointer outline-none transition-colors focus-visible:bg-[#eef7f0] ${
                  selected ? "bg-[#eef7f0]" : "hover:bg-[#f8fbf8]"
                }`}
                key={row.id}
                onClick={() => onSelect(row)}
                onKeyDown={(event) => {
                  if (event.target !== event.currentTarget) return;
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onSelect(row);
                  }
                }}
                tabIndex={0}
              >
                <td className={cellClass}>
                  <div className="grid">
                    <span className="font-bold text-[#172317]">
                      {getAccountDisplayName(row)}
                      {isSelf ? <span className="ml-1.5 text-[11px] font-black text-[#159734]">(Bạn)</span> : null}
                    </span>
                    <span className="text-[12px] text-[#697684]">
                      @{row.username}
                      <span className="ml-2 font-mono text-[11px] text-[#8793a1]" title={row.id}>
                        {shortAccountId(row.id)}
                      </span>
                    </span>
                  </div>
                </td>
                <td className={`${cellClass} text-[#172317]`}>
                  {row.email ? (
                    <span className="block max-w-[240px] truncate" title={row.email}>
                      {row.email}
                    </span>
                  ) : (
                    <span className="text-[#8793a1]">—</span>
                  )}
                </td>
                <td className={cellClass}>
                  <AccountRoleTags roles={row.roles} />
                </td>
                <td className={cellClass}>
                  <AccountStatusBadge enabled={row.enabled} />
                </td>
                <td className={`${cellClass} whitespace-nowrap tabular-nums text-[#536078]`}>
                  {formatAccountDate(row.createdAt)}
                </td>
                <td className={`${cellClass} text-right`}>
                  <span className="inline-flex items-center gap-1.5">
                    <button
                      aria-label={`Chỉnh sửa tài khoản ${row.username}`}
                      className={actionButtonClass}
                      disabled={!row.editable}
                      onClick={(event) => {
                        event.stopPropagation();
                        onEdit(row);
                      }}
                      title={row.editable ? "Chỉnh sửa" : "Không thể chỉnh sửa tài khoản quản trị cấp cao"}
                      type="button"
                    >
                      <Pencil size={15} aria-hidden="true" />
                    </button>
                    <button
                      aria-label={`Đặt lại mật khẩu tài khoản ${row.username}`}
                      className={actionButtonClass}
                      disabled={!row.editable}
                      onClick={(event) => {
                        event.stopPropagation();
                        onResetPassword(row);
                      }}
                      title={
                        row.editable ? "Đặt lại mật khẩu" : "Không thể đặt lại mật khẩu tài khoản quản trị cấp cao"
                      }
                      type="button"
                    >
                      <KeyRound size={15} aria-hidden="true" />
                    </button>
                    <button
                      aria-label={`${statusLabel} ${row.username}`}
                      className={actionButtonClass}
                      disabled={statusLocked}
                      onClick={(event) => {
                        event.stopPropagation();
                        onToggleStatus(row);
                      }}
                      title={
                        !row.editable
                          ? "Không thể khóa tài khoản quản trị cấp cao"
                          : isSelf && row.enabled
                            ? "Bạn không thể tự khóa tài khoản của mình"
                            : statusLabel
                      }
                      type="button"
                    >
                      {row.enabled ? <Lock size={15} aria-hidden="true" /> : <LockOpen size={15} aria-hidden="true" />}
                    </button>
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
