import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { DataTable } from "@/components/shared/list/DataTable";
import { ListFilterToolbar } from "@/components/shared/list/ListFilterToolbar";
import { ListPageHeader } from "@/components/shared/list/ListPageHeader";
import { LIST_CODE_TEXT, LIST_MUTED_TEXT, LIST_SECONDARY_BUTTON } from "@/components/shared/list/list-ui.constants";
import { TablePagination } from "@/components/shared/list/TablePagination";
import { TimeRangeSelect } from "@/components/shared/list/TimeRangeSelect";
import { useListUrlState } from "@/components/shared/list/useListUrlState";
import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";

import { useAuthorization } from "../../../auth/useAuthorization";
import { ROLES } from "../../../auth/roles";
import { ConfirmDialog } from "../../product/components/ConfirmDialog";
import { formatDateTime } from "../../order/utils/order-formatters";
import { DisposalStatusBadge } from "../components/QualityComponents";
import { qualityApi } from "../services/quality.api";
import type {
    DisposalListQuery,
    DisposalListResult,
    DisposalRequest,
    DisposalStatusCounts
} from "../types/quality.types";
import { isCustomerReturnDisposal } from "../utils/inventory-labels";
import styles from "./CustomerReturnListPage.module.css";

const PAGE_SIZE = 10;
const STATUS_TABS = [
    { key: "ALL", label: "Tất cả" },
    { key: "PENDING_APPROVAL", label: "Chờ xử lý" },
    { key: "APPROVED", label: "Đã xử lý" }
] as const;

type DisposalStatusFilter = NonNullable<DisposalListQuery["status"]>;

export function DisposalListPage() {
    const navigate = useNavigate();
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole([ROLES.SUPER_ADMIN, ROLES.MANAGER]);
    const listState = useListUrlState();
    const { keyword, page, setPage, timeRange } = listState;
    const status = (STATUS_TABS.some((tab) => tab.key === listState.status)
        ? listState.status
        : "ALL") as DisposalStatusFilter;
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

    useEffect(() => {
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
    }, [keyword, page, reloadKey, setPage, status, timeRange?.fromDate, timeRange?.toDate]);

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

    function getRowActions(request: DisposalRequest): RowAction[] {
        const actions: RowAction[] = [{
            label: "Xem chi tiết",
            onSelect: () => navigate(`/management/inventory/disposals/${request.id}`)
        }];
        if (request.status === "PENDING_APPROVAL" && !isCustomerReturnDisposal(request) && canApprove) {
            actions.push({
                label: "Phê duyệt",
                onSelect: () => {
                    setApprovalError("");
                    setConfirmRequest(request);
                }
            });
        }
        return actions;
    }

    return (
        <section className="flex h-full min-h-0 flex-col gap-4 text-[#172317]">
            <ListPageHeader
                title="Yêu cầu tiêu hủy"
                description="Theo dõi yêu cầu và lịch sử xử lý tiêu hủy hàng hỏng, hàng khách trả."
                actions={(
                    <button
                        className={LIST_SECONDARY_BUTTON}
                        disabled={loading}
                        onClick={() => setReloadKey((key) => key + 1)}
                        type="button"
                    >
                        <RefreshCw size={17} aria-hidden="true" />
                        Làm mới
                    </button>
                )}
            />

            <ListFilterToolbar
                activeChip={status}
                chips={STATUS_TABS.map((tab) => ({
                    key: tab.key,
                    label: tab.label,
                    count: countsError ? undefined : statusCounts ? (statusCounts[tab.key] ?? 0) : null
                }))}
                onChipChange={(value) => {
                    setOpenActionId(null);
                    listState.setStatus(value);
                }}
                search={{
                    value: listState.searchDraft,
                    placeholder: "Tìm mã yêu cầu hoặc mã phiếu trả...",
                    onChange: listState.setSearchDraft
                }}
            >
                <TimeRangeSelect onChange={listState.setTime} value={listState.time} />
            </ListFilterToolbar>

            {notice ? <div className={styles.notice} role="status">{notice}</div> : null}

            <DataTable
                caption="Danh sách yêu cầu tiêu hủy"
                columns={[
                    {
                        key: "code",
                        header: "Mã yêu cầu",
                        render: (request) => <DisposalCodeCell request={request} />
                    },
                    {
                        key: "source",
                        header: "Nguồn",
                        render: (request) => (isCustomerReturnDisposal(request) ? "Khách trả hàng" : "Hàng hỏng / hết hạn")
                    },
                    {
                        key: "items",
                        header: "Hàng tiêu hủy",
                        render: (request) => <DisposalItemsCell request={request} />
                    },
                    {
                        key: "quantity",
                        header: "Số lượng",
                        align: "right",
                        className: "font-bold",
                        render: (request) => request.lines
                            .reduce((total, line) => total + line.quantity, 0)
                            .toLocaleString("vi-VN")
                    },
                    {
                        key: "status",
                        header: "Trạng thái",
                        render: (request) => (
                            <DisposalStatusBadge customerReturn={isCustomerReturnDisposal(request)} status={request.status} />
                        )
                    },
                    {
                        key: "requestedAt",
                        header: "Thời gian",
                        render: (request) => formatDateTime(request.requestedAt)
                    }
                ]}
                emptyDescription={listState.hasFilter
                    ? "Thử đổi từ khóa, trạng thái hoặc khoảng thời gian."
                    : "Yêu cầu tiêu hủy được tạo khi kiểm hàng phân loại hàng cần tiêu hủy."}
                emptyTitle="Chưa có yêu cầu tiêu hủy phù hợp"
                error={error || null}
                getRowKey={(request) => request.id}
                loading={loading}
                onRetry={() => setReloadKey((key) => key + 1)}
                onRowClick={(request) => navigate(`/management/inventory/disposals/${request.id}`)}
                pagination={(
                    <TablePagination
                        disabled={loading}
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
                )}
                rowActions={(request) => (
                    <RowActionMenu
                        actions={getRowActions(request)}
                        isOpen={openActionId === request.id}
                        menuLabel={`Thao tác yêu cầu ${request.code}`}
                        onOpenChange={(open) => setOpenActionId((current) => {
                            if (open) return request.id;
                            return current === request.id ? null : current;
                        })}
                        triggerLabel={`Mở thao tác cho yêu cầu ${request.code}`}
                    />
                )}
                rows={result?.content ?? []}
            />

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

function DisposalCodeCell({ request }: { request: DisposalRequest }) {
    const references = [
        request.returnCode ? `Phiếu trả ${request.returnCode}` : null,
        request.inspectionCode ? `Kiểm hàng ${request.inspectionCode}` : null
    ].filter(Boolean);

    return (
        <div className="grid">
            <Link
                className={`${LIST_CODE_TEXT} hover:text-[#159734] hover:underline`}
                onClick={(event) => event.stopPropagation()}
                to={`/management/inventory/disposals/${request.id}`}
            >
                {request.code}
            </Link>
            {references.length ? <span className={LIST_MUTED_TEXT}>{references.join(" · ")}</span> : null}
        </div>
    );
}

function DisposalItemsCell({ request }: { request: DisposalRequest }) {
    const [first, ...rest] = request.lines;
    if (!first) return <span className={LIST_MUTED_TEXT}>Chưa có dòng hàng</span>;

    return (
        <div className="grid max-w-[280px]">
            <span className="truncate font-bold" title={first.skuName}>
                {first.skuName}
                {rest.length ? <span className={LIST_MUTED_TEXT}> +{rest.length}</span> : null}
            </span>
            <span className={LIST_MUTED_TEXT}>{first.skuCode}</span>
        </div>
    );
}
