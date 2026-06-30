import { routePaths } from "./path";
import { PosSalePage } from "../features/sales/pages/PosSalePage";

export const appRoutes = {
    home: {
        path: routePaths.home,
        redirectTo: routePaths.posSales
    },
    sales: {
        path: routePaths.posSales,
        component: PosSalePage
    },
    orders: {
        path: routePaths.orders
    },
    invoices: {
        path: routePaths.invoices
    },
    invoiceDetail: {
        path: routePaths.invoiceDetail
    },
    returns: {
        path: routePaths.returns
    },
    refunds: {
        path: routePaths.refunds
    },
    productCategories: {
        path: routePaths.categories
    },
    products: {
        path: routePaths.products
    },
    productVariants: {
        path: routePaths.productVariants
    },
    priceLists: {
        path: routePaths.priceLists
    },
    inventoryLocations: {
        path: routePaths.inventoryLocations
    },
    inventoryBalances: {
        path: routePaths.inventoryBalances
    },
    inventoryTransactions: {
        path: routePaths.inventoryTransactions
    },
    customers: {
        path: routePaths.customers
    },
    customerGroups: {
        path: routePaths.customerGroups
    },
    manufacturingBoms: {
        path: routePaths.bomList
    },
    productionOrders: {
        path: routePaths.productionOrders
    }
} as const;
