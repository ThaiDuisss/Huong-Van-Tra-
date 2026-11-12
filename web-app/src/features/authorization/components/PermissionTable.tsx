import { Pencil, Trash2 } from "lucide-react";

import { IconButton } from "../../../components/ui/Button";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import type { PermissionResponse } from "../types/authorization.types";

export function PermissionTable({
  canManage,
  onDelete,
  onEdit,
  rows,
}: {
  canManage: boolean;
  onDelete: (row: PermissionResponse) => void;
  onEdit: (row: PermissionResponse) => void;
  rows: PermissionResponse[];
}) {
  return (
    <div className="min-w-0 overflow-x-auto">
      <Table className="min-w-[880px]">
        <TableHead>
          <TableRow>
            <TableHeaderCell>Định danh</TableHeaderCell>
            <TableHeaderCell>Tên hiển thị</TableHeaderCell>
            <TableHeaderCell>Mô tả</TableHeaderCell>
            <TableHeaderCell>Vai trò đang dùng</TableHeaderCell>
            {canManage ? <TableHeaderCell className="text-right">Thao tác</TableHeaderCell> : null}
          </TableRow>
        </TableHead>
        <tbody>
          {rows.map((row) => (
            <TableRow className="transition-colors hover:bg-[#f8fbf8]" key={row.id}>
              <TableCell className="align-top">
                <span className="font-mono text-[13px] font-bold text-[#172317]">{row.name}</span>
                {row.builtIn ? (
                  <StatusBadge className="ml-2" shape="tag" tone="info">
                    mặc định
                  </StatusBadge>
                ) : null}
              </TableCell>
              <TableCell className="align-top font-bold text-[#172317]">{row.displayName}</TableCell>
              <TableCell className="align-top max-w-[320px] text-[#536171]">{row.description || "—"}</TableCell>
              <TableCell className="align-top">
                {row.assignedRoles.length === 0 ? (
                  <span className="text-xs font-semibold text-[#8793a1]">Chưa gán</span>
                ) : (
                  <span className="flex flex-wrap gap-1">
                    {row.assignedRoles.map((role) => (
                      <StatusBadge key={role} shape="tag" tone="success">
                        {role}
                      </StatusBadge>
                    ))}
                  </span>
                )}
              </TableCell>
              {canManage ? (
                <TableCell className="align-top text-right">
                  <span className="inline-flex gap-1.5">
                    <IconButton aria-label={`Sửa ${row.name}`} onClick={() => onEdit(row)} variant="secondary">
                      <Pencil size={14} aria-hidden="true" />
                    </IconButton>
                    <IconButton
                      aria-label={`Xóa ${row.name}`}
                      disabled={row.assignedRoles.length > 0}
                      onClick={() => onDelete(row)}
                      title={row.assignedRoles.length > 0 ? "Gỡ khỏi các vai trò trước khi xóa" : "Xóa quyền"}
                      variant="secondary"
                    >
                      <Trash2 size={14} aria-hidden="true" />
                    </IconButton>
                  </span>
                </TableCell>
              ) : null}
            </TableRow>
          ))}
        </tbody>
      </Table>
    </div>
  );
}
