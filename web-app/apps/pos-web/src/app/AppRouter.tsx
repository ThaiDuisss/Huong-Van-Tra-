import { Navigate, Route, Routes } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { SystemShell } from "../components/layout/System/SystemShell";
import { BomListPage } from "../features/manufacturing/pages/BomListPage";
import { ProductionOrderListPage } from "../features/manufacturing/pages/ProductionOrderListPage";
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
                            to={routePaths.bomList}
                            replace
                        />
                    }
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
