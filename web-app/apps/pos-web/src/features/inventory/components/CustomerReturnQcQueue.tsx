import { useEffect, useState } from "react";

import type { TimeRange } from "../../../components/shared/TimeHierarchyFilter.utils";
import orderStyles from "../../order/pages/OrderListPage.module.css";
import { ReturnInspectionModal } from "../../order/components/ReturnInspectionModal";
import { OrderStatusBadge } from "../../order/components/OrderStatusBadge";
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
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import styles from "../pages/CustomerReturnListPage.module.css";
import { ListFilterBar, ListMessage, ListPagination } from "./CustomerReturnListUi";

const PAGE_SIZE = 10;
const STATUS_OPTIONS = [
    { label: "Tất cả", value: "ALL" },
    { label: "Chờ nhận hàng", value: "CREATED" },
    { label: "Đã nhận hàng", value: "RECEIVED" },
    { label: "Đã hoàn tất", value: "COMPLETED" },
    { label: "Đã hủy", value: "CANCELLED" }
];

export function CustomerReturnQcQueue({ onInspectionCreated, onPageChange, page, timeRange }: { onInspectionCreated: () => void; onPageChange: (page: number) => void; page: number; timeRange: TimeRange | null }) {
    const [returnCode, setReturnCode] = useState("");
    const [orderCode, setOrderCode] = useState("");
    const [status, setStatus] = useState("ALL");
    const debouncedReturnCode = useDebouncedValue(returnCode.trim());
    const debouncedOrderCode = useDebouncedValue(orderCode.trim());
    const [reloadKey, setReloadKey] = useState(0);
    const [rows, setRows] = useState<OrderReturnListRow[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [selectedReturn, setSelectedReturn] = useState<OrderReturnListRow | null>(null);
    const [qcSummary, setQcSummary] = useState<CustomerReturnQcSummaryResponse | null>(null);
    const [selectionLoading, setSelectionLoading] = useState(false);
    const [selectionError, setSelectionError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        if (returnCode.trim() !== debouncedReturnCode || orderCode.trim() !== debouncedOrderCode) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            getOrderReturnList({
                fromDate: timeRange?.fromDate,
                orderCode: debouncedOrderCode,
                page,
                paged: true,
                returnCode: debouncedReturnCode,
                size: PAGE_SIZE,
                sort: "createdAt,desc",
                status: status === "ALL" ? undefined : status,
                toDate: timeRange?.toDate
            })
                .then((response) => {
                    if (!active) return;
                    setRows(response.content ?? []);
                    setTotalPages(response.totalPages ?? 0);
                    setTotalElements(response.totalElements ?? 0);
                })
                .catch((loadError: unknown) => {
                    if (active) setError(getReturnErrorMessage(loadError));
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });
        return () => { active = false; };
    }, [page, reloadKey, status, returnCode, orderCode, debouncedReturnCode, debouncedOrderCode, timeRange?.fromDate, timeRange?.toDate]);

    const resetFilters = () => {
        setReturnCode("");
        setOrderCode("");
        setStatus("ALL");
        onPageChange(0);
    };

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
        <section className={styles.section}>
            <div className={styles.sectionHeading}>
                <div>
                    <h2 className={styles.sectionTitle}>Phiếu hoàn hàng</h2>
                    <p className={styles.sectionDescription}>Phiếu đã nhận hàng có thể bắt đầu kiểm tra, kể cả khi chưa có phiếu kiểm định.</p>
                </div>
                <button className={orderStyles.secondaryButton} onClick={reload} type="button">Tải lại</button>
            </div>
            <ListFilterBar
                onOrderCodeChange={(value) => { setOrderCode(value); onPageChange(0); }}
                onReset={resetFilters}
                onReturnCodeChange={(value) => { setReturnCode(value); onPageChange(0); }}
                onStatusChange={(value) => { setStatus(value); onPageChange(0); }}
                orderCode={orderCode}
                returnCode={returnCode}
                status={status}
                statusOptions={STATUS_OPTIONS}
            />
            {notice ? <div className={styles.notice}>{notice}</div> : null}
            {error ? <div className={styles.error}>{error}</div> : null}
            <div className={orderStyles.tableCard}>
                <div className={orderStyles.tableWrap}>
                    <table className={`${orderStyles.orderTable} ${styles.dataTable} ${styles.returnTable}`}>
                        <thead><tr><th>Phiếu hoàn</th><th>Đơn hàng</th><th>Trạng thái</th><th>Còn kiểm</th><th>Thao tác</th></tr></thead>
                        <tbody>
                            {loading ? <ListMessage colSpan={5} message="Đang tải phiếu hoàn..." /> : null}
                            {!loading && error ? <ListMessage colSpan={5} message="Không thể hiển thị phiếu hoàn hàng" /> : null}
                            {!loading && !error && rows.map((row, index) => {
                                const remaining = row.remainingQcQuantity == null ? null : toReturnNumber(row.remainingQcQuantity);
                                const canInspect = row.status === "RECEIVED" && remaining !== 0 && !row.isPendingDisposition;
                                return (
                                    <tr className={index % 2 === 0 ? orderStyles.zebraFirstRow : orderStyles.zebraSecondRow} key={row.id}>
                                        <td className={orderStyles.code}>{row.returnCode ?? "Chưa có mã phiếu"}</td>
                                        <td>{row.orderCode ?? "--"}</td>
                                        <td><OrderStatusBadge
                                            label={row.isPendingDisposition ? "Đang tạo phiếu kiểm định" : row.status === "RECEIVED" ? "Đã nhận hàng" : row.status === "CREATED" ? "Chờ nhận hàng" : row.status === "COMPLETED" ? "Đã hoàn tất" : "Đã hủy"}
                                            status={row.isPendingDisposition || row.status === "RECEIVED" ? "PROCESSING" : row.status === "CREATED" ? "PENDING" : row.status}
                                        /></td>
                                        <td>{remaining == null ? "Chưa xác định" : formatReturnQuantity(remaining)}</td>
                                        <td>
                                            {canInspect ? (
                                                <button className={styles.actionButton} onClick={() => setSelectedId(row.id)} type="button">Xử lý kho</button>
                                            ) : row.isPendingDisposition ? "Chờ xử lý lệnh" : "--"}
                                        </td>
                                    </tr>
                                );
                            })}
                            {!loading && !error && rows.length === 0 ? <ListMessage colSpan={5} message="Không có phiếu hoàn hàng phù hợp" /> : null}
                        </tbody>
                    </table>
                </div>
                {!loading && !error ? <ListPagination itemLabel="phiếu hoàn" onPageChange={onPageChange} page={page} pageSize={PAGE_SIZE} totalItems={totalElements} totalPages={totalPages} /> : null}
            </div>
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
