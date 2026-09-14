import { CartList } from "../cart/CartList";
import { CartNote } from "../cart/CartNote";
import { OrderTypeSelector } from "../cart/OrderTypeSelector";
import { ProductSearchBar } from "../product/ProductSearchBar";
import { formatCurrency } from "../../services/money";
import { MessageSquarePlus } from "lucide-react";
import type {
    SaleCartItem,
    SaleManualDiscount,
    SaleOrderType,
    SaleProductPreview
} from "../../types/sale.types";
import styles from "../../pages/PosSalePage.module.css";
import { InvoiceTabs } from "./InvoiceTabs";

interface SaleWorkspaceProps {
    searchQuery: string;
    searchResults: SaleProductPreview[];
    searchLoading: boolean;
    searchError: string;
    addingProductId: string | null;
    cartItems: SaleCartItem[];
    note: string;
    orderType: SaleOrderType;
    onSearchChange(value: string): void;
    onSearchClear(): void;
    onProductAdd(product: SaleProductPreview): void;
    onProductIncrement(productId: string): void;
    onProductDecrement(productId: string): void;
    onProductRemove(productId: string): void;
    onProductDiscountChange(productId: string, discount: SaleManualDiscount | null): void;
    onProductNoteChange(productId: string, note: string): void;
    onCustomTeaOpen(): void;
    onInvoiceClose(): void;
    onNoteChange(value: string): void;
    onOrderTypeChange(value: SaleOrderType): void;
}

export function SaleWorkspace({
    searchQuery,
    searchResults,
    searchLoading,
    searchError,
    addingProductId,
    cartItems,
    note,
    orderType,
    onSearchChange,
    onSearchClear,
    onProductAdd,
    onProductIncrement,
    onProductDecrement,
    onProductRemove,
    onProductDiscountChange,
    onProductNoteChange,
    onCustomTeaOpen,
    onInvoiceClose,
    onNoteChange,
    onOrderTypeChange
}: SaleWorkspaceProps) {
    return (
        <section className={styles.saleWorkspace}>
            <div className={styles.topbar}>
                <ProductSearchBar
                    value={searchQuery}
                    results={searchResults}
                    loading={searchLoading}
                    error={searchError}
                    addingProductId={addingProductId}
                    formatCurrency={formatCurrency}
                    onChange={onSearchChange}
                    onClear={onSearchClear}
                    onAdd={onProductAdd}
                />

                <InvoiceTabs onCloseActive={onInvoiceClose} />
                <button
                    className={styles.customTeaButton}
                    onClick={onCustomTeaOpen}
                    type="button"
                >
                    <MessageSquarePlus size={18} aria-hidden="true" />
                    <span>Trà theo yêu cầu</span>
                </button>
            </div>

            <CartList
                items={cartItems}
                formatCurrency={formatCurrency}
                onIncrement={onProductIncrement}
                onDecrement={onProductDecrement}
                onRemove={onProductRemove}
                onDiscountChange={onProductDiscountChange}
                onNoteChange={onProductNoteChange}
            />

            <CartNote value={note} onChange={onNoteChange} />

            <OrderTypeSelector
                value={orderType}
                onChange={onOrderTypeChange}
            />
        </section>
    );
}
