import { ArrowUpDown, ChevronRight, Filter, Plus, RefreshCw } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../../components/ui/select";
import { DataTable, type DataTableColumn } from "../../../components/shared/list/DataTable";
import { ListFilterToolbar } from "../../../components/shared/list/ListFilterToolbar";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button } from "../../../components/ui/Button";
import { buttonClassName } from "../../../components/ui/button-styles";
import { Field, NativeSelect } from "../../../components/ui/Field";
import { ModalShell } from "../../../components/ui/Overlay";
import { type BadgeTone, StatusBadge as UiStatusBadge } from "../../../components/ui/StatusBadge";
import {
  stocktakeScopeLabels,
  stocktakeStatusFilterOptions,
  stocktakeStatusLabels,
} from "../constants/stocktake.constants";
import type {
  StocktakeListQuery,
  StocktakeOptions,
  StocktakeSort,
  StocktakeSummary,
} from "../types/stocktake.types";
import { formatVietnamDateTime } from "../utils/inventory-format";

export function StocktakePageHeader({ onRefresh }: { onRefresh: () => void }) {
  return (
    <ListPageHeader
      actions={
        <>
          <Button onClick={onRefresh}>
            <RefreshCw size={17} /> Làm mới
          </Button>
          <Link className={buttonClassName({ variant: "primary" })} to="/management/inventory/stocktakes/new">
            <Plus size={17} /> Tạo phiên kiểm kê
          </Link>
        </>
      }
      description="Theo dõi số đếm thực tế, chênh lệch và phiếu điều chỉnh tồn kho."
      title="Kiểm kê kho"
    />
  );
}

export function StocktakeFilterBar({
  branches,
  onChange,
  onReset,
  query,
}: {
  branches: StocktakeOptions["branches"];
  onChange: (patch: Partial<StocktakeListQuery>) => void;
  onReset: () => void;
  query: StocktakeListQuery;
}) {
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterDraft, setFilterDraft] = useState({
    branchId: query.branchId,
    scopeType: query.scopeType,
  });
  const appliedFilterCount = Number(query.branchId !== "ALL") + Number(query.scopeType !== "ALL");

  function openFilter() {
    setFilterDraft({ branchId: query.branchId, scopeType: query.scopeType });
    setFilterOpen(true);
  }

  return (
    <>
      <ListFilterToolbar
        activeChip={query.status}
        chips={[{ key: "ALL", label: "Tất cả" }, ...stocktakeStatusFilterOptions.map(({ label, value }) => ({ key: value, label }))]}
        chipsLabel="Lọc theo trạng thái kiểm kê"
        onChipChange={(status) => onChange({ page: 0, status: status as StocktakeListQuery["status"] })}
        search={{
          onChange: (keyword) => onChange({ keyword, page: 0 }),
          placeholder: "Tìm mã kiểm kê...",
          value: query.keyword,
        }}
      >
        <Button aria-expanded={filterOpen} className="relative" onClick={openFilter} size="search">
          <Filter size={17} aria-hidden="true" />
          Bộ lọc
          {appliedFilterCount > 0 ? (
            <span className="absolute -right-1.5 -top-1.5 grid min-w-5 place-items-center rounded-full bg-[#159734] px-1.5 text-[10px] font-black leading-5 text-white shadow-sm">
              {appliedFilterCount}
            </span>
          ) : null}
        </Button>
        <SortSelect onChange={(sort) => onChange({ page: 0, sort })} value={query.sort} />
      </ListFilterToolbar>
      {filterOpen ? (
        <StocktakeFilterPopover
          branches={branches}
          draft={filterDraft}
          onApply={() => {
            onChange({ ...filterDraft, page: 0 });
            setFilterOpen(false);
          }}
          onCancel={() => setFilterOpen(false)}
          onClear={() => {
            onReset();
            setFilterDraft({ branchId: "ALL", scopeType: "ALL" });
          }}
          onChange={setFilterDraft}
        />
      ) : null}
    </>
  );
}

function StocktakeFilterPopover({
  branches,
  draft,
  onApply,
  onCancel,
  onChange,
  onClear,
}: {
  branches: StocktakeOptions["branches"];
  draft: Pick<StocktakeListQuery, "branchId" | "scopeType">;
  onApply: () => void;
  onCancel: () => void;
  onChange: (value: Pick<StocktakeListQuery, "branchId" | "scopeType">) => void;
  onClear: () => void;
}) {
  return (
    <ModalShell
      className="w-[min(560px,calc(100vw-32px))]"
      labelledBy="stocktake-filter-title"
      onClose={onCancel}
    >
        <div className="flex min-h-16 items-center justify-between border-b border-[#eef2f0] px-6">
          <h2 className="text-base font-black text-[#172317]" id="stocktake-filter-title">Bộ lọc kiểm kê</h2>
          <Button onClick={onClear} size="xs" variant="link">
            Xóa tất cả
          </Button>
        </div>
        <div className="grid gap-5 p-6 sm:grid-cols-2">
          <SelectField
            label="Chi nhánh"
            onChange={(branchId) => onChange({ ...draft, branchId })}
            options={[
              { label: "Tất cả chi nhánh", value: "ALL" },
              ...branches.map((branch) => ({ label: branch.name, value: branch.id })),
            ]}
            value={draft.branchId}
          />
          <SelectField
            label="Phạm vi kiểm kê"
            onChange={(scopeType) =>
              onChange({ ...draft, scopeType: scopeType as StocktakeListQuery["scopeType"] })
            }
            options={[
              { label: "Tất cả phạm vi", value: "ALL" },
              ...Object.entries(stocktakeScopeLabels).map(([value, label]) => ({ label, value })),
            ]}
            value={draft.scopeType}
          />
        </div>
        <div className="flex items-center justify-end gap-3 border-t border-[#eef2f0] bg-white px-6 py-4">
          <Button onClick={onCancel}>Hủy</Button>
          <Button onClick={onApply} variant="primary">
            Áp dụng bộ lọc
          </Button>
        </div>
    </ModalShell>
  );
}

function SortSelect({
  onChange,
  value,
}: {
  onChange: (value: StocktakeSort) => void;
  value: StocktakeSort;
}) {
  const options: Array<{ label: string; value: StocktakeSort }> = [
    { label: "Mới tạo gần đây", value: "createdAt,desc" },
    { label: "Tạo từ lâu đến mới", value: "createdAt,asc" },
    { label: "Mã kiểm kê A–Z", value: "countCode,asc" },
    { label: "Mã kiểm kê Z–A", value: "countCode,desc" },
  ];
  return (
    <Select onValueChange={(next) => onChange(next as StocktakeSort)} value={value}>
      <SelectTrigger
        aria-label="Sắp xếp danh sách kiểm kê"
        className={`${sortTrigger} min-w-[210px]`}
      >
        <ArrowUpDown size={17} aria-hidden="true" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="w-56">
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

const stocktakeColumns: DataTableColumn<StocktakeSummary>[] = [
  { className: "font-black text-[#159734]", header: "Mã kiểm kê", key: "countCode", render: (item) => item.countCode },
  { header: "Chi nhánh", key: "branch", render: (item) => item.branch.name },
  { header: "Phạm vi", key: "scope", render: (item) => item.scopeLabel },
  { header: "Trạng thái", key: "status", render: (item) => <StatusBadge status={item.status} /> },
  { className: "font-bold", header: "Tiến độ", key: "progress", render: (item) => `${item.countedLines}/${item.totalLines}` },
  { header: "Chênh lệch", key: "variance", render: (item) => item.varianceLines },
  {
    header: "Bắt đầu",
    key: "startedAt",
    render: (item) => (item.startedAt ? formatVietnamDateTime(item.startedAt) : "-"),
  },
];

export function StocktakeTable({
  loading,
  stocktakes,
  totalElements,
  totalPages,
  page,
  onPageChange,
}: {
  loading: boolean;
  stocktakes: StocktakeSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <DataTable
      caption="Danh sách phiên kiểm kê"
      columns={stocktakeColumns}
      emptyDescription="Thử thay đổi bộ lọc hoặc tạo phiên kiểm kê mới."
      emptyTitle="Chưa có phiên kiểm kê phù hợp"
      getRowKey={(item) => item.id}
      loading={loading}
      minWidth={940}
      pagination={
        <TablePagination
          disabled={loading}
          itemLabel="phiên kiểm kê"
          onPageChange={onPageChange}
          page={page}
          pageSize={Math.max(stocktakes.length, 1)}
          summary={<>Tổng {totalElements.toLocaleString("vi-VN")} phiên kiểm kê</>}
          totalItems={totalElements}
          totalPages={totalPages}
        />
      }
      rowActions={(item) => (
        <Link
          aria-label={`Mở ${item.countCode}`}
          className="grid size-8 place-items-center rounded-lg text-[#159734] hover:bg-[#eaf8ee]"
          to={`/management/inventory/stocktakes/${item.id}`}
        >
          <ChevronRight size={18} />
        </Link>
      )}
      rows={stocktakes}
    />
  );
}


const statusBadgeTones: Record<StocktakeSummary["status"], BadgeTone> = {
  CANCELLED: "danger",
  COMPLETED: "success",
  COUNTING: "sky",
  DRAFT: "neutral",
  PENDING_APPROVAL: "warning",
};

export function StatusBadge({ status }: { status: StocktakeSummary["status"] }) {
  return <UiStatusBadge tone={statusBadgeTones[status]}>{stocktakeStatusLabels[status]}</UiStatusBadge>;
}

function SelectField({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: Array<{ label: string; value: string }>;
  value: string;
}) {
  return (
    <Field label={label}>
      <NativeSelect onChange={(event) => onChange(event.currentTarget.value)} value={value}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </NativeSelect>
    </Field>
  );
}

// Kit gap: SelectTrigger chưa có cỡ 44px cho thanh công cụ (cạnh SearchInput), nên giữ class riêng.
const sortTrigger =
  "inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#273a2b] shadow-sm transition-colors hover:bg-[#f7fbf8]";
