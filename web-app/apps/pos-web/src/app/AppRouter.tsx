import { Navigate, Route, Routes } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { SystemShell } from "../components/layout/System/SystemShell";
import { BomListPage } from "../features/manufacturing/pages/BomListPage";
import { ProductionOrderListPage } from "../features/manufacturing/pages/ProductionOrderListPage";
import { RawMaterialListPage } from "../features/manufacturing/pages/RawMaterialListPage";
import { CustomerListPage } from "../features/customer/pages/CustomerListPage";
import { InvoiceDetailPage } from "../features/order/pages/InvoiceDetailPage";
import { InvoiceListPage } from "../features/order/pages/InvoiceListPage";
import { OrderListPage } from "../features/order/pages/OrderListPage";
import { InventoryBalancePage } from "../features/inventory/pages/InventoryBalancePage";
import { InventoryLocationPage } from "../features/inventory/pages/InventoryLocationPage";
import { InventoryTransactionPage } from "../features/inventory/pages/InventoryTransactionPage";
import { CategoryListPage } from "../features/product/pages/CategoryListPage";
import { UnitListPage } from "../features/product/pages/UnitListPage";
import { PriceListPage } from "../features/product/pages/PriceListPage";
import { ProductListPage } from "../features/product/pages/ProductListPage";
import { ProductVariantListPage } from "../features/product/pages/ProductVariantListPage";
import { routePaths } from "../routes/path";
import { PosSalePage } from "../features/sales/pages/PosSalePage";

export function AppRouter() {
    return (
        <Routes>
            <Route
                path={routePaths.home}
                element={
                    <Navigate
                        to={routePaths.posSales}
                        replace
                    />
                }
            />

            <Route
                path={routePaths.posSales}
                element={<PosSalePage />}
            />

            <Route
                path={routePaths.system}
                element={<SystemShell />}
            >
                <Route
                    index
                    element={
                        <Navigate
                            to={routePaths.customers}
                            replace
                        />
                    }
                />
                <Route
                    path="orders"
                    element={<OrderListPage />}
                />
                <Route
                    path="orders/invoices"
                    element={<InvoiceListPage />}
                />
                <Route
                    path="orders/invoices/:invoiceId"
                    element={<InvoiceDetailPage />}
                />
                <Route
                    path="orders/returns"
                    element={<OrderListPage />}
                />
                <Route
                    path="orders/refunds"
                    element={<OrderListPage />}
                />
                <Route
                    path="categories"
                    element={<CategoryListPage />}
                />
                <Route
                    path="units"
                    element={<UnitListPage />}
                />
                <Route
                    path="products"
                    element={<ProductListPage />}
                />

                <Route
                    path="products/:productId/variants"
                    element={<ProductVariantListPage />}
                />
                <Route
                    path="product-variants"
                    element={<ProductVariantListPage />}
                />

                <Route
                    path="price-lists"
                    element={<PriceListPage />}
                />

                <Route
                    path="inventory/locations"
                    element={<InventoryLocationPage />}
                />
                <Route
                    path="inventory/transactions"
                    element={<InventoryTransactionPage />}
                />
                <Route
                    path="inventory/balances"
                    element={<InventoryBalancePage />}
                />
                <Route
                    path="customers"
                    element={<CustomerListPage />}
                />
                <Route
                    path="customers/groups"
                    element={<CustomerListPage />}
                />
                <Route
                    path="manufacturing/boms"
                    element={<BomListPage />}
                />
                <Route
                    path="manufacturing/production-orders"
                    element={<ProductionOrderListPage />}
                />
                <Route
                    path="manufacturing/raw-materials"
                    element={<RawMaterialListPage />}
                />
            </Route>

            <Route
                path="*"
                element={<NotFoundPage />}
            />
        </Routes>
    );
}
