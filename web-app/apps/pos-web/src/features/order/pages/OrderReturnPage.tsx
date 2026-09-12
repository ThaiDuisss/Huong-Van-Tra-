import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Eye,
    Plus,
    RefreshCw,
    Search,
    X,
    XCircle
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { TimeSidebar } from "../../../components/shared/TimeSidebar";
import {
    buildTodayTimeNode,
    getDateRangeFromTimeNode,
    type TimeNode
} from "../../../components/shared/TimeHierarchyFilter.utils";
import {
    getOrderById,
    getOrderReturnEligibilityStats,
    getOrders
} from "../services/order.api";
import {
    cancelOrderReturn,
    createCustomerReturnInspection,
    createOrderReturn,
    getCustomerReturnQcSummary,
    getOrderReturnList,
    getOrderReturnDetail,
    getOrderReturns,
    postCustomerReturnInspection,
    receiveOrderReturn
} from "../services/order-return.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import type { InventoryLocationResponse } from "../../inventory/types/inventory.types";
import type {
    OrderDetailResponse,
    OrderItemResponse,
    OrderPageResponse,
    OrderQueryParams,
    OrderReturnEligibility,
    OrderReturnEligibilityStatsResponse,
    OrderSummaryResponse
} from "../types/order.types";
import type {
    CreateOrderReturnPayload,
    CustomerReturnInspectionResponse,
    CustomerReturnQcResult,
    CustomerReturnQcSummaryLine,
    CustomerReturnQcSummaryResponse,
    OrderReturnListRow,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason,
    ReturnStatus
} from "../types/order-return.types";
import {
    RETURN_CONDITION_LABELS,
    RETURN_CONDITIONS,
    CUSTOMER_RETURN_QC_RESULT_EFFECTS,
    CUSTOMER_RETURN_QC_RESULTS,
    RETURN_REASON_LABELS,
    RETURN_REASONS,
    calculateAvailableReturnQuantity,
    canCreateReturn,
    formatReturnQuantity,
    getAllowedCustomerReturnQcResults,
    getCustomerReturnQcResultLabel,
    getCustomerReturnQcStatusLabel,
    getReturnCode,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel,
    getReturnStatusLabel,
    toReturnNumber
} from "../utils/order-return.utils";
import {
    formatDateTime,
    getCustomerName,
    getOrderCode,
    getPaymentStatusText,
    getOrderStatusText
} from "../utils/order-formatters";
import styles from "./OrderReturnPage.module.css";

type ReturnStatusFilter = "ALL" | ReturnStatus;
type OrderEligibilityFilter = OrderReturnEligibility;
type WizardStep = 1 | 2 | 3 | 4;

interface ReturnDraftItem {
    selected: boolean;
    quantity: string;
    reason: ReturnReason;
    condition: ReturnCondition;
    note: string;
}

interface ConfirmAction {
    type: "receive" | "cancel";
    returnItem: OrderReturnListRow;
}

interface QcDraftLine {
    selected: boolean;
    quantity: string;
    result: CustomerReturnQcResult;
    destinationLocationId: string;
    note: string;
}

const RETURN_STATUS_FILTERS: {
    key: ReturnStatusFilter;
    label: string;
}[] = [
    { key: "ALL", label: "Tất cả" },
    { key: "CREATED", label: "Đã tạo" },
    { key: "RECEIVED", label: "Đã nhận hàng" },
    { key: "CANCELLED", label: "Đã hủy" }
];

const ORDER_ELIGIBILITY_FILTERS: {
    countKey: keyof OrderReturnEligibilityStatsResponse;
    key: OrderEligibilityFilter;
    label: string;
}[] = [
    { key: "ALL", label: "Tất cả", countKey: "total" },
    { key: "ELIGIBLE", label: "Đủ điều kiện", countKey: "eligible" },
    { key: "INELIGIBLE", label: "Không đủ điều kiện", countKey: "ineligible" }
];

const RETURN_LIST_PAGE_SIZE = 20;
const ORDER_SEARCH_PAGE_SIZE = 10;

const emptyReturnPage: OrderPageResponse<OrderReturnListRow> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: RETURN_LIST_PAGE_SIZE
};

const emptyOrderSearchPage: OrderPageResponse<OrderSummaryResponse> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: ORDER_SEARCH_PAGE_SIZE
};

const emptyOrderEligibilityStats: OrderReturnEligibilityStatsResponse = {
    total: 0,
    eligible: 0,
    ineligible: 0
};

function useDebouncedValue<T>(value: T, delayMs: number) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setDebouncedValue(value);
        }, delayMs);

        return () => window.clearTimeout(timerId);
    }, [delayMs, value]);

    return debouncedValue;
}

function getItemKey(item: OrderItemResponse, index: number) {
    return item.id ?? item.productVariantId ?? `item-${index}`;
}

function getItemName(item: OrderItemResponse) {
    return item.productNameSnapshot?.trim() || "Sản phẩm";
}

function getItemVariant(item: OrderItemResponse) {
    return item.variantNameSnapshot?.trim() || "Không có phân loại";
}

function getReturnItemCount(returnItem: OrderReturnListRow) {
    if (returnItem.itemCount !== null && returnItem.itemCount !== undefined) {
        return toReturnNumber(returnItem.itemCount);
    }

    return (returnItem.items ?? []).reduce(
        (total, item) => total + toReturnNumber(item.quantity),
        0
    );
}

function createDefaultDraftItem(): ReturnDraftItem {
    return {
        selected: false,
        quantity: "1",
        reason: "DEFECTIVE",
        condition: "OPENED",
        note: ""
    };
}

function getDraftQuantity(draft?: ReturnDraftItem) {
    if (!draft?.quantity.trim()) {
        return 0;
    }

    const quantity = Number(draft.quantity);

    return Number.isFinite(quantity) ? quantity : 0;
}

function getQcQuantity(draft?: QcDraftLine) {
    if (!draft?.quantity.trim()) {
        return 0;
    }

    const quantity = Number(draft.quantity);

    return Number.isFinite(quantity) ? quantity : 0;
}

function getDefaultQcResult(condition?: ReturnCondition | null) {
    return condition === "SEALED" ? "RESTOCK" : "QUARANTINE";
}

function createDefaultQcDraftLine(
    line?: CustomerReturnQcSummaryLine | null
): QcDraftLine {
    return {
        selected: false,
        quantity: "1",
        result: getDefaultQcResult(line?.condition),
        destinationLocationId: "",
        note: ""
    };
}

function findReturnItem(
    returnItem: OrderReturnListRow,
    returnItemId: string
) {
    return (returnItem.items ?? []).find((item) => item.id === returnItemId);
}

function getReturnLineName(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);

    return item?.productNameSnapshot?.trim() || "Sản phẩm";
}

function getReturnLineMeta(
    returnItem: OrderReturnListRow,
    line: CustomerReturnQcSummaryLine
) {
    const item = findReturnItem(returnItem, line.returnItemId);
    const sku = item?.skuSnapshot?.trim() || "--";
    const variant = item?.variantNameSnapshot?.trim() || "Không có phân loại";

    return `${sku} · ${variant}`;
}

function getReturnLineReasonLabel(reason?: string | null) {
    if (!reason) {
        return "--";
    }

    return reason in RETURN_REASON_LABELS
        ? RETURN_REASON_LABELS[reason as ReturnReason]
        : reason;
}

function isSaleableReturnLocation(location: InventoryLocationResponse) {
    return (
        location.status === "ACTIVE" &&
        location.type !== "DAMAGED" &&
        location.type !== "QUARANTINE"
    );
}

function getLocationOptionLabel(location: InventoryLocationResponse) {
    const code = String(location.code ?? "").trim();
    const name = String(location.name ?? "").trim();

    if (code && name) {
        return `${name} (${code})`;
    }

    return name || code || "Vị trí kho";
}

function getPageNumbers(totalPages: number, currentPage: number) {
    const pageCount = Math.max(totalPages, 1);
    const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
    const length = Math.min(pageCount, 5);

    return Array.from({ length }, (_, index) => start + index);
}

export function OrderReturnPage() {
    const [searchTerm, setSearchTerm] = useState("");
    const debouncedSearchTerm = useDebouncedValue(searchTerm.trim(), 350);
    const [selectedTimeNode, setSelectedTimeNode] = useState<TimeNode | null>(
        buildTodayTimeNode
    );
    const [statusFilter, setStatusFilter] =
        useState<ReturnStatusFilter>("ALL");
    const [returnPage, setReturnPage] = useState(emptyReturnPage);
    const [currentPage, setCurrentPage] = useState(0);
    const [isTimeCollapsed, setIsTimeCollapsed] = useState(true);
    const [isRowsLoading, setIsRowsLoading] = useState(false);
    const [rowsError, setRowsError] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [wizardStep, setWizardStep] = useState<WizardStep>(1);
    const [orderSearchTerm, setOrderSearchTerm] = useState("");
    const debouncedOrderSearchTerm = useDebouncedValue(
        orderSearchTerm.trim(),
        350
    );
    const [orderResultsPage, setOrderResultsPage] =
        useState(emptyOrderSearchPage);
    const [orderSearchPage, setOrderSearchPage] = useState(0);
    const [orderEligibilityFilter, setOrderEligibilityFilter] =
        useState<OrderEligibilityFilter>("ELIGIBLE");
    const [orderEligibilityStats, setOrderEligibilityStats] = useState(
        emptyOrderEligibilityStats
    );
    const [isOrderSearchLoading, setIsOrderSearchLoading] = useState(false);
    const [orderSearchError, setOrderSearchError] = useState<string | null>(
        null
    );
    const [selectedOrderSummary, setSelectedOrderSummary] =
        useState<OrderSummaryResponse | null>(null);
    const [selectedOrder, setSelectedOrder] =
        useState<OrderDetailResponse | null>(null);
    const [selectedOrderReturns, setSelectedOrderReturns] = useState<
        OrderReturnResponse[]
    >([]);
    const [isSelectedOrderLoading, setIsSelectedOrderLoading] = useState(false);
    const [selectedOrderError, setSelectedOrderError] = useState<string | null>(
        null
    );
    const [draftItems, setDraftItems] = useState<Record<string, ReturnDraftItem>>(
        {}
    );
    const [returnNote, setReturnNote] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [activeReturn, setActiveReturn] =
        useState<OrderReturnListRow | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(
        null
    );
    const [isTransitioning, setIsTransitioning] = useState(false);
    const [toast, setToast] = useState<string | null>(null);
    const timeRange = getDateRangeFromTimeNode(selectedTimeNode);

    useEffect(() => {
        if (!toast) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setToast(null);
        }, 3200);

        return () => window.clearTimeout(timerId);
    }, [toast]);

    useEffect(() => {
        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsRowsLoading(true);
            setRowsError(null);

            getOrderReturnList({
                fromDate: timeRange?.fromDate,
                keyword: debouncedSearchTerm,
                status:
                    statusFilter === "ALL" ? undefined : statusFilter,
                toDate: timeRange?.toDate,
                page: currentPage,
                size: RETURN_LIST_PAGE_SIZE,
                sort: "createdAt,desc"
            })
                .then((page) => {
                    if (!ignore) {
                        setReturnPage({
                            ...emptyReturnPage,
                            ...page,
                            content: page.content ?? []
                        });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setReturnPage(emptyReturnPage);
                        setRowsError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsRowsLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        currentPage,
        debouncedSearchTerm,
        reloadKey,
        statusFilter,
        timeRange?.fromDate,
        timeRange?.toDate
    ]);

    useEffect(() => {
        if (!isCreateOpen) {
            return;
        }

        let ignore = false;

        getOrderReturnEligibilityStats(debouncedOrderSearchTerm)
            .then((stats) => {
                if (!ignore) {
                    setOrderEligibilityStats({
                        ...emptyOrderEligibilityStats,
                        ...stats
                    });
                }
            })
            .catch(() => {
                if (!ignore) {
                    setOrderEligibilityStats(emptyOrderEligibilityStats);
                }
            });

        return () => {
            ignore = true;
        };
    }, [debouncedOrderSearchTerm, isCreateOpen]);

    useEffect(() => {
        if (!isCreateOpen) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsOrderSearchLoading(true);
            setOrderSearchError(null);

            const orderQuery: OrderQueryParams = {
                keyword: debouncedOrderSearchTerm,
                returnEligibility: orderEligibilityFilter,
                page: orderSearchPage,
                size: ORDER_SEARCH_PAGE_SIZE,
                sort: "businessStatus"
            };

            getOrders(
                orderEligibilityFilter === "ELIGIBLE"
                    ? {
                          ...orderQuery,
                          status: "COMPLETED"
                      }
                    : orderQuery
            )
                .then((ordersPage) => {
                    if (!ignore) {
                        setOrderResultsPage({
                            ...emptyOrderSearchPage,
                            ...ordersPage,
                            content: ordersPage.content ?? []
                        });
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setOrderResultsPage(emptyOrderSearchPage);
                        setOrderSearchError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsOrderSearchLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [
        debouncedOrderSearchTerm,
        isCreateOpen,
        orderEligibilityFilter,
        orderSearchPage
    ]);

    const visibleRows = returnPage.content ?? [];
    const totalPages = Math.max(returnPage.totalPages ?? 0, 1);
    const totalElements = returnPage.totalElements ?? 0;
    const fromRecord =
        totalElements > 0 ? returnPage.number * returnPage.size + 1 : 0;
    const toRecord = Math.min(
        (returnPage.number + 1) * returnPage.size,
        totalElements
    );

    const selectedOrderItems = selectedOrder?.items ?? [];
    const selectedDraftEntries = selectedOrderItems.filter((item, index) => {
        const key = getItemKey(item, index);

        return draftItems[key]?.selected;
    });
    const draftValidationError = getDraftValidationError(
        selectedOrder,
        selectedOrderReturns,
        draftItems
    );
    const isNextDisabled =
        isSubmitting ||
        (wizardStep === 1 &&
            (!selectedOrder ||
                isSelectedOrderLoading ||
                !canCreateReturn(selectedOrder))) ||
        (wizardStep === 2 && selectedDraftEntries.length === 0) ||
        (wizardStep === 3 && Boolean(draftValidationError));

    const resetCreateFlow = () => {
        setIsCreateOpen(false);
        setWizardStep(1);
        setOrderSearchTerm("");
        setOrderResultsPage(emptyOrderSearchPage);
        setOrderSearchPage(0);
        setOrderEligibilityFilter("ELIGIBLE");
        setOrderEligibilityStats(emptyOrderEligibilityStats);
        setOrderSearchError(null);
        setSelectedOrderSummary(null);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setSelectedOrderError(null);
        setDraftItems({});
        setReturnNote("");
        setIsSubmitting(false);
    };

    const openCreateFlow = () => {
        setIsCreateOpen(true);
        setWizardStep(1);
    };

    const handleSearchChange = (value: string) => {
        setSearchTerm(value);
        setCurrentPage(0);
    };

    const handleStatusFilterChange = (status: ReturnStatusFilter) => {
        setStatusFilter(status);
        setCurrentPage(0);
    };

    const handleOrderSearchChange = (value: string) => {
        setOrderSearchTerm(value);
        setOrderSearchPage(0);
    };

    const handleOrderEligibilityFilterChange = (
        filter: OrderEligibilityFilter
    ) => {
        setOrderEligibilityFilter(filter);
        setOrderSearchPage(0);
        setSelectedOrderSummary(null);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setSelectedOrderError(null);
        setDraftItems({});
    };

    const selectTimeNode = (node: TimeNode) => {
        setSelectedTimeNode(node);
        setCurrentPage(0);
    };

    const clearTimeRange = () => {
        setSelectedTimeNode(null);
        setCurrentPage(0);
    };

    const selectOrderForReturn = (order: OrderSummaryResponse) => {
        setSelectedOrderSummary(order);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setSelectedOrderError(null);
        setDraftItems({});
        setWizardStep(1);
        setIsSelectedOrderLoading(true);

        Promise.all([getOrderById(order.id), getOrderReturns(order.id)])
            .then(([orderDetail, returns]) => {
                setSelectedOrder(orderDetail);
                setSelectedOrderReturns(returns ?? []);
                setDraftItems(
                    Object.fromEntries(
                        (orderDetail.items ?? []).map((item, index) => [
                            getItemKey(item, index),
                            createDefaultDraftItem()
                        ])
                    )
                );
            })
            .catch((error: unknown) => {
                setSelectedOrderError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsSelectedOrderLoading(false);
            });
    };

    const updateDraftItem = (
        itemKey: string,
        update: Partial<ReturnDraftItem>
    ) => {
        setDraftItems((current) => {
            const nextItem = {
                ...(current[itemKey] ?? createDefaultDraftItem()),
                ...update
            };

            if (update.reason === "CHANGE_OF_MIND") {
                nextItem.condition = "SEALED";
            }

            return {
                ...current,
                [itemKey]: nextItem
            };
        });
    };

    const moveToNextStep = () => {
        if (wizardStep === 1 && !selectedOrder) {
            setToast("Vui lòng chọn đơn hàng trước khi tiếp tục.");
            return;
        }

        if (wizardStep === 1 && !canCreateReturn(selectedOrder)) {
            setToast(
                "Chỉ đơn hàng đã hoàn tất và thanh toán mới được hoàn hàng."
            );
            return;
        }

        if (wizardStep === 2 && selectedDraftEntries.length === 0) {
            setToast("Vui lòng chọn ít nhất một sản phẩm cần hoàn.");
            return;
        }

        if (wizardStep === 3 && draftValidationError) {
            setToast(draftValidationError);
            return;
        }

        setWizardStep((step) => Math.min(4, step + 1) as WizardStep);
    };

    const submitReturn = () => {
        if (!selectedOrder || draftValidationError) {
            setToast(
                draftValidationError ??
                    "Vui lòng chọn đơn hàng và sản phẩm cần hoàn."
            );
            return;
        }

        const payloadItems: CreateOrderReturnPayload["items"] = [];

        selectedOrderItems.forEach((item, index) => {
            const itemKey = getItemKey(item, index);
            const draft = draftItems[itemKey];

            if (!item.id || !draft?.selected) {
                return;
            }

            payloadItems.push({
                orderItemId: item.id,
                quantity: getDraftQuantity(draft),
                reason: draft.reason,
                condition: draft.condition,
                note: draft.note.trim() || null
            });
        });

        const payload: CreateOrderReturnPayload = {
            note: returnNote.trim() || null,
            items: payloadItems
        };

        setIsSubmitting(true);
        createOrderReturn(selectedOrder.id, payload)
            .then((createdReturn) => {
                const row: OrderReturnListRow = {
                    ...createdReturn,
                    orderId: createdReturn.orderId ?? selectedOrder.id,
                    orderCode: getOrderCode(selectedOrder),
                    customerName: getCustomerName(selectedOrder),
                    customerPhone: selectedOrder.customerPhone,
                    itemCount: createdReturn.items?.length ?? 0
                };

                setReloadKey((key) => key + 1);
                setToast("Đã tạo phiếu hoàn hàng.");
                resetCreateFlow();
                openReturnDetail(row);
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsSubmitting(false);
            });
    };

    const openReturnDetail = (row: OrderReturnListRow) => {
        setActiveReturn(row);
        setDetailError(null);
        setIsDetailLoading(true);

        getOrderReturnDetail(row.id)
            .then((detail) => {
                setActiveReturn({
                    ...row,
                    ...detail,
                    orderCode: row.orderCode,
                    customerName: row.customerName
                });
            })
            .catch((error: unknown) => {
                setDetailError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsDetailLoading(false);
            });
    };

    const closeReturnDetail = () => {
        setActiveReturn(null);
        setDetailError(null);
        setIsDetailLoading(false);
        setConfirmAction(null);
    };

    const applyTransitionResult = (
        updatedReturn: OrderReturnResponse,
        fallback: OrderReturnListRow
    ) => {
        const row: OrderReturnListRow = {
            ...fallback,
            ...updatedReturn,
            orderCode: fallback.orderCode,
            customerName: fallback.customerName
        };

        setActiveReturn(row);
        setReturnPage((current) => ({
            ...current,
            content: current.content.map((item) =>
                item.id === row.id ? row : item
            )
        }));
        setSelectedOrderReturns((current) =>
            current.map((item) => (item.id === row.id ? row : item))
        );
    };

    const executeTransition = () => {
        if (!confirmAction) {
            return;
        }

        const { type, returnItem } = confirmAction;
        const action =
            type === "receive" ? receiveOrderReturn : cancelOrderReturn;

        setIsTransitioning(true);
        action(returnItem.id)
            .then((updatedReturn) => {
                applyTransitionResult(updatedReturn, returnItem);
                setToast(
                    type === "receive"
                        ? "Đã xác nhận nhận hàng."
                        : "Đã hủy phiếu hoàn."
                );
                setConfirmAction(null);
                setReloadKey((key) => key + 1);
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsTransitioning(false);
            });
    };

    return (
        <section
            className={`${styles.page} ${
                isTimeCollapsed ? styles.pageTimeCollapsed : ""
            }`}
        >
            <TimeSidebar
                activeDate={timeRange?.selectedDate ?? undefined}
                collapsed={isTimeCollapsed}
                enableCalendar
                onClear={clearTimeRange}
                onSelect={selectTimeNode}
                onToggleCollapse={() =>
                    setIsTimeCollapsed((collapsed) => !collapsed)
                }
                selectedNode={selectedTimeNode}
            />

            <div className={styles.content}>
                <div className={styles.header}>
                    <div className={styles.title}>
                        <h1>Hoàn hàng</h1>
                        <p>Quản lý và theo dõi các phiếu hoàn hàng</p>
                    </div>

                    <div className={styles.headerActions}>
                        <button
                            className={styles.secondaryButton}
                            onClick={() => setReloadKey((key) => key + 1)}
                            type="button"
                        >
                            <RefreshCw size={18} aria-hidden="true" />
                            Làm mới
                        </button>
                        <button
                            className={styles.primaryButton}
                            onClick={openCreateFlow}
                            type="button"
                        >
                            <Plus size={18} aria-hidden="true" />
                            Tạo phiếu hoàn
                        </button>
                    </div>
                </div>

                <div className={styles.workspace}>
                <div className={styles.filterRows}>
                    <div className={styles.filterToolbar}>
                        <label className={styles.searchBox}>
                            <Search size={20} aria-hidden="true" />
                            <input
                                aria-label="Tìm phiếu hoàn"
                                onChange={(event) =>
                                    handleSearchChange(event.target.value)
                                }
                                placeholder="Tìm mã đơn, khách hàng..."
                                value={searchTerm}
                            />
                        </label>

                        <div className={styles.filterGroup}>
                            <span className={styles.filterLabel}>
                                Trạng thái:
                            </span>
                            <div className={styles.quickFilters}>
                                {RETURN_STATUS_FILTERS.map((filter) => (
                                    <button
                                        className={`${styles.filterChip} ${
                                            statusFilter === filter.key
                                                ? styles.activeFilterChip
                                                : ""
                                        }`}
                                        key={filter.key}
                                        onClick={() =>
                                            handleStatusFilterChange(
                                                filter.key
                                            )
                                        }
                                        type="button"
                                    >
                                        {filter.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>

                <div className={styles.tableCard}>
                    <div className={styles.tableWrap}>
                        <table className={styles.returnTable}>
                            <colgroup>
                                <col style={{ width: "20%" }} />
                                <col style={{ width: "20%" }} />
                                <col style={{ width: "18%" }} />
                                <col style={{ width: "16%" }} />
                                <col style={{ width: "18%" }} />
                                <col style={{ width: "8%" }} />
                            </colgroup>
                            <thead>
                                <tr>
                                    <th>Mã phiếu hoàn</th>
                                    <th>Mã đơn hàng</th>
                                    <th>Sản phẩm</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày tạo</th>
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {isRowsLoading ? (
                                    <ReturnTableMessage
                                        description="Dữ liệu đang được tải từ Order-Service"
                                        title="Đang tải phiếu hoàn"
                                    />
                                ) : null}

                                {!isRowsLoading && rowsError ? (
                                    <ReturnTableMessage
                                        description={rowsError}
                                        title="Không tải được danh sách"
                                    />
                                ) : null}

                                {!isRowsLoading && !rowsError
                                    ? visibleRows.map((row, index) => (
                                          <tr
                                              className={
                                                  index % 2 === 0
                                                      ? styles.zebraFirstRow
                                                      : styles.zebraSecondRow
                                              }
                                              key={row.id}
                                              onClick={() =>
                                                  openReturnDetail(row)
                                              }
                                          >
                                              <td className={styles.code}>
                                                  {getReturnCode(row)}
                                              </td>
                                              <td className={styles.code}>
                                                  {row.orderCode || "--"}
                                              </td>
                                              <td>
                                                  {formatReturnQuantity(
                                                      getReturnItemCount(row)
                                                  )}{" "}
                                                  sản phẩm
                                              </td>
                                              <td>
                                                  <ReturnStatusBadge
                                                      status={row.status}
                                                  />
                                              </td>
                                              <td>
                                                  {formatDateTime(
                                                      row.createdAt
                                                  )}
                                              </td>
                                              <td
                                                  className={
                                                      styles.actionCell
                                                  }
                                              >
                                                  <button
                                                      aria-label={`Xem ${getReturnCode(
                                                          row
                                                      )}`}
                                                      className={
                                                          styles.iconButton
                                                      }
                                                      onClick={(event) => {
                                                          event.stopPropagation();
                                                          openReturnDetail(row);
                                                      }}
                                                      type="button"
                                                  >
                                                      <Eye
                                                          size={18}
                                                          aria-hidden="true"
                                                      />
                                                  </button>
                                              </td>
                                          </tr>
                                      ))
                                    : null}

                                {!isRowsLoading &&
                                !rowsError &&
                                visibleRows.length === 0 ? (
                                    <ReturnTableMessage
                                        description="Thử đổi từ khóa hoặc tạo phiếu hoàn mới"
                                        title="Chưa có phiếu hoàn phù hợp"
                                    />
                                ) : null}
                            </tbody>
                        </table>
                    </div>

                    <div className={styles.pagination}>
                        <span>
                            Hiển thị {fromRecord} - {toRecord} trong{" "}
                            {totalElements} phiếu hoàn
                        </span>
                        <div className={styles.pages}>
                            <button
                                className={styles.pageButton}
                                disabled={currentPage === 0}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.max(0, page - 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronLeft size={18} aria-hidden="true" />
                            </button>
                            {getPageNumbers(totalPages, currentPage).map(
                                (page) => (
                                    <button
                                        className={`${styles.pageButton} ${
                                            page === currentPage
                                                ? styles.activePage
                                                : ""
                                        }`}
                                        key={page}
                                        onClick={() => setCurrentPage(page)}
                                        type="button"
                                    >
                                        {page + 1}
                                    </button>
                                )
                            )}
                            {totalPages > 5 ? <span>...</span> : null}
                            <button
                                className={styles.pageButton}
                                disabled={currentPage >= totalPages - 1}
                                onClick={() =>
                                    setCurrentPage((page) =>
                                        Math.min(totalPages - 1, page + 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronRight size={18} aria-hidden="true" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {isCreateOpen ? (
                <div
                    className={styles.modalBackdrop}
                    onClick={resetCreateFlow}
                    role="presentation"
                >
                    <div
                        aria-modal="true"
                        className={styles.createModal}
                        onClick={(event) => event.stopPropagation()}
                        role="dialog"
                    >
                        <div className={styles.modalHeader}>
                            <div>
                                <h2>Tạo phiếu hoàn hàng</h2>
                                <p>Bước {wizardStep} / 4</p>
                            </div>
                            <button
                                aria-label="Đóng tạo phiếu hoàn"
                                className={styles.closeButton}
                                onClick={resetCreateFlow}
                                type="button"
                            >
                                <X size={18} aria-hidden="true" />
                            </button>
                        </div>

                        <div className={styles.stepper}>
                            {[
                                "Chọn đơn",
                                "Chọn sản phẩm",
                                "Lý do",
                                "Xác nhận"
                            ].map((label, index) => (
                                <span
                                    className={
                                        wizardStep >= index + 1
                                            ? styles.activeStep
                                            : ""
                                    }
                                    key={label}
                                >
                                    {label}
                                </span>
                            ))}
                        </div>

                        <div className={styles.modalBody}>
                            {wizardStep === 1 ? (
                                <OrderPicker
                                    eligibilityFilter={orderEligibilityFilter}
                                    eligibilityStats={orderEligibilityStats}
                                    isLoading={isOrderSearchLoading}
                                    currentPage={orderSearchPage}
                                    onEligibilityFilterChange={
                                        handleOrderEligibilityFilterChange
                                    }
                                    onPageChange={setOrderSearchPage}
                                    onSearchChange={handleOrderSearchChange}
                                    onSelectOrder={selectOrderForReturn}
                                    orderPage={orderResultsPage}
                                    searchError={orderSearchError}
                                    searchTerm={orderSearchTerm}
                                    selectedOrder={selectedOrder}
                                    selectedOrderError={selectedOrderError}
                                    selectedOrderSummary={selectedOrderSummary}
                                    isSelectedOrderLoading={
                                        isSelectedOrderLoading
                                    }
                                />
                            ) : null}

                            {wizardStep === 2 && selectedOrder ? (
                                <ReturnItemPicker
                                    draftItems={draftItems}
                                    items={selectedOrderItems}
                                    onUpdateDraftItem={updateDraftItem}
                                    returns={selectedOrderReturns}
                                />
                            ) : null}

                            {wizardStep === 3 && selectedOrder ? (
                                <ReturnReasonForm
                                    draftItems={draftItems}
                                    items={selectedOrderItems}
                                    onNoteChange={setReturnNote}
                                    onUpdateDraftItem={updateDraftItem}
                                    returnNote={returnNote}
                                />
                            ) : null}

                            {wizardStep === 4 && selectedOrder ? (
                                <ReturnConfirmation
                                    draftItems={draftItems}
                                    items={selectedOrderItems}
                                    order={selectedOrder}
                                    returnNote={returnNote}
                                />
                            ) : null}
                        </div>

                        <div className={styles.modalFooter}>
                            <button
                                className={styles.secondaryButton}
                                disabled={wizardStep === 1 || isSubmitting}
                                onClick={() =>
                                    setWizardStep(
                                        (step) =>
                                            Math.max(1, step - 1) as WizardStep
                                    )
                                }
                                type="button"
                            >
                                <ChevronLeft size={18} aria-hidden="true" />
                                Quay lại
                            </button>

                            {wizardStep < 4 ? (
                                <button
                                    className={styles.primaryButton}
                                    disabled={isNextDisabled}
                                    onClick={moveToNextStep}
                                    type="button"
                                >
                                    Tiếp tục
                                    <ChevronRight
                                        size={18}
                                        aria-hidden="true"
                                    />
                                </button>
                            ) : (
                                <button
                                    className={styles.primaryButton}
                                    disabled={isSubmitting}
                                    onClick={submitReturn}
                                    type="button"
                                >
                                    {isSubmitting
                                        ? "Đang tạo..."
                                        : "Tạo phiếu hoàn"}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            ) : null}

            {activeReturn ? (
                <ReturnDetailModal
                    detailError={detailError}
                    isDetailLoading={isDetailLoading}
                    isTransitioning={isTransitioning}
                    onClose={closeReturnDetail}
                    onRequestCancel={(returnItem) =>
                        setConfirmAction({
                            type: "cancel",
                            returnItem
                        })
                    }
                    onRequestComplete={(returnItem) =>
                        setConfirmAction({
                            type: "receive",
                            returnItem
                        })
                    }
                    onToast={setToast}
                    returnItem={activeReturn}
                />
            ) : null}

            {confirmAction ? (
                <div className={styles.confirmBackdrop} role="presentation">
                    <div
                        aria-modal="true"
                        className={styles.confirmDialog}
                        role="dialog"
                    >
                        <h2>
                            {confirmAction.type === "receive"
                                ? "Xác nhận cửa hàng đã nhận hàng trả lại từ khách?"
                                : "Hủy phiếu hoàn?"}
                        </h2>
                        <p>
                            {confirmAction.type === "receive"
                                ? "Sau khi xác nhận nhận hàng, phiếu hoàn sẽ không thể hủy."
                                : "Bạn có chắc muốn hủy phiếu hoàn hàng này?"}
                        </p>
                        <div className={styles.confirmActions}>
                            <button
                                className={styles.secondaryButton}
                                disabled={isTransitioning}
                                onClick={() => setConfirmAction(null)}
                                type="button"
                            >
                                Giữ lại
                            </button>
                            <button
                                className={
                                    confirmAction.type === "receive"
                                        ? styles.primaryButton
                                        : styles.dangerButton
                                }
                                disabled={isTransitioning}
                                onClick={executeTransition}
                                type="button"
                            >
                                {isTransitioning
                                    ? "Đang xử lý..."
                                    : "Xác nhận"}
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}

            {toast ? <div className={styles.toast}>{toast}</div> : null}
            </div>
        </section>
    );
}

function getDraftValidationError(
    order: OrderDetailResponse | null,
    returns: OrderReturnResponse[],
    draftItems: Record<string, ReturnDraftItem>
) {
    if (!order) {
        return "Vui lòng chọn đơn hàng cần hoàn.";
    }

    if (!canCreateReturn(order)) {
        return "Đơn hàng không đủ điều kiện hoàn.";
    }

    const selectedEntries = (order.items ?? [])
        .map((item, index) => ({
            index,
            item
        }))
        .filter(({ index, item }) => {
            return draftItems[getItemKey(item, index)]?.selected;
        });

    if (selectedEntries.length === 0) {
        return "Vui lòng chọn ít nhất một sản phẩm cần hoàn.";
    }

    for (const { index, item } of selectedEntries) {
        const itemKey = getItemKey(item, index);
        const draft = draftItems[itemKey];
        const quantity = getDraftQuantity(draft);
        const availableQuantity = calculateAvailableReturnQuantity(
            item,
            returns
        );

        if (quantity <= 0) {
            return "Số lượng hoàn phải lớn hơn 0.";
        }

        if (quantity > availableQuantity) {
            return "Số lượng hoàn vượt quá số lượng còn có thể hoàn.";
        }

        if (draft.reason === "CHANGE_OF_MIND" && draft.condition !== "SEALED") {
            return "Khách đổi ý chỉ áp dụng với sản phẩm còn nguyên seal.";
        }

        if (draft.reason === "OTHER" && !draft.note.trim()) {
            return "Vui lòng nhập ghi chú cho lý do khác.";
        }
    }

    return null;
}

function isOrderEligibleForReturn(
    order: Pick<OrderSummaryResponse, "paymentStatus" | "status"> | null
) {
    return (
        order?.status === "COMPLETED" &&
        (order.paymentStatus === "PAID" || order.paymentStatus === "PARTIALLY_REFUNDED")
    );
}

function getOrderIneligibilityReason(
    order: Pick<OrderSummaryResponse, "paymentStatus" | "status"> | null
) {
    if (!order || isOrderEligibleForReturn(order)) {
        return null;
    }

    if (order.status === "CANCELLED") {
        return "Đã hủy";
    }

    if (order.status === "PAYMENT_FAILED" || order.paymentStatus === "FAILED") {
        return "Thanh toán thất bại";
    }

    if (order.paymentStatus !== "PAID") {
        return "Chưa thanh toán";
    }

    return "Chưa hoàn tất";
}

function getOrderEmptyTitle(
    filter: OrderEligibilityFilter,
    hasSearchTerm: boolean
) {
    if (hasSearchTerm) {
        return "Không tìm thấy đơn hàng phù hợp.";
    }

    if (filter === "INELIGIBLE") {
        return "Không có đơn hàng không đủ điều kiện.";
    }

    if (filter === "ALL") {
        return "Không có đơn hàng.";
    }

    return "Không có đơn hàng đủ điều kiện hoàn.";
}

function getOrderEmptyDescription(
    filter: OrderEligibilityFilter,
    hasSearchTerm: boolean
) {
    if (hasSearchTerm) {
        return "Thử đổi từ khóa tìm kiếm.";
    }

    if (filter === "INELIGIBLE") {
        return "Hiện chưa có đơn hàng cần kiểm tra điều kiện hoàn.";
    }

    if (filter === "ALL") {
        return "Hiện chưa có đơn hàng trong hệ thống.";
    }

    return "Hiện chưa có đơn hàng hoàn tất và đã thanh toán.";
}

function OrderPicker({
    currentPage,
    eligibilityFilter,
    eligibilityStats,
    isLoading,
    isSelectedOrderLoading,
    onEligibilityFilterChange,
    onPageChange,
    onSearchChange,
    onSelectOrder,
    orderPage,
    searchError,
    searchTerm,
    selectedOrder,
    selectedOrderError,
    selectedOrderSummary
}: {
    currentPage: number;
    eligibilityFilter: OrderEligibilityFilter;
    eligibilityStats: OrderReturnEligibilityStatsResponse;
    isLoading: boolean;
    isSelectedOrderLoading: boolean;
    onEligibilityFilterChange: (filter: OrderEligibilityFilter) => void;
    onPageChange: (page: number) => void;
    onSearchChange: (value: string) => void;
    onSelectOrder: (order: OrderSummaryResponse) => void;
    orderPage: OrderPageResponse<OrderSummaryResponse>;
    searchError: string | null;
    searchTerm: string;
    selectedOrder: OrderDetailResponse | null;
    selectedOrderError: string | null;
    selectedOrderSummary: OrderSummaryResponse | null;
}) {
    const orderResults = orderPage.content ?? [];
    const totalPages = Math.max(orderPage.totalPages ?? 0, 1);
    const totalElements = orderPage.totalElements ?? 0;
    const hasSearchTerm = searchTerm.trim().length > 0;
    const selectedOrderReason = getOrderIneligibilityReason(selectedOrder);

    return (
        <div className={styles.orderPicker}>
            <div className={styles.orderPickerHeader}>
                <label className={styles.searchBox}>
                    <Search size={20} aria-hidden="true" />
                    <input
                        aria-label="Tìm đơn hàng để hoàn"
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Tìm mã đơn, khách hàng, SĐT..."
                        value={searchTerm}
                    />
                </label>
                <div className={styles.orderEligibilityFilter}>
                    <span className={styles.filterLabel}>Điều kiện</span>
                    <div className={styles.quickFilters}>
                        {ORDER_ELIGIBILITY_FILTERS.map((filter) => (
                            <button
                                className={`${styles.filterChip} ${
                                    eligibilityFilter === filter.key
                                        ? styles.activeFilterChip
                                        : ""
                                }`}
                                key={filter.key}
                                onClick={() =>
                                    onEligibilityFilterChange(filter.key)
                                }
                                type="button"
                            >
                                {filter.label}
                                <span>
                                    {eligibilityStats[filter.countKey] ?? 0}
                                </span>
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            <div className={styles.orderPickerGrid}>
                <div className={styles.orderResults}>
                    {isLoading ? (
                        <PanelMessage
                            description="Đang tải danh sách đơn hàng"
                            title="Đang tìm kiếm"
                        />
                    ) : null}
                    {!isLoading && searchError ? (
                        <PanelMessage
                            description={searchError}
                            title="Không tải được đơn hàng"
                        />
                    ) : null}
                    {!isLoading && !searchError
                        ? orderResults.map((order) => {
                              const ineligibilityReason =
                                  getOrderIneligibilityReason(order);

                              return (
                                  <button
                                      aria-disabled={
                                          ineligibilityReason ? true : undefined
                                      }
                                      className={`${styles.orderOption} ${
                                          selectedOrderSummary?.id === order.id
                                              ? styles.selectedOrderOption
                                              : ""
                                      } ${
                                          ineligibilityReason
                                              ? styles.disabledOrderOption
                                              : ""
                                      }`}
                                      key={order.id}
                                      onClick={() => onSelectOrder(order)}
                                      type="button"
                                  >
                                      <strong>{getOrderCode(order)}</strong>
                                      <span>{getCustomerName(order)}</span>
                                      <small>
                                          {formatDateTime(order.createdAt)} ·{" "}
                                          {getPaymentStatusText(order)}
                                      </small>
                                      {ineligibilityReason ? (
                                          <em
                                              className={
                                                  styles.ineligibleReason
                                              }
                                          >
                                              {ineligibilityReason}
                                          </em>
                                      ) : null}
                                  </button>
                              );
                          })
                        : null}
                    {!isLoading &&
                    !searchError &&
                    orderResults.length === 0 ? (
                        <PanelMessage
                            description={getOrderEmptyDescription(
                                eligibilityFilter,
                                hasSearchTerm
                            )}
                            title={getOrderEmptyTitle(
                                eligibilityFilter,
                                hasSearchTerm
                            )}
                        />
                    ) : null}
                    {!isLoading && !searchError && totalElements > 0 ? (
                        <div className={styles.orderSearchPagination}>
                            <button
                                className={styles.pageButton}
                                disabled={currentPage === 0}
                                onClick={() =>
                                    onPageChange(Math.max(0, currentPage - 1))
                                }
                                type="button"
                            >
                                <ChevronLeft size={16} aria-hidden="true" />
                            </button>
                            {getPageNumbers(totalPages, currentPage).map(
                                (page) => (
                                    <button
                                        className={`${styles.pageButton} ${
                                            page === currentPage
                                                ? styles.activePage
                                                : ""
                                        }`}
                                        key={page}
                                        onClick={() => onPageChange(page)}
                                        type="button"
                                    >
                                        {page + 1}
                                    </button>
                                )
                            )}
                            <button
                                className={styles.pageButton}
                                disabled={currentPage >= totalPages - 1}
                                onClick={() =>
                                    onPageChange(
                                        Math.min(totalPages - 1, currentPage + 1)
                                    )
                                }
                                type="button"
                            >
                                <ChevronRight size={16} aria-hidden="true" />
                            </button>
                        </div>
                    ) : null}
                </div>

                <div className={styles.selectedOrderPanel}>
                    {isSelectedOrderLoading ? (
                        <PanelMessage
                            description="Đang kiểm tra điều kiện hoàn"
                            title="Đang tải đơn đã chọn"
                        />
                    ) : null}
                    {!isSelectedOrderLoading && selectedOrderError ? (
                        <PanelMessage
                            description={selectedOrderError}
                            title="Không tải được đơn đã chọn"
                        />
                    ) : null}
                    {!isSelectedOrderLoading && selectedOrder ? (
                        <>
                            <h3>{getOrderCode(selectedOrder)}</h3>
                            <dl>
                                <div>
                                    <dt>Khách hàng</dt>
                                    <dd>{getCustomerName(selectedOrder)}</dd>
                                </div>
                                <div>
                                    <dt>Trạng thái đơn</dt>
                                    <dd>{getOrderStatusText(selectedOrder)}</dd>
                                </div>
                                <div>
                                    <dt>Ngày đặt</dt>
                                    <dd>{formatDateTime(selectedOrder.createdAt)}</dd>
                                </div>
                                <div>
                                    <dt>Thanh toán</dt>
                                    <dd>
                                        {getPaymentStatusText(selectedOrder)}
                                    </dd>
                                </div>
                            </dl>
                            {selectedOrderReason ? (
                                <p className={styles.warningNote}>
                                    {selectedOrderReason}
                                </p>
                            ) : null}
                        </>
                    ) : null}
                    {!isSelectedOrderLoading &&
                    !selectedOrder &&
                    !selectedOrderError ? (
                        <PanelMessage
                            description="Chi tiết đơn sẽ hiển thị tại đây"
                            title="Chưa chọn đơn hàng"
                        />
                    ) : null}
                </div>
            </div>
        </div>
    );
}

function ReturnItemPicker({
    draftItems,
    items,
    onUpdateDraftItem,
    returns
}: {
    draftItems: Record<string, ReturnDraftItem>;
    items: OrderItemResponse[];
    onUpdateDraftItem: (
        itemKey: string,
        update: Partial<ReturnDraftItem>
    ) => void;
    returns: OrderReturnResponse[];
}) {
    return (
        <div className={styles.itemTableWrap}>
            <table className={styles.itemTable}>
                <thead>
                    <tr>
                        <th>Sản phẩm</th>
                        <th>Đã mua</th>
                        <th>Còn được hoàn</th>
                        <th>Số lượng hoàn</th>
                    </tr>
                </thead>
                <tbody>
                    {items.map((item, index) => {
                        const itemKey = getItemKey(item, index);
                        const draft = draftItems[itemKey];
                        const availableQuantity = calculateAvailableReturnQuantity(
                            item,
                            returns
                        );

                        return (
                            <tr key={itemKey}>
                                <td>
                                    <label className={styles.itemSelector}>
                                        <input
                                            checked={Boolean(draft?.selected)}
                                            disabled={availableQuantity <= 0}
                                            onChange={(event) =>
                                                onUpdateDraftItem(itemKey, {
                                                    selected:
                                                        event.target.checked
                                                })
                                            }
                                            type="checkbox"
                                        />
                                        <span>
                                            <strong>{getItemName(item)}</strong>
                                            <small>
                                                {item.skuSnapshot || "--"} ·{" "}
                                                {getItemVariant(item)}
                                            </small>
                                        </span>
                                    </label>
                                </td>
                                <td>{formatReturnQuantity(item.quantity)}</td>
                                <td>
                                    {formatReturnQuantity(availableQuantity)}
                                </td>
                                <td>
                                    <input
                                        className={styles.quantityInput}
                                        disabled={
                                            !draft?.selected ||
                                            availableQuantity <= 0
                                        }
                                        max={availableQuantity}
                                        min="0"
                                        onChange={(event) =>
                                            onUpdateDraftItem(itemKey, {
                                                quantity: event.target.value
                                            })
                                        }
                                        type="number"
                                        value={draft?.quantity ?? "1"}
                                    />
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

function ReturnReasonForm({
    draftItems,
    items,
    onNoteChange,
    onUpdateDraftItem,
    returnNote
}: {
    draftItems: Record<string, ReturnDraftItem>;
    items: OrderItemResponse[];
    onNoteChange: (value: string) => void;
    onUpdateDraftItem: (
        itemKey: string,
        update: Partial<ReturnDraftItem>
    ) => void;
    returnNote: string;
}) {
    const selectedEntries = items
        .map((item, index) => ({
            index,
            item
        }))
        .filter(({ index, item }) => {
            return draftItems[getItemKey(item, index)]?.selected;
        });

    return (
        <div className={styles.reasonForm}>
            {selectedEntries.map(({ index, item }) => {
                const itemKey = getItemKey(item, index);
                const draft = draftItems[itemKey] ?? createDefaultDraftItem();

                return (
                    <div className={styles.reasonRow} key={itemKey}>
                        <div className={styles.reasonTitle}>
                            <strong>{getItemName(item)}</strong>
                            <span>
                                {formatReturnQuantity(getDraftQuantity(draft))}{" "}
                                sản phẩm
                            </span>
                        </div>
                        <div className={styles.reasonGrid}>
                            <label>
                                <span>Lý do</span>
                                <select
                                    onChange={(event) =>
                                        onUpdateDraftItem(itemKey, {
                                            reason: event.target
                                                .value as ReturnReason
                                        })
                                    }
                                    value={draft.reason}
                                >
                                    {RETURN_REASONS.map((reason) => (
                                        <option key={reason} value={reason}>
                                            {RETURN_REASON_LABELS[reason]}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label>
                                <span>Tình trạng</span>
                                <select
                                    disabled={draft.reason === "CHANGE_OF_MIND"}
                                    onChange={(event) =>
                                        onUpdateDraftItem(itemKey, {
                                            condition: event.target
                                                .value as ReturnCondition
                                        })
                                    }
                                    value={draft.condition}
                                >
                                    {RETURN_CONDITIONS.map((condition) => (
                                        <option
                                            key={condition}
                                            value={condition}
                                        >
                                            {RETURN_CONDITION_LABELS[condition]}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label className={styles.fullField}>
                                <span>Ghi chú sản phẩm</span>
                                <textarea
                                    onChange={(event) =>
                                        onUpdateDraftItem(itemKey, {
                                            note: event.target.value
                                        })
                                    }
                                    placeholder={
                                        draft.reason === "OTHER"
                                            ? "Bắt buộc khi chọn lý do khác"
                                            : "Ghi chú thêm nếu có"
                                    }
                                    value={draft.note}
                                />
                            </label>
                        </div>
                    </div>
                );
            })}

            <label className={styles.fullField}>
                <span>Ghi chú phiếu hoàn</span>
                <textarea
                    onChange={(event) => onNoteChange(event.target.value)}
                    placeholder="Ghi chú chung nếu có"
                    value={returnNote}
                />
            </label>
        </div>
    );
}

function ReturnConfirmation({
    draftItems,
    items,
    order,
    returnNote
}: {
    draftItems: Record<string, ReturnDraftItem>;
    items: OrderItemResponse[];
    order: OrderDetailResponse;
    returnNote: string;
}) {
    const selectedEntries = items
        .map((item, index) => ({
            index,
            item
        }))
        .filter(({ index, item }) => {
            return draftItems[getItemKey(item, index)]?.selected;
        });

    return (
        <div className={styles.confirmation}>
            <div className={styles.summaryPanel}>
                <h3>{getOrderCode(order)}</h3>
                <p>{getCustomerName(order)}</p>
                {returnNote.trim() ? <span>{returnNote.trim()}</span> : null}
            </div>

            <div className={styles.confirmItems}>
                {selectedEntries.map(({ index, item }) => {
                    const itemKey = getItemKey(item, index);
                    const draft = draftItems[itemKey] ?? createDefaultDraftItem();

                    return (
                        <div className={styles.confirmItem} key={itemKey}>
                            <strong>{getItemName(item)}</strong>
                            <span>
                                SL {formatReturnQuantity(getDraftQuantity(draft))}
                            </span>
                            <small>
                                {item.skuSnapshot || "--"} ·{" "}
                                {getReturnReasonLabel(draft.reason)} ·{" "}
                                {getReturnConditionLabel(draft.condition)}
                            </small>
                            {draft.note.trim() ? (
                                <p>{draft.note.trim()}</p>
                            ) : null}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

function ReturnDetailModal({
    detailError,
    isDetailLoading,
    isTransitioning,
    onClose,
    onRequestCancel,
    onRequestComplete,
    onToast,
    returnItem
}: {
    detailError: string | null;
    isDetailLoading: boolean;
    isTransitioning: boolean;
    onClose: () => void;
    onRequestCancel: (returnItem: OrderReturnListRow) => void;
    onRequestComplete: (returnItem: OrderReturnListRow) => void;
    onToast: (message: string) => void;
    returnItem: OrderReturnListRow;
}) {
    const canTransition = returnItem.status === "CREATED";
    const [qcSummary, setQcSummary] =
        useState<CustomerReturnQcSummaryResponse | null>(null);
    const [isQcSummaryLoading, setIsQcSummaryLoading] = useState(false);
    const [qcSummaryError, setQcSummaryError] = useState<string | null>(null);
    const [qcReloadKey, setQcReloadKey] = useState(0);
    const [isQcOpen, setIsQcOpen] = useState(false);
    const [qcDraftLines, setQcDraftLines] = useState<Record<string, QcDraftLine>>(
        {}
    );
    const [qcNote, setQcNote] = useState("");
    const [qcFormError, setQcFormError] = useState<string | null>(null);
    const [activeLocations, setActiveLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [quarantineLocations, setQuarantineLocations] = useState<
        InventoryLocationResponse[]
    >([]);
    const [isLocationLoading, setIsLocationLoading] = useState(false);
    const [locationError, setLocationError] = useState<string | null>(null);
    const [isCreatingInspection, setIsCreatingInspection] = useState(false);
    const [createdInspection, setCreatedInspection] =
        useState<CustomerReturnInspectionResponse | null>(null);
    const [isPostingInspection, setIsPostingInspection] = useState(false);

    const branchId = qcSummary?.branchId ?? returnItem.branchId ?? null;
    const summaryLines = useMemo(
        () => qcSummary?.lines ?? [],
        [qcSummary?.lines]
    );
    const remainingLines = useMemo(
        () =>
            summaryLines.filter(
                (line) => toReturnNumber(line.remainingQcQuantity) > 0
            ),
        [summaryLines]
    );
    const restockLocations = activeLocations.filter(isSaleableReturnLocation);
    const normalizedQuarantineLocations = quarantineLocations.filter(
        (location) =>
            location.status === "ACTIVE" && location.type === "QUARANTINE"
    );
    const isQcCompleted = qcSummary?.qcStatus === "COMPLETED";

    useEffect(() => {
        const timerId = window.setTimeout(() => {
            setQcSummary(null);
            setQcSummaryError(null);
            setIsQcOpen(false);
            setCreatedInspection(null);
            setQcDraftLines({});
            setQcNote("");
            setQcFormError(null);
            setActiveLocations([]);
            setQuarantineLocations([]);
            setLocationError(null);
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [returnItem.id]);

    useEffect(() => {
        if (returnItem.status !== "RECEIVED") {
            const timerId = window.setTimeout(() => {
                setQcSummary(null);
                setQcSummaryError(null);
                setIsQcSummaryLoading(false);
            }, 0);

            return () => window.clearTimeout(timerId);
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsQcSummaryLoading(true);
            setQcSummaryError(null);

            getCustomerReturnQcSummary(returnItem.id)
                .then((summary) => {
                    if (!ignore) {
                        setQcSummary(summary);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setQcSummary(null);
                        setQcSummaryError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsQcSummaryLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [qcReloadKey, returnItem.id, returnItem.status]);

    useEffect(() => {
        if (!isQcOpen || !branchId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsLocationLoading(true);
            setLocationError(null);

            Promise.all([
                inventoryLocationApi.list({
                    branchId,
                    page: 0,
                    size: 100,
                    status: "ACTIVE"
                }),
                inventoryLocationApi.list({
                    branchId,
                    page: 0,
                    size: 100,
                    status: "ACTIVE",
                    type: "QUARANTINE"
                })
            ])
                .then(([activePage, quarantinePage]) => {
                    if (!ignore) {
                        setActiveLocations(activePage.content ?? []);
                        setQuarantineLocations(quarantinePage.content ?? []);
                    }
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setActiveLocations([]);
                        setQuarantineLocations([]);
                        setLocationError(getReturnErrorMessage(error));
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsLocationLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [branchId, isQcOpen]);

    useEffect(() => {
        if (!isQcOpen || createdInspection) {
            return;
        }

        const timerId = window.setTimeout(() => {
            setQcDraftLines((current) => {
                const nextEntries = remainingLines.map((line) => [
                    line.returnItemId,
                    current[line.returnItemId] ?? createDefaultQcDraftLine(line)
                ]);

                return Object.fromEntries(nextEntries);
            });
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [createdInspection, isQcOpen, remainingLines]);

    const openQcForm = () => {
        if (returnItem.status !== "RECEIVED") {
            onToast("Phiếu hoàn chưa ở trạng thái đã nhận hàng.");
            return;
        }

        if (isQcCompleted) {
            onToast("Đã kiểm tra xong hàng trả.");
            return;
        }

        setIsQcOpen(true);
        setCreatedInspection(null);
        setQcFormError(null);
    };

    const updateQcDraftLine = (
        returnItemId: string,
        update: Partial<QcDraftLine>
    ) => {
        setQcDraftLines((current) => {
            const line = summaryLines.find(
                (summaryLine) => summaryLine.returnItemId === returnItemId
            );
            const currentLine =
                current[returnItemId] ?? createDefaultQcDraftLine(line);
            const nextLine = {
                ...currentLine,
                ...update
            };

            if (update.result) {
                nextLine.destinationLocationId = "";
            }

            return {
                ...current,
                [returnItemId]: nextLine
            };
        });
    };

    const getLocationOptions = (result: CustomerReturnQcResult) => {
        if (result === "QUARANTINE") {
            return normalizedQuarantineLocations;
        }

        if (result === "RESTOCK") {
            return restockLocations;
        }

        return [];
    };

    const validateQcDraft = () => {
        if (!branchId) {
            return "Phiếu hoàn chưa có thông tin chi nhánh để chọn vị trí kho.";
        }

        const selectedLines = remainingLines.filter(
            (line) => qcDraftLines[line.returnItemId]?.selected
        );

        if (selectedLines.length === 0) {
            return "Vui lòng chọn ít nhất một sản phẩm cần kiểm tra.";
        }

        for (const line of selectedLines) {
            const draft =
                qcDraftLines[line.returnItemId] ?? createDefaultQcDraftLine(line);
            const quantity = getQcQuantity(draft);
            const remainingQuantity = toReturnNumber(line.remainingQcQuantity);
            const allowedResults = getAllowedCustomerReturnQcResults(
                line.condition
            );

            if (quantity <= 0) {
                return "Số lượng kiểm tra phải lớn hơn 0.";
            }

            if (quantity > remainingQuantity) {
                return "Số lượng kiểm tra vượt quá số lượng còn lại.";
            }

            if (!allowedResults.includes(draft.result)) {
                return "Tình trạng hàng này không được nhập lại tồn bán được.";
            }

            if (draft.result === "RESTOCK") {
                const isValidLocation = restockLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return "Vị trí nhập lại không phải vị trí có thể bán.";
                }
            }

            if (draft.result === "QUARANTINE") {
                if (normalizedQuarantineLocations.length === 0) {
                    return "Chi nhánh chưa có vị trí cách ly hàng.";
                }

                const isValidLocation = normalizedQuarantineLocations.some(
                    (location) =>
                        String(location.id) === draft.destinationLocationId
                );

                if (!draft.destinationLocationId || !isValidLocation) {
                    return "Vui lòng chọn vị trí cách ly.";
                }
            }

            if (
                line.condition === "SEALED" &&
                draft.result === "WRITE_OFF" &&
                draft.note.trim().length < 5 &&
                qcNote.trim().length < 5
            ) {
                return "Vui lòng nhập lý do khi loại bỏ sản phẩm còn nguyên seal.";
            }
        }

        return null;
    };

    const createInspection = () => {
        const validationError = validateQcDraft();

        if (validationError) {
            setQcFormError(validationError);
            return;
        }

        setQcFormError(null);
        setIsCreatingInspection(true);

        createCustomerReturnInspection(returnItem.id, {
            note: qcNote.trim() || null,
            lines: remainingLines
                .filter((line) => qcDraftLines[line.returnItemId]?.selected)
                .map((line) => {
                    const draft =
                        qcDraftLines[line.returnItemId] ??
                        createDefaultQcDraftLine(line);

                    return {
                        returnItemId: line.returnItemId,
                        quantity: getQcQuantity(draft),
                        result: draft.result,
                        destinationLocationId:
                            draft.result === "WRITE_OFF"
                                ? null
                                : draft.destinationLocationId,
                        note: draft.note.trim() || null
                    };
                })
        })
            .then((inspection) => {
                setCreatedInspection(inspection);
                onToast("Đã tạo phiếu kiểm tra hàng trả.");
            })
            .catch((error: unknown) => {
                setQcFormError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsCreatingInspection(false);
            });
    };

    const postInspection = () => {
        if (!createdInspection) {
            return;
        }

        const confirmed = window.confirm(
            "Xác nhận xử lý tồn kho theo kết quả kiểm tra?\n\nThao tác này sẽ cập nhật tồn kho và không nên thực hiện lặp lại."
        );

        if (!confirmed) {
            return;
        }

        setIsPostingInspection(true);
        postCustomerReturnInspection(createdInspection.id)
            .then(() => {
                onToast("Đã xử lý tồn kho theo phiếu kiểm tra.");
                setCreatedInspection(null);
                setIsQcOpen(false);
                setQcNote("");
                setQcDraftLines({});
                setQcReloadKey((key) => key + 1);
            })
            .catch((error: unknown) => {
                setQcFormError(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsPostingInspection(false);
            });
    };

    const getDestinationName = (locationId?: string | null) => {
        if (!locationId) {
            return "--";
        }

        const location = [...activeLocations, ...quarantineLocations].find(
            (item) => String(item.id) === locationId
        );

        return location ? getLocationOptionLabel(location) : "--";
    };

    return (
        <div
            className={styles.modalBackdrop}
            onClick={onClose}
            role="presentation"
        >
            <aside
                aria-label="Chi tiết phiếu hoàn"
                aria-modal="true"
                className={styles.detailModal}
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className={styles.modalHeader}>
                    <div>
                        <h2>{getReturnCode(returnItem)}</h2>
                        <p>{returnItem.orderCode || "Đơn hàng"}</p>
                    </div>
                    <button
                        aria-label="Đóng chi tiết phiếu hoàn"
                        className={styles.closeButton}
                        onClick={onClose}
                        type="button"
                    >
                        <X size={18} aria-hidden="true" />
                    </button>
                </div>

                {isDetailLoading ? (
                    <PanelMessage
                        description="Đang tải thông tin phiếu hoàn"
                        title="Đang tải chi tiết"
                    />
                ) : null}
                {!isDetailLoading && detailError ? (
                    <PanelMessage
                        description={detailError}
                        title="Không tải được chi tiết"
                    />
                ) : null}
                {!isDetailLoading && !detailError ? (
                    <>
                        <section className={styles.detailSection}>
                            <div className={styles.detailInfoGrid}>
                                <InfoRow
                                    label="Trạng thái"
                                    value={
                                        <ReturnStatusBadge
                                            status={returnItem.status}
                                        />
                                    }
                                />
                                <InfoRow
                                    label="Khách hàng"
                                    value={returnItem.customerName || "--"}
                                />
                                <InfoRow
                                    label="Ngày tạo"
                                    value={formatDateTime(returnItem.createdAt)}
                                />
                                <InfoRow
                                    label="Cập nhật"
                                    value={formatDateTime(returnItem.updatedAt)}
                                />
                            </div>
                            {returnItem.note ? (
                                <p className={styles.detailNote}>
                                    {returnItem.note}
                                </p>
                            ) : null}
                        </section>

                        <section className={styles.detailSection}>
                            <h3>Sản phẩm hoàn</h3>
                            <div className={styles.detailItems}>
                                {(returnItem.items ?? []).map((item, index) => (
                                    <div
                                        className={styles.detailItem}
                                        key={item.id ?? index}
                                    >
                                        <div>
                                            <strong>
                                                {item.productNameSnapshot ||
                                                    "Sản phẩm"}
                                            </strong>
                                            <span>
                                                {item.skuSnapshot || "--"} ·{" "}
                                                {item.variantNameSnapshot ||
                                                    "Không có phân loại"}
                                            </span>
                                        </div>
                                        <div>
                                            <strong>
                                                SL{" "}
                                                {formatReturnQuantity(
                                                    item.quantity
                                                )}
                                            </strong>
                                            <small>
                                                {getReturnReasonLabel(
                                                    item.reason
                                                )}{" "}
                                                ·{" "}
                                                {getReturnConditionLabel(
                                                    item.condition
                                                )}
                                            </small>
                                        </div>
                                        {item.note ? <p>{item.note}</p> : null}
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className={styles.detailSection}>
                            <div className={styles.qcHeader}>
                                <div>
                                    <h3>Kiểm tra hàng trả</h3>
                                    {returnItem.status === "RECEIVED" ? (
                                        <p>
                                            Trạng thái xử lý kho:{" "}
                                            <strong>
                                                {isQcSummaryLoading
                                                    ? "Đang tải..."
                                                    : getCustomerReturnQcStatusLabel(
                                                          qcSummary?.qcStatus
                                                      )}
                                            </strong>
                                        </p>
                                    ) : null}
                                </div>
                                {returnItem.status === "RECEIVED" &&
                                qcSummary &&
                                !isQcCompleted ? (
                                    <button
                                        className={styles.primaryButton}
                                        disabled={
                                            isQcSummaryLoading ||
                                            isCreatingInspection ||
                                            isPostingInspection
                                        }
                                        onClick={openQcForm}
                                        type="button"
                                    >
                                        <Plus size={18} aria-hidden="true" />
                                        Kiểm tra hàng trả
                                    </button>
                                ) : null}
                            </div>

                            {returnItem.status === "CREATED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn chưa được nhận hàng nên chưa thể QC."
                                    title="Chưa được QC"
                                />
                            ) : null}
                            {returnItem.status === "CANCELLED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn đã hủy nên không được QC."
                                    title="Không được QC"
                                />
                            ) : null}
                            {returnItem.status === "COMPLETED" ? (
                                <PanelMessage
                                    description="Phiếu hoàn đã hoàn tất nên không tạo lượt QC mới."
                                    title="Không tạo QC mới"
                                />
                            ) : null}

                            {returnItem.status === "RECEIVED" ? (
                                <>
                                    {isQcSummaryLoading ? (
                                        <PanelMessage
                                            description="Đang tải số lượng đã kiểm tra và còn lại."
                                            title="Đang tải QC summary"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading && qcSummaryError ? (
                                        <PanelMessage
                                            description={qcSummaryError}
                                            title="Không tải được QC summary"
                                        />
                                    ) : null}
                                    {!isQcSummaryLoading &&
                                    !qcSummaryError &&
                                    qcSummary ? (
                                        <div className={styles.qcSummaryList}>
                                            {summaryLines.map((line) => (
                                                <div
                                                    className={styles.qcSummaryItem}
                                                    key={line.returnItemId}
                                                >
                                                    <div>
                                                        <strong>
                                                            {getReturnLineName(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </strong>
                                                        <span>
                                                            {getReturnLineMeta(
                                                                returnItem,
                                                                line
                                                            )}
                                                        </span>
                                                    </div>
                                                    <div>
                                                        <span>
                                                            Đã trả:{" "}
                                                            {formatReturnQuantity(
                                                                line.returnedQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Đã kiểm tra:{" "}
                                                            {formatReturnQuantity(
                                                                line.postedQcQuantity
                                                            )}
                                                        </span>
                                                        <span>
                                                            Còn lại:{" "}
                                                            {formatReturnQuantity(
                                                                line.remainingQcQuantity
                                                            )}
                                                        </span>
                                                    </div>
                                                </div>
                                            ))}
                                            {isQcCompleted ? (
                                                <p className={styles.qcDoneNote}>
                                                    Đã kiểm tra xong hàng trả
                                                </p>
                                            ) : null}
                                        </div>
                                    ) : null}

                                    {isQcOpen ? (
                                        <div className={styles.qcPanel}>
                                            <div className={styles.qcPanelHeader}>
                                                <div>
                                                    <strong>
                                                        Tạo phiếu kiểm tra
                                                    </strong>
                                                    <span>
                                                        Chỉ hiển thị sản phẩm còn
                                                        số lượng chưa QC.
                                                    </span>
                                                </div>
                                                <button
                                                    className={styles.secondaryButton}
                                                    disabled={
                                                        isCreatingInspection ||
                                                        isPostingInspection
                                                    }
                                                    onClick={() => {
                                                        setIsQcOpen(false);
                                                        setCreatedInspection(null);
                                                        setQcFormError(null);
                                                    }}
                                                    type="button"
                                                >
                                                    Đóng
                                                </button>
                                            </div>

                                            {locationError ? (
                                                <p className={styles.qcError}>
                                                    {locationError}
                                                </p>
                                            ) : null}
                                            {isLocationLoading ? (
                                                <PanelMessage
                                                    description="Đang tải vị trí nhập lại và cách ly của chi nhánh."
                                                    title="Đang tải vị trí kho"
                                                />
                                            ) : null}

                                            {!createdInspection ? (
                                                <>
                                                    <div
                                                        className={
                                                            styles.qcFormList
                                                        }
                                                    >
                                                        {remainingLines.map(
                                                            (line) => {
                                                                const draft =
                                                                    qcDraftLines[
                                                                        line
                                                                            .returnItemId
                                                                    ] ??
                                                                    createDefaultQcDraftLine(
                                                                        line
                                                                    );
                                                                const allowedResults =
                                                                    getAllowedCustomerReturnQcResults(
                                                                        line.condition
                                                                    );
                                                                const locationOptions =
                                                                    getLocationOptions(
                                                                        draft.result
                                                                    );

                                                                return (
                                                                    <div
                                                                        className={
                                                                            styles.qcFormItem
                                                                        }
                                                                        key={
                                                                            line.returnItemId
                                                                        }
                                                                    >
                                                                        <label
                                                                            className={
                                                                                styles.qcItemSelector
                                                                            }
                                                                        >
                                                                            <input
                                                                                checked={
                                                                                    draft.selected
                                                                                }
                                                                                onChange={(
                                                                                    event
                                                                                ) =>
                                                                                    updateQcDraftLine(
                                                                                        line.returnItemId,
                                                                                        {
                                                                                            selected:
                                                                                                event
                                                                                                    .target
                                                                                                    .checked
                                                                                        }
                                                                                    )
                                                                                }
                                                                                type="checkbox"
                                                                            />
                                                                            <span>
                                                                                <strong>
                                                                                    {getReturnLineName(
                                                                                        returnItem,
                                                                                        line
                                                                                    )}
                                                                                </strong>
                                                                                <small>
                                                                                    {getReturnLineMeta(
                                                                                        returnItem,
                                                                                        line
                                                                                    )}
                                                                                </small>
                                                                            </span>
                                                                        </label>
                                                                        <div
                                                                            className={
                                                                                styles.qcMetaGrid
                                                                            }
                                                                        >
                                                                            <InfoRow
                                                                                label="Tình trạng"
                                                                                value={getReturnConditionLabel(
                                                                                    line.condition
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Lý do"
                                                                                value={getReturnLineReasonLabel(
                                                                                    line.reason
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Đã trả"
                                                                                value={formatReturnQuantity(
                                                                                    line.returnedQuantity
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Đã kiểm tra"
                                                                                value={formatReturnQuantity(
                                                                                    line.postedQcQuantity
                                                                                )}
                                                                            />
                                                                            <InfoRow
                                                                                label="Còn lại"
                                                                                value={formatReturnQuantity(
                                                                                    line.remainingQcQuantity
                                                                                )}
                                                                            />
                                                                        </div>
                                                                        <div
                                                                            className={
                                                                                styles.qcInputGrid
                                                                            }
                                                                        >
                                                                            <label>
                                                                                <span>
                                                                                    Số lượng QC
                                                                                    lần
                                                                                    này
                                                                                </span>
                                                                                <input
                                                                                    className={
                                                                                        styles.quantityInput
                                                                                    }
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    max={toReturnNumber(
                                                                                        line.remainingQcQuantity
                                                                                    )}
                                                                                    min="0"
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                quantity:
                                                                                                    event
                                                                                                        .target
                                                                                                        .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    type="number"
                                                                                    value={
                                                                                        draft.quantity
                                                                                    }
                                                                                />
                                                                            </label>
                                                                            <label>
                                                                                <span>
                                                                                    Kết quả QC
                                                                                </span>
                                                                                <select
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                result: event
                                                                                                    .target
                                                                                                    .value as CustomerReturnQcResult
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    value={
                                                                                        draft.result
                                                                                    }
                                                                                >
                                                                                    {CUSTOMER_RETURN_QC_RESULTS.map(
                                                                                        (
                                                                                            result
                                                                                        ) => (
                                                                                            <option
                                                                                                disabled={
                                                                                                    !allowedResults.includes(
                                                                                                        result
                                                                                                    )
                                                                                                }
                                                                                                key={
                                                                                                    result
                                                                                                }
                                                                                                value={
                                                                                                    result
                                                                                                }
                                                                                            >
                                                                                                {getCustomerReturnQcResultLabel(
                                                                                                    result
                                                                                                )}
                                                                                            </option>
                                                                                        )
                                                                                    )}
                                                                                </select>
                                                                            </label>
                                                                            <label>
                                                                                <span>
                                                                                    Vị trí đích
                                                                                </span>
                                                                                <select
                                                                                    disabled={
                                                                                        !draft.selected ||
                                                                                        draft.result ===
                                                                                            "WRITE_OFF" ||
                                                                                        isLocationLoading
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                destinationLocationId:
                                                                                                    event
                                                                                                        .target
                                                                                                        .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    value={
                                                                                        draft.destinationLocationId
                                                                                    }
                                                                                >
                                                                                    <option value="">
                                                                                        Chọn vị trí
                                                                                    </option>
                                                                                    {locationOptions.map(
                                                                                        (
                                                                                            location
                                                                                        ) => (
                                                                                            <option
                                                                                                key={String(
                                                                                                    location.id
                                                                                                )}
                                                                                                value={String(
                                                                                                    location.id
                                                                                                )}
                                                                                            >
                                                                                                {getLocationOptionLabel(
                                                                                                    location
                                                                                                )}
                                                                                            </option>
                                                                                        )
                                                                                    )}
                                                                                </select>
                                                                            </label>
                                                                            <label
                                                                                className={
                                                                                    styles.fullField
                                                                                }
                                                                            >
                                                                                <span>
                                                                                    Ghi chú dòng
                                                                                </span>
                                                                                <textarea
                                                                                    disabled={
                                                                                        !draft.selected
                                                                                    }
                                                                                    onChange={(
                                                                                        event
                                                                                    ) =>
                                                                                        updateQcDraftLine(
                                                                                            line.returnItemId,
                                                                                            {
                                                                                                note: event
                                                                                                    .target
                                                                                                    .value
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                    placeholder="Ghi chú tình trạng kiểm tra"
                                                                                    value={
                                                                                        draft.note
                                                                                    }
                                                                                />
                                                                            </label>
                                                                        </div>
                                                                        <p
                                                                            className={
                                                                                styles.qcEffect
                                                                            }
                                                                        >
                                                                            {
                                                                                CUSTOMER_RETURN_QC_RESULT_EFFECTS[
                                                                                    draft
                                                                                        .result
                                                                                ]
                                                                            }
                                                                        </p>
                                                                    </div>
                                                                );
                                                            }
                                                        )}
                                                    </div>

                                                    {remainingLines.length ===
                                                    0 ? (
                                                        <PanelMessage
                                                            description="Không còn sản phẩm cần QC trong phiếu hoàn này."
                                                            title="Đã kiểm tra xong"
                                                        />
                                                    ) : null}

                                                    <label
                                                        className={
                                                            styles.fullField
                                                        }
                                                    >
                                                        <span>
                                                            Ghi chú phiếu kiểm tra
                                                        </span>
                                                        <textarea
                                                            onChange={(event) =>
                                                                setQcNote(
                                                                    event.target
                                                                        .value
                                                                )
                                                            }
                                                            placeholder="Ghi chú chung nếu có"
                                                            value={qcNote}
                                                        />
                                                    </label>
                                                </>
                                            ) : (
                                                <div
                                                    className={
                                                        styles.qcReviewPanel
                                                    }
                                                >
                                                    <div>
                                                        <strong>
                                                            {createdInspection.inspectionCode ||
                                                                "Phiếu kiểm tra"}
                                                        </strong>
                                                        <span>
                                                            Trạng thái:{" "}
                                                            {createdInspection.status ||
                                                                "CREATED"}
                                                        </span>
                                                    </div>
                                                    <div
                                                        className={
                                                            styles.qcReviewList
                                                        }
                                                    >
                                                        {(
                                                            createdInspection.lines ??
                                                            []
                                                        ).map((line) => {
                                                            const summaryLine =
                                                                summaryLines.find(
                                                                    (item) =>
                                                                        item.returnItemId ===
                                                                        line.returnItemId
                                                                );

                                                            return (
                                                                <div
                                                                    className={
                                                                        styles.qcReviewItem
                                                                    }
                                                                    key={line.id}
                                                                >
                                                                    <strong>
                                                                        {summaryLine
                                                                            ? getReturnLineName(
                                                                                  returnItem,
                                                                                  summaryLine
                                                                              )
                                                                            : "Sản phẩm"}
                                                                    </strong>
                                                                    <span>
                                                                        SL{" "}
                                                                        {formatReturnQuantity(
                                                                            line.quantity
                                                                        )}{" "}
                                                                        ·{" "}
                                                                        {getCustomerReturnQcResultLabel(
                                                                            line.result
                                                                        )}
                                                                    </span>
                                                                    <small>
                                                                        Vị trí:{" "}
                                                                        {getDestinationName(
                                                                            line.destinationLocationId
                                                                        )}
                                                                    </small>
                                                                    {line.note ? (
                                                                        <p>
                                                                            {
                                                                                line.note
                                                                            }
                                                                        </p>
                                                                    ) : null}
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            )}

                                            {qcFormError ? (
                                                <p className={styles.qcError}>
                                                    {qcFormError}
                                                </p>
                                            ) : null}

                                            <div className={styles.qcActions}>
                                                {!createdInspection ? (
                                                    <button
                                                        className={
                                                            styles.primaryButton
                                                        }
                                                        disabled={
                                                            isCreatingInspection ||
                                                            isLocationLoading ||
                                                            remainingLines.length ===
                                                                0
                                                        }
                                                        onClick={
                                                            createInspection
                                                        }
                                                        type="button"
                                                    >
                                                        {isCreatingInspection
                                                            ? "Đang tạo..."
                                                            : "Tạo phiếu kiểm tra"}
                                                    </button>
                                                ) : (
                                                    <button
                                                        className={
                                                            styles.primaryButton
                                                        }
                                                        disabled={
                                                            isPostingInspection
                                                        }
                                                        onClick={
                                                            postInspection
                                                        }
                                                        type="button"
                                                    >
                                                        <CheckCircle2
                                                            size={18}
                                                            aria-hidden="true"
                                                        />
                                                        {isPostingInspection
                                                            ? "Đang xử lý..."
                                                            : "Xác nhận xử lý kho"}
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    ) : null}
                                </>
                            ) : null}
                        </section>

                        {canTransition ? (
                            <div className={styles.detailActions}>
                                <button
                                    className={styles.secondaryButton}
                                    disabled={isTransitioning}
                                    onClick={() => onRequestCancel(returnItem)}
                                    type="button"
                                >
                                    <XCircle size={18} aria-hidden="true" />
                                    Hủy phiếu
                                </button>
                                <button
                                    className={styles.primaryButton}
                                    disabled={isTransitioning}
                                    onClick={() =>
                                        onRequestComplete(returnItem)
                                    }
                                    type="button"
                                >
                                    <CheckCircle2 size={18} aria-hidden="true" />
                                    Xác nhận nhận hàng
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </aside>
        </div>
    );
}

function ReturnStatusBadge({ status }: { status?: ReturnStatus | null }) {
    const statusClass =
        status === "RECEIVED"
            ? styles.completedBadge
            : status === "COMPLETED"
                ? styles.completedBadge
                : status === "CANCELLED"
                    ? styles.cancelledBadge
                    : styles.createdBadge;

    return (
        <span className={`${styles.statusBadge} ${statusClass}`}>
            {getReturnStatusLabel(status)}
        </span>
    );
}

function ReturnTableMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <tr>
            <td className={styles.emptyCell} colSpan={6}>
                <strong>{title}</strong>
                <span>{description}</span>
            </td>
        </tr>
    );
}

function PanelMessage({
    description,
    title
}: {
    description: string;
    title: string;
}) {
    return (
        <div className={styles.panelMessage}>
            <strong>{title}</strong>
            <span>{description}</span>
        </div>
    );
}

function InfoRow({
    label,
    value
}: {
    label: string;
    value: ReactNode;
}) {
    return (
        <div className={styles.infoRow}>
            <span>{label}</span>
            <strong>{value || "--"}</strong>
        </div>
    );
}
