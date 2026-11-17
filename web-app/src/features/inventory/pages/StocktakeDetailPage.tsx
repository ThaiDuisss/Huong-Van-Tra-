import {
  ArrowLeft,
  CheckCircle2,
  ClipboardCheck,
  LoaderCircle,
  Play,
  RefreshCw,
  RotateCcw,
  Save,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { Button } from "../../../components/ui/Button";
import { TextInput } from "../../../components/ui/Field";
import { Table, TableCell, TableHead, TableHeaderCell, TableRow } from "../../../components/ui/Table";
import { StatusBadge } from "../components/StocktakeComponents";
import { useStocktakeActionDialog } from "../components/useStocktakeActionDialog";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeDetail, StocktakeLine } from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { ROLES } from "../../../auth/roles";

type CountDraft = { quantity: string; reasonCode: string; note: string };
type LineFilter = "ALL" | "UNCOUNTED" | "VARIANCE";
const operationRoles = [ROLES.SUPER_ADMIN, ROLES.MANAGER, ROLES.INVENTORY_STAFF];
const approvalRoles = [ROLES.SUPER_ADMIN, ROLES.MANAGER];
const openStatuses: StocktakeDetail["status"][] = ["DRAFT", "COUNTING", "PENDING_APPROVAL"];

export function StocktakeDetailPage() {
  const { stocktakeId } = useParams();
  const { roles } = useAuth();
  const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);
  const [drafts, setDrafts] = useState<Record<string, CountDraft>>({});
  const [dirtyIds, setDirtyIds] = useState<string[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState("");
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<LineFilter>("ALL");
  const { ask, dialog } = useStocktakeActionDialog();

  const applyStocktake = useCallback((next: StocktakeDetail) => {
    setStocktake(next);
    setSelectedIds([]);
    // Màn duyệt mặc định chỉ hiện các dòng có chênh lệch
    setFilter(next.status === "PENDING_APPROVAL" ? "VARIANCE" : "ALL");
  }, []);

  const load = useCallback(async () => {
    if (!stocktakeId) return;
    setLoading(true);
    setError("");
    try {
      applyStocktake(await stocktakeApi.getStocktake(stocktakeId));
    } catch (loadError) {
      setError(messageOf(loadError, "Không thể tải chi tiết kiểm kê."));
    } finally {
      setLoading(false);
    }
  }, [applyStocktake, stocktakeId]);

  useEffect(() => {
    const handle = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(handle);
  }, [load]);
  useEffect(() => {
    if (!stocktake) return;
    const handle = window.setTimeout(() => {
      setDrafts(
        Object.fromEntries(
          stocktake.lines.map((line) => [
            line.id,
            {
              note: line.note ?? "",
              quantity: line.countedQuantity === null ? "" : String(line.countedQuantity),
              reasonCode: line.reasonCode ?? "",
            },
          ]),
        ),
      );
      setDirtyIds([]);
    }, 0);
    return () => window.clearTimeout(handle);
  }, [stocktake]);

  const canOperate = hasAnyRole(roles, operationRoles);
  const canApprove = hasAnyRole(roles, approvalRoles);
  const status = stocktake?.status;
  const canCancel = Boolean(status && canApprove && openStatuses.includes(status));
  const reviewMode = status === "PENDING_APPROVAL" || status === "COMPLETED";
  const canSelect = status === "PENDING_APPROVAL" && canApprove;
  const hideExpected = Boolean(stocktake?.blindCount && status === "COUNTING" && !canApprove);

  const varianceLines = useMemo(
    () => stocktake?.lines.filter((line) => (line.varianceQuantity ?? 0) !== 0) ?? [],
    [stocktake],
  );
  const visibleLines = useMemo(
    () =>
      stocktake?.lines.filter((line) => {
        if (filter === "UNCOUNTED") return drafts[line.id]?.quantity === "";
        if (filter === "VARIANCE") return (line.varianceQuantity ?? 0) !== 0;
        return true;
      }) ?? [],
    [drafts, filter, stocktake],
  );
  const shortage = varianceLines.reduce(
    (total, line) => total + Math.min(line.varianceQuantity ?? 0, 0),
    0,
  );
  const surplus = varianceLines.reduce(
    (total, line) => total + Math.max(line.varianceQuantity ?? 0, 0),
    0,
  );
  const varianceValue = varianceLines.reduce(
    (total, line) => total + Math.abs(line.varianceValue),
    0,
  );

  if (!stocktakeId) return <Navigate replace to="/management/inventory/stocktakes" />;

  function updateDraft(lineId: string, patch: Partial<CountDraft>) {
    setDrafts((current) => ({ ...current, [lineId]: { ...current[lineId], ...patch } }));
    setDirtyIds((current) => (current.includes(lineId) ? current : [...current, lineId]));
  }

  function toggleLine(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
  }

  async function runAction(name: string, action: () => Promise<StocktakeDetail>) {
    setPendingAction(name);
    setError("");
    try {
      applyStocktake(await action());
    } catch (actionError) {
      setError(messageOf(actionError, `Không thể thực hiện thao tác ${name}.`));
    } finally {
      setPendingAction("");
    }
  }

  async function saveCounts(ids = dirtyIds) {
    if (!stocktake || ids.length === 0) return stocktake;
    const invalid = ids.find(
      (id) => drafts[id]?.quantity === "" || Number(drafts[id]?.quantity) < 0,
    );
    if (invalid) {
      setError("Số lượng thực đếm phải là số lớn hơn hoặc bằng 0.");
      return null;
    }
    return stocktakeApi.saveStocktakeCount({
      stocktakeId: stocktake.id,
      lines: ids.map((id) => ({
        countedQuantity: Number(drafts[id].quantity),
        lineId: id,
        note: drafts[id].note.trim() || undefined,
        reasonCode: drafts[id].reasonCode.trim() || undefined,
      })),
    });
  }

  async function finish() {
    if (!stocktake) return;
    const missing = stocktake.lines.filter((line) => drafts[line.id]?.quantity === "");
    if (missing.length) {
      setFilter("UNCOUNTED");
      setError(`Còn ${missing.length} dòng chưa nhập số lượng thực đếm.`);
      return;
    }
    if (
      (await ask({
        title: "Gửi kết quả kiểm kê",
        description:
          "Số lượng đã đếm sẽ được khóa để hệ thống tính chênh lệch. Không có chênh lệch thì kỳ kiểm kê hoàn tất ngay; có chênh lệch thì chuyển quản lý duyệt.",
        confirmLabel: "Gửi kết quả",
      })) === null
    )
      return;
    setPendingAction("finish");
    setError("");
    try {
      if (dirtyIds.length && !(await saveCounts())) return;
      applyStocktake(await stocktakeApi.finishStocktake({ stocktakeId: stocktake.id }));
    } catch (finishError) {
      setError(messageOf(finishError, "Không thể gửi kết quả kiểm kê."));
    } finally {
      setPendingAction("");
    }
  }

  return (
    <section className="grid gap-4 text-[#172317]">
      <header className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3">
          <Link
            className="mt-1 grid size-9 place-items-center rounded-lg border border-[#dce5e1]"
            to="/management/inventory/stocktakes"
          >
            <ArrowLeft size={18} />
          </Link>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold sm:text-3xl">
                {stocktake?.countCode ?? "Chi tiết kiểm kê"}
              </h1>
              {stocktake ? <StatusBadge status={stocktake.status} /> : null}
            </div>
            <p className="mt-1 text-sm text-gray-500">
              {stocktake?.branch.name ?? "Đang tải dữ liệu..."}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            disabled={loading || Boolean(pendingAction)}
            onClick={() => void load()}
          >
            <RefreshCw size={17} /> Làm mới
          </Button>
          {status === "DRAFT" && canApprove ? (
            <Button
              variant="primary"
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Bắt đầu kiểm kê",
                  description:
                    "Hệ thống sẽ khóa phạm vi, ghi nhận số tồn hiện tại và mở bước nhập số lượng.",
                  confirmLabel: "Bắt đầu",
                });
                if (confirmed !== null)
                  void runAction("start", () => stocktakeApi.startStocktake({ stocktakeId }));
              }}
            >
              {pendingAction === "start" ? (
                <LoaderCircle className="animate-spin" size={17} />
              ) : (
                <Play size={17} />
              )}{" "}
              {pendingAction === "start" ? "Đang chuẩn bị..." : "Bắt đầu kiểm kê"}
            </Button>
          ) : null}
          {stocktake?.status === "COUNTING" && canOperate ? (
            <>
              <Button
                disabled={!dirtyIds.length || Boolean(pendingAction)}
                onClick={() =>
                  void runAction("save", async () => (await saveCounts()) ?? stocktake)
                }
              >
                <Save size={17} /> Lưu ({dirtyIds.length})
              </Button>
              <Button
                variant="primary"
                disabled={Boolean(pendingAction)}
                onClick={() => void finish()}
              >
                <CheckCircle2 size={17} /> Gửi kết quả
              </Button>
            </>
          ) : null}
          {status === "PENDING_APPROVAL" && canApprove ? (
            <>
              <Button
                disabled={!selectedIds.length || Boolean(pendingAction)}
                onClick={async () => {
                  const reason = await ask({
                    title: "Yêu cầu kiểm kê lại",
                    description: `${selectedIds.length} dòng đã chọn sẽ quay về chưa đếm để nhân viên đếm lại.`,
                    confirmLabel: "Yêu cầu kiểm kê lại",
                    inputLabel: "Lý do",
                    inputPlaceholder: "Nhập lý do cần kiểm kê lại",
                    requireInput: true,
                  });
                  if (reason)
                    void runAction("return", () =>
                      stocktakeApi.returnStocktake({
                        lineIds: selectedIds,
                        reason: reason.trim(),
                        stocktakeId,
                      }),
                    );
                }}
              >
                <RotateCcw size={17} /> Kiểm kê lại ({selectedIds.length})
              </Button>
              <Button
                variant="primary"
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const confirmed = await ask({
                    title: "Phê duyệt và cập nhật tồn kho",
                    description: `Hệ thống sẽ tạo phiếu điều chỉnh cho ${varianceLines.length} dòng chênh lệch và cập nhật tồn kho ngay. Kỳ kiểm kê sẽ hoàn tất.`,
                    confirmLabel: "Phê duyệt",
                  });
                  if (confirmed !== null)
                    void runAction("approve", () => stocktakeApi.approveStocktake({ stocktakeId }));
                }}
              >
                {pendingAction === "approve" ? (
                  <LoaderCircle className="animate-spin" size={17} />
                ) : (
                  <CheckCircle2 size={17} />
                )}{" "}
                Phê duyệt & cập nhật tồn kho
              </Button>
            </>
          ) : null}
          {canCancel ? (
            <Button
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const rejecting = status === "PENDING_APPROVAL";
                const reason = await ask({
                  title: rejecting ? "Từ chối kết quả kiểm kê" : "Hủy kỳ kiểm kê",
                  description: "Kỳ kiểm kê sẽ dừng hoàn toàn và tồn kho không thay đổi.",
                  confirmLabel: rejecting ? "Từ chối" : "Hủy kỳ",
                  inputLabel: rejecting ? "Lý do từ chối" : "Lý do hủy",
                  inputPlaceholder: rejecting ? "Nhập lý do từ chối" : "Nhập lý do hủy kỳ kiểm kê",
                  requireInput: true,
                  tone: "danger",
                });
                if (reason)
                  void runAction("cancel", () =>
                    stocktakeApi.cancelStocktake({ reason: reason.trim(), stocktakeId }),
                  );
              }}
              variant="dangerOutline"
            >
              <XCircle size={17} /> {status === "PENDING_APPROVAL" ? "Từ chối" : "Hủy kỳ kiểm kê"}
            </Button>
          ) : null}
        </div>
      </header>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">
          {error}
        </div>
      ) : null}
      {loading && !stocktake ? (
        <div className="flex min-h-72 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white">
          <LoaderCircle className="animate-spin" size={20} /> Đang tải...
        </div>
      ) : null}
      {stocktake ? (
        <>
          <StatusNotice stocktake={stocktake} />

          <section className="grid gap-3 rounded-xl border border-[#dce5e1] bg-white p-4 shadow-[0_8px_24px_rgba(31,53,43,0.05)] sm:grid-cols-2 xl:grid-cols-5">
            <Info label="Phạm vi" value={stocktake.scopeLabel} />
            <Info label="Tổng mặt hàng cần đếm" value={String(stocktake.totalLines)} />
            <Info label="Đã đếm" value={`${stocktake.countedLines}/${stocktake.totalLines}`} />
            <Info
              label="Số mặt hàng chênh lệch"
              value={
                hideExpected ? "Chỉ hiện sau khi hoàn tất đếm" : String(stocktake.varianceLines)
              }
            />
            <Info
              label="Thời điểm ghi nhận tồn hệ thống"
              value={
                stocktake.startedAt ? formatVietnamDateTime(stocktake.startedAt) : "Chưa ghi nhận"
              }
            />
          </section>

          {reviewMode ? (
            <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <SummaryCard label="Mặt hàng chênh lệch" value={String(varianceLines.length)} />
              <SummaryCard
                label="Tổng thiếu"
                tone="red"
                value={formatVietnamNumber(Math.abs(shortage))}
              />
              <SummaryCard label="Tổng thừa" tone="green" value={formatVietnamNumber(surplus)} />
              <SummaryCard
                label="Giá trị chênh lệch"
                value={`${formatVietnamNumber(varianceValue)} ₫`}
              />
            </section>
          ) : null}

          <section className="overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4ece8] p-4">
              <div>
                <h2 className="font-black">{reviewMode ? "Kết quả đối chiếu" : "Dòng kiểm kê"}</h2>
                <p className="text-xs text-[#697684]">
                  {canSelect
                    ? "Tồn cần đối chiếu đã gồm phát sinh trong lúc kiểm kê. Chọn các dòng nghi ngờ để yêu cầu kiểm kê lại."
                    : reviewMode
                      ? "Tồn cần đối chiếu đã gồm các giao dịch phát sinh trong lúc kiểm kê."
                      : "Nhập số lượng thực tế và lưu các dòng đã thay đổi."}
                </p>
              </div>
              <div className="flex gap-2">
                {(["ALL", "UNCOUNTED", "VARIANCE"] as const)
                  .filter((value) => !(reviewMode && value === "UNCOUNTED"))
                  .map((value) => (
                    <button
                      className={`h-9 rounded-lg px-3 text-xs font-bold ${filter === value ? "bg-[#159734] text-white" : "bg-[#f1f5f2] text-[#536171]"}`}
                      key={value}
                      onClick={() => setFilter(value)}
                      type="button"
                    >
                      {value === "ALL"
                        ? "Tất cả"
                        : value === "UNCOUNTED"
                          ? "Chưa đếm"
                          : "Có chênh lệch"}
                    </button>
                  ))}
              </div>
            </header>
            <div className="overflow-auto">
              {reviewMode ? (
                <ReviewTable
                  canSelect={canSelect}
                  lines={visibleLines}
                  onToggle={toggleLine}
                  selectedIds={selectedIds}
                />
              ) : (
                <Table className="min-w-[1120px]">
                  <TableHead>
                    <TableRow>
                      {[
                        "Mặt hàng",
                        "Vị trí",
                        "Lô hàng / Hạn dùng",
                        "Tồn hệ thống lúc bắt đầu",
                        "Số lượng thực tế",
                        "Chênh lệch",
                        "Lý do",
                        "Ghi chú",
                      ].map((label) => (
                        <TableHeaderCell key={label}>
                          {label}
                        </TableHeaderCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <tbody>
                    {visibleLines.map((line) => {
                      const draft = drafts[line.id];
                      const counted = draft?.quantity === "" ? null : Number(draft?.quantity);
                      const variance =
                        counted === null ? line.varianceQuantity : counted - line.expectedQuantity;
                      const editable = stocktake.status === "COUNTING" && canOperate;
                      return (
                        <TableRow
                          className={dirtyIds.includes(line.id) ? "bg-amber-50/50" : ""}
                          key={line.id}
                        >
                          <TableCell>
                            <strong className="block">{line.inventoryItemCode}</strong>
                            <span className="text-xs text-[#697684]">{line.inventoryItemName}</span>
                          </TableCell>
                          <TableCell>{line.locationName}</TableCell>
                          <TableCell>
                            {line.batchCode ?? "-"}
                            {line.expiryDate ? (
                              <small className="block text-[#697684]">HSD {line.expiryDate}</small>
                            ) : null}
                          </TableCell>
                          <TableCell className="font-bold">
                            {hideExpected
                              ? "••••"
                              : `${formatVietnamNumber(line.snapshotQuantity)} ${line.unit}`}
                          </TableCell>
                          <TableCell>
                            <TextInput
                              className="min-w-24"
                              disabled={!editable}
                              min="0"
                              onChange={(event) =>
                                updateDraft(line.id, { quantity: event.currentTarget.value })
                              }
                              step="any"
                              type="number"
                              value={draft?.quantity ?? ""}
                            />
                          </TableCell>
                          <TableCell
                            className={`font-black ${!hideExpected && variance && variance < 0 ? "text-red-700" : !hideExpected && variance && variance > 0 ? "text-emerald-700" : ""}`}
                          >
                            {hideExpected || variance === null
                              ? "-"
                              : formatVietnamNumber(variance)}
                          </TableCell>
                          <TableCell>
                            <TextInput
                              className="min-w-24"
                              disabled={!editable}
                              onChange={(event) =>
                                updateDraft(line.id, { reasonCode: event.currentTarget.value })
                              }
                              placeholder="Lý do chênh lệch"
                              value={draft?.reasonCode ?? ""}
                            />
                          </TableCell>
                          <TableCell>
                            <TextInput
                              className="min-w-24"
                              disabled={!editable}
                              onChange={(event) =>
                                updateDraft(line.id, { note: event.currentTarget.value })
                              }
                              placeholder="Ghi chú"
                              value={draft?.note ?? ""}
                            />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </tbody>
                </Table>
              )}
            </div>
          </section>

          {stocktake.status === "COMPLETED" ? <AdjustmentResultCard stocktake={stocktake} /> : null}
        </>
      ) : null}
      {dialog}
    </section>
  );
}

function ReviewTable({
  canSelect,
  lines,
  onToggle,
  selectedIds,
}: {
  canSelect: boolean;
  lines: StocktakeLine[];
  onToggle: (id: string) => void;
  selectedIds: string[];
}) {
  const columns = [
    ...(canSelect ? ["Chọn"] : []),
    "Mặt hàng",
    "Vị trí / Lô",
    "Tồn lúc bắt đầu",
    "Phát sinh",
    "Tồn cần đối chiếu",
    "Thực tế",
    "Chênh lệch",
    "Giá trị",
    "Lý do",
  ];
  return (
    <Table className="min-w-[1160px]">
      <TableHead>
        <TableRow>
          {columns.map((label) => (
            <TableHeaderCell key={label}>
              {label}
            </TableHeaderCell>
          ))}
        </TableRow>
      </TableHead>
      <tbody>
        {lines.map((line) => {
          const variance = line.varianceQuantity ?? 0;
          return (
            <TableRow className={selectedIds.includes(line.id) ? "bg-amber-50/50" : ""} key={line.id}>
              {canSelect ? (
                <TableCell>
                  <input
                    aria-label={`Chọn ${line.inventoryItemCode} để kiểm kê lại`}
                    checked={selectedIds.includes(line.id)}
                    onChange={() => onToggle(line.id)}
                    type="checkbox"
                  />
                </TableCell>
              ) : null}
              <TableCell>
                <strong className="block">{line.inventoryItemCode}</strong>
                <span className="text-xs text-[#697684]">{line.inventoryItemName}</span>
              </TableCell>
              <TableCell>
                {line.locationName}
                <small className="block text-[#697684]">{line.batchCode ?? "Không theo lô"}</small>
              </TableCell>
              <TableCell>
                {formatVietnamNumber(line.snapshotQuantity)}
              </TableCell>
              <TableCell>
                {formatVietnamNumber(line.movementQuantity)}
              </TableCell>
              <TableCell className="font-bold">
                {formatVietnamNumber(line.expectedQuantity)}
              </TableCell>
              <TableCell>
                {formatVietnamNumber(line.countedQuantity ?? 0)}
              </TableCell>
              <TableCell
                className={`font-black ${variance < 0 ? "text-red-700" : variance > 0 ? "text-emerald-700" : ""}`}
              >
                {variance > 0 ? "+" : ""}
                {formatVietnamNumber(variance)} {line.unit}
              </TableCell>
              <TableCell>
                {formatVietnamNumber(Math.abs(line.varianceValue))} ₫
              </TableCell>
              <TableCell>
                {line.reasonCode || line.note || "Chưa có"}
              </TableCell>
            </TableRow>
          );
        })}
        {!lines.length ? (
          <TableRow>
            <TableCell className="py-8 text-center text-sm text-[#697684]" colSpan={columns.length}>
              Không có mặt hàng phù hợp.
            </TableCell>
          </TableRow>
        ) : null}
      </tbody>
    </Table>
  );
}

function StatusNotice({ stocktake }: { stocktake: StocktakeDetail }) {
  if (stocktake.status === "COUNTING" && stocktake.returnReason) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800">
        Quản lý yêu cầu kiểm kê lại: {stocktake.returnReason}
      </div>
    );
  }
  if (stocktake.status === "CANCELLED") {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
        Kỳ kiểm kê đã dừng
        {stocktake.cancelledAt ? ` lúc ${formatVietnamDateTime(stocktake.cancelledAt)}` : ""}
        {stocktake.cancelReason ? ` · Lý do: ${stocktake.cancelReason}` : ""}. Tồn kho không thay
        đổi.
      </div>
    );
  }
  return null;
}

function AdjustmentResultCard({ stocktake }: { stocktake: StocktakeDetail }) {
  const adjustment = stocktake.adjustmentDocument;
  return (
    <section className="flex flex-col gap-4 rounded-xl border border-[#dce5e1] bg-white p-5 shadow-[0_8px_24px_rgba(31,53,43,0.05)] md:flex-row md:items-center md:justify-between">
      <div className="flex gap-3">
        <span className="grid size-10 place-items-center rounded-full bg-[#eaf8ee] text-[#159734]">
          <ClipboardCheck size={20} />
        </span>
        <div>
          <h2 className="font-black">
            {adjustment ? `Phiếu điều chỉnh ${adjustment.documentCode}` : "Không cần điều chỉnh"}
          </h2>
          <p className="text-sm text-[#697684]">
            {adjustment
              ? `Đã cập nhật tồn kho${adjustment.postedAt ? ` lúc ${formatVietnamDateTime(adjustment.postedAt)}` : ""}.`
              : "Số lượng thực tế khớp tồn hệ thống, tồn kho giữ nguyên."}
          </p>
        </div>
      </div>
      {stocktake.completedAt ? (
        <span className="text-sm font-bold text-[#536171]">
          Hoàn tất {formatVietnamDateTime(stocktake.completedAt)}
        </span>
      ) : null}
    </section>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-[#f7faf8] px-3 py-2">
      <span className="block text-xs font-bold text-[#697684]">{label}</span>
      <strong className="mt-1 block text-sm">{value}</strong>
    </div>
  );
}

function SummaryCard({
  label,
  tone,
  value,
}: {
  label: string;
  tone?: "red" | "green";
  value: string;
}) {
  return (
    <div className="rounded-xl border border-[#dce5e1] bg-white p-4">
      <span className="text-xs font-bold text-[#697684]">{label}</span>
      <strong
        className={`mt-2 block text-xl ${tone === "red" ? "text-red-700" : tone === "green" ? "text-emerald-700" : ""}`}
      >
        {value}
      </strong>
    </div>
  );
}
function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

