import { ArrowLeft, CheckCircle2, LoaderCircle, RefreshCw, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import { hasAnyRole } from "../../../auth/role-utils";
import { StatusBadge } from "../components/StocktakeComponents";
import { useStocktakeActionDialog } from "../components/useStocktakeActionDialog";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeDetail } from "../types/stocktake.types";
import { formatVietnamNumber } from "../utils/inventory-format";

const managerRoles = ["ADMIN", "MANAGER", "MANAGER"];

export function StocktakeReconciliationPage() {
  const { stocktakeId } = useParams();
  const navigate = useNavigate();
  const { roles, userId } = useAuth();
  const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [filter, setFilter] = useState<"ALL" | "SHORTAGE" | "SURPLUS">("ALL");
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState("");
  const [error, setError] = useState("");
  const { ask, dialog } = useStocktakeActionDialog();
  const canManage = hasAnyRole(roles, managerRoles);

  const load = useCallback(async () => {
    if (!stocktakeId) return;
    setLoading(true);
    setError("");
    try {
      setStocktake(await stocktakeApi.getStocktake(stocktakeId));
    } catch (loadError) {
      setError(messageOf(loadError, "Không thể tải dữ liệu chênh lệch."));
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
  const visibleLines = varianceLines.filter((line) => {
    if (filter === "SHORTAGE") return (line.varianceQuantity ?? 0) < 0;
    if (filter === "SURPLUS") return (line.varianceQuantity ?? 0) > 0;
    return true;
  });
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
  const counter = stocktake?.assignments.find((assignment) => assignment.role === "COUNTER");
  const recountAssigneeId = counter?.userId ?? userId;

  if (!stocktakeId) return <Navigate replace to="/management/inventory/stocktakes" />;

  async function submitApproval() {
    if (!stocktake) return;
    const confirmed = await ask({
      confirmLabel: "Gửi duyệt",
      description: "Các kết quả chênh lệch sẽ được khóa và chuyển sang bước phê duyệt.",
      title: "Gửi quản lý duyệt",
    });
    if (confirmed === null) return;
    setPending("submit");
    setError("");
    try {
      await stocktakeApi.submitStocktakeApproval({ stocktakeId: stocktake.id });
      navigate(`/management/inventory/stocktakes/${stocktake.id}`);
    } catch (submitError) {
      setError(messageOf(submitError, "Không thể gửi quản lý duyệt."));
    } finally {
      setPending("");
    }
  }

  async function requestRecount() {
    if (!stocktake || !recountAssigneeId)
      return setError("Không xác định được người thực hiện kiểm kê lại.");
    if (!selectedIds.length) return setError("Chọn ít nhất một mặt hàng cần kiểm kê lại.");
    if (!reason.trim()) return setError("Vui lòng nhập lý do yêu cầu kiểm kê lại.");
    setPending("recount");
    setError("");
    try {
      await stocktakeApi.requestStocktakeRecount({
        assignedUserId: recountAssigneeId,
        lineIds: selectedIds,
        reason: reason.trim(),
        stocktakeId: stocktake.id,
      });
      navigate(`/management/inventory/stocktakes/${stocktake.id}`);
    } catch (recountError) {
      setError(messageOf(recountError, "Không thể yêu cầu kiểm kê lại."));
    } finally {
      setPending("");
    }
  }

  function toggleLine(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
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
              <h1 className="text-2xl font-bold sm:text-3xl">Kiểm tra chênh lệch</h1>
              {stocktake ? <StatusBadge status={stocktake.status} /> : null}
            </div>
            <p className="mt-1 text-sm text-gray-500">
              {stocktake?.countCode ?? "Đang tải..."} · {stocktake?.branch.name ?? ""}
            </p>
          </div>
        </div>
        <button
          className={secondaryButton}
          disabled={loading}
          onClick={() => void load()}
          type="button"
        >
          <RefreshCw size={17} /> Làm mới
        </button>
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
          {stocktake.status !== "RECONCILING" ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800">
              Kỳ kiểm kê không còn ở bước kiểm tra chênh lệch. Dữ liệu bên dưới chỉ dùng để xem lại.
            </div>
          ) : null}
          <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <Info label="Mặt hàng chênh lệch" value={String(varianceLines.length)} />
            <Info label="Tổng thiếu" value={formatVietnamNumber(Math.abs(shortage))} tone="red" />
            <Info label="Tổng thừa" value={formatVietnamNumber(surplus)} tone="green" />
            <Info label="Giá trị chênh lệch" value={`${formatVietnamNumber(varianceValue)} ₫`} />
            <Info label="Đã chọn kiểm kê lại" value={String(selectedIds.length)} />
          </section>

          <section className="overflow-hidden rounded-xl border border-[#dce5e1] bg-white shadow-[0_12px_32px_rgba(31,53,43,0.06)]">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4ece8] p-4">
              <div>
                <h2 className="font-black">Danh sách chênh lệch</h2>
                <p className="text-xs text-[#697684]">
                  Kiểm tra số tồn, phát sinh và nguyên nhân trước khi gửi duyệt.
                </p>
              </div>
              <div className="flex gap-2">
                {(["ALL", "SHORTAGE", "SURPLUS"] as const).map((value) => (
                  <button
                    className={`h-9 rounded-lg px-3 text-xs font-bold ${filter === value ? "bg-[#159734] text-white" : "bg-[#f1f5f2] text-[#536171]"}`}
                    key={value}
                    onClick={() => setFilter(value)}
                    type="button"
                  >
                    {value === "ALL" ? "Tất cả" : value === "SHORTAGE" ? "Thiếu" : "Thừa"}
                  </button>
                ))}
              </div>
            </header>
            <div className="overflow-auto">
              <table className="w-full min-w-[1160px] text-left text-sm">
                <thead className="bg-[#f3f7f4] text-xs font-black uppercase text-[#536171]">
                  <tr>
                    {[
                      "Chọn",
                      "Mặt hàng",
                      "Vị trí / Lô",
                      "Tồn lúc bắt đầu",
                      "Phát sinh",
                      "Tồn cần đối chiếu",
                      "Thực tế",
                      "Chênh lệch",
                      "Giá trị",
                      "Lý do",
                    ].map((label) => (
                      <th className="border-b border-[#dce5e1] px-3 py-3" key={label}>
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visibleLines.map((line) => (
                    <tr key={line.id}>
                      <td className="border-b border-[#edf2ef] px-3">
                        <input
                          checked={selectedIds.includes(line.id)}
                          disabled={stocktake.status !== "RECONCILING" || !canManage}
                          onChange={() => toggleLine(line.id)}
                          type="checkbox"
                        />
                      </td>
                      <td className="border-b border-[#edf2ef] px-3 py-3">
                        <strong className="block">{line.inventoryItemCode}</strong>
                        <span className="text-xs text-[#697684]">{line.inventoryItemName}</span>
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {line.locationName}
                        <small className="block text-[#697684]">
                          {line.batchCode ?? "Không theo lô"}
                        </small>
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.snapshotQuantity)}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(line.movementQuantity)}
                      </td>
                      <td className="border-b border-[#edf2ef] px-3 font-bold">
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
                      <td className="border-b border-[#edf2ef] px-3">
                        {formatVietnamNumber(Math.abs(line.varianceValue))} ₫
                      </td>
                      <td className="border-b border-[#edf2ef] px-3">
                        {line.reasonCode || line.note || "Chưa có"}
                      </td>
                    </tr>
                  ))}
                  {!visibleLines.length ? (
                    <tr>
                      <td className="p-8 text-center text-sm text-[#697684]" colSpan={10}>
                        Không có mặt hàng chênh lệch phù hợp.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </section>

          {stocktake.status === "RECONCILING" && canManage ? (
            <section className="grid gap-3 rounded-xl border border-[#dce5e1] bg-white p-4 lg:grid-cols-[1fr_auto] lg:items-end">
              <label className="grid gap-1.5 text-sm font-bold">
                Lý do kiểm kê lại
                <textarea
                  className="min-h-20 rounded-xl border border-[#dce5e1] px-3 py-2 text-sm font-semibold outline-none focus:border-[#159734]"
                  onChange={(event) => setReason(event.currentTarget.value)}
                  placeholder="Nhập lý do cho các mặt hàng đã chọn"
                  value={reason}
                />
              </label>
              <div className="flex flex-wrap gap-2">
                <button
                  className={secondaryButton}
                  disabled={Boolean(pending)}
                  onClick={() => void requestRecount()}
                  type="button"
                >
                  <RotateCcw size={17} /> Kiểm kê lại ({selectedIds.length})
                </button>
                <button
                  className={primaryButton}
                  disabled={Boolean(pending)}
                  onClick={() => void submitApproval()}
                  type="button"
                >
                  <CheckCircle2 size={17} /> Gửi quản lý duyệt
                </button>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
      {dialog}
    </section>
  );
}

function Info({ label, tone, value }: { label: string; tone?: "red" | "green"; value: string }) {
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
const primaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#159734] px-4 text-sm font-bold text-white disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-[#dce5e1] bg-white px-4 text-sm font-bold text-[#536171] disabled:opacity-50";
