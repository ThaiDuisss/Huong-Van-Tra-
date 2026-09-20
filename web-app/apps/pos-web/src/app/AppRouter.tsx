import { Navigate, Outlet, Route, Routes, useParams } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { SystemShell } from "../components/layout/System/SystemShell";
import { ProductionOrderListPage } from "../features/manufacturing/pages/ProductionOrderListPage";
import { ProductionOrderCreatePage as ManufacturingProductionOrderCreatePage } from "../features/manufacturing/pages/ProductionOrderCreatePage";
import { ProductionOrderDetailPage as ManufacturingProductionOrderDetailPage } from "../features/manufacturing/pages/ProductionOrderDetailPage";
import { RawMaterialListPage } from "../features/manufacturing/pages/RawMaterialListPage";
import { SupplierListPage } from "../features/manufacturing/pages/SupplierListPage";
import { CustomerListPage } from "../features/customer/pages/CustomerListPage";
import { CustomerTierManagementPage } from "../features/customer/pages/CustomerTierManagementPage";
import { DashboardOverviewPage } from "../features/dashboard/pages/DashboardOverviewPage";
import { InvoiceDetailPage } from "../features/order/pages/InvoiceDetailPage";
import { InvoiceListPage } from "../features/order/pages/InvoiceListPage";
import { OrderListPage } from "../features/order/pages/OrderListPage";
import { SalesHistoryPage } from "../features/order/pages/SalesHistoryPage";
import { InventoryBalancePage } from "../features/inventory/pages/InventoryBalancePage";
import { GoodsReceiptCreatePage } from "../features/inventory/pages/GoodsReceiptCreatePage";
import { GoodsReceiptDetailPage } from "../features/inventory/pages/GoodsReceiptDetailPage";
import { GoodsReceiptListPage } from "../features/inventory/pages/GoodsReceiptListPage";
import { GoodsReceiptImportPage } from "../features/inventory/pages/GoodsReceiptImportPage";
import { GoodsReceiptImportReviewPage } from "../features/inventory/pages/GoodsReceiptImportReviewPage";
import { GoodsReceiptImportListPage } from "../features/inventory/pages/GoodsReceiptImportListPage";
import { CustomerReturnDetailPage } from "../features/inventory/pages/CustomerReturnDetailPage";
import { CustomerReturnListPage } from "../features/inventory/pages/CustomerReturnListPage";
import { CustomBundleManagementDetailPage } from "../features/inventory/pages/CustomBundleManagementDetailPage";
import { CustomBundleManagementListPage } from "../features/inventory/pages/CustomBundleManagementListPage";
import { InventoryLocationPage } from "../features/inventory/pages/InventoryLocationPage";
import { InventoryManagementPage } from "../features/inventory/pages/InventoryManagementPage";
import { InventoryTransactionPage } from "../features/inventory/pages/InventoryTransactionPage";
import { DisposalDetailPage } from "../features/inventory/pages/DisposalDetailPage";
import { DisposalListPage } from "../features/inventory/pages/DisposalListPage";
import { QualityBatchDetailPage } from "../features/inventory/pages/QualityBatchDetailPage";
import { QualityDashboardPage } from "../features/inventory/pages/QualityDashboardPage";
import { ReorderPolicyPage } from "../features/inventory/pages/ReorderPolicyPage";
import { PosCustomBundleCreatePage } from "../features/inventory/pages/PosCustomBundleCreatePage";
import { PosCustomBundleDetailPage } from "../features/inventory/pages/PosCustomBundleDetailPage";
import { PosCustomerReturnCreatePage } from "../features/inventory/pages/PosCustomerReturnCreatePage";
import { PosCustomerReturnDetailPage } from "../features/inventory/pages/PosCustomerReturnDetailPage";
import { StockDocumentDetailPage } from "../features/inventory/pages/StockDocumentDetailPage";
import { StockDocumentListPage } from "../features/inventory/pages/StockDocumentListPage";
import { StocktakeCreatePage } from "../features/inventory/pages/StocktakeCreatePage";
import { StocktakeAdjustmentPage } from "../features/inventory/pages/StocktakeAdjustmentPage";
import { StocktakeAdjustmentListPage } from "../features/inventory/pages/StocktakeAdjustmentListPage";
import { StocktakeDetailPage } from "../features/inventory/pages/StocktakeDetailPage";
import { StocktakeListPage } from "../features/inventory/pages/StocktakeListPage";
import { StocktakeReconciliationPage } from "../features/inventory/pages/StocktakeReconciliationPage";
import { StockAlertDetailPage } from "../features/inventory/pages/StockAlertDetailPage";
import { StockAlertListPage } from "../features/inventory/pages/StockAlertListPage";
import { SupplierReturnCreatePage } from "../features/inventory/pages/SupplierReturnCreatePage";
import { SupplierReturnDetailPage } from "../features/inventory/pages/SupplierReturnDetailPage";
import { SupplierReturnListPage } from "../features/inventory/pages/SupplierReturnListPage";
import { TransferCreatePage } from "../features/inventory/pages/TransferCreatePage";
import { TransferDetailPage } from "../features/inventory/pages/TransferDetailPage";
import { TransferListPage } from "../features/inventory/pages/TransferListPage";
import { CouponListPage } from "../features/marketing/pages/CouponListPage";
import { CouponUsageListPage } from "../features/marketing/pages/CouponUsageListPage";
import { DiscountProgramListPage } from "../features/marketing/pages/DiscountProgramListPage";
import { PromotionDetailPage } from "../features/marketing/pages/PromotionDetailPage";
import { PromotionPage } from "../features/marketing/pages/PromotionPage";
import { CategoryListPage } from "../features/product/pages/CategoryListPage";
import { UnitListPage } from "../features/product/pages/UnitListPage";
import { PriceListPage } from "../features/product/pages/PriceListPage";
import { ProductListPage } from "../features/product/pages/ProductListPage";
import { ProductImportPage } from "../features/product/pages/ProductImportPage";
import { ProductVariantImportPage } from "../features/product/pages/ProductVariantImportPage";
import { ProductImportReviewPage } from "../features/product/pages/ProductImportReviewPage";
import { ImportBatchListPage } from "../features/product/pages/ImportBatchListPage";
import { ProductVariantListPage } from "../features/product/pages/ProductVariantListPage";
import { ProductVariantDetailPage } from "../features/product/pages/ProductVariantDetailPage";
import { routePaths } from "../routes/path";
import { PosSalePage } from "../features/sales/pages/PosSalePage";
import { PosShell } from "../components/layout/Pos/PosShell";
import { ShelfManagementPage } from "../features/inventory/pages/ShelfManagementPage";
import { useAuth } from "../auth/auth-context";
import { ProtectedRoute } from "../auth/ProtectedRoute";
import {
  BACKOFFICE_ROLES,
  INVENTORY_OPERATION_ROLES,
  INVENTORY_ROLES,
  MANUFACTURING_ROLES,
  MARKETING_ROLES,
  ORDER_ROLES,
  POS_WORKSPACE_ROLES,
  PRODUCT_ROLES,
  getDefaultRouteForRoles,
  type AppRole,
} from "../auth/role-utils";

function RoleGate({ roles }: { roles: AppRole[] }) {
  return (
    <ProtectedRoute roles={roles}>
      <Outlet />
    </ProtectedRoute>
  );
}

function RedirectToManufacturingProductionOrderDetail() {
  const { productionOrderId } = useParams();
  return (
    <Navigate
      to={routePaths.productionOrderDetail.replace(":id", productionOrderId ?? "")}
      replace
    />
  );
}

function RoleBasedRedirect() {
  const { roles, user } = useAuth();

  if (!user && !roles) {
    return (
      <main className="grid min-h-dvh place-items-center bg-[#f7f8fb] text-sm font-bold text-[#315f3c]">
        Đang tải quyền truy cập...
      </main>
    );
  }

  return <Navigate to={getDefaultRouteForRoles(roles)} replace />;
}

export function AppRouter() {
  return (
    <Routes>
      <Route
        path={routePaths.home}
        element={
          <ProtectedRoute>
            <RoleBasedRedirect />
          </ProtectedRoute>
        }
      />

      <Route
        path={routePaths.posSales}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosSalePage />
          </ProtectedRoute>
        }
      />
      <Route
        path={routePaths.posCustomBundleCreate}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosCustomBundleCreatePage />
          </ProtectedRoute>
        }
      />
      <Route
        path={routePaths.posCustomBundleDetail}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosCustomBundleDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path={routePaths.posCustomerReturnCreate}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosCustomerReturnCreatePage />
          </ProtectedRoute>
        }
      />
      <Route
        path={routePaths.posCustomerReturnDetail}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosCustomerReturnDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path={routePaths.salesHistory}
        element={
          <ProtectedRoute roles={POS_WORKSPACE_ROLES}>
            <PosShell>
              <SalesHistoryPage />
            </PosShell>
          </ProtectedRoute>
        }
      />

      <Route
        path={routePaths.system}
        element={
          <ProtectedRoute roles={BACKOFFICE_ROLES}>
            <SystemShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to={routePaths.systemDashboard} replace />} />
        <Route path="dashboard" element={<DashboardOverviewPage />} />
        <Route element={<RoleGate roles={ORDER_ROLES} />}>
          <Route path="orders" element={<OrderListPage />} />
          <Route path="orders/invoices" element={<InvoiceListPage />} />
          <Route path="orders/invoices/:invoiceId" element={<InvoiceDetailPage />} />
          <Route path="orders/returns" element={<OrderListPage />} />
          <Route path="orders/refunds" element={<OrderListPage />} />
        </Route>
        <Route element={<RoleGate roles={PRODUCT_ROLES} />}>
          <Route path="categories" element={<CategoryListPage />} />
          <Route path="units" element={<UnitListPage />} />
          <Route path="products" element={<ProductListPage />} />

          <Route path="products/:productId/variants" element={<ProductVariantListPage />} />
          <Route path="product-variants" element={<ProductVariantListPage />} />
          <Route path="product-variants/:id" element={<ProductVariantDetailPage />} />

          <Route path="price-lists" element={<PriceListPage />} />
        </Route>

        <Route element={<RoleGate roles={PRODUCT_ROLES} />}>
          <Route path="import-excel" element={<ImportBatchListPage />} />
          <Route path="import-excel/new" element={<ProductImportPage />} />
          <Route path="import-excel/variants/new" element={<ProductVariantImportPage />} />
          <Route path="import-excel/:batchId" element={<ProductImportReviewPage />} />
        </Route>

        <Route element={<RoleGate roles={INVENTORY_ROLES} />}>
          <Route path="inventory/locations" element={<InventoryLocationPage />} />
          <Route path="inventory/transactions" element={<InventoryTransactionPage />} />
          <Route path="inventory/shelves" element={<ShelfManagementPage />} />
          <Route path="inventory/balances" element={<InventoryBalancePage />} />
          <Route
            path="inventory/documents"
            element={<Navigate to={routePaths.inventoryDocuments} replace />}
          />
          <Route
            path="inventory/raw-material-receipts"
            element={<Navigate to={routePaths.goodsReceipts} replace />}
          />
          <Route
            path="inventory/production-orders"
            element={<Navigate to={routePaths.inventoryProductionOrderList} replace />}
          />
          <Route
            path="inventory/tea-bundles"
            element={<Navigate to={routePaths.customBundles} replace />}
          />
          <Route
            path="inventory/branch-transfers"
            element={<Navigate to={routePaths.inventoryTransfers} replace />}
          />
          <Route
            path="inventory/stock-counts"
            element={<Navigate to={routePaths.inventoryStocktakes} replace />}
          />
          <Route
            path="inventory/customer-returns"
            element={<Navigate to={routePaths.inventoryCustomerReturnList} replace />}
          />
          <Route
            path="inventory/supplier-returns"
            element={<Navigate to={routePaths.inventorySupplierReturnList} replace />}
          />
          <Route
            path="inventory/waste-handling"
            element={<Navigate to={routePaths.inventoryQuality} replace />}
          />
          <Route
            path="inventory/replenishment-alerts"
            element={<Navigate to={routePaths.inventoryAlerts} replace />}
          />
          <Route path="inventory/:workspaceId" element={<InventoryManagementPage />} />
        </Route>
        <Route element={<RoleGate roles={["ADMIN", "BRANCH_MANAGER"]} />}>
          <Route path="customers" element={<CustomerListPage />} />
          <Route path="customers/groups" element={<CustomerListPage />} />
          <Route path="customers/tiers" element={<CustomerTierManagementPage />} />
        </Route>
        <Route element={<RoleGate roles={MARKETING_ROLES} />}>
          <Route path="marketing/events" element={<DiscountProgramListPage />} />
          <Route path="marketing/discount-programs" element={<PromotionPage />} />
          <Route path="marketing/discount-programs/:programId" element={<PromotionDetailPage />} />
          <Route path="marketing/discount-codes" element={<CouponListPage />} />
          <Route path="marketing/discount-history" element={<CouponUsageListPage />} />
        </Route>
        <Route element={<RoleGate roles={MANUFACTURING_ROLES} />}>
          <Route path="manufacturing/production-orders" element={<ProductionOrderListPage />} />
          <Route path="manufacturing/production-orders/new" element={<ManufacturingProductionOrderCreatePage />} />
          <Route path="manufacturing/production-orders/:id" element={<ManufacturingProductionOrderDetailPage />} />
          <Route path="manufacturing/ingredients" element={<RawMaterialListPage />} />
          <Route path="manufacturing/suppliers" element={<SupplierListPage />} />
        </Route>
      </Route>

      <Route
        path={routePaths.management}
        element={
          <ProtectedRoute roles={BACKOFFICE_ROLES}>
            <SystemShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to={routePaths.inventoryDocuments} replace />} />
        <Route element={<RoleGate roles={INVENTORY_ROLES} />}>
          <Route path="inventory/documents" element={<StockDocumentListPage />} />
          <Route path="inventory/documents/:documentId" element={<StockDocumentDetailPage />} />
        </Route>
        <Route element={<RoleGate roles={INVENTORY_OPERATION_ROLES} />}>
          <Route path="inventory/receipts" element={<GoodsReceiptListPage />} />
          <Route path="inventory/receipts/new" element={<GoodsReceiptCreatePage />} />
          <Route path="inventory/receipts/:receiptId" element={<GoodsReceiptDetailPage />} />
          <Route path="import-excel" element={<GoodsReceiptImportListPage />} />
          <Route path="import-excel/new" element={<GoodsReceiptImportPage />} />
          <Route path="import-excel/:batchId" element={<GoodsReceiptImportReviewPage />} />
        </Route>
        <Route element={<RoleGate roles={MANUFACTURING_ROLES} />}>
          <Route
            path="inventory/production-orders"
            element={<Navigate to={routePaths.productionOrders} replace />}
          />
          <Route
            path="inventory/production-orders/new"
            element={<Navigate to={routePaths.productionOrderCreate} replace />}
          />
          <Route
            path="inventory/production-orders/:productionOrderId"
            element={<RedirectToManufacturingProductionOrderDetail />}
          />
          <Route path="inventory/custom-bundles" element={<CustomBundleManagementListPage />} />
          <Route
            path="inventory/custom-bundles/:bundleId"
            element={<CustomBundleManagementDetailPage />}
          />
        </Route>
        <Route element={<RoleGate roles={INVENTORY_OPERATION_ROLES} />}>
          <Route path="inventory/transfers" element={<TransferListPage />} />
          <Route path="inventory/transfers/new" element={<TransferCreatePage />} />
          <Route path="inventory/transfers/:transferId" element={<TransferDetailPage />} />
          <Route path="inventory/stocktakes" element={<StocktakeListPage />} />
          <Route path="inventory/stocktakes/new" element={<StocktakeCreatePage />} />
          <Route path="inventory/stocktakes/:stocktakeId" element={<StocktakeDetailPage />} />
          <Route
            path="inventory/stocktakes/:stocktakeId/reconciliation"
            element={<StocktakeReconciliationPage />}
          />
          <Route
            path="inventory/stocktakes/:stocktakeId/adjustment"
            element={<StocktakeAdjustmentPage />}
          />
          <Route path="inventory/stocktake-adjustments" element={<StocktakeAdjustmentListPage />} />
          <Route path="inventory/customer-returns" element={<CustomerReturnListPage />} />
          <Route
            path="inventory/customer-returns/:returnId"
            element={<CustomerReturnDetailPage />}
          />
          <Route path="inventory/supplier-returns" element={<SupplierReturnListPage />} />
          <Route path="inventory/supplier-returns/new" element={<SupplierReturnCreatePage />} />
          <Route
            path="inventory/supplier-returns/:supplierReturnId"
            element={<SupplierReturnDetailPage />}
          />
          <Route path="inventory/quality" element={<QualityDashboardPage />} />
          <Route path="inventory/quality/batches/:batchId" element={<QualityBatchDetailPage />} />
          <Route path="inventory/disposals" element={<DisposalListPage />} />
          <Route path="inventory/disposals/:disposalId" element={<DisposalDetailPage />} />
          <Route path="inventory/alerts" element={<StockAlertListPage />} />
          <Route path="inventory/alerts/:alertId" element={<StockAlertDetailPage />} />
          <Route path="inventory/reorder-policies" element={<ReorderPolicyPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
