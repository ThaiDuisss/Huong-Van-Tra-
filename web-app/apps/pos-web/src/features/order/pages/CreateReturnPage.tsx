import {
    ArrowLeft,
    ChevronLeft,
    ChevronRight,
    Search
} from "lucide-react";
import { useEffect, useState } from "react";

import { useNavigate, useParams } from "react-router-dom";

import { getOrderById, getOrders } from "../services/order.api";
import { createOrderReturn, getOrderReturns } from "../services/order-return.api";
import type {
    OrderDetailResponse,
    OrderItemResponse,
    OrderPageResponse,
    OrderQueryParams,
    OrderSummaryResponse
} from "../types/order.types";
import type {
    CreateOrderReturnPayload,
    OrderReturnResponse,
    ReturnCondition,
    ReturnReason
} from "../types/order-return.types";
import {
    RETURN_CONDITION_LABELS,
    RETURN_CONDITIONS,
    RETURN_REASON_LABELS,
    RETURN_REASONS,
    calculateAvailableReturnQuantity,
    canCreateReturn,
    formatReturnQuantity,
    getReturnConditionLabel,
    getReturnErrorMessage,
    getReturnReasonLabel
} from "../utils/order-return.utils";
import {
    formatDateTime,
    getCustomerName,
    getOrderCode,
    getPaymentStatusText
} from "../utils/order-formatters";
import styles from "./CreateReturnPage.module.css";

type WizardStep = 1 | 2 | 3 | 4;

interface ReturnDraftItem {
    selected: boolean;
    quantity: string;
    reason: ReturnReason;
    condition: ReturnCondition;
    note: string;
}

const ORDER_SEARCH_PAGE_SIZE = 6;

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

function getItemKey(item: OrderItemResponse, index: number) {
    return item.id ?? item.productVariantId ?? `item-${index}`;
}

function getItemName(item: OrderItemResponse) {
    return item.productNameSnapshot?.trim() || "Sản phẩm";
}

function getItemVariant(item: OrderItemResponse) {
    return item.variantNameSnapshot?.trim() || "Không có phân loại";
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
        const availableQuantity = calculateAvailableReturnQuantity(item, returns);

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
    const [isSubmitting, setIsSubmitting] = useState(false);

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
                page: orderSearchPage,
                size: ORDER_SEARCH_PAGE_SIZE,
                sort: "businessStatus"
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
    }, [debouncedOrderSearchTerm, orderSearchPage]);

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
            (!selectedOrder || isSelectedOrderLoading || !canCreateReturn(selectedOrder))) ||
        (wizardStep === 2 && selectedDraftEntries.length === 0) ||
        (wizardStep === 3 && Boolean(draftValidationError));

    const handleBackToList = () => {
        navigate(orderId ? "/system/orders" : "/system/orders/returns");
    };

    const resetOrderDependentState = () => {
        setSelectedOrderSummary(null);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setDraftItems({});
        setReturnNote("");
        setIsSubmitting(false);
    };

    const handleChangeOrder = () => {
        resetOrderDependentState();
        setOrderSearchTerm("");
        setOrderSearchPage(0);
        setOrderResultsPage(emptyOrderSearchPage);
        setWizardStep(1);

        if (orderId) {
            navigate("/system/orders/returns/create");
        }
    };

    const handleOrderSearchChange = (value: string) => {
        setOrderSearchTerm(value);
        setOrderSearchPage(0);
    };

    const selectOrderForReturn = (order: OrderSummaryResponse) => {
        setSelectedOrderSummary(order);
        setSelectedOrder(null);
        setSelectedOrderReturns([]);
        setDraftItems({});
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
            setToast("Chỉ đơn hàng đã hoàn tất và thanh toán mới được hoàn hàng.");
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
                setToast("Tạo phiếu hoàn hàng thành công");
                window.setTimeout(() => {
                    navigate(`/system/orders/returns/${createdReturn.id}`);
                }, 350);
            })
            .catch((error: unknown) => {
                setToast(getReturnErrorMessage(error));
            })
            .finally(() => {
                setIsSubmitting(false);
            });
    };

    const renderStepContent = () => {
        if (wizardStep === 1) {
            return (
                <OrderPicker
                    isLoading={isOrderSearchLoading}
                    currentPage={orderSearchPage}
                    onPageChange={setOrderSearchPage}
                    onSearchChange={handleOrderSearchChange}
                    onSelectOrder={selectOrderForReturn}
                    orderPage={orderResultsPage}
                    searchError={orderSearchError}
                    searchTerm={orderSearchTerm}
                    selectedOrderSummary={selectedOrderSummary}
                />
            );
        }

        if (wizardStep === 2 && selectedOrder) {
            return (
                <ReturnItemPicker
                    draftItems={draftItems}
                    items={selectedOrderItems}
                    onUpdateDraftItem={updateDraftItem}
                    returns={selectedOrderReturns}
                />
            );
        }

        if (wizardStep === 3 && selectedOrder) {
            return (
                <ReturnReasonForm
                    draftItems={draftItems}
                    items={selectedOrderItems}
                    onNoteChange={setReturnNote}
                    onUpdateDraftItem={updateDraftItem}
                    returnNote={returnNote}
                />
            );
        }

        if (wizardStep === 4 && selectedOrder) {
            return (
                <ReturnConfirmation
                    draftItems={draftItems}
                    items={selectedOrderItems}
                    order={selectedOrder}
                    returnNote={returnNote}
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
                <button className={styles.backButton} onClick={handleBackToList} type="button">
                    <ArrowLeft size={18} aria-hidden="true" />
                    Quay lại
                </button>
                <div className={styles.title}>
                    <h1>Tạo phiếu hoàn hàng</h1>
                    <p>Thực hiện theo các bước để tạo phiếu hoàn</p>
                </div>
            </header>

            <div className={styles.stepper}>
                {WIZARD_STEPS.map((item) => {
                    const state =
                        item.step < wizardStep
                            ? "completed"
                            : item.step === wizardStep
                                ? "active"
                                : "upcoming";

                    return (
                        <div
                            className={`${styles.step} ${styles[state]}`}
                            key={item.step}
                        >
                            <span>{item.step}</span>
                            <strong>{item.label}</strong>
                        </div>
                    );
                })}
            </div>

            <section className={styles.card}>
                {wizardStep !== 1 ? (
                    <div className={styles.cardHeader}>
                        <div>
                            <h2>{WIZARD_STEPS[wizardStep - 1].label}</h2>
                            {selectedOrder ? (
                                <p>Đơn hàng: {getOrderCode(selectedOrder)}</p>
                            ) : null}
                        </div>
                        {selectedOrder ? (
                            <button
                                className={styles.secondaryButton}
                                disabled={isSubmitting}
                                onClick={handleChangeOrder}
                                type="button"
                            >
                                Đổi đơn hàng
                            </button>
                        ) : null}
                    </div>
                ) : null}

                {renderStepContent()}
            </section>

            <footer className={styles.footer}>
                <button
                    className={styles.secondaryButton}
                    disabled={wizardStep === 1 || isSubmitting}
                    onClick={() =>
                        setWizardStep((step) => Math.max(1, step - 1) as WizardStep)
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
                        <ChevronRight size={18} aria-hidden="true" />
                    </button>
                ) : (
                    <button
                        className={styles.primaryButton}
                        disabled={isSubmitting}
                        onClick={submitReturn}
                        type="button"
                    >
                        {isSubmitting ? "Đang tạo..." : "Tạo phiếu hoàn hàng"}
                    </button>
                )}
            </footer>

            {toast ? <div className={styles.toast}>{toast}</div> : null}
        </section>
    );
}

function OrderPicker({
    isLoading,
    currentPage,
    onPageChange,
    onSearchChange,
    onSelectOrder,
    orderPage,
    searchError,
    searchTerm,
    selectedOrderSummary
}: {
    isLoading: boolean;
    currentPage: number;
    onPageChange: (page: number) => void;
    onSearchChange: (value: string) => void;
    onSelectOrder: (order: OrderSummaryResponse) => void;
    orderPage: OrderPageResponse<OrderSummaryResponse>;
    searchError: string | null;
    searchTerm: string;
    selectedOrderSummary: OrderSummaryResponse | null;
}) {
    const orderResults = orderPage.content ?? [];
    const totalPages = Math.max(orderPage.totalPages ?? 0, 1);
    const totalElements = orderPage.totalElements ?? 0;
    const hasSearchTerm = searchTerm.trim().length > 0;

    return (
        <div className={styles.orderPicker}>
            <div className={styles.orderPickerHeader}>
                <label className={styles.searchBox}>
                    <Search size={18} aria-hidden="true" />
                    <input
                        aria-label="Tìm đơn hàng cần hoàn"
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Tìm mã đơn, khách hàng, SĐT..."
                        value={searchTerm}
                    />
                </label>
            </div>

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
                    ? orderResults.map((order) => (
                          <button
                              className={`${styles.orderOption} ${
                                  selectedOrderSummary?.id === order.id
                                      ? styles.selectedOrderOption
                                      : ""
                              }`}
                              key={order.id}
                              onClick={() => onSelectOrder(order)}
                              type="button"
                          >
                              <strong>{getOrderCode(order)}</strong>
                              <span>{getCustomerName(order)}</span>
                              <small>{formatDateTime(order.createdAt)}</small>
                              <small>{getPaymentStatusText(order)}</small>
                          </button>
                      ))
                    : null}
                {!isLoading && !searchError && orderResults.length === 0 ? (
                    <PanelMessage
                        description={
                            hasSearchTerm
                                ? "Không có đơn hàng đủ điều kiện phù hợp với từ khóa."
                                : "Không có đơn hàng đủ điều kiện hoàn."
                        }
                        title={
                            hasSearchTerm
                                ? "Không tìm thấy đơn hàng"
                                : "Chưa có đơn phù hợp"
                        }
                    />
                ) : null}
                {!isLoading && !searchError && totalElements > 0 ? (
                    <div className={styles.orderSearchPagination}>
                        <button
                            className={styles.secondaryButton}
                            disabled={currentPage === 0}
                            onClick={() => onPageChange(Math.max(0, currentPage - 1))}
                            type="button"
                        >
                            <ChevronLeft size={16} aria-hidden="true" />
                            Trước
                        </button>
                        <span>
                            Trang {currentPage + 1} / {totalPages}
                        </span>
                        <button
                            className={styles.secondaryButton}
                            disabled={currentPage >= totalPages - 1}
                            onClick={() =>
                                onPageChange(Math.min(totalPages - 1, currentPage + 1))
                            }
                            type="button"
                        >
                            Sau
                            <ChevronRight size={16} aria-hidden="true" />
                        </button>
                    </div>
                ) : null}
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
                                                    selected: event.target.checked
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
                                <td>{formatReturnQuantity(availableQuantity)}</td>
                                <td>
                                    <input
                                        className={styles.quantityInput}
                                        disabled={!draft?.selected || availableQuantity <= 0}
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
                                {formatReturnQuantity(getDraftQuantity(draft))} sản phẩm
                            </span>
                        </div>
                        <div className={styles.reasonGrid}>
                            <label>
                                <span>Lý do</span>
                                <select
                                    onChange={(event) =>
                                        onUpdateDraftItem(itemKey, {
                                            reason: event.target.value as ReturnReason
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
                                            condition: event.target.value as ReturnCondition
                                        })
                                    }
                                    value={draft.condition}
                                >
                                    {RETURN_CONDITIONS.map((condition) => (
                                        <option key={condition} value={condition}>
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
                            {draft.note.trim() ? <p>{draft.note.trim()}</p> : null}
                        </div>
                    );
                })}
            </div>
        </div>
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
