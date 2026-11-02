import { useEffect, useState } from "react";
import { TimeSidebar } from "@/components/shared/TimeSidebar";
import { useTimeFilter } from "@/components/shared/useTimeFilter";

import { useAuthorization } from "../../../auth/useAuthorization";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import orderStyles from "../../order/pages/OrderListPage.module.css";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { DisposalTable } from "../components/QualityComponents";
import {
    ListFilterBar,
    ListMessage,
    ListPagination,
    type CustomerReturnStatusTab
} from "../components/CustomerReturnListUi";
import { qualityApi } from "../services/quality.api";
import type {
    DisposalListQuery,
    DisposalListResult,
    DisposalRequest,
    DisposalStatusCounts
} from "../types/quality.types";
import styles from "./CustomerReturnListPage.module.css";
import disposalStyles from "./DisposalListPage.module.css";
import { isCustomerReturnDisposal } from "../utils/inventory-labels";
import { ROLES } from "../../../auth/roles";

const PAGE_SIZE = 10;
const STATUS_TABS: CustomerReturnStatusTab[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "PENDING_APPROVAL", label: "Chờ xử lý" },
    { key: "APPROVED", label: "Đã xử lý" }
];

export function DisposalListPage() {
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole([ROLES.SUPER_ADMIN, ROLES.MANAGER]);
    const [searchTerm, setSearchTerm] = useState("");
    const keyword = useDebouncedValue(searchTerm.trim(), 300);
    const [status, setStatus] = useState<DisposalListQuery["status"]>("ALL");
    const [page, setPage] = useState(0);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [reloadKey, setReloadKey] = useState(0);
    const [result, setResult] = useState<DisposalListResult | null>(null);
    const [statusCounts, setStatusCounts] = useState<DisposalStatusCounts | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [countsError, setCountsError] = useState("");
    const [notice, setNotice] = useState("");
    const [confirmRequest, setConfirmRequest] = useState<DisposalRequest | null>(null);
    const [approvalError, setApprovalError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [openActionId, setOpenActionId] = useState<string | null>(null);
    const { selectedTimeNode, selectTimeNode, timeRange, timeZone } = useTimeFilter(() => {
        setOpenActionId(null);
        setPage(0);
    });

    useEffect(() => {
        if (searchTerm.trim() !== keyword) return;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void qualityApi.listDisposalRequests({
                fromDate: timeRange?.fromDate,
                toDate: timeRange?.toDate,
                keyword, page, size: PAGE_SIZE, status
            })
                .then((response) => {
                    if (!active) return;
                    setOpenActionId(null);
                    setResult(response);
                    if (page > 0 && page >= response.totalPages) {
                        setPage(Math.max(0, response.totalPages - 1));
                    }
                })
                .catch((loadError: unknown) => {
                    if (active) {
                        setError(loadError instanceof Error
                            ? loadError.message
                            : "Không thể tải danh sách yêu cầu tiêu hủy.");
                    }
                })
                .finally(() => {
                    if (active) setLoading(false);
                });
        });
        return () => { active = false; };
    }, [keyword, page, reloadKey, searchTerm, status, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setStatusCounts(null);
            setCountsError("");
            void qualityApi.getDisposalStatusCounts(keyword || undefined, timeRange?.fromDate, timeRange?.toDate)
                .then((counts) => {
                    if (active) setStatusCounts(counts);
                })
                .catch(() => {
                    if (active) setCountsError("Không thể tải số lượng yêu cầu theo trạng thái.");
                });
        });
        return () => { active = false; };
    }, [keyword, reloadKey, timeRange?.fromDate, timeRange?.toDate]);

    async function approveRequest() {
        if (!confirmRequest || isCustomerReturnDisposal(confirmRequest) || submitting || !canApprove) return;
        setSubmitting(true);
        setApprovalError("");
        try {
            await qualityApi.approveDisposalRequest({ disposalId: confirmRequest.id });
            setNotice(`Đã phê duyệt yêu cầu tiêu hủy ${confirmRequest.code}.`);
            setConfirmRequest(null);
            setReloadKey((key) => key + 1);
        } catch (approveError) {
            setApprovalError(approveError instanceof Error
                ? approveError.message
                : "Không thể phê duyệt yêu cầu tiêu hủy.");
        } finally {
            setSubmitting(false);
        }
    }

    const hasFilter = Boolean(searchTerm.trim()) || status !== "ALL" || timeRange !== null;

    return (
        <section className={`${orderStyles.page} ${isTimeCollapsed ? orderStyles.pageTimeCollapsed : ""}`}>
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={() => selectTimeNode(null)}
                onSelect={selectTimeNode}
                onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                selectedNode={selectedTimeNode}
                timeZone={timeZone}
            />
            <div className={styles.content}>
                <header className={orderStyles.header}>
                    <div className={orderStyles.title}>
                        <h1>Yêu cầu tiêu hủy</h1>
                        <p>Theo dõi yêu cầu và lịch sử xử lý tiêu hủy.</p>
                    </div>
                </header>

                <section className={styles.section}>
                    <ListFilterBar
                        activeStatus={status}
                        onSearchChange={(value) => {
                            setOpenActionId(null);
                            setSearchTerm(value);
                            setPage(0);
                        }}
                        onStatusChange={(value) => {
                            setOpenActionId(null);
                            setStatus(value as DisposalListQuery["status"]);
                            setPage(0);
                        }}
                        searchPlaceholder="Tìm mã yêu cầu hoặc mã phiếu trả"
                        searchTerm={searchTerm}
                        statusCounts={statusCounts ?? undefined}
                        statusTabs={STATUS_TABS}
                    />

                    {notice ? <div className={styles.notice}>{notice}</div> : null}
                    {countsError ? <div className={styles.error}>{countsError}</div> : null}
                    {error ? <div className={styles.error}>{error}</div> : null}

                    <div className={orderStyles.tableCard}>
                        <div className={orderStyles.tableWrap}>
                            {loading || error || !result?.content.length ? (
                                <table className={`${orderStyles.orderTable} ${styles.dataTable} ${disposalStyles.table}`}>
                                    <thead>
                                        <tr>
                                            <th>Mã yêu cầu</th>
                                            <th>Thời gian</th>
                                            <th>Trạng thái</th>
                                            <th>Phiếu trả</th>
                                            <th>Phiếu kiểm định</th>
                                            <th>Thao tác</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {loading ? <ListMessage colSpan={6} message="Đang tải danh sách yêu cầu tiêu hủy..." /> : null}
                                        {!loading && error ? <ListMessage colSpan={6} message="Không thể hiển thị yêu cầu tiêu hủy." /> : null}
                                        {!loading && !error && !result?.content.length ? (
                                            <ListMessage
                                                colSpan={6}
                                                message={hasFilter
                                                    ? "Chưa có yêu cầu tiêu hủy phù hợp bộ lọc."
                                                    : "Chưa có yêu cầu tiêu hủy phù hợp."}
                                            />
                                        ) : null}
                                    </tbody>
                                </table>
                            ) : (
                                <DisposalTable
                                    canApprove={canApprove}
                                    disposals={result.content}
                                    openActionId={openActionId}
                                    onOpenActionChange={(requestId, open) => setOpenActionId((current) => {
                                        if (open) return requestId;
                                        return current === requestId ? null : current;
                                    })}
                                    onApprove={(request) => {
                                        if (isCustomerReturnDisposal(request)) return;
                                        setApprovalError("");
                                        setConfirmRequest(request);
                                    }}
                                />
                            )}
                        </div>
                        {!loading && !error ? (
                            <ListPagination
                                itemLabel="yêu cầu"
                                onPageChange={(nextPage) => {
                                    setOpenActionId(null);
                                    setPage(nextPage);
                                }}
                                page={page}
                                pageSize={PAGE_SIZE}
                                totalItems={result?.totalElements ?? 0}
                                totalPages={result?.totalPages ?? 0}
                            />
                        ) : null}
                    </div>
                </section>
            </div>

            {confirmRequest && !isCustomerReturnDisposal(confirmRequest) ? (
                <ConfirmDialog
                    confirmLabel="Xác nhận phê duyệt"
                    description={`Bạn xác nhận phê duyệt yêu cầu tiêu hủy ${confirmRequest.code}?`}
                    error={approvalError}
                    onCancel={() => setConfirmRequest(null)}
                    onConfirm={() => void approveRequest()}
                    submitting={submitting}
                    title="Xác nhận phê duyệt tiêu hủy"
                />
            ) : null}
        </section>
    );
}
