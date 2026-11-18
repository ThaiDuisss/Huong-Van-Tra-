import { CartList } from "../cart/CartList";
import { CartNote } from "../cart/CartNote";
import { ProductSearchBar } from "../product/ProductSearchBar";
import { formatCurrency } from "../../services/money";
import type {
    SaleCartItem,
    SaleManualDiscount,
    SaleProductPreview
} from "../../types/sale.types";
import styles from "../../pages/PosSalePage.module.css";
import { InvoiceTabs, type InvoiceTabView } from "./InvoiceTabs";

interface SaleWorkspaceProps {
    searchQuery: string;
    searchResults: SaleProductPreview[];
    searchLoading: boolean;
    searchTotal: number;
    searchHasMore: boolean;
    searchLoadingMore: boolean;
    onSearchLoadMore(): void;
    searchError: string;
    addingProductId: string | null;
    cartItems: SaleCartItem[];
    note: string;
    onSearchChange(value: string): void;
    onSearchClear(): void;
    onProductAdd(product: SaleProductPreview): void;
    onProductIncrement(productId: string): void;
    onProductDecrement(productId: string): void;
    onProductQuantityChange(productId: string, quantity: number): void;
    onProductUnitChange(productId: string): void;
    onProductRemove(productId: string): void;
    onProductDiscountChange(productId: string, discount: SaleManualDiscount | null): void;
    onProductNoteChange(productId: string, note: string): void;
    invoiceTabs: InvoiceTabView[];
    activeInvoiceId: string;
    canAddInvoice: boolean;
    onInvoiceSelect(id: string): void;
    onInvoiceNew(): void;
    onInvoiceClose(id: string): void;
    onNoteChange(value: string): void;
}

export function SaleWorkspace({
    searchQuery,
    searchResults,
    searchLoading,
    searchTotal,
    searchHasMore,
    searchLoadingMore,
    onSearchLoadMore,
    searchError,
    addingProductId,
    cartItems,
    note,
    onSearchChange,
    onSearchClear,
    onProductAdd,
    onProductIncrement,
    onProductDecrement,
    onProductQuantityChange,
    onProductUnitChange,
    onProductRemove,
    onProductDiscountChange,
    onProductNoteChange,
    invoiceTabs,
    activeInvoiceId,
    canAddInvoice,
    onInvoiceSelect,
    onInvoiceNew,
    onInvoiceClose,
    onNoteChange
}: SaleWorkspaceProps) {
    return (
        <section className={styles.saleWorkspace}>
            <div className={styles.topbar}>
                <ProductSearchBar
                    value={searchQuery}
                    results={searchResults}
                    loading={searchLoading}
                    total={searchTotal}
                    hasMore={searchHasMore}
                    loadingMore={searchLoadingMore}
                    onLoadMore={onSearchLoadMore}
                    error={searchError}
                    addingProductId={addingProductId}
                    formatCurrency={formatCurrency}
                    onChange={onSearchChange}
                    onClear={onSearchClear}
                    onAdd={onProductAdd}
                />

                <InvoiceTabs
                    tabs={invoiceTabs}
                    activeId={activeInvoiceId}
                    canAdd={canAddInvoice}
                    onSelect={onInvoiceSelect}
                    onNew={onInvoiceNew}
                    onClose={onInvoiceClose}
                />
            </div>

            <CartList
                items={cartItems}
                formatCurrency={formatCurrency}
                onIncrement={onProductIncrement}
                onDecrement={onProductDecrement}
                onQuantityChange={onProductQuantityChange}
                onUnitChange={onProductUnitChange}
                onRemove={onProductRemove}
                onDiscountChange={onProductDiscountChange}
                onNoteChange={onProductNoteChange}
            />

            <CartNote value={note} onChange={onNoteChange} />
        </section>
    );
}
