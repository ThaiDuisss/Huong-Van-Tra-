import { KeyRound, Lock, LockOpen, Pencil } from "lucide-react";

import { IconButton } from "../../../components/ui/Button";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import type { AccountResponse } from "../types/account.types";
import { formatAccountDate, getAccountDisplayName, shortAccountId } from "../utils/account.utils";
import { AccountRoleTags, AccountStatusBadge } from "./AccountBadges";

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
      <Table className="min-w-[1040px]">
        <TableHead>
          <TableRow>
            <TableHeaderCell>Tài khoản</TableHeaderCell>
            <TableHeaderCell>Email</TableHeaderCell>
            <TableHeaderCell>Vai trò</TableHeaderCell>
            <TableHeaderCell>Trạng thái</TableHeaderCell>
            <TableHeaderCell>Ngày tạo</TableHeaderCell>
            <TableHeaderCell className="text-right">Thao tác</TableHeaderCell>
          </TableRow>
        </TableHead>
        <tbody>
          {rows.map((row) => {
            const selected = row.id === selectedId;
            const isSelf = Boolean(currentUserId) && row.id === currentUserId;
            const statusLocked = !row.editable || (isSelf && row.enabled);
            const statusLabel = row.enabled ? "Khóa tài khoản" : "Mở khóa tài khoản";

            return (
              <TableRow
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
                <TableCell>
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
                </TableCell>
                <TableCell>
                  {row.email ? (
                    <span className="block max-w-[240px] truncate" title={row.email}>
                      {row.email}
                    </span>
                  ) : (
                    <span className="text-[#8793a1]">—</span>
                  )}
                </TableCell>
                <TableCell>
                  <AccountRoleTags roles={row.roles} />
                </TableCell>
                <TableCell>
                  <AccountStatusBadge enabled={row.enabled} />
                </TableCell>
                <TableCell className="whitespace-nowrap tabular-nums text-[#536078]">
                  {formatAccountDate(row.createdAt)}
                </TableCell>
                <TableCell className="text-right">
                  <span className="inline-flex items-center gap-1.5">
                    <IconButton
                      aria-label={`Chỉnh sửa tài khoản ${row.username}`}
                      variant="secondary"
                      disabled={!row.editable}
                      onClick={(event) => {
                        event.stopPropagation();
                        onEdit(row);
                      }}
                      title={row.editable ? "Chỉnh sửa" : "Không thể chỉnh sửa tài khoản quản trị cấp cao"}
                    >
                      <Pencil size={15} aria-hidden="true" />
                    </IconButton>
                    <IconButton
                      aria-label={`Đặt lại mật khẩu tài khoản ${row.username}`}
                      variant="secondary"
                      disabled={!row.editable}
                      onClick={(event) => {
                        event.stopPropagation();
                        onResetPassword(row);
                      }}
                      title={
                        row.editable ? "Đặt lại mật khẩu" : "Không thể đặt lại mật khẩu tài khoản quản trị cấp cao"
                      }
                    >
                      <KeyRound size={15} aria-hidden="true" />
                    </IconButton>
                    <IconButton
                      aria-label={`${statusLabel} ${row.username}`}
                      variant="secondary"
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
                    >
                      {row.enabled ? <Lock size={15} aria-hidden="true" /> : <LockOpen size={15} aria-hidden="true" />}
                    </IconButton>
                  </span>
                </TableCell>
              </TableRow>
            );
          })}
        </tbody>
      </Table>
    </div>
  );
}
