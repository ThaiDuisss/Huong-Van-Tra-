import {
  ArrowLeft,
  CheckCircle2,
  ClipboardCheck,
  LoaderCircle,
  RefreshCw,
  Send,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { StatusBadge } from "../components/StocktakeComponents";
import { useStocktakeActionDialog } from "../components/useStocktakeActionDialog";
import { adjustmentStatusLabels } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeDetail } from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

const operationRoles = ["ADMIN", "MANAGER", "INVENTORY_STAFF", "MANAGER"];
const approvalRoles = ["ADMIN", "MANAGER", "MANAGER"];

export function StocktakeAdjustmentPage() {
  const { stocktakeId } = useParams();
  const { roles } = useAuth();
  const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState("");
  const [error, setError] = useState("");
  const { ask, dialog } = useStocktakeActionDialog();

  const load = useCallback(async () => {
    if (!stocktakeId) return;
    setLoading(true);
    setError("");
    try {
      setStocktake(await stocktakeApi.getStocktake(stocktakeId));
    } catch (loadError) {
      setError(messageOf(loadError, "Không thể tải phiếu điều chỉnh."));
    } finally {
      setLoading(false);
    }
  }, [stocktakeId]);

  useEffect(() => {
    const handle = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  const varianceLines = useMemo(
    () => stocktake?.lines.filter((line) => (line.varianceQuantity ?? 0) !== 0) ?? [],
    [stocktake],
  );
  const varianceValue = useMemo(
    () => varianceLines.reduce((total, line) => total + Math.abs(line.varianceValue), 0),
    [varianceLines],
  );
  const canOperate = hasAnyRole(roles, operationRoles);
  const canApprove = hasAnyRole(roles, approvalRoles);
  const adjustment = stocktake?.adjustmentDocument;

  if (!stocktakeId) return <Navigate replace to="/management/inventory/stocktakes" />;

  async function runAction(name: string, action: () => Promise<StocktakeDetail>) {
    setPendingAction(name);
    setError("");
    try {
      setStocktake(await action());
    } catch (actionError) {
      setError(messageOf(actionError, "Không thể thực hiện thao tác với phiếu điều chỉnh."));
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
            to={`/management/inventory/stocktakes/${stocktakeId}`}
          >
            <ArrowLeft size={18} />
          </Link>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold sm:text-3xl">Phiếu điều chỉnh tồn kho</h1>
              {stocktake ? <StatusBadge status={stocktake.status} /> : null}
            </div>
            <p className="mt-1 text-sm text-gray-500">
              Từ phiên kiểm kê {stocktake?.countCode ?? "..."} ·{" "}
              {stocktake?.branch.name ?? "Đang tải"}
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
          {stocktake?.status === "APPROVED" &&
          !adjustment &&
          varianceLines.length > 0 &&
          canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={() =>
                void runAction("create", () =>
                  stocktakeApi.createAdjustmentFromStocktake({
                    branchId: stocktake.branch.id,
                    inventoryTakeId: stocktake.id,
                    note: "Điều chỉnh từ kiểm kê",
                    reasonCode: "STOCK_COUNT",
                  }),
                )
              }
              type="button"
            >
              <ClipboardCheck size={17} /> Tạo phiếu
            </button>
          ) : null}
          {adjustment?.status === "DRAFT" && canApprove ? (
            <>
              <button
                className={dangerButton}
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const confirmed = await ask({
                    title: "Từ chối phiếu điều chỉnh",
                    description:
                      "Phiếu sẽ bị từ chối và kỳ kiểm kê quay lại bước kiểm tra chênh lệch.",
                    confirmLabel: "Từ chối",
                    tone: "danger",
                  });
                  if (confirmed !== null)
                    void runAction("reject", () =>
                      stocktakeApi.rejectStocktakeAdjustment({
                        adjustmentId: adjustment.id,
                        stocktakeId,
                      }),
                    );
                }}
                type="button"
              >
                <XCircle size={17} /> Từ chối
              </button>
              <button
                className={primaryButton}
                disabled={Boolean(pendingAction)}
                onClick={async () => {
                  const confirmed = await ask({
                    title: "Duyệt phiếu điều chỉnh",
                    description: "Phiếu sau khi duyệt sẽ sẵn sàng cập nhật vào tồn kho.",
                    confirmLabel: "Duyệt phiếu",
                  });
                  if (confirmed !== null)
                    void runAction("approve", () =>
                      stocktakeApi.approveStocktakeAdjustment({
                        adjustmentId: adjustment.id,
                        stocktakeId,
                      }),
                    );
                }}
                type="button"
              >
                <CheckCircle2 size={17} /> Duyệt phiếu
              </button>
            </>
          ) : null}
          {adjustment?.status === "APPROVED" && canOperate ? (
            <button
              className={primaryButton}
              disabled={Boolean(pendingAction)}
              onClick={async () => {
                const confirmed = await ask({
                  title: "Cập nhật tồn kho",
                  description: "Số lượng tồn sẽ được cập nhật theo kết quả kiểm kê đã duyệt.",
                  confirmLabel: "Cập nhật tồn kho",
                });
                if (confirmed !== null)
                  void runAction("post", () =>
                    stocktakeApi.postStocktakeAdjustment({
                      adjustmentId: adjustment.id,
                      stocktakeId,
                    }),
                  );
              }}
              type="button"
            >
              <Send size={17} /> Cập nhật tồn kho
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
            <Info label="Mã phiếu" value={adjustment?.documentCode ?? "Chưa tạo"} />
            <Info
              label="Trạng thái"
              value={adjustment ? adjustmentStatusLabels[adjustment.status] : "Chờ tạo phiếu"}
            />
            <Info label="Số mặt hàng chênh lệch" value={String(varianceLines.length)} />
            <Info label="Giá trị chênh lệch" value={`${formatVietnamNumber(varianceValue)} ₫`} />
            <Info
              label="Thời gian cập nhật tồn kho"
              value={
                adjustment?.postedAt ? formatVietnamDateTime(adjustment.postedAt) : "Chưa cập nhật"
              }
            />
          </section>
          {stocktake.status !== "APPROVED" && !adjustment ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800">
              Chỉ có thể tạo phiếu sau khi kết quả kiểm kê được phê duyệt.
            </div>
          ) : null}
          {stocktake.status === "APPROVED" && varianceLines.length === 0 ? (
            <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
              Phiên kiểm kê không có chênh lệch nên không cần tạo phiếu điều chỉnh.
            </div>
          ) : null}
          <section className="overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <header className="border-b border-[#e4ece8] p-4">
              <h2 className="font-black">Chi tiết chênh lệch</h2>
              <p className="text-xs text-[#697684]">
                Số tồn cần đối chiếu đã bao gồm các giao dịch nhập, xuất hoặc chuyển kho phát sinh
                trong lúc kiểm kê.
              </p>
            </header>
            <div className="overflow-auto">
              <table className="w-full min-w-[1100px] text-left text-sm">
                <thead className="bg-[#f3f7f4] text-xs font-black uppercase text-[#536171]">
                  <tr>
                    {[
                      "Mặt hàng",
                      "Vị trí",
                      "Tồn lúc bắt đầu",
                      "Phát sinh khi kiểm kê",
                      "Tồn cần đối chiếu",
                      "Số lượng thực tế",
                      "Chênh lệch cần cập nhật",
                      "Giá trị chênh lệch",
                      "Lý do",
                    ].map((label) => (
                      <th className="border-b border-[#dce5e1] px-3 py-3" key={label}>
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {varianceLines.map((line) => (
                    <tr key={line.id}>
                      <td className="border-b border-[#edf2ef] px-3 py-3">
                        <strong className="block">{line.inventoryItemCode}</strong>
                        <span className="text-xs text-[#697684]">{line.inventoryItemName}</span>
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">{line.locationName}</td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.snapshotQuantity)}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.movementQuantity)}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.expectedQuantity)}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.countedQuantity ?? 0)}
                      </td>
                      <td
                        className={`border-b border-[#edf2ef] px-3 font-black ${(line.varianceQuantity ?? 0) < 0 ? "text-red-700" : "text-emerald-700"}`}
                      >
                        {(line.varianceQuantity ?? 0) > 0 ? "+" : ""}
                        {formatVietnamNumber(line.varianceQuantity ?? 0)} {line.unit}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3 font-bold">
                        {formatVietnamNumber(Math.abs(line.varianceValue))} ₫
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">{line.reasonCode ?? "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      ) : null}
      {dialog}
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
