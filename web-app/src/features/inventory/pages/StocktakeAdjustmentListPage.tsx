import { RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { DataTable, type DataTableColumn } from "../../../components/shared/list/DataTable";
import { ListPageHeader } from "../../../components/shared/list/ListPageHeader";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button, IconButton } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import { adjustmentStatusLabels } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type {
  AdjustmentListItem,
  AdjustmentStatus,
  StocktakeOptions,
} from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const pageSize = 20;

export function StocktakeAdjustmentListPage() {
  const [items, setItems] = useState<AdjustmentListItem[]>([]);
  const [options, setOptions] = useState<StocktakeOptions | null>(null);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [branchId, setBranchId] = useState("ALL");
  const [status, setStatus] = useState<"ALL" | AdjustmentStatus>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [result, stocktakeOptions] = await Promise.all([
        stocktakeApi.listAdjustments({
          branchId: branchId === "ALL" ? undefined : branchId,
          keyword: appliedKeyword || undefined,
          page,
          size: pageSize,
          status: status === "ALL" ? undefined : status,
        }),
        options ? Promise.resolve(options) : stocktakeApi.getStocktakeOptions(),
      ]);
      setItems(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
      setOptions(stocktakeOptions);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Không thể tải danh sách phiếu.");
    } finally {
      setLoading(false);
    }
  }, [appliedKeyword, branchId, options, page, status]);

  useEffect(() => {
    const handle = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  function applySearch() {
    setPage(0);
    setAppliedKeyword(keyword.trim());
  }

  function resetFilters() {
    setKeyword("");
    setAppliedKeyword("");
    setBranchId("ALL");
    setStatus("ALL");
    setPage(0);
  }

  // Phiếu từ kiểm kê được xem ngay trong màn chi tiết kỳ kiểm kê
  const targetOf = (item: AdjustmentListItem) =>
    item.referenceType === "STOCK_COUNT" && item.referenceId
      ? `/management/inventory/stocktakes/${item.referenceId}`
      : null;
  const quantityOf = (item: AdjustmentListItem) =>
    item.lines.reduce((total, line) => total + line.adjustmentQuantity, 0);

  const columns: DataTableColumn<AdjustmentListItem>[] = [
    {
      key: "documentCode",
      header: "Mã phiếu",
      className: "font-black text-[#159734]",
      render: (item) => item.documentCode,
    },
    {
      key: "stocktake",
      header: "Kỳ kiểm kê",
      render: (item) => {
        const target = targetOf(item);
        return target ? (
          <Link className="font-bold text-[#159734] hover:underline" to={target}>
            {item.note?.match(/CT-[\w-]+/)?.[0] ?? "Xem kỳ kiểm kê"}
          </Link>
        ) : (
          "Điều chỉnh thủ công"
        );
      },
    },
    {
      key: "branch",
      header: "Chi nhánh",
      render: (item) =>
        options?.branches.find((candidate) => candidate.id === item.branchId)?.name ??
        item.branchId.slice(0, 8),
    },
    { key: "createdAt", header: "Ngày tạo", render: (item) => formatVietnamDateTime(item.createdAt) },
    { key: "lineCount", header: "Số mặt hàng", render: (item) => item.lines.length },
    {
      key: "quantity",
      header: "Tổng điều chỉnh",
      render: (item) => {
        const quantity = quantityOf(item);
        return (
          <span className={`font-black ${quantity < 0 ? "text-red-700" : "text-emerald-700"}`}>
            {quantity > 0 ? "+" : ""}
            {formatVietnamNumber(quantity)}
          </span>
        );
      },
    },
    { key: "status", header: "Trạng thái", render: (item) => <Status status={item.status} /> },
    {
      key: "postedAt",
      header: "Ngày cập nhật",
      render: (item) => (item.postedAt ? formatVietnamDateTime(item.postedAt) : "Chưa cập nhật"),
    },
    {
      key: "action",
      header: "",
      hideOnMobile: true,
      render: (item) => {
        const target = targetOf(item);
        return target ? (
          <Link className="font-bold text-[#159734] hover:underline" to={target}>
            Xem phiếu
          </Link>
        ) : null;
      },
    },
  ];

  return (
    <section className="grid min-h-[calc(100vh-120px)] content-start gap-4 text-[#172317]">
      <ListPageHeader
        actions={
          <Button disabled={loading} onClick={() => void load()}>
            <RefreshCw size={17} /> Làm mới
          </Button>
        }
        description="Quản lý tập trung các phiếu được tạo từ kết quả kiểm kê."
        title="Phiếu cập nhật tồn kho"
      />

      <section className="grid gap-3 rounded-xl border border-[#dce5e1] bg-white p-4 shadow-[0_8px_24px_rgba(31,53,43,0.05)] lg:grid-cols-[minmax(260px,1fr)_220px_220px_auto] lg:items-end">
        <Field label="Tìm phiếu">
          <div className="flex gap-2">
            <TextInput
              className="min-w-0 flex-1"
              onChange={(event) => setKeyword(event.currentTarget.value)}
              onKeyDown={(event) => event.key === "Enter" && applySearch()}
              placeholder="Nhập mã phiếu"
              value={keyword}
            />
            <IconButton aria-label="Tìm kiếm" onClick={applySearch} size="md" variant="primary">
              <Search size={17} />
            </IconButton>
          </div>
        </Field>
        <SelectField
          label="Chi nhánh"
          onChange={(value) => {
            setBranchId(value);
            setPage(0);
          }}
          options={[
            { label: "Tất cả", value: "ALL" },
            ...(options?.branches.map((branch) => ({ label: branch.name, value: branch.id })) ??
              []),
          ]}
          value={branchId}
        />
        <SelectField
          label="Trạng thái"
          onChange={(value) => {
            setStatus(value as "ALL" | AdjustmentStatus);
            setPage(0);
          }}
          options={[
            { label: "Tất cả", value: "ALL" },
            ...Object.entries(adjustmentStatusLabels).map(([value, label]) => ({ label, value })),
          ]}
          value={status}
        />
        <Button onClick={resetFilters}>Xóa bộ lọc</Button>
      </section>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">
          {error}
        </div>
      ) : null}

      <DataTable
        caption="Danh sách phiếu cập nhật tồn kho"
        columns={columns}
        emptyDescription="Thử thay đổi bộ lọc hoặc tạo phiếu từ kỳ kiểm kê đã duyệt."
        emptyTitle="Chưa có phiếu phù hợp"
        getRowKey={(item) => item.id}
        loading={loading}
        minWidth={1100}
        pagination={
          <TablePagination
            disabled={loading}
            itemLabel="phiếu"
            onPageChange={setPage}
            page={page}
            pageSize={pageSize}
            totalItems={totalElements}
            totalPages={totalPages}
          />
        }
        rows={items}
      />
    </section>
  );
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
function Status({ status }: { status: AdjustmentStatus }) {
  return (
    <StatusBadge tone={status === "REJECTED" ? "danger" : status === "POSTED" ? "success" : "warning"}>
      {adjustmentStatusLabels[status]}
    </StatusBadge>
  );
}
