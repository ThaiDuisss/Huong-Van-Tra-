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
import { Button } from "@/components/ui/Button";

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

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setPage(0);
    };

    const selectStatus = (newStatus: DisposalStatusFilter) => {
        setStatus(newStatus);
        setPage(0);
    };

    return (
        <OrderPageLayout
            title="Tiêu hủy"
            description="Theo dõi và xử lý yêu cầu tiêu hủy hàng hóa."
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
                <Button disabled={loading} onClick={() => setReloadKey((key) => key + 1)}>
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

                <OrderDataTable<DisposalRequest>
                    caption="Danh sách yêu cầu tiêu hủy"
                    rows={result?.content ?? []}
                    columns={[
                        {
                            key: "code",
                            header: "Mã yêu cầu",
                            width: "18%",
                            cellClassName: styles.returnCodeText,
                            render: (request: DisposalRequest) => <DisposalCodeCell request={request} />
                        },
                        {
                            key: "status",
                            header: "Trạng thái",
                            width: "16%",
                            align: "center",
                            render: (request: DisposalRequest) => (
                                <DisposalStatusBadge customerReturn={isCustomerReturnDisposal(request)} status={request.status} />
                            )
                        },
                        {
                            key: "source",
                            header: "Nguồn",
                            width: "16%",
                            render: (request: DisposalRequest) => (isCustomerReturnDisposal(request) ? "Khách trả hàng" : "Hàng hỏng / hết hạn")
                        },
                        {
                            key: "items",
                            header: "Hàng tiêu hủy",
                            width: "24%",
                            render: (request: DisposalRequest) => <DisposalItemsCell request={request} />
                        },
                        {
                            key: "quantity",
                            header: "Số lượng",
                            width: "10%",
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
                            key: "requestedAt",
                            header: "Thời gian",
                            width: "16%",
                            render: (request: DisposalRequest) => formatDateTime(request.requestedAt)
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
                className="hover:underline"
                onClick={(event) => event.stopPropagation()}
                title={request.code}
                to={`/management/inventory/disposals/${request.id}`}
            >
                {request.code}
            </Link>
            {references.length ? <span className={styles.mutedText} title={references.join(" · ")}>{references.join(" · ")}</span> : null}
        </div>
    );
}

function DisposalItemsCell({ request }: { request: DisposalRequest }) {
    const [first, ...rest] = request.lines;
    if (!first) return <span className={styles.mutedText}>Chưa có dòng hàng</span>;

    return (
        <div className="grid min-w-0">
            <span className="truncate font-bold" title={request.lines.map((line) => line.skuName).join(" · ")}>
                {first.skuName}
                {rest.length ? <span className={styles.mutedText}> +{rest.length}</span> : null}
            </span>
            <span className={styles.mutedText}>{first.skuCode}</span>
        </div>
    );
}
