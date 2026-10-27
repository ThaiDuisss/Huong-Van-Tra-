import { Pencil, Trash2 } from "lucide-react";

import type { PermissionResponse } from "../types/authorization.types";

const headClass = "whitespace-nowrap border-b border-[#dce8df] px-4 py-2.5";
const cellClass = "border-b border-[#edf0f4] px-4 py-2.5 align-top";
const actionButtonClass =
  "inline-flex size-8 items-center justify-center rounded-lg border border-[#dce5e1] bg-white text-[#536078] transition hover:border-[#159734] hover:text-[#159734] disabled:cursor-not-allowed disabled:opacity-40";

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
      <table className="w-full min-w-[880px] text-left text-sm">
        <thead className="bg-[#eaf7ec] text-xs font-black text-[#172317]">
          <tr>
            <th className={headClass} scope="col">Định danh</th>
            <th className={headClass} scope="col">Tên hiển thị</th>
            <th className={headClass} scope="col">Mô tả</th>
            <th className={headClass} scope="col">Vai trò đang dùng</th>
            {canManage ? <th className={`${headClass} text-right`} scope="col">Thao tác</th> : null}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr className="transition-colors hover:bg-[#f8fbf8]" key={row.id}>
              <td className={cellClass}>
                <span className="font-mono text-[13px] font-bold text-[#172317]">{row.name}</span>
                {row.builtIn ? (
                  <span className="ml-2 rounded-md bg-[#eef3ff] px-1.5 py-0.5 text-[10px] font-black uppercase text-[#3b5bdb]">
                    mặc định
                  </span>
                ) : null}
              </td>
              <td className={`${cellClass} font-bold text-[#172317]`}>{row.displayName}</td>
              <td className={`${cellClass} max-w-[320px] text-[#536171]`}>{row.description || "—"}</td>
              <td className={cellClass}>
                {row.assignedRoles.length === 0 ? (
                  <span className="text-xs font-semibold text-[#8793a1]">Chưa gán</span>
                ) : (
                  <span className="flex flex-wrap gap-1">
                    {row.assignedRoles.map((role) => (
                      <span
                        className="rounded-md border border-[#d8eadc] bg-[#f4faf5] px-1.5 py-0.5 text-[11px] font-bold text-[#1b583e]"
                        key={role}
                      >
                        {role}
                      </span>
                    ))}
                  </span>
                )}
              </td>
              {canManage ? (
                <td className={`${cellClass} text-right`}>
                  <span className="inline-flex gap-1.5">
                    <button aria-label={`Sửa ${row.name}`} className={actionButtonClass} onClick={() => onEdit(row)} type="button">
                      <Pencil size={14} aria-hidden="true" />
                    </button>
                    <button
                      aria-label={`Xóa ${row.name}`}
                      className={actionButtonClass}
                      disabled={row.assignedRoles.length > 0}
                      onClick={() => onDelete(row)}
                      title={row.assignedRoles.length > 0 ? "Gỡ khỏi các vai trò trước khi xóa" : "Xóa quyền"}
                      type="button"
                    >
                      <Trash2 size={14} aria-hidden="true" />
                    </button>
                  </span>
                </td>
              ) : null}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
