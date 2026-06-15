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
    manufacturingBoms: {
        path: routePaths.bomList
    },
    productionOrders: {
        path: routePaths.productionOrders
    }
} as const;
