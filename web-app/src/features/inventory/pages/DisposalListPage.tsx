import {
    OrderPageLayout,
    OrderListWorkspace,
    OrderToolbar,
    OrderStatusFilters,
    OrderDataTable,
    OrderPagination,
} from "@/features/order/components/shared/OrderListUI";
import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { TimeSidebar } from "@/components/shared/TimeSidebar";
import { useTimeFilter } from "@/components/shared/useTimeFilter";
import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";
import { Button } from "@/components/ui/Button";

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

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => setDebouncedValue(value), delayMs);
        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

export function DisposalListPage() {
    const navigate = useNavigate();
    const { hasAnyRole } = useAuthorization();
    const canApprove = hasAnyRole([ROLES.SUPER_ADMIN, ROLES.MANAGER]);

    const [searchTerm, setSearchTerm] = useState("");
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);

    const [status, setStatus] = useState<DisposalStatusFilter>("ALL");
    const [page, setPage] = useState(0);

    const {
        selectedTimeNode,
        selectTimeNode,
        clearTimeRange,
        isTimeCollapsed,
        setIsTimeCollapsed,
        timeRange,
    } = useTimeFilter(() => setPage(0));

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
                keyword: debouncedSearchTerm,
                page,
                size: PAGE_SIZE,
                status
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
    }, [debouncedSearchTerm, page, reloadKey, setPage, status, timeRange?.fromDate, timeRange?.toDate]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setStatusCounts(null);
            setCountsError("");
            void qualityApi.getDisposalStatusCounts(
                debouncedSearchTerm || undefined,
                timeRange?.fromDate,
                timeRange?.toDate
            )
                .then((counts) => {
                    if (active) setStatusCounts(counts);
                })
                .catch(() => {
                    if (active) setCountsError("Không thể tải số lượng yêu cầu theo trạng thái.");
                });
        });
        return () => { active = false; };
    }, [debouncedSearchTerm, reloadKey, timeRange?.fromDate, timeRange?.toDate]);

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

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setPage(0);
    };

    const selectStatus = (newStatus: DisposalStatusFilter) => {
        setOpenActionId(null);
        setStatus(newStatus);
        setPage(0);
    };

    return (
        <OrderPageLayout
            title="Yêu cầu tiêu hủy"
            description="Theo dõi yêu cầu và lịch sử xử lý tiêu hủy hàng hỏng, hàng khách trả."
            sidebarCollapsed={isTimeCollapsed}
            sidebar={
                <TimeSidebar
                    activeDate={timeRange?.selectedDate ?? undefined}
                    collapsed={isTimeCollapsed}
                    enableCalendar
                    onClear={clearTimeRange}
                    onSelect={selectTimeNode}
                    onToggleCollapse={() => setIsTimeCollapsed((collapsed) => !collapsed)}
                    selectedNode={selectedTimeNode}
                />
            }
            actions={
                <Button disabled={loading} onClick={() => setReloadKey((key) => key + 1)} size="lg">
                    <RefreshCw size={17} aria-hidden="true" />
                    Làm mới
                </Button>
            }
        >
            <OrderListWorkspace>
                <OrderToolbar
                    search={{
                        "aria-label": "Tìm kiếm yêu cầu tiêu hủy",
                        placeholder: "Tìm mã yêu cầu hoặc mã phiếu trả...",
                        value: searchTerm,
                        onChange: handleSearchChange,
                        onKeyDown: (event) => {
                            if (event.key === "Escape") {
                                handleSearchChange("");
                            }
                        },
                    }}
                >
                    <OrderStatusFilters
                        label="Trạng thái:"
                        options={STATUS_TABS.map((tab) => ({
                            ...tab,
                            count: countsError ? undefined : statusCounts ? (statusCounts[tab.key] ?? 0) : undefined,
                        }))}
                        value={status}
                        onChange={selectStatus}
                    />
                </OrderToolbar>

                {notice ? <div className={styles.notice} role="status">{notice}</div> : null}

                <OrderDataTable<DisposalRequest>
                    caption="Danh sách yêu cầu tiêu hủy"
                    rows={result?.content ?? []}
                    columns={[
                        {
                            key: "code",
                            header: "Mã yêu cầu",
                            width: "18%",
                            render: (request: DisposalRequest) => <DisposalCodeCell request={request} />
                        },
                        {
                            key: "source",
                            header: "Nguồn",
                            width: "14%",
                            render: (request: DisposalRequest) => (isCustomerReturnDisposal(request) ? "Khách trả hàng" : "Hàng hỏng / hết hạn")
                        },
                        {
                            key: "items",
                            header: "Hàng tiêu hủy",
                            width: "22%",
                            render: (request: DisposalRequest) => <DisposalItemsCell request={request} />
                        },
                        {
                            key: "quantity",
                            header: "Số lượng",
                            width: "12%",
                            align: "right",
                            render: (request: DisposalRequest) => (
                                <strong>
                                    {request.lines
                                        .reduce((total, line) => total + line.quantity, 0)
                                        .toLocaleString("vi-VN")}
                                </strong>
                            )
                        },
                        {
                            key: "status",
                            header: "Trạng thái",
                            width: "14%",
                            align: "center",
                            render: (request: DisposalRequest) => (
                                <DisposalStatusBadge customerReturn={isCustomerReturnDisposal(request)} status={request.status} />
                            )
                        },
                        {
                            key: "requestedAt",
                            header: "Thời gian",
                            width: "16%",
                            render: (request: DisposalRequest) => formatDateTime(request.requestedAt)
                        },
                        {
                            key: "actions",
                            header: "Thao tác",
                            width: "8%",
                            align: "center",
                            render: (request: DisposalRequest) => (
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
                            )
                        }
                    ]}
                    emptyDescription="Thử đổi từ khóa, trạng thái hoặc khoảng thời gian"
                    emptyTitle="Chưa có yêu cầu tiêu hủy phù hợp"
                    error={error || null}
                    getRowKey={(request: DisposalRequest) => request.id}
                    loading={loading}
                    loadingDescription="Dữ liệu đang được tải từ Inventory-Service"
                    loadingTitle="Đang tải yêu cầu tiêu hủy"
                    errorTitle="Không tải được danh sách yêu cầu tiêu hủy"
                    onRetry={() => setReloadKey((key) => key + 1)}
                    onRowClick={(request: DisposalRequest) => navigate(`/management/inventory/disposals/${request.id}`)}
                    pagination={
                        <OrderPagination
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
                    }
                    minWidth={800}
                />
            </OrderListWorkspace>

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
        </OrderPageLayout>
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
                className={styles.codeLink}
                onClick={(event) => event.stopPropagation()}
                to={`/management/inventory/disposals/${request.id}`}
            >
                {request.code}
            </Link>
            {references.length ? <span className={styles.mutedText}>{references.join(" · ")}</span> : null}
        </div>
    );
}

function DisposalItemsCell({ request }: { request: DisposalRequest }) {
    const [first, ...rest] = request.lines;
    if (!first) return <span className={styles.mutedText}>Chưa có dòng hàng</span>;

    return (
        <div className="grid max-w-[280px]">
            <span className="truncate font-bold" title={first.skuName}>
                {first.skuName}
                {rest.length ? <span className={styles.mutedText}> +{rest.length}</span> : null}
            </span>
            <span className={styles.mutedText}>{first.skuCode}</span>
        </div>
    );
}
