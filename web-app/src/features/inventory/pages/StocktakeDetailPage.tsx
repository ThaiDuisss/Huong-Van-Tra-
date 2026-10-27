import {
  ArrowLeft,
  CheckCircle2,
  ClipboardCheck,
  LoaderCircle,
  Play,
  RefreshCw,
  Save,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { StatusBadge } from "../components/StocktakeComponents";
import { useStocktakeActionDialog } from "../components/useStocktakeActionDialog";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeDetail } from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";
import { ROLES } from "../../../auth/roles";

type CountDraft = { quantity: string; reasonCode: string; note: string };
const operationRoles = [ROLES.SUPER_ADMIN, ROLES.MANAGER, ROLES.INVENTORY_STAFF];
const approvalRoles = [ROLES.SUPER_ADMIN, ROLES.MANAGER];

export function StocktakeDetailPage() {
  const { stocktakeId } = useParams();
  const { roles } = useAuth();
  const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);
  const [drafts, setDrafts] = useState<Record<string, CountDraft>>({});
  const [dirtyIds, setDirtyIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState("");
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<"ALL" | "UNCOUNTED" | "VARIANCE">("ALL");
  const { ask, dialog } = useStocktakeActionDialog();

  const load = useCallback(async () => {
    if (!stocktakeId) return;
    setLoading(true);
    setError("");
    try {
      setStocktake(await stocktakeApi.getStocktake(stocktakeId));
    } catch (loadError) {
      setError(messageOf(loadError, "Không thể tải chi tiết kiểm kê."));
    } finally {
      setLoading(false);
    }
  }, [stocktakeId]);

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
  const canCancel = Boolean(
    stocktake &&
    canApprove &&
    ["DRAFT", "FROZEN", "COUNTING", "RECONCILING", "PENDING_APPROVAL", "RETURNED"].includes(
      stocktake.status,
    ),
  );
  const hideExpected = Boolean(
    stocktake?.blindCount && stocktake.status === "COUNTING" && !canApprove,
  );
  const visibleLines = useMemo(
    () =>
      stocktake?.lines.filter((line) => {
        if (filter === "UNCOUNTED") return drafts[line.id]?.quantity === "";
        if (filter === "VARIANCE")
          return line.varianceQuantity !== null && line.varianceQuantity !== 0;
        return true;
      }) ?? [],
    [drafts, filter, stocktake],
  );

  if (!stocktakeId) return <Navigate replace to="/management/inventory/stocktakes" />;

  function updateDraft(lineId: string, patch: Partial<CountDraft>) {
    setDrafts((current) => ({ ...current, [lineId]: { ...current[lineId], ...patch } }));
    setDirtyIds((current) => (current.includes(lineId) ? current : [...current, lineId]));
  }

  async function runAction(name: string, action: () => Promise<StocktakeDetail>) {
    setPendingAction(name);
    setError("");
    try {
      setStocktake(await action());
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
      !(await ask({
        title: "Gửi kết quả kiểm kê",
        description: "Sau khi gửi, số lượng đã đếm sẽ được khóa để hệ thống tính chênh lệch.",
        confirmLabel: "Gửi kết quả",
      }))
    )
      return;
    setPendingAction("finish");
    setError("");
    try {
      if (dirtyIds.length && !(await saveCounts())) return;
      setStocktake(await stocktakeApi.finishStocktake({ stocktakeId: stocktake.id }));
    } catch (finishError) {
      setError(messageOf(finishError, "Không thể hoàn tất kiểm kê."));
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
          <button
            className={secondaryButton}
            disabled={loading || Boolean(pendingAction)}
            onClick={() => void load()}
            type="button"
          >
            <RefreshCw size={17} /> Làm mới
          </button>
          {stocktake?.status === "DRAFT" && canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Bắt đầu kiểm kê",
                  description:
                    "Hệ thống sẽ kiểm tra phạm vi, ghi nhận số tồn hiện tại và mở bước nhập số lượng.",
                  confirmLabel: "Bắt đầu",
                });
                if (confirmed === null) return;
                void runAction("prepare", async () => {
                  await stocktakeApi.freezeStocktake({ stocktakeId });
                  return stocktakeApi.startStocktake({ stocktakeId });
                });
              }}
              type="button"
            >
              {pendingAction === "prepare" ? (
                <LoaderCircle className="animate-spin" size={17} />
              ) : (
                <Play size={17} />
              )}{" "}
              {pendingAction === "prepare" ? "Đang chuẩn bị..." : "Bắt đầu kiểm kê"}
            </button>
          ) : null}
          {stocktake?.status === "FROZEN" && canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Bắt đầu kiểm kê",
                  description: "Hệ thống sẽ ghi nhận số tồn hiện tại và mở bước nhập số lượng.",
                  confirmLabel: "Bắt đầu",
                });
                if (confirmed !== null)
                  void runAction("start", () => stocktakeApi.startStocktake({ stocktakeId }));
              }}
              type="button"
            >
              <Play size={17} /> Bắt đầu kiểm kê
            </button>
          ) : null}
          {stocktake?.status === "COUNTING" && canOperate ? (
            <button
              className={secondaryButton}
              disabled={!dirtyIds.length || Boolean(pendingAction)}
              onClick={() => void runAction("save", async () => (await saveCounts()) ?? stocktake)}
              type="button"
            >
              <Save size={17} /> Lưu ({dirtyIds.length})
            </button>
          ) : null}
          {stocktake?.status === "COUNTING" && canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={() => void finish()}
              type="button"
            >
              <CheckCircle2 size={17} /> Gửi kết quả
            </button>
          ) : null}
          {stocktake?.status === "RECONCILING" && canOperate ? (
            <Link
              className={primaryButton}
              to={`/management/inventory/stocktakes/${stocktakeId}/reconciliation`}
            >
              <ClipboardCheck size={17} /> Xử lý chênh lệch
            </Link>
          ) : null}
          {stocktake?.status === "PENDING_APPROVAL" && canApprove ? (
            <>
              <button
                className={secondaryButton}
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const reason = await ask({
                    title: "Yêu cầu kiểm kê lại",
                    description: "Kết quả sẽ được trả về để nhân viên thực hiện lại việc đếm.",
                    confirmLabel: "Yêu cầu kiểm kê lại",
                    inputLabel: "Lý do",
                    inputPlaceholder: "Nhập lý do cần kiểm kê lại",
                    requireInput: true,
                  });
                  if (reason)
                    void runAction("return", () =>
                      stocktakeApi.returnStocktake({ reason: reason.trim(), stocktakeId }),
                    );
                }}
                type="button"
              >
                Yêu cầu kiểm kê lại
              </button>
              <button
                className={secondaryButton}
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const reason = await ask({
                    title: "Từ chối kết quả kiểm kê",
                    description:
                      "Kết quả bị từ chối sẽ không thể tiếp tục xử lý điều chỉnh tồn kho.",
                    confirmLabel: "Từ chối",
                    inputLabel: "Lý do từ chối",
                    inputPlaceholder: "Nhập lý do từ chối",
                    requireInput: true,
                    tone: "danger",
                  });
                  if (reason)
                    void runAction("reject", () =>
                      stocktakeApi.rejectStocktake({ reason: reason.trim(), stocktakeId }),
                    );
                }}
                type="button"
              >
                Từ chối
              </button>
              <button
                className={primaryButton}
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const confirmed = await ask({
                    title: "Phê duyệt kết quả",
                    description:
                      "Kết quả sau khi duyệt có thể được dùng để cập nhật chênh lệch vào tồn kho.",
                    confirmLabel: "Phê duyệt",
                  });
                  if (confirmed !== null)
                    void runAction("approve", () => stocktakeApi.approveStocktake({ stocktakeId }));
                }}
                type="button"
              >
                Phê duyệt
              </button>
            </>
          ) : null}
          {stocktake?.status === "RETURNED" && canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={() =>
                void runAction("reopen", () => stocktakeApi.reopenStocktake({ stocktakeId }))
              }
              type="button"
            >
              Mở kiểm kê lại
            </button>
          ) : null}
          {stocktake?.status === "APPROVED" && stocktake.varianceLines === 0 && canApprove ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Hoàn tất kỳ kiểm kê",
                  description: "Kết quả không có chênh lệch. Kỳ kiểm kê sẽ được đóng.",
                  confirmLabel: "Hoàn tất",
                });
                if (confirmed !== null)
                  void runAction("close", () => stocktakeApi.closeStocktake({ stocktakeId }));
              }}
              type="button"
            >
              Đóng kỳ
            </button>
          ) : null}
          {stocktake?.status === "POSTED" && canApprove ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Hoàn tất kỳ kiểm kê",
                  description: "Mọi chênh lệch đã được cập nhật. Kỳ kiểm kê sẽ được đóng.",
                  confirmLabel: "Hoàn tất",
                });
                if (confirmed !== null)
                  void runAction("close", () => stocktakeApi.closeStocktake({ stocktakeId }));
              }}
              type="button"
            >
              Đóng kỳ
            </button>
          ) : null}
          {canCancel ? (
            <button
              className={dangerButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const reason = await ask({
                  title: "Hủy kỳ kiểm kê",
                  description: "Kỳ đã hủy sẽ dừng hoàn toàn và không thể tiếp tục.",
                  confirmLabel: "Hủy kỳ",
                  inputLabel: "Lý do hủy",
                  inputPlaceholder: "Nhập lý do hủy kỳ kiểm kê",
                  requireInput: true,
                  tone: "danger",
                });
                if (reason) {
                  void runAction("cancel", () =>
                    stocktakeApi.cancelStocktake({ reason: reason.trim(), stocktakeId }),
                  );
                }
              }}
              type="button"
            >
              <XCircle size={17} /> Hủy kỳ kiểm kê
            </button>
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
                stocktake.snapshotAt ? formatVietnamDateTime(stocktake.snapshotAt) : "Chưa ghi nhận"
              }
            />
          </section>

          <section className="overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4ece8] p-4">
              <div>
                <h2 className="font-black">Dòng kiểm kê</h2>
                <p className="text-xs text-[#697684]">
                  Nhập số lượng thực tế và lưu các dòng đã thay đổi.
                </p>
              </div>
              <div className="flex gap-2">
                {(["ALL", "UNCOUNTED", "VARIANCE"] as const).map((value) => (
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
              <table className="w-full min-w-[1120px] text-left text-sm">
                <thead className="bg-[#f3f7f4] text-xs font-black uppercase text-[#536171]">
                  <tr>
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
                      <th className="border-b border-[#dce5e1] px-3 py-3" key={label}>
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visibleLines.map((line) => {
                    const draft = drafts[line.id];
                    const counted = draft?.quantity === "" ? null : Number(draft?.quantity);
                    const variance =
                      counted === null ? line.varianceQuantity : counted - line.expectedQuantity;
                    return (
                      <tr
                        className={dirtyIds.includes(line.id) ? "bg-amber-50/50" : ""}
                        key={line.id}
                      >
                        <td className="border-b border-[#edf2ef] px-3 py-3">
                          <strong className="block">{line.inventoryItemCode}</strong>
                          <span className="text-xs text-[#697684]">{line.inventoryItemName}</span>
                        </td>
                        <td className="border-b border-[#edf2ef] px-3">{line.locationName}</td>
                        <td className="border-b border-[#edf2ef] px-3">
                          {line.batchCode ?? "-"}
                          {line.expiryDate ? (
                            <small className="block text-[#697684]">HSD {line.expiryDate}</small>
                          ) : null}
                        </td>
                        <td className="border-b border-[#edf2ef] px-3 font-bold">
                          {hideExpected
                            ? "••••"
                            : `${formatVietnamNumber(line.snapshotQuantity)} ${line.unit}`}
                        </td>
                        <td className="border-b border-[#edf2ef] px-3">
                          <input
                            className={inputClass}
                            disabled={stocktake.status !== "COUNTING" || !canOperate}
                            min="0"
                            onChange={(event) =>
                              updateDraft(line.id, { quantity: event.currentTarget.value })
                            }
                            step="any"
                            type="number"
                            value={draft?.quantity ?? ""}
                          />
                        </td>
                        <td
                          className={`border-b border-[#edf2ef] px-3 font-black ${!hideExpected && variance && variance < 0 ? "text-red-700" : !hideExpected && variance && variance > 0 ? "text-emerald-700" : ""}`}
                        >
                          {hideExpected || variance === null ? "-" : formatVietnamNumber(variance)}
                        </td>
                        <td className="border-b border-[#edf2ef] px-3">
                          <input
                            className={inputClass}
                            disabled={stocktake.status !== "COUNTING" || !canOperate}
                            onChange={(event) =>
                              updateDraft(line.id, { reasonCode: event.currentTarget.value })
                            }
                            placeholder="Lý do chênh lệch"
                            value={draft?.reasonCode ?? ""}
                          />
                        </td>
                        <td className="border-b border-[#edf2ef] px-3">
                          <input
                            className={inputClass}
                            disabled={stocktake.status !== "COUNTING" || !canOperate}
                            onChange={(event) =>
                              updateDraft(line.id, { note: event.currentTarget.value })
                            }
                            placeholder="Ghi chú"
                            value={draft?.note ?? ""}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {["APPROVED", "POSTED", "COMPLETED"].includes(stocktake.status) ? (
            <AdjustmentLinkCard stocktake={stocktake} />
          ) : null}
        </>
      ) : null}
      {dialog}
    </section>
  );
}

function AdjustmentLinkCard({ stocktake }: { stocktake: StocktakeDetail }) {
  const adjustment = stocktake.adjustmentDocument;
  return (
    <section className="flex flex-col gap-4 rounded-xl border border-[#dce5e1] bg-white p-5 shadow-[0_8px_24px_rgba(31,53,43,0.05)] md:flex-row md:items-center md:justify-between">
      <div className="flex gap-3">
        <span className="grid size-10 place-items-center rounded-full bg-[#eaf8ee] text-[#159734]">
          <ClipboardCheck size={20} />
        </span>
        <div>
          <h2 className="font-black">Phiếu điều chỉnh tồn kho</h2>
          <p className="text-sm text-[#697684]">
            {adjustment
              ? adjustment.documentCode
              : stocktake.varianceLines
                ? `${stocktake.varianceLines} dòng chênh lệch cần xử lý.`
                : "Không có chênh lệch cần điều chỉnh."}
          </p>
        </div>
      </div>
      {stocktake.varianceLines > 0 || adjustment ? (
        <Link
          className={primaryButton}
          to={`/management/inventory/stocktakes/${stocktake.id}/adjustment`}
        >
          {adjustment ? "Xem phiếu điều chỉnh" : "Xử lý điều chỉnh"}
        </Link>
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
function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
const primaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white hover:bg-[#12862d] disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#536171] disabled:opacity-50";
const dangerButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-red-200 bg-white px-4 text-sm font-bold text-red-700 hover:bg-red-50 disabled:opacity-50";
const inputClass =
  "h-9 w-full min-w-24 rounded-lg border border-[#dce5e1] bg-white px-2 text-sm font-semibold outline-none focus:border-[#159734] disabled:bg-[#f4f6f5]";
