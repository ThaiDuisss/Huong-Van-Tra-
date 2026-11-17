import {
    ArrowLeft,
    CalendarDays,
    Check,
    ChevronLeft,
    ChevronRight,
    Info,
    LoaderCircle
} from "lucide-react";
import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { TablePagination } from "../../../components/shared/list/TablePagination";
import { Button } from "../../../components/ui/Button";
import { Field, TextArea, TextInput } from "../../../components/ui/Field";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge, type BadgeTone } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import { routePaths } from "../../../routes/path";

import { getOrderById, getOrders } from "../services/order.api";
import { checkOrderReturnEligibility, createOrderReturn, getOrderReturns } from "../services/order-return.api";
import { ReturnProductSelectionTable } from "../components/ReturnProductSelectionTable";
import { ReturnProductReasonCard } from "../components/ReturnProductReasonCard";
import { ReturnConfirmationSummary } from "../components/ReturnConfirmationSummary";
import { ReturnEligibilityStatus } from "../components/ReturnEligibilityStatus";
import { ReturnPolicyModal } from "../components/ReturnPolicyModal";
import type {
    OrderDetailResponse,
    OrderItemResponse,
    OrderPageResponse,
    OrderQueryParams,
    OrderSummaryResponse
} from "../types/order.types";
import type {
    CreateOrderReturnPayload,
    CheckOrderReturnEligibilityPayload,
    OrderReturnEligibilityResponse,
    OrderReturnResponse
} from "../types/order-return.types";
import {
    getReturnErrorMessage
} from "../utils/order-return.utils";
import {
    formatCurrency,
    formatDateTime,
    getCustomerName,
    getOrderCode
} from "../utils/order-formatters";
import styles from "./CreateReturnPage.module.css";
import {
    buildReturnPayloadItems,
    getReturnItemKey as getItemKey,
    validateReturnDraftQuantities,
    type ReturnDraftItem
} from "../utils/return-quantity.validation";

type WizardStep = 1 | 2 | 3 | 4;
type OrderSearchTimeFilter =
    | "ALL"
    | "TODAY"
    | "YESTERDAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "CUSTOM";

const ORDER_SEARCH_PAGE_SIZE = 6;
const ORDER_SEARCH_DEFAULT_SORT: OrderQueryParams["sort"] = "createdAt,desc";

const ORDER_SEARCH_TIME_FILTERS: Array<{
    label: string;
    value: OrderSearchTimeFilter;
}> = [
    { label: "Tất cả thời gian", value: "ALL" },
    { label: "Hôm nay", value: "TODAY" },
    { label: "Hôm qua", value: "YESTERDAY" },
    { label: "7 ngày gần đây", value: "LAST_7_DAYS" },
    { label: "30 ngày gần đây", value: "LAST_30_DAYS" },
    { label: "Tùy chọn khoảng ngày", value: "CUSTOM" }
];

const WIZARD_STEPS: Array<{ step: WizardStep; label: string }> = [
    { step: 1, label: "Chọn đơn" },
    { step: 2, label: "Chọn sản phẩm" },
    { step: 3, label: "Lý do" },
    { step: 4, label: "Xác nhận" }
];

const emptyOrderSearchPage: OrderPageResponse<OrderSummaryResponse> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: ORDER_SEARCH_PAGE_SIZE
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

function padDatePart(value: number) {
    return String(value).padStart(2, "0");
}

function formatLocalDate(date: Date) {
    return `${date.getFullYear()}-${padDatePart(
        date.getMonth() + 1
    )}-${padDatePart(date.getDate())}`;
}

function addDays(date: Date, days: number) {
    const result = new Date(date);
    result.setDate(result.getDate() + days);

    return result;
}

function getOrderSearchDateRange(
    filter: OrderSearchTimeFilter,
    customFromDate: string,
    customToDate: string
) {
    const today = new Date();
    const todayDate = formatLocalDate(today);

    if (filter === "TODAY") {
        return { fromDate: todayDate, toDate: todayDate };
    }

    if (filter === "YESTERDAY") {
        const yesterday = formatLocalDate(addDays(today, -1));

        return { fromDate: yesterday, toDate: yesterday };
    }

    if (filter === "LAST_7_DAYS") {
        return {
            fromDate: formatLocalDate(addDays(today, -6)),
            toDate: todayDate
        };
    }

    if (filter === "LAST_30_DAYS") {
        return {
            fromDate: formatLocalDate(addDays(today, -29)),
            toDate: todayDate
        };
    }

    if (filter === "CUSTOM") {
        return {
            fromDate: customFromDate || undefined,
            toDate: customToDate || undefined
        };
    }

    return {};
}

function getCustomerDisplay(order: OrderSummaryResponse) {
    const customerName = getCustomerName(order);
    const customerPhone = order.customerPhone?.trim();

    return customerPhone ? `${customerName} · ${customerPhone}` : customerName;
}

function createDefaultDraftItem(): ReturnDraftItem {
    return {
        selected: false,
        quantity: "1",
        reason: "",
        condition: "OPENED",
        note: ""
    };
}

function buildDraftItems(order: OrderDetailResponse) {
    return Object.fromEntries(
        (order.items ?? []).map((item, index) => [
            getItemKey(item, index),
            createDefaultDraftItem()
        ])
    );
}

function getDraftValidationError(
    order: OrderDetailResponse | null,
    draftItems: Record<string, ReturnDraftItem>
) {
    if (!order) {
        return "Vui lòng chọn đơn hàng cần hoàn.";
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
        if (!draft.reason) {
            return "Vui lòng chọn lý do trả hàng cho từng sản phẩm.";
        }
        if (draft.reason === "OTHER" && !draft.note.trim()) {
            return "Vui lòng nhập ghi chú khi chọn lý do khác.";
        }
    }

    return null;
}

export function CreateReturnPage() {
    const { orderId } = useParams();
    const navigate = useNavigate();
    const [wizardStep, setWizardStep] = useState<WizardStep>(orderId ? 2 : 1);
    const [toast, setToast] = useState<string | null>(null);

    const [orderSearchTerm, setOrderSearchTerm] = useState("");
    const debouncedOrderSearchTerm = useDebouncedValue(orderSearchTerm.trim(), 350);
    const [orderResultsPage, setOrderResultsPage] = useState(emptyOrderSearchPage);
    const [orderSearchPage, setOrderSearchPage] = useState(0);
    const [isOrderSearchLoading, setIsOrderSearchLoading] = useState(false);
    const [orderSearchError, setOrderSearchError] = useState<string | null>(null);
    const [orderSearchTimeFilter, setOrderSearchTimeFilter] =
        useState<OrderSearchTimeFilter>("ALL");
    const [customFromDate, setCustomFromDate] = useState("");
    const [customToDate, setCustomToDate] = useState("");

    const [selectedOrderSummary, setSelectedOrderSummary] =
        useState<OrderSummaryResponse | null>(null);
    const [selectedOrder, setSelectedOrder] = useState<OrderDetailResponse | null>(null);
    const [selectedOrderReturns, setSelectedOrderReturns] = useState<
        OrderReturnResponse[]
    >([]);
    const [isSelectedOrderLoading, setIsSelectedOrderLoading] = useState(
        Boolean(orderId)
    );
    const [draftItems, setDraftItems] = useState<Record<string, ReturnDraftItem>>({});
    const [returnNote, setReturnNote] = useState("");
    const [refundRequired, setRefundRequired] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [eligibilityRevision, setEligibilityRevision] = useState(0);
    const [eligibilityState, setEligibilityState] = useState<{
        orderId: string;
        key: string;
        response: OrderReturnEligibilityResponse | null;
        error: string | null;
    } | null>(null);

    // Modal state for viewing return policies
    const [isPolicyModalOpen, setIsPolicyModalOpen] = useState(false);
    // Track open state of reason accordion cards
    const [openReasonCards, setOpenReasonCards] = useState<Record<string, boolean>>({});

    const orderSearchDateRange = useMemo(
        () =>
            getOrderSearchDateRange(
                orderSearchTimeFilter,
                customFromDate,
                customToDate
            ),
        [customFromDate, customToDate, orderSearchTimeFilter]
    );

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
            setIsOrderSearchLoading(true);
            setOrderSearchError(null);

            const orderQuery: OrderQueryParams = {
                keyword: debouncedOrderSearchTerm,
                returnEligibility: "ELIGIBLE",
                status: "COMPLETED",
                fromDate: orderSearchDateRange.fromDate,
                toDate: orderSearchDateRange.toDate,
                page: orderSearchPage,
                size: ORDER_SEARCH_PAGE_SIZE,
                sort: ORDER_SEARCH_DEFAULT_SORT
            };

            getOrders(orderQuery)
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
        orderSearchDateRange.fromDate,
        orderSearchDateRange.toDate,
        orderSearchPage
    ]);

    useEffect(() => {
        if (!orderId) {
            return;
        }

        let ignore = false;
        const timerId = window.setTimeout(() => {
            setIsSelectedOrderLoading(true);

            Promise.all([getOrderById(orderId), getOrderReturns(orderId)])
                .then(([orderDetail, returns]) => {
                    if (ignore) {
                        return;
                    }

                    setSelectedOrderSummary(orderDetail);
                    setSelectedOrder(orderDetail);
                    setSelectedOrderReturns(returns ?? []);
                    setEligibilityState(null);
                    setDraftItems(buildDraftItems(orderDetail));
                    setWizardStep(2);
                })
                .catch((error: unknown) => {
                    if (!ignore) {
                        setSelectedOrder(null);
                        setSelectedOrderReturns([]);
                        setDraftItems({});
                        setToast(getReturnErrorMessage(error));
                        setWizardStep(1);
                    }
                })
                .finally(() => {
                    if (!ignore) {
                        setIsSelectedOrderLoading(false);
                    }
                });
        }, 0);

        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [orderId]);

    const selectedOrderItems = selectedOrder?.items ?? [];
    const localQuantityValidation = useMemo(() => validateReturnDraftQuantities(
        selectedOrder?.items ?? [], draftItems, selectedOrderReturns
    ), [selectedOrder, draftItems, selectedOrderReturns]);
    const eligibilityRequest = useMemo<CheckOrderReturnEligibilityPayload>(() => ({
        items: localQuantityValidation.items.map(({ orderItemId, quantity, draft }) => ({
            orderItemId,
            quantity,
            reason: draft.reason || null,
            condition: draft.condition,
            note: draft.note.trim() || null
        }))
    }), [localQuantityValidation]);
    const eligibilityOrderId = selectedOrder?.id;
    const eligibilityKey = JSON.stringify({ orderId: eligibilityOrderId, ...eligibilityRequest,
        remaining: localQuantityValidation.remainingQuantities, revision: eligibilityRevision });
    const hasReasonSelection = eligibilityRequest.items.some(item => item.reason !== null);
    const shouldCheckEligibility = wizardStep >= 3 && Boolean(eligibilityOrderId) && hasReasonSelection;
    const eligibility = eligibilityState?.key === eligibilityKey ? eligibilityState.response : null;
    const quantityValidation = validateReturnDraftQuantities(
        selectedOrderItems, draftItems, selectedOrderReturns,
        eligibilityState?.orderId === eligibilityOrderId ? eligibilityState?.response?.items : undefined
    );
    const draftValidationError = quantityValidation.error ?? getDraftValidationError(selectedOrder, draftItems);
    const eligibilityError = eligibilityState?.key === eligibilityKey ? eligibilityState.error : null;
    const isCheckingEligibility = shouldCheckEligibility && eligibilityState?.key !== eligibilityKey;

    // Deduplicate return policy statements from eligibility preview
    const policyList = useMemo(() => {
        const list: string[] = [];
        (eligibility?.items ?? []).forEach(it => {
            (it.policy ?? []).forEach(p => {
                if (p && !list.includes(p)) list.push(p);
            });
        });
        return list;
    }, [eligibility]);

    useEffect(() => {
        if (!shouldCheckEligibility || !eligibilityOrderId) return;
        let ignore = false;
        const timerId = window.setTimeout(() => {
            checkOrderReturnEligibility(eligibilityOrderId, eligibilityRequest)
                .then(response => {
                    if (!ignore) setEligibilityState({ orderId: eligibilityOrderId, key: eligibilityKey, response, error: null });
                })
                .catch(() => {
                    if (!ignore) setEligibilityState({ orderId: eligibilityOrderId, key: eligibilityKey, response: null, error: "Không thể kiểm tra điều kiện hoàn. Vui lòng thử lại." });
                });
        }, 350);
        return () => {
            ignore = true;
            window.clearTimeout(timerId);
        };
    }, [eligibilityKey, eligibilityOrderId, eligibilityRequest, shouldCheckEligibility]);

    const isCreateDisabled = isSubmitting || Boolean(draftValidationError) || isCheckingEligibility || !eligibility?.eligible;
    const isNextDisabled =
        isSubmitting ||
        (wizardStep === 1 &&
            (!selectedOrder || isSelectedOrderLoading)) ||
        (wizardStep === 2 && Boolean(quantityValidation.error)) ||
        (wizardStep === 3 && isCreateDisabled);

    const isNextLoading =
        (wizardStep === 1 && isSelectedOrderLoading) ||
        (wizardStep === 3 && isCheckingEligibility);

    const handleBackToList = () => {
        navigate(orderId ? "/system/orders" : "/system/orders/returns");
    };

    const handleOrderSearchChange = (value: string) => {
        setOrderSearchTerm(value);
        setOrderSearchPage(0);
    };

    const handleOrderSearchTimeFilterChange = (value: OrderSearchTimeFilter) => {
        setOrderSearchTimeFilter(value);
        setOrderSearchPage(0);
    };

    const handleCustomFromDateChange = (value: string) => {
        setCustomFromDate(value);
        setOrderSearchPage(0);
    };

    const handleCustomToDateChange = (value: string) => {
        setCustomToDate(value);
        setOrderSearchPage(0);
    };

    const selectOrderForReturn = (order: OrderSummaryResponse) => {
        setSelectedOrderSummary(order);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setEligibilityState(null);
        setDraftItems({});
        setOpenReasonCards({});
        setWizardStep(1);
        setIsSelectedOrderLoading(true);

        Promise.all([getOrderById(order.id), getOrderReturns(order.id)])
            .then(([orderDetail, returns]) => {
                setSelectedOrder(orderDetail);
                setSelectedOrderReturns(returns ?? []);
                setDraftItems(buildDraftItems(orderDetail));
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsSelectedOrderLoading(false);
            });
    };

    const updateDraftItem = (itemKey: string, update: Partial<ReturnDraftItem>) => {
        setDraftItems((current) => {
            const nextItem = {
                ...(current[itemKey] ?? createDefaultDraftItem()),
                ...update
            };

            return {
                ...current,
                [itemKey]: nextItem
            };
        });
    };

    const handleDeselectAll = () => {
        setDraftItems((current) => {
            const next: Record<string, ReturnDraftItem> = {};
            for (const [key, val] of Object.entries(current)) {
                next[key] = { ...val, selected: false };
            }
            return next;
        });
    };

    const toggleReasonCard = (itemKey: string) => {
        setOpenReasonCards((prev) => ({
            ...prev,
            [itemKey]: prev[itemKey] === false ? true : false
        }));
    };

    const selectedEntries = useMemo(() => {
        return (selectedOrder?.items ?? [])
            .map((item, index) => ({
                item,
                index,
                itemKey: getItemKey(item, index)
            }))
            .filter(({ itemKey }) => draftItems[itemKey]?.selected);
    }, [selectedOrder, draftItems]);

    const moveToNextStep = () => {
        if (isSubmitting) return;

        if (wizardStep === 1 && (!selectedOrder || isSelectedOrderLoading)) {
            setToast("Vui lòng chọn đơn hàng trước khi tiếp tục.");
            return;
        }

        if ((wizardStep === 2 || wizardStep === 3) && quantityValidation.error) {
            setToast(quantityValidation.error);
            return;
        }

        if (wizardStep === 3) {
            // Check if any selected item is missing a return reason
            for (const { itemKey, item } of selectedEntries) {
                const draft = draftItems[itemKey];
                const itemName = item.productNameSnapshot?.trim() || "Sản phẩm";

                if (!draft?.reason) {
                    setOpenReasonCards((prev) => ({ ...prev, [itemKey]: true }));
                    setToast(`Vui lòng chọn lý do trả hàng cho: ${itemName}.`);
                    const cardElement = document.getElementById(`reason-card-${itemKey}`);
                    cardElement?.scrollIntoView({ behavior: "smooth", block: "center" });
                    return;
                }

                if (draft.reason === "OTHER" && !draft.note?.trim()) {
                    setOpenReasonCards((prev) => ({ ...prev, [itemKey]: true }));
                    setToast(`Vui lòng nhập ghi chú khi chọn lý do khác cho: ${itemName}.`);
                    const cardElement = document.getElementById(`reason-card-${itemKey}`);
                    cardElement?.scrollIntoView({ behavior: "smooth", block: "center" });
                    return;
                }
            }

            if (draftValidationError) {
                setToast(draftValidationError);
                return;
            }

            if (isCheckingEligibility || !eligibility?.eligible) {
                setToast("Vui lòng hoàn tất kiểm tra điều kiện và xử lý các điều kiện không đạt trước khi tiếp tục.");
                return;
            }
        }

        setWizardStep((step) => Math.min(4, step + 1) as WizardStep);
    };

    const submitReturn = async () => {
        if (isSubmitting || wizardStep !== 4) {
            return;
        }

        if (!selectedOrder || isCreateDisabled) {
            setToast(
                draftValidationError ??
                    "Vui lòng kiểm tra các điều kiện hoàn hàng trước khi tạo phiếu."
            );
            return;
        }

        setIsSubmitting(true);
        try {
            const [freshOrder, freshReturns] = await Promise.all([
                getOrderById(selectedOrder.id), getOrderReturns(selectedOrder.id)
            ]);
            setSelectedOrder(freshOrder);
            setSelectedOrderReturns(freshReturns);
            const freshValidation = validateReturnDraftQuantities(freshOrder.items ?? [], draftItems, freshReturns);
            if (freshValidation.error) {
                setWizardStep(2);
                throw new Error(freshValidation.error);
            }
            const payload: CreateOrderReturnPayload = {
                note: returnNote.trim() || null,
                refundRequired,
                items: buildReturnPayloadItems(freshOrder.items ?? [], draftItems, freshReturns)
            };
            const latestEligibility = await checkOrderReturnEligibility(freshOrder.id, payload);
            setEligibilityState({ orderId: freshOrder.id, key: eligibilityKey, response: latestEligibility, error: null });
            const latestValidation = validateReturnDraftQuantities(
                freshOrder.items ?? [], draftItems, freshReturns, latestEligibility.items
            );
            if (latestValidation.error) {
                setWizardStep(2);
                throw new Error(latestValidation.error);
            }
            if (!latestEligibility.eligible) {
                throw new Error("Điều kiện hoàn hàng đã thay đổi. Vui lòng kiểm tra lại trước khi tạo phiếu.");
            }
            payload.items = buildReturnPayloadItems(freshOrder.items ?? [], draftItems, freshReturns, latestEligibility.items);
            const createdReturn = await createOrderReturn(freshOrder.id, payload);
            setToast("Tạo phiếu hoàn hàng thành công");
            window.setTimeout(() => navigate(`${routePaths.returns}?detail=${encodeURIComponent(createdReturn.id)}`), 350);
        } catch (error: unknown) {
            setToast(getReturnErrorMessage(error));
            setEligibilityRevision(current => current + 1);
        } finally {
            setIsSubmitting(false);
        }
    };

    const renderStepContent = () => {
        if (wizardStep === 1) {
            return (
                <OrderPicker
                    isLoading={isOrderSearchLoading}
                    currentPage={orderSearchPage}
                    onPageChange={setOrderSearchPage}
                    onCustomFromDateChange={handleCustomFromDateChange}
                    onCustomToDateChange={handleCustomToDateChange}
                    onSearchChange={handleOrderSearchChange}
                    onTimeFilterChange={handleOrderSearchTimeFilterChange}
                    onSelectOrder={selectOrderForReturn}
                    orderPage={orderResultsPage}
                    searchError={orderSearchError}
                    searchTerm={orderSearchTerm}
                    customFromDate={customFromDate}
                    customToDate={customToDate}
                    timeFilter={orderSearchTimeFilter}
                    selectedOrderSummary={selectedOrderSummary}
                />
            );
        }

        if (wizardStep === 2 && selectedOrder) {
            return (
                <ReturnProductSelectionTable
                    draftItems={draftItems}
                    items={selectedOrderItems}
                    onDeselectAll={handleDeselectAll}
                    onUpdateDraftItem={updateDraftItem}
                    validation={quantityValidation}
                />
            );
        }

        if (wizardStep === 3 && selectedOrder) {
            return (
                <div className={styles.reasonForm}>
                    {/* Subtle Policy Helper Line (No Heavy Banner) */}
                    <div className={styles.policySubtleBar}>
                        <div className={styles.policySubtleLeft}>
                            <Info size={15} className={styles.policySubtleIcon} aria-hidden="true" />
                            <span>
                                Hàng hoàn sẽ được kiểm tra tình trạng trước khi xử lý nhập kho và hoàn tiền (nếu có).
                            </span>
                        </div>
                        <Button onClick={() => setIsPolicyModalOpen(true)} size="xs" variant="link">
                            Xem chính sách chi tiết
                        </Button>
                    </div>

                    {/* Accordion List for each selected item */}
                    <div className={styles.reasonCardList}>
                        {selectedEntries.map(({ item, itemKey }) => (
                            <ReturnProductReasonCard
                                key={itemKey}
                                item={item}
                                itemKey={itemKey}
                                draft={draftItems[itemKey] ?? createDefaultDraftItem()}
                                isOpen={openReasonCards[itemKey] !== false}
                                onToggle={() => toggleReasonCard(itemKey)}
                                onUpdateDraft={(update) => updateDraftItem(itemKey, update)}
                            />
                        ))}
                    </div>

                    {/* Refund Required Toggle */}
                    <div style={{ padding: "1rem", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                        <label style={{ display: "flex", alignItems: "center", gap: "0.75rem", cursor: "pointer", fontWeight: 600, fontSize: "0.95rem" }}>
                            <input
                                checked={refundRequired}
                                onChange={(event) => setRefundRequired(event.target.checked)}
                                style={{ width: "18px", height: "18px", cursor: "pointer" }}
                                type="checkbox"
                            />
                            <span>Yêu cầu hoàn tiền cho khách hàng</span>
                        </label>
                        <p style={{ margin: "0.5rem 0 0 2rem", fontSize: "0.85rem", color: "#64748b", lineHeight: 1.4 }}>
                            {refundRequired
                                ? "Phiếu hoàn sẽ chuyển sang luồng hoàn tiền sau khi nhận hàng và kiểm kho hoàn tất."
                                : "Phiếu hoàn KHÔNG phát sinh hoàn tiền (ví dụ: đổi ngang, tặng phẩm, bảo hành). Phiếu sẽ tự động hoàn tất khi kiểm kho 100%."}
                        </p>
                    </div>

                    {/* General Return Note */}
                    <Field label="Ghi chú phiếu hoàn">
                        <TextArea
                            onChange={(event) => setReturnNote(event.target.value)}
                            placeholder="Ghi chú chung nếu có"
                            value={returnNote}
                        />
                    </Field>

                    {/* Consolidated Eligibility Verification */}
                    <ReturnEligibilityStatus
                        response={eligibility}
                        checking={isCheckingEligibility}
                        error={eligibilityError}
                        items={selectedOrderItems}
                        ready={hasReasonSelection}
                        onRetry={() => setEligibilityRevision((current) => current + 1)}
                    />
                </div>
            );
        }

        if (wizardStep === 4 && selectedOrder) {
            return (
                <ReturnConfirmationSummary
                    order={selectedOrder}
                    items={selectedOrderItems}
                    draftItems={draftItems}
                    refundRequired={refundRequired}
                    returnNote={returnNote}
                    eligibility={eligibility}
                    isCheckingEligibility={isCheckingEligibility}
                    eligibilityError={eligibilityError}
                    onRetryEligibility={() => setEligibilityRevision((current) => current + 1)}
                />
            );
        }

        return (
            <PanelMessage
                description="Vui lòng quay lại bước chọn đơn hàng."
                title="Chưa có dữ liệu"
            />
        );
    };

    return (
        <section className={styles.page}>
            <header className={styles.header}>
                <Button onClick={handleBackToList}>
                    <ArrowLeft size={18} aria-hidden="true" />
                    Quay lại
                </Button>
                <div className={styles.title}>
                    <h1>Tạo phiếu hoàn hàng</h1>
                    <p>Thực hiện theo các bước để tạo phiếu hoàn</p>
                </div>
            </header>

            <ol aria-label="Các bước tạo phiếu hoàn" className={styles.stepper}>
                {WIZARD_STEPS.map((item) => {
                    const state =
                        item.step < wizardStep
                            ? "completed"
                            : item.step === wizardStep
                                ? "active"
                                : "upcoming";
                    // Chỉ quay lại được bước đã xong; bước sau phải đi qua nút Tiếp tục để được kiểm tra.
                    const canJump = state === "completed" && !isSubmitting && !(orderId && item.step === 1);

                    return (
                        <li key={item.step}>
                            <button
                                aria-current={state === "active" ? "step" : undefined}
                                className={`${styles.step} ${styles[state]}`}
                                disabled={!canJump}
                                onClick={() => setWizardStep(item.step)}
                                title={canJump ? `Quay lại bước ${item.label}` : undefined}
                                type="button"
                            >
                                <span>
                                    {state === "completed" ? <Check size={14} aria-hidden="true" /> : item.step}
                                </span>
                                <strong>{item.label}</strong>
                            </button>
                        </li>
                    );
                })}
            </ol>

            <section className={styles.card}>
                {renderStepContent()}
                {wizardStep >= 3 && quantityValidation.error ? (
                    <p className={styles.quantityError} role="alert">
                        {quantityValidation.error} Vui lòng quay lại bước chọn sản phẩm để kiểm tra.
                    </p>
                ) : null}
            </section>

            <footer className={styles.footer}>
                <WizardSummary
                    customer={selectedOrderSummary ? getCustomerDisplay(selectedOrderSummary) : null}
                    entries={selectedEntries.map(({ item, itemKey }) => ({
                        item,
                        quantity: Number(draftItems[itemKey]?.quantity) || 0
                    }))}
                    orderCode={selectedOrder?.orderCode ?? selectedOrderSummary?.orderCode ?? null}
                    refundRequired={refundRequired}
                />
                <Button
                    disabled={wizardStep === 1 || isSubmitting}
                    onClick={() =>
                        setWizardStep((step) => Math.max(1, step - 1) as WizardStep)
                    }
                >
                    <ChevronLeft size={18} aria-hidden="true" />
                    Quay lại
                </Button>

                {wizardStep < 4 ? (
                    <Button
                        aria-busy={isNextLoading || undefined}
                        disabled={isNextDisabled}
                        onClick={moveToNextStep}
                        variant="primary"
                    >
                        {isNextLoading ? (
                            <>
                                <LoaderCircle className={styles.spinner} size={18} aria-hidden="true" />
                                {wizardStep === 1 ? "Đang tải đơn..." : "Đang kiểm tra..."}
                            </>
                        ) : (
                            <>
                                Tiếp tục
                                <ChevronRight size={18} aria-hidden="true" />
                            </>
                        )}
                    </Button>
                ) : (
                    <Button disabled={isCreateDisabled} onClick={submitReturn} variant="primary">
                        {isSubmitting ? (
                            <>
                                <LoaderCircle className={styles.spinner} size={18} aria-hidden="true" />
                                Đang tạo...
                            </>
                        ) : (
                            "Tạo phiếu hoàn hàng"
                        )}
                    </Button>
                )}
            </footer>

            {/* Detailed Policy Modal */}
            <ReturnPolicyModal
                isOpen={isPolicyModalOpen}
                onClose={() => setIsPolicyModalOpen(false)}
                policies={policyList}
            />

            {toast ? <div className={styles.toast}>{toast}</div> : null}
        </section>
    );
}

function OrderPicker({
    customFromDate,
    customToDate,
    isLoading,
    currentPage,
    onCustomFromDateChange,
    onCustomToDateChange,
    onPageChange,
    onSearchChange,
    onSelectOrder,
    onTimeFilterChange,
    orderPage,
    searchError,
    searchTerm,
    timeFilter,
    selectedOrderSummary
}: {
    customFromDate: string;
    customToDate: string;
    isLoading: boolean;
    currentPage: number;
    onCustomFromDateChange: (value: string) => void;
    onCustomToDateChange: (value: string) => void;
    onPageChange: (page: number) => void;
    onSearchChange: (value: string) => void;
    onSelectOrder: (order: OrderSummaryResponse) => void;
    onTimeFilterChange: (value: OrderSearchTimeFilter) => void;
    orderPage: OrderPageResponse<OrderSummaryResponse>;
    searchError: string | null;
    searchTerm: string;
    timeFilter: OrderSearchTimeFilter;
    selectedOrderSummary: OrderSummaryResponse | null;
}) {
    const orderResults = orderPage.content ?? [];
    const totalPages = Math.max(orderPage.totalPages ?? 0, 1);
    const totalElements = orderPage.totalElements ?? 0;
    const hasSearchTerm = searchTerm.trim().length > 0;
    const hasTimeFilter =
        timeFilter !== "ALL" &&
        (timeFilter !== "CUSTOM" || Boolean(customFromDate || customToDate));
    const handleOrderRowKeyDown = (
        event: KeyboardEvent<HTMLTableRowElement>,
        order: OrderSummaryResponse
    ) => {
        if (event.key !== "Enter" && event.key !== " ") {
            return;
        }

        event.preventDefault();
        onSelectOrder(order);
    };

    return (
        <div className={styles.orderPicker}>
            <div className={styles.orderPickerHeader}>
                <SearchInput
                    aria-label="Tìm đơn hàng cần hoàn"
                    onChange={onSearchChange}
                    placeholder="Tìm mã đơn, khách hàng, SĐT..."
                    value={searchTerm}
                />
                <label className={styles.timeSelect}>
                    <CalendarDays size={18} aria-hidden="true" />
                    <select
                        aria-label="Lọc thời gian mua hàng"
                        onChange={(event) =>
                            onTimeFilterChange(
                                event.target.value as OrderSearchTimeFilter
                            )
                        }
                        value={timeFilter}
                    >
                        {ORDER_SEARCH_TIME_FILTERS.map((filter) => (
                            <option key={filter.value} value={filter.value}>
                                {filter.label}
                            </option>
                        ))}
                    </select>
                </label>
            </div>

            {timeFilter === "CUSTOM" ? (
                <div className={styles.customDateRange}>
                    <Field label="Từ ngày">
                        <TextInput
                            aria-label="Từ ngày mua hàng"
                            onChange={(event) =>
                                onCustomFromDateChange(event.target.value)
                            }
                            type="date"
                            value={customFromDate}
                        />
                    </Field>
                    <Field label="Đến ngày">
                        <TextInput
                            aria-label="Đến ngày mua hàng"
                            onChange={(event) =>
                                onCustomToDateChange(event.target.value)
                            }
                            type="date"
                            value={customToDate}
                        />
                    </Field>
                </div>
            ) : null}

            <div className={styles.orderResults}>
                <div className={styles.orderTableWrap}>
                    <Table className={styles.orderTable}>
                        <colgroup>
                            <col style={{ width: "22%" }} />
                            <col style={{ width: "28%" }} />
                            <col style={{ width: "18%" }} />
                            <col style={{ width: "14%" }} />
                            <col style={{ width: "18%" }} />
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableHeaderCell>Mã đơn hàng</TableHeaderCell>
                                <TableHeaderCell>Khách hàng</TableHeaderCell>
                                <TableHeaderCell>Thời gian</TableHeaderCell>
                                <TableHeaderCell className="text-right">Tình trạng hoàn</TableHeaderCell>
                                <TableHeaderCell className="text-center">Giá trị đơn hàng</TableHeaderCell>
                            </TableRow>
                        </TableHead>
                        <tbody>
                            {isLoading ? (
                                <TableRow>
                                    <TableCell className={styles.emptyCell} colSpan={5}>
                                        <strong>Đang tìm kiếm</strong>
                                        <span>Đang tải danh sách đơn hàng</span>
                                    </TableCell>
                                </TableRow>
                            ) : null}
                            {!isLoading && searchError ? (
                                <TableRow>
                                    <TableCell className={styles.emptyCell} colSpan={5}>
                                        <strong>Không tải được đơn hàng</strong>
                                        <span>{searchError}</span>
                                    </TableCell>
                                </TableRow>
                            ) : null}
                            {!isLoading && !searchError
                                ? orderResults.map((order, index) => (
                                      <TableRow
                                          aria-selected={
                                              selectedOrderSummary?.id === order.id
                                          }
                                          className={`${styles.orderTableRow} ${
                                              index % 2 === 0
                                                  ? styles.zebraFirstRow
                                                  : styles.zebraSecondRow
                                          } ${
                                              selectedOrderSummary?.id === order.id
                                                  ? styles.selectedOrderOption
                                                  : ""
                                          }`}
                                          key={order.id}
                                          onClick={() => onSelectOrder(order)}
                                          onKeyDown={(event) =>
                                              handleOrderRowKeyDown(event, order)
                                          }
                                          tabIndex={0}
                                      >
                                          <TableCell className={styles.orderCodeCell}>
                                              {getOrderCode(order)}
                                          </TableCell>
                                          <TableCell>{getCustomerDisplay(order)}</TableCell>
                                          <TableCell className={styles.orderDateCell}>
                                              {formatDateTime(order.createdAt)}
                                          </TableCell>
                                          <TableCell className="text-right">
                                              <OrderReturnStatusTag order={order} />
                                          </TableCell>
                                          <TableCell className={`text-center ${styles.orderAmountCell}`}>
                                              {formatCurrency(order.finalAmount)}
                                          </TableCell>
                                      </TableRow>
                                  ))
                                : null}
                            {!isLoading && !searchError && orderResults.length === 0 ? (
                                <TableRow>
                                    <TableCell className={styles.emptyCell} colSpan={5}>
                                        <strong>
                                            {hasSearchTerm || hasTimeFilter
                                                ? "Không tìm thấy đơn hàng"
                                                : "Chưa có đơn phù hợp"}
                                        </strong>
                                        <span>
                                            {hasSearchTerm || hasTimeFilter
                                                ? "Không có đơn hàng đủ điều kiện phù hợp với từ khóa."
                                                : "Không có đơn hàng đủ điều kiện hoàn."}
                                        </span>
                                    </TableCell>
                                </TableRow>
                            ) : null}
                        </tbody>
                    </Table>
                </div>
                {!isLoading && !searchError && totalElements > 0 ? (
                    <TablePagination
                        itemLabel="đơn hàng"
                        onPageChange={onPageChange}
                        page={currentPage}
                        pageSize={orderPage.size || orderResults.length}
                        totalItems={totalElements}
                        totalPages={totalPages}
                    />
                ) : null}
            </div>
        </div>
    );
}

const ORDER_RETURN_STATUS_TAGS: Record<string, { label: string; tone: BadgeTone }> = {
    NONE: { label: "Chưa hoàn", tone: "neutral" },
    PARTIALLY_RETURNED: { label: "Đã hoàn một phần", tone: "warning" },
    FULLY_RETURNED: { label: "Đã hoàn hết", tone: "danger" }
};

function OrderReturnStatusTag({ order }: { order: OrderSummaryResponse }) {
    const tag = ORDER_RETURN_STATUS_TAGS[order.returnStatus ?? "NONE"] ?? ORDER_RETURN_STATUS_TAGS.NONE;
    return (
        <StatusBadge shape="tag" tone={tag.tone}>
            {order.returnStatusLabel?.trim() || tag.label}
        </StatusBadge>
    );
}

/** Tóm tắt cố định cạnh nút Tiếp tục: đơn đã chọn, số sản phẩm, giá trị hàng trả tạm tính. */
function WizardSummary({
    customer,
    entries,
    orderCode,
    refundRequired
}: {
    customer: string | null;
    entries: Array<{ item: OrderItemResponse; quantity: number }>;
    orderCode: string | null;
    refundRequired: boolean;
}) {
    if (!orderCode) {
        return <p className={styles.wizardSummaryEmpty}>Chọn đơn hàng để bắt đầu.</p>;
    }
    const totalQuantity = entries.reduce((sum, entry) => sum + entry.quantity, 0);
    // Tạm tính theo đơn giá thực bán của từng dòng; số tiền hoàn chính xác do hệ thống tính khi hoàn tiền.
    const estimatedValue = entries.reduce((sum, { item, quantity }) => {
        const lineQuantity = Number(item.quantity) || 0;
        const unitPrice = lineQuantity > 0 && item.lineTotal != null
            ? Number(item.lineTotal) / lineQuantity
            : Number(item.actualUnitPrice ?? item.originalUnitPrice ?? 0);
        return sum + unitPrice * quantity;
    }, 0);

    return (
        <dl className={styles.wizardSummary}>
            <div>
                <dt>Đơn hàng</dt>
                <dd>{orderCode}{customer ? ` · ${customer}` : ""}</dd>
            </div>
            <div>
                <dt>Sản phẩm hoàn</dt>
                <dd>{entries.length} dòng · SL {totalQuantity.toLocaleString("vi-VN")}</dd>
            </div>
            {refundRequired && entries.length > 0 ? (
                <div>
                    <dt>Giá trị hàng trả (tạm tính)</dt>
                    <dd>{formatCurrency(estimatedValue)}</dd>
                </div>
            ) : null}
        </dl>
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
