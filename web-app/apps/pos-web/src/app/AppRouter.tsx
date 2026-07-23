import { Navigate, Route, Routes } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { SystemShell } from "../components/layout/System/SystemShell";
import { BomListPage } from "../features/manufacturing/pages/BomListPage";
import { BomDetailPage } from "../features/manufacturing/pages/BomDetailPage";
import { ProductionOrderListPage } from "../features/manufacturing/pages/ProductionOrderListPage";
import { RawMaterialListPage } from "../features/manufacturing/pages/RawMaterialListPage";
import { CustomerListPage } from "../features/customer/pages/CustomerListPage";
import { CustomerTierManagementPage } from "../features/customer/pages/CustomerTierManagementPage";
import { InvoiceDetailPage } from "../features/order/pages/InvoiceDetailPage";
import { InvoiceListPage } from "../features/order/pages/InvoiceListPage";
import { OrderListPage } from "../features/order/pages/OrderListPage";
import { SalesHistoryPage } from "../features/order/pages/SalesHistoryPage";
import { InventoryBalancePage } from "../features/inventory/pages/InventoryBalancePage";
import { InventoryLocationPage } from "../features/inventory/pages/InventoryLocationPage";
import { InventoryTransactionPage } from "../features/inventory/pages/InventoryTransactionPage";
import { CouponListPage } from "../features/marketing/pages/CouponListPage";
import { CouponUsageListPage } from "../features/marketing/pages/CouponUsageListPage";
import { DiscountProgramListPage } from "../features/marketing/pages/DiscountProgramListPage";
import { PromotionDetailPage } from "../features/marketing/pages/PromotionDetailPage";
import { PromotionPage } from "../features/marketing/pages/PromotionPage";
import { CategoryListPage } from "../features/product/pages/CategoryListPage";
import { UnitListPage } from "../features/product/pages/UnitListPage";
import { PriceListPage } from "../features/product/pages/PriceListPage";
import { ProductListPage } from "../features/product/pages/ProductListPage";
import { ProductVariantListPage } from "../features/product/pages/ProductVariantListPage";
import { ProductVariantDetailPage } from "../features/product/pages/ProductVariantDetailPage";
import { routePaths } from "../routes/path";
import { PosSalePage } from "../features/sales/pages/PosSalePage";
import { PosShell } from "../components/layout/Pos/PosShell";
import { ProtectedRoute } from "../auth/ProtectedRoute";

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
                element={
                    <ProtectedRoute>
                        <PosSalePage />
                    </ProtectedRoute>
                }
            />

            <Route
                path={routePaths.salesHistory}
                element={
                    <ProtectedRoute>
                        <PosShell>
                            <SalesHistoryPage />
                        </PosShell>
                    </ProtectedRoute>
                }
            />

            <Route
                path={routePaths.system}
                element={
                    <ProtectedRoute>
                        <SystemShell />
                    </ProtectedRoute>
                }
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
                    path="product-variants/:id"
                    element={<ProductVariantDetailPage />}
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
                    path="marketing/events"
                    element={<DiscountProgramListPage />}
                />
                <Route
                    path="marketing/discount-programs"
                    element={<PromotionPage />}
                />
                <Route
                    path="marketing/discount-programs/:programId"
                    element={<PromotionDetailPage />}
                />
                <Route
                    path="marketing/discount-codes"
                    element={<CouponListPage />}
                />
                <Route
                    path="marketing/discount-history"
                    element={<CouponUsageListPage />}
                />
                <Route
                    path="marketing/discount-rules"
                    element={<PromotionPage />}
                />
                <Route
                    path="customers/tiers"
                    element={<CustomerTierManagementPage />}
                />
                <Route
                    path="manufacturing/boms"
                    element={<BomListPage />}
                />
                <Route
                    path="manufacturing/boms/:id"
                    element={<BomDetailPage />}
                />
                <Route
                    path="manufacturing/production-orders"
                    element={<ProductionOrderListPage />}
                />
                <Route
                    path="manufacturing/ingredients"
                    element={<RawMaterialListPage />}
                />
            </Route>

            <Route
                path="*"
                element={<NotFoundPage />}
            />
        </Routes >
    );
}
