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
    inventoryStockIn: {
        path: routePaths.inventoryStockIn
    },
    inventoryAdjustment: {
        path: routePaths.inventoryAdjustment
    },
    inventoryTransactions: {
        path: routePaths.inventoryTransactions
    },
    manufacturingBoms: {
        path: routePaths.bomList
    },
    productionOrders: {
        path: routePaths.productionOrders
    }
} as const;
