import {
  Beaker,
  BookmarkCheck,
  CheckCircle2,
  Eye,
  GitCompare,
  Sparkles,
  X,
} from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "../../../components/ui/Button";
import { formatVietnamNumber } from "../../inventory/utils/inventory-format";
import type {
  AiFormulaProposal,
  ProposalStatus,
} from "../types/product-formula-analysis.types";

const STATUS_BADGE: Record<ProposalStatus, { label: string; className: string }> = {
  NEW: {
    label: "Mới đề xuất",
    className: "border-[#b8e2c2] bg-[#edf8ef] text-[#159734]",
  },
  SAVED: {
    label: "Đã lưu đề xuất",
    className: "border-sky-200 bg-sky-50 text-sky-700",
  },
  TESTING: {
    label: "Đang thử nghiệm",
    className: "border-amber-200 bg-amber-50 text-amber-800",
  },
  APPROVED: {
    label: "Đã gửi phê duyệt",
    className: "border-emerald-300 bg-emerald-100 text-emerald-800",
  },
};

export function AiFormulaProposalSection({
  proposals,
  onChangeStatus,
}: {
  proposals: AiFormulaProposal[];
  onChangeStatus: (id: string, status: ProposalStatus) => void;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [modalMode, setModalMode] = useState<"detail" | "compare">("detail");

  const selected = useMemo(
    () => proposals.find((p) => p.id === selectedId) ?? null,
    [proposals, selectedId],
  );

  const openModal = (id: string, mode: "detail" | "compare") => {
    setSelectedId(id);
    setModalMode(mode);
  };

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#cde8d4] bg-gradient-to-r from-[#f3fbf5] via-white to-[#f7faf8] p-4 shadow-pos-card">
        <div className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-[#159734] text-white shadow-sm">
            <Sparkles size={20} aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-base font-black text-[#172317]">
              AI Recommend công thức sản xuất mới
            </h2>
            <p className="text-xs font-semibold text-[#536078]">
              Đề xuất công thức mới dựa trên sản lượng bán sản phẩm, công thức BOM hiện tại và tần suất nguyên liệu bán chạy.
            </p>
          </div>
        </div>
        <span className="rounded-full border border-[#b8e2c2] bg-white px-3 py-1 text-xs font-black text-[#159734]">
          {proposals.length} công thức đề xuất
        </span>
      </div>

      {proposals.length === 0 ? (
        <div className="rounded-xl border border-[#e8eef0] bg-white p-8 text-center text-sm font-semibold text-[#697684]">
          Chưa có dữ liệu sản phẩm và công thức BOM phù hợp bộ lọc để AI đề xuất.
        </div>
      ) : (
        <div className="grid gap-4">
          {proposals.map((proposal) => {
            const badge = STATUS_BADGE[proposal.status];
            return (
              <article
                key={proposal.id}
                className="grid gap-4 rounded-xl border border-[#e8eef0] bg-white p-5 shadow-pos-card"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="grid gap-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded bg-[#edf8ef] px-2 py-0.5 text-xs font-black text-[#159734]">
                        {proposal.code}
                      </span>
                      <h3 className="text-base font-black text-[#172317]">
                        {proposal.proposedName}
                      </h3>
                      <span
                        className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${badge.className}`}
                      >
                        {badge.label}
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs font-semibold text-[#536078]">
                      <span>
                        Sản phẩm tham chiếu:{" "}
                        <strong className="text-[#172317]">{proposal.baseProductName}</strong>
                      </span>
                      <span>
                        Công thức BOM tham chiếu:{" "}
                        <strong className="text-[#172317]">{proposal.referenceFormulaName}</strong>
                      </span>
                    </div>
                  </div>
                </div>

                <div className="grid gap-2 rounded-lg border border-[#edf2f5] bg-[#f9fbfc] p-3 text-xs text-[#334155]">
                  <div>
                    <strong className="font-black text-[#172317]">Lý do AI đề xuất: </strong>
                    {proposal.reason}
                  </div>
                  <div>
                    <strong className="font-black text-[#172317]">
                      Điểm khác biệt so với công thức hiện tại:{" "}
                    </strong>
                    {proposal.keyDifferences.join(" ")}
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full min-w-[480px] border-collapse text-left text-xs">
                    <thead>
                      <tr className="border-b border-[#e8eef0] text-[#697684]">
                        <th className="py-2 pr-3 font-bold">Mã NL</th>
                        <th className="py-2 pr-3 font-bold">Nguyên liệu đề xuất</th>
                        <th className="py-2 pr-3 text-right font-bold">Định mức</th>
                        <th className="py-2 pr-3 text-right font-bold">Tỷ lệ (%)</th>
                        <th className="py-2 font-bold">Ghi chú thay đổi</th>
                      </tr>
                    </thead>
                    <tbody>
                      {proposal.proposedIngredients.map((ing) => (
                        <tr
                          key={`${proposal.id}-${ing.ingredientCode}`}
                          className="border-b border-[#f1f5f9]"
                        >
                          <td className="py-2 pr-3 font-bold text-[#536078]">
                            {ing.ingredientCode}
                          </td>
                          <td className="py-2 pr-3 font-semibold text-[#172317]">
                            {ing.ingredientName}
                          </td>
                          <td className="py-2 pr-3 text-right font-bold tabular-nums">
                            {formatVietnamNumber(ing.quantity)} {ing.unitSymbol}
                          </td>
                          <td className="py-2 pr-3 text-right font-black tabular-nums text-[#159734]">
                            {ing.ratioPercent}%
                          </td>
                          <td className="py-2 text-[#536078]">{ing.diffNote || "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#edf2f5] pt-3">
                  <div className="flex flex-wrap gap-2">
                    <Button onClick={() => openModal(proposal.id, "detail")}>
                      <Eye size={14} aria-hidden="true" />
                      Xem chi tiết
                    </Button>
                    <Button onClick={() => openModal(proposal.id, "compare")}>
                      <GitCompare size={14} aria-hidden="true" />
                      So sánh với công thức gốc
                    </Button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      disabled={proposal.status === "SAVED"}
                      onClick={() => onChangeStatus(proposal.id, "SAVED")}
                    >
                      <BookmarkCheck size={14} aria-hidden="true" />
                      Lưu đề xuất
                    </Button>
                    <Button
                      disabled={proposal.status === "TESTING"}
                      onClick={() => onChangeStatus(proposal.id, "TESTING")}
                    >
                      <Beaker size={14} aria-hidden="true" />
                      Gửi thử nghiệm
                    </Button>
                    <Button
                      disabled={proposal.status === "APPROVED"}
                      onClick={() => onChangeStatus(proposal.id, "APPROVED")}
                      variant="primary"
                    >
                      <CheckCircle2 size={14} aria-hidden="true" />
                      Gửi phê duyệt
                    </Button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {selected ? (
        <div
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
          role="dialog"
        >
          <div className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-[#e2e8f0] bg-white p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-[#edf2f5] pb-4">
              <div>
                <h3 className="text-lg font-black text-[#172317]">{selected.proposedName}</h3>
                <p className="text-xs font-semibold text-[#697684]">
                  Tham chiếu: {selected.baseProductName} • {selected.referenceFormulaName}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedId(null)}
                className="rounded-lg p-1.5 text-[#697684] hover:bg-[#f1f5f9]"
                aria-label="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            <div className="mt-4 flex gap-2 border-b border-[#edf2f5] pb-3">
              <button
                type="button"
                onClick={() => setModalMode("detail")}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold ${
                  modalMode === "detail"
                    ? "bg-[#159734] text-white"
                    : "bg-[#f4f7f9] text-[#536078]"
                }`}
              >
                Chi tiết công thức đề xuất
              </button>
              <button
                type="button"
                onClick={() => setModalMode("compare")}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold ${
                  modalMode === "compare"
                    ? "bg-[#159734] text-white"
                    : "bg-[#f4f7f9] text-[#536078]"
                }`}
              >
                So sánh với công thức gốc
              </button>
            </div>

            {modalMode === "detail" ? (
              <div className="mt-4 grid gap-4 text-sm">
                <p className="rounded-lg bg-[#f8fafc] p-3 text-xs leading-relaxed text-[#334155]">
                  <strong className="font-black text-[#172317]">Lý do AI đề xuất: </strong>
                  {selected.reason}
                </p>
                <ul className="grid list-disc gap-1 pl-5 text-xs text-[#334155]">
                  {selected.keyDifferences.map((diff) => (
                    <li key={diff}>{diff}</li>
                  ))}
                </ul>
              </div>
            ) : (
              <div className="mt-4 grid gap-4 md:grid-cols-2">
                <div className="rounded-xl border border-[#e2e8f0] p-4">
                  <h4 className="mb-3 text-sm font-black text-[#536078]">
                    Công thức BOM gốc ({selected.referenceFormulaName})
                  </h4>
                  <ul className="grid gap-2 text-xs">
                    {selected.referenceIngredients.map((ing) => (
                      <li
                        key={ing.ingredientCode}
                        className="flex items-center justify-between border-b border-[#f1f5f9] pb-1.5"
                      >
                        <span className="font-semibold text-[#172317]">
                          {ing.ingredientName} ({ing.ingredientCode})
                        </span>
                        <span className="font-bold tabular-nums">
                          {formatVietnamNumber(ing.quantity)} {ing.unitSymbol} ({ing.ratioPercent}%)
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="rounded-xl border border-[#b8e2c2] bg-[#f8fdf9] p-4">
                  <h4 className="mb-3 text-sm font-black text-[#159734]">
                    Công thức AI đề xuất
                  </h4>
                  <ul className="grid gap-2 text-xs">
                    {selected.proposedIngredients.map((ing) => (
                      <li
                        key={ing.ingredientCode}
                        className="flex items-center justify-between border-b border-[#e2f2e6] pb-1.5"
                      >
                        <div>
                          <span className="font-bold text-[#172317]">
                            {ing.ingredientName} ({ing.ingredientCode})
                          </span>
                          {ing.diffNote ? (
                            <p className="text-[11px] text-[#536078]">{ing.diffNote}</p>
                          ) : null}
                        </div>
                        <span className="font-bold tabular-nums text-[#159734]">
                          {formatVietnamNumber(ing.quantity)} {ing.unitSymbol} ({ing.ratioPercent}%)
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
