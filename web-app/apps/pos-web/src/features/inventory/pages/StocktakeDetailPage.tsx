import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { StocktakeRoleSwitch } from "../components/StocktakeComponents";
import { adjustmentStatusLabels, stocktakeStatusLabels } from "../constants/stocktake.constants";
import { stocktakeApi } from "../services/stocktake.api";
import type { StocktakeDetail, StocktakeRole } from "../types/stocktake.types";
import { formatVietnamDateTime, formatVietnamNumber } from "../utils/inventory-format";

function readRole(): StocktakeRole {
    const stored = window.localStorage.getItem("hvt.stocktake.role");
    return stored && ["WAREHOUSE", "BRANCH_MANAGER", "ADMIN", "DIRECTOR", "ACCOUNTANT", "SALES_STAFF"].includes(stored) ? stored as StocktakeRole : "WAREHOUSE";
}

export function StocktakeDetailPage() {
    const { stocktakeId } = useParams();
    const [role, setRole] = useState<StocktakeRole>(readRole);
    const [stocktake, setStocktake] = useState<StocktakeDetail | null>(null);

    useEffect(() => {
        if (!stocktakeId) return;
        void stocktakeApi.getStocktake(stocktakeId).then(setStocktake);
    }, [stocktakeId]);

    if (!stocktakeId) return <Navigate to="/management/inventory/stocktakes" replace />;

    return (
        <section className="grid gap-4">
            <header className="flex items-end justify-between gap-3">
                <div className="grid gap-1">
                    <Link className="text-sm font-black text-[#159734]" to="/management/inventory/stocktakes">Quay lại danh sách</Link>
                    <h1 className="text-2xl font-black text-[#172317]">{stocktake?.countCode ?? "Chi tiết kiểm kê"}</h1>
                    <p className="text-sm font-semibold text-[#697684]">{stocktake?.branch.name ?? ""}</p>
                </div>
                <StocktakeRoleSwitch onChange={setRole} value={role} />
            </header>

            {stocktake ? (
                <section className="grid gap-4">
                    <div className="grid gap-3 rounded-xl border border-[#e8eef0] bg-white p-4 shadow-pos-card md:grid-cols-4">
                        <Info label="Trạng thái" value={stocktakeStatusLabels[stocktake.status]} />
                        <Info label="Phạm vi" value={stocktake.scopeLabel} />
                        <Info label="Tổng dòng" value={formatVietnamNumber(stocktake.totalLines)} />
                        <Info label="Đã đếm" value={formatVietnamNumber(stocktake.countedLines)} />
                        <Info label="Variance line" value={formatVietnamNumber(stocktake.varianceLines)} />
                        <Info label="Snapshot" value={stocktake.snapshotAt ? formatVietnamDateTime(stocktake.snapshotAt) : "-"} />
                        <Info label="Bắt đầu đếm" value={stocktake.countingStartedAt ? formatVietnamDateTime(stocktake.countingStartedAt) : "-"} />
                        <Info label="Hoàn tất đếm" value={stocktake.countingCompletedAt ? formatVietnamDateTime(stocktake.countingCompletedAt) : "-"} />
                        <Info label="Adjustment" value={stocktake.adjustmentDocument ? adjustmentStatusLabels[stocktake.adjustmentDocument.status] : "Chưa tạo"} />
                        <Info label="Ghi chú" value={stocktake.note ?? "-"} />
                    </div>

                    <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                        <table className="w-full min-w-[980px] text-left text-sm">
                            <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                                <tr>
                                    <th className="px-4 py-3">Item</th>
                                    <th>Vị trí</th>
                                    <th>Batch</th>
                                    <th>Snapshot</th>
                                    <th>Counted</th>
                                    <th>Variance</th>
                                    <th>Reason</th>
                                </tr>
                            </thead>
                            <tbody>
                                {stocktake.lines.map((line) => (
                                    <tr key={line.id}>
                                        <td className="border-t border-[#edf0f4] px-4 py-3">{line.inventoryItemCode} · {line.inventoryItemName}</td>
                                        <td className="border-t border-[#edf0f4]">{line.locationName}</td>
                                        <td className="border-t border-[#edf0f4]">{line.batchCode ?? "-"}</td>
                                        <td className="border-t border-[#edf0f4]">{formatVietnamNumber(line.snapshotQuantity)}</td>
                                        <td className="border-t border-[#edf0f4]">{line.countedQuantity === null ? "-" : formatVietnamNumber(line.countedQuantity)}</td>
                                        <td className="border-t border-[#edf0f4]">{line.varianceQuantity === null ? "-" : formatVietnamNumber(line.varianceQuantity)}</td>
                                        <td className="border-t border-[#edf0f4]">{line.reasonCode ?? "-"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>
            ) : (
                <div className="rounded-xl border border-[#e8eef0] bg-white p-6 text-sm font-bold shadow-pos-card">Đang tải...</div>
            )}
        </section>
    );
}

function Info({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 rounded-lg bg-[#f7f8fb] px-3 py-2">
            <span className="text-xs font-black text-[#697684]">{label}</span>
            <strong className="text-sm font-black text-[#172317]">{value}</strong>
        </div>
    );
}
