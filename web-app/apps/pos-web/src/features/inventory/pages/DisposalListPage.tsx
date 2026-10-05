import { useEffect, useState } from "react";

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

const PAGE_SIZE = 10;
const STATUS_TABS: CustomerReturnStatusTab[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "PENDING_APPROVAL", label: "Chờ duyệt" },
    { key: "APPROVED", label: "Đã duyệt" }
];

export function DisposalListPage() {
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole(["ADMIN", "MANAGER"]);
    const [searchTerm, setSearchTerm] = useState("");
    const keyword = useDebouncedValue(searchTerm.trim(), 300);
    const [status, setStatus] = useState<DisposalListQuery["status"]>("ALL");
    const [page, setPage] = useState(0);
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

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoading(true);
            setError("");
            void qualityApi.listDisposalRequests({ keyword, page, size: PAGE_SIZE, status })
                .then((response) => {
                    if (!active) return;
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
    }, [keyword, page, reloadKey, status]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setStatusCounts(null);
            setCountsError("");
            void qualityApi.getDisposalStatusCounts(keyword || undefined)
                .then((counts) => {
                    if (active) setStatusCounts(counts);
                })
                .catch(() => {
                    if (active) setCountsError("Không thể tải số lượng yêu cầu theo trạng thái.");
                });
        });
        return () => { active = false; };
    }, [keyword, reloadKey]);

    async function approveRequest() {
        if (!confirmRequest || submitting || !canApprove) return;
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

    const hasFilter = Boolean(searchTerm.trim()) || status !== "ALL";

    return (
        <section className={styles.page}>
            <div className={styles.content}>
                <header className={orderStyles.header}>
                    <div className={orderStyles.title}>
                        <h1>Phê duyệt tiêu hủy hàng trả</h1>
                        <p>Theo dõi và phê duyệt các yêu cầu tiêu hủy hàng trả.</p>
                    </div>
                </header>

                <section className={styles.section}>
                    <ListFilterBar
                        activeStatus={status}
                        onSearchChange={(value) => {
                            setSearchTerm(value);
                            setPage(0);
                        }}
                        onStatusChange={(value) => {
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
                                <table className={orderStyles.orderTable}>
                                    <thead>
                                        <tr>
                                            <th>Mã yêu cầu</th>
                                            <th>Trạng thái</th>
                                            <th>Phiếu trả</th>
                                            <th>Phiếu kiểm định</th>
                                            <th>Thao tác</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {loading ? <ListMessage colSpan={5} message="Đang tải danh sách yêu cầu tiêu hủy..." /> : null}
                                        {!loading && error ? <ListMessage colSpan={5} message="Không thể hiển thị yêu cầu tiêu hủy." /> : null}
                                        {!loading && !error && !result?.content.length ? (
                                            <ListMessage
                                                colSpan={5}
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
                                    onApprove={(request) => {
                                        setApprovalError("");
                                        setConfirmRequest(request);
                                    }}
                                />
                            )}
                        </div>
                        {!loading && !error ? (
                            <ListPagination
                                itemLabel="yêu cầu"
                                onPageChange={setPage}
                                page={page}
                                pageSize={PAGE_SIZE}
                                totalItems={result?.totalElements ?? 0}
                                totalPages={result?.totalPages ?? 0}
                            />
                        ) : null}
                    </div>
                </section>
            </div>

            {confirmRequest ? (
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
