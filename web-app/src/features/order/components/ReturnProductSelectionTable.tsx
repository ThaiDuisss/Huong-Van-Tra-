import { useMemo, useState } from "react";
import { Minus, Plus } from "lucide-react";

import { Button } from "../../../components/ui/Button";
import { SearchInput } from "../../../components/ui/SearchInput";
import { StatusBadge } from "../../../components/ui/StatusBadge";
import {
    Table,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRow
} from "../../../components/ui/Table";
import type { OrderItemResponse } from "../types/order.types";
import { formatReturnQuantity } from "../utils/order-return.utils";
import {
    getReturnItemKey as getItemKey,
    type ReturnDraftItem,
    type validateReturnDraftQuantities
} from "../utils/return-quantity.validation";
import { ReturnSelectedSummary } from "./ReturnSelectedSummary";
import styles from "../pages/CreateReturnPage.module.css";

interface ReturnProductSelectionTableProps {
    items: OrderItemResponse[];
    draftItems: Record<string, ReturnDraftItem>;
    validation: ReturnType<typeof validateReturnDraftQuantities>;
    onUpdateDraftItem: (
        itemKey: string,
        update: Partial<ReturnDraftItem>
    ) => void;
    onDeselectAll: () => void;
}

function getItemName(item: OrderItemResponse) {
    return item.productNameSnapshot?.trim() || "Sản phẩm";
}

function getItemVariant(item: OrderItemResponse) {
    return item.variantNameSnapshot?.trim() || "Không có phân loại";
}

export function ReturnProductSelectionTable({
    items,
    draftItems,
    validation,
    onUpdateDraftItem,
    onDeselectAll
}: ReturnProductSelectionTableProps) {
    const [searchTerm, setSearchTerm] = useState("");
    const [filterTab, setFilterTab] = useState<"ALL" | "SELECTED">("ALL");

    const totalItems = items.length;

    // Filter items based on search keyword and filter tab
    const filteredItemsWithIndices = useMemo(() => {
        const normalizedSearch = searchTerm.trim().toLowerCase();

        return items
            .map((item, index) => ({ item, index, itemKey: getItemKey(item, index) }))
            .filter(({ item, itemKey }) => {
                const isSelected = Boolean(draftItems[itemKey]?.selected);

                // Tab filter
                if (filterTab === "SELECTED" && !isSelected) {
                    return false;
                }

                // Search filter
                if (normalizedSearch) {
                    const name = (item.productNameSnapshot || "").toLowerCase();
                    const sku = (item.skuSnapshot || "").toLowerCase();
                    const variant = (item.variantNameSnapshot || "").toLowerCase();

                    return (
                        name.includes(normalizedSearch) ||
                        sku.includes(normalizedSearch) ||
                        variant.includes(normalizedSearch)
                    );
                }

                return true;
            });
    }, [items, draftItems, filterTab, searchTerm]);

    // Calculate selected summary
    const { selectedCount, totalQuantity } = useMemo(() => {
        let count = 0;
        let total = 0;

        items.forEach((item, index) => {
            const key = getItemKey(item, index);
            const draft = draftItems[key];
            if (draft?.selected) {
                count += 1;
                const parsed = Number(draft.quantity);
                if (/^\d+$/.test(draft.quantity.trim()) && Number.isSafeInteger(parsed) && parsed > 0) {
                    total += parsed;
                }
            }
        });

        return { selectedCount: count, totalQuantity: total };
    }, [items, draftItems]);

    const handleQuantityChange = (itemKey: string, nextValue: string) => {
        onUpdateDraftItem(itemKey, { quantity: nextValue });
    };

    const handleStepQuantity = (
        itemKey: string,
        currentQtyStr: string,
        delta: number,
        maxRemaining: number
    ) => {
        const currentVal = Number(currentQtyStr);
        const base = Number.isSafeInteger(currentVal) && currentVal >= 1 ? currentVal : 1;
        const nextVal = Math.max(1, Math.min(maxRemaining, base + delta));
        onUpdateDraftItem(itemKey, { quantity: String(nextVal) });
    };

    const handleToggleSelect = (
        itemKey: string,
        checked: boolean,
        availableQuantity: number
    ) => {
        if (!checked) {
            onUpdateDraftItem(itemKey, { selected: false });
            return;
        }

        // When checking, ensure valid quantity between 1 and available
        const currentQtyStr = draftItems[itemKey]?.quantity ?? "";
        const currentQty = Number(currentQtyStr);
        let nextQty = currentQtyStr;

        if (
            !/^\d+$/.test(currentQtyStr.trim()) ||
            !Number.isSafeInteger(currentQty) ||
            currentQty < 1 ||
            currentQty > availableQuantity
        ) {
            nextQty = "1";
        }

        onUpdateDraftItem(itemKey, {
            selected: true,
            quantity: nextQty
        });
    };

    return (
        <div className={styles.productSelectionSection}>
            {/* Toolbar: Search, Filter Tabs, Deselect All */}
            <div className={styles.tableToolbar}>
                <SearchInput
                    aria-label="Tìm kiếm sản phẩm cần hoàn"
                    className={styles.tableSearchBox}
                    onChange={setSearchTerm}
                    placeholder="Tìm sản phẩm theo tên, SKU..."
                    type="text"
                    value={searchTerm}
                />

                <div className={styles.tableControlsRight}>
                    <div className={styles.filterTabGroup} role="tablist" aria-label="Bộ lọc sản phẩm">
                        <button
                            type="button"
                            role="tab"
                            aria-selected={filterTab === "ALL"}
                            className={`${styles.filterTab} ${filterTab === "ALL" ? styles.activeFilterTab : ""}`}
                            onClick={() => setFilterTab("ALL")}
                        >
                            Tất cả ({totalItems})
                        </button>
                        <button
                            type="button"
                            role="tab"
                            aria-selected={filterTab === "SELECTED"}
                            className={`${styles.filterTab} ${filterTab === "SELECTED" ? styles.activeFilterTab : ""}`}
                            onClick={() => setFilterTab("SELECTED")}
                        >
                            Đã chọn ({selectedCount})
                        </button>
                    </div>

                    {selectedCount > 0 && (
                        <Button onClick={onDeselectAll} size="sm">
                            Bỏ chọn tất cả
                        </Button>
                    )}
                </div>
            </div>

            {/* Scrollable Table with Sticky Header */}
            <div className={styles.itemTableWrap}>
                <Table className={styles.itemTable}>
                    <colgroup>
                        <col style={{ width: "48%" }} />
                        <col style={{ width: "16%" }} />
                        <col style={{ width: "16%" }} />
                        <col style={{ width: "20%" }} />
                    </colgroup>
                    <TableHead>
                        <TableRow>
                            <TableHeaderCell>Sản phẩm</TableHeaderCell>
                            <TableHeaderCell>Đã mua</TableHeaderCell>
                            <TableHeaderCell>Còn được hoàn</TableHeaderCell>
                            <TableHeaderCell>Số lượng hoàn</TableHeaderCell>
                        </TableRow>
                    </TableHead>
                    <tbody>
                        {filteredItemsWithIndices.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={4} className={styles.emptyTableState}>
                                    {filterTab === "SELECTED" && selectedCount === 0 ? (
                                        <span>Chưa có sản phẩm nào được chọn.</span>
                                    ) : (
                                        <span>Không tìm thấy sản phẩm phù hợp với từ khóa.</span>
                                    )}
                                </TableCell>
                            </TableRow>
                        ) : (
                            filteredItemsWithIndices.map(({ item, itemKey }) => {
                                const draft = draftItems[itemKey];
                                const isSelected = Boolean(draft?.selected);
                                const availableQuantity =
                                    validation.remainingQuantities[itemKey] ?? 0;
                                const isExhausted = availableQuantity < 1;
                                const error = isSelected ? validation.errors[itemKey] : null;
                                const errorId = `quantity-error-${itemKey}`;
                                const numQty = Number(draft?.quantity ?? "1");
                                const canStepDown =
                                    isSelected &&
                                    /^\d+$/.test(draft?.quantity?.trim() ?? "") &&
                                    numQty > 1;
                                const canStepUp =
                                    isSelected &&
                                    /^\d+$/.test(draft?.quantity?.trim() ?? "") &&
                                    numQty < availableQuantity;

                                return (
                                    <TableRow
                                        key={itemKey}
                                        className={isSelected ? styles.selectedTableRow : undefined}
                                    >
                                        <TableCell>
                                            <label className={styles.itemSelector}>
                                                <input
                                                    checked={isSelected}
                                                    disabled={!isSelected && isExhausted}
                                                    onChange={(event) =>
                                                        handleToggleSelect(
                                                            itemKey,
                                                            event.target.checked,
                                                            availableQuantity
                                                        )
                                                    }
                                                    type="checkbox"
                                                    aria-label={`Chọn sản phẩm ${getItemName(item)}`}
                                                />
                                                <span className={styles.itemDetails}>
                                                    <strong className={styles.itemName}>
                                                        {getItemName(item)}
                                                    </strong>
                                                    <small className={styles.itemMeta}>
                                                        {item.skuSnapshot ? (
                                                            <span className={styles.skuTag}>
                                                                {item.skuSnapshot}
                                                            </span>
                                                        ) : null}
                                                        <span>{getItemVariant(item)}</span>
                                                    </small>
                                                </span>
                                            </label>
                                        </TableCell>
                                        <TableCell className={styles.quantityCell}>
                                            {formatReturnQuantity(item.quantity)}
                                        </TableCell>
                                        <TableCell className={styles.quantityCell}>
                                            {isExhausted ? (
                                                <StatusBadge shape="tag">Hết SL hoàn</StatusBadge>
                                            ) : (
                                                formatReturnQuantity(availableQuantity)
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            <div
                                                className={`${styles.quantityStepper} ${
                                                    error ? styles.quantityStepperError : ""
                                                }`}
                                            >
                                                <button
                                                    type="button"
                                                    className={styles.stepButton}
                                                    disabled={!canStepDown}
                                                    onClick={() =>
                                                        handleStepQuantity(
                                                            itemKey,
                                                            draft?.quantity ?? "1",
                                                            -1,
                                                            availableQuantity
                                                        )
                                                    }
                                                    aria-label={`Giảm số lượng ${getItemName(item)}`}
                                                >
                                                    <Minus size={14} aria-hidden="true" />
                                                </button>
                                                <input
                                                    className={styles.quantityInput}
                                                    aria-label={`Số lượng hoàn ${getItemName(item)}`}
                                                    aria-invalid={Boolean(error)}
                                                    aria-describedby={error ? errorId : undefined}
                                                    disabled={!isSelected}
                                                    inputMode="numeric"
                                                    onChange={(event) =>
                                                        handleQuantityChange(
                                                            itemKey,
                                                            event.target.value
                                                        )
                                                    }
                                                    type="text"
                                                    value={draft?.quantity ?? "1"}
                                                />
                                                <button
                                                    type="button"
                                                    className={styles.stepButton}
                                                    disabled={!canStepUp}
                                                    onClick={() =>
                                                        handleStepQuantity(
                                                            itemKey,
                                                            draft?.quantity ?? "1",
                                                            1,
                                                            availableQuantity
                                                        )
                                                    }
                                                    aria-label={`Tăng số lượng ${getItemName(item)}`}
                                                >
                                                    <Plus size={14} aria-hidden="true" />
                                                </button>
                                            </div>
                                            {error ? (
                                                <p
                                                    className={styles.quantityError}
                                                    id={errorId}
                                                    role="alert"
                                                >
                                                    {error}
                                                </p>
                                            ) : null}
                                        </TableCell>
                                    </TableRow>
                                );
                            })
                        )}
                    </tbody>
                </Table>
            </div>

            {/* Selection Summary at Bottom of Step 2 */}
            <ReturnSelectedSummary
                selectedCount={selectedCount}
                totalQuantity={totalQuantity}
            />
        </div>
    );
}
