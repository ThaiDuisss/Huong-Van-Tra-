import { Navigate, Route, Routes } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { SystemShell } from "../components/layout/System/SystemShell";
import { BomListPage } from "../features/manufacturing/pages/BomListPage";
import { ProductionOrderListPage } from "../features/manufacturing/pages/ProductionOrderListPage";
import { CustomerListPage } from "../features/customer/pages/CustomerListPage";
import { InventoryBalancePage } from "../features/inventory/pages/InventoryBalancePage";
import { InventoryLocationPage } from "../features/inventory/pages/InventoryLocationPage";
import { InventoryTransactionPage } from "../features/inventory/pages/InventoryTransactionPage";
import { StockAdjustmentPage } from "../features/inventory/pages/StockAdjustmentPage";
import { StockInPage } from "../features/inventory/pages/StockInPage";
import { CategoryListPage } from "../features/product/pages/CategoryListPage";
import { PriceListDetailPage } from "../features/product/pages/PriceListDetailPage";
import { PriceListPage } from "../features/product/pages/PriceListPage";
import { ProductDetailPage } from "../features/product/pages/ProductDetailPage";
import { ProductListPage } from "../features/product/pages/ProductListPage";
import { ProductVariantDetailPage } from "../features/product/pages/ProductVariantDetailPage";
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
                    path="categories"
                    element={<CategoryListPage />}
                />
                <Route
                    path="products"
                    element={<ProductListPage />}
                />
                <Route
                    path="products/:id"
                    element={<ProductDetailPage />}
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
                    path="product-variants/:id"
                    element={<ProductVariantDetailPage />}
                />
                <Route
                    path="price-lists"
                    element={<PriceListPage />}
                />
                <Route
                    path="price-lists/:id"
                    element={<PriceListDetailPage />}
                />
                <Route
                    path="inventory/locations"
                    element={<InventoryLocationPage />}
                />
                <Route
                    path="inventory/balances"
                    element={<InventoryBalancePage />}
                />
                <Route
                    path="inventory/stock-in"
                    element={<StockInPage />}
                />
                <Route
                    path="inventory/adjustment"
                    element={<StockAdjustmentPage />}
                />
                <Route
                    path="inventory/transactions"
                    element={<InventoryTransactionPage />}
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
            </Route>

            <Route
                path="*"
                element={<NotFoundPage />}
            />
        </Routes>
    );
}
