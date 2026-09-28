import { useEffect, useState } from "react";

import { ReturnInspectionModal } from "../../order/components/ReturnInspectionModal";
import {
    getCustomerReturnQcSummary,
    getOrderReturnDetail,
    getOrderReturnList
} from "../../order/services/order-return.api";
import type {
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow
} from "../../order/types/order-return.types";
import {
    formatReturnQuantity,
    getReturnErrorMessage,
    toReturnNumber
} from "../../order/utils/order-return.utils";

const PAGE_SIZE = 20;

export function CustomerReturnQcQueue({ onInspectionCreated }: { onInspectionCreated: () => void }) {
    const [page, setPage] = useState(0);
    const [reloadKey, setReloadKey] = useState(0);
    const [rows, setRows] = useState<OrderReturnListRow[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [selectedReturn, setSelectedReturn] = useState<OrderReturnListRow | null>(null);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [selectionLoading, setSelectionLoading] = useState(false);
    const [selectionError, setSelectionError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            getOrderReturnList({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
                .then((response) => {
                    if (!active) return;
                    setRows(response.content ?? []);
                    setTotalPages(response.totalPages ?? 0);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });
        return () => { active = false; };
    }, [page, reloadKey]);

    useEffect(() => {
        if (!selectedId) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setSelectionLoading(true);
            setSelectionError("");
            setSelectedReturn(null);
            setQcSummary(null);
            Promise.all([
                getOrderReturnDetail(selectedId),
                getCustomerReturnQcSummary(selectedId)
            ])
                .then(([detail, summary]) => {
                    if (!active) return;
                    if (detail.status !== "RECEIVED" || summary.returnStatus !== "RECEIVED") {
                        setSelectionError("Phiếu hoàn không còn ở trạng thái đã nhận hàng. Vui lòng tải lại danh sách.");
                        return;
                    }
                    if (!(summary.lines ?? []).some((line) => toReturnNumber(line.remainingQcQuantity) > 0)) {
                        setSelectionError("Phiếu hoàn không còn số lượng cần xử lý kho. Vui lòng tải lại danh sách.");
                        return;
                    }
                    setSelectedReturn(detail);
                    setQcSummary(summary);
                })
                .catch((loadError: unknown) => {
                    if (active) setSelectionError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setSelectionLoading(false);
                });
        });
        return () => { active = false; };
    }, [selectedId]);

    const reload = () => {
        setReloadKey((key) => key + 1);
        onInspectionCreated();
    };
    const closeSelection = () => {
        setSelectedId(null);
        setSelectedReturn(null);
        setQcSummary(null);
        setSelectionError("");
    };

    return (
        <section className="grid gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                    <h2 className="text-lg font-black text-[#172317]">Phiếu hoàn hàng</h2>
                    <p className="text-sm font-semibold text-[#697684]">Phiếu đã nhận hàng có thể bắt đầu kiểm tra, kể cả khi chưa có phiếu kiểm định.</p>
                </div>
                <button className="h-9 rounded-lg border border-[#d8eadc] px-3 text-sm font-bold" onClick={reload} type="button">Tải lại</button>
            </div>
            {notice ? <div className="rounded-lg bg-green-50 p-3 text-sm font-semibold text-green-800">{notice}</div> : null}
            {loading ? <div className="rounded-xl bg-white p-5 text-sm font-semibold">Đang tải phiếu hoàn...</div> : null}
            {!loading && error ? <div className="rounded-xl bg-red-50 p-5 text-sm font-semibold text-red-700">{error}</div> : null}
            {!loading && !error ? (
                <div className="overflow-x-auto rounded-xl border border-[#e8eef0] bg-white shadow-pos-card">
                    <table className="w-full min-w-[760px] text-left text-sm">
                        <thead className="bg-[#eaf7ec] text-xs font-black uppercase text-[#172317]">
                            <tr><th className="px-4 py-3">Phiếu hoàn</th><th>Đơn hàng</th><th>Trạng thái</th><th>Còn kiểm</th><th>Thao tác</th></tr>
                        </thead>
                        <tbody>
                            {rows.map((row) => {
                                const remaining = row.remainingQcQuantity == null ? null : toReturnNumber(row.remainingQcQuantity);
                                const canInspect = row.status === "RECEIVED" && remaining !== 0 && !row.isPendingDisposition;
                                return (
                                    <tr className="border-t border-[#edf0f4]" key={row.id}>
                                        <td className="px-4 py-3 font-black">{row.returnCode ?? "Chưa có mã phiếu"}</td>
                                        <td>{row.orderCode ?? "--"}</td>
                                        <td>{row.isPendingDisposition ? "Đang tạo phiếu kiểm định" : row.status === "RECEIVED" ? "Đã nhận hàng" : row.status === "CREATED" ? "Chờ nhận hàng" : row.status === "COMPLETED" ? "Đã hoàn tất" : "Đã hủy"}</td>
                                        <td>{remaining == null ? "Chưa xác định" : formatReturnQuantity(remaining)}</td>
                                        <td>
                                            {canInspect ? (
                                                <button className="rounded-lg bg-[#159734] px-3 py-2 font-bold text-white" onClick={() => setSelectedId(row.id)} type="button">Xử lý kho</button>
                                            ) : row.isPendingDisposition ? "Chờ xử lý lệnh" : "--"}
                                        </td>
                                    </tr>
                                );
                            })}
                            {rows.length === 0 ? <tr><td className="px-4 py-5 text-[#697684]" colSpan={5}>Chưa có phiếu hoàn hàng.</td></tr> : null}
                        </tbody>
                    </table>
                </div>
            ) : null}
            {totalPages > 1 ? (
                <div className="flex items-center justify-end gap-3 text-sm font-semibold">
                    <button disabled={page === 0} onClick={() => setPage((value) => value - 1)} type="button">Trước</button>
                    <span>Trang {page + 1}/{totalPages}</span>
                    <button disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} type="button">Sau</button>
                </div>
            ) : null}
            {selectedId && (selectionLoading || selectionError) ? (
                <div className="rounded-xl border border-[#e8eef0] bg-white p-4 text-sm font-semibold">
                    <p>{selectionLoading ? "Đang tải dữ liệu kiểm hàng..." : selectionError}</p>
                    <button className="mt-2 text-[#159734]" onClick={closeSelection} type="button">Đóng</button>
                </div>
            ) : null}
            {selectedReturn && qcSummary ? (
                <ReturnInspectionModal
                    isOpen
                    onClose={closeSelection}
                    onRefresh={reload}
                    onSuccess={reload}
                    onToast={setNotice}
                    qcSummary={qcSummary}
                    returnItem={selectedReturn}
                />
            ) : null}
        </section>
    );
}
