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
    manufacturingBoms: {
        path: routePaths.bomList
    },
    productionOrders: {
        path: routePaths.productionOrders
    }
} as const;
