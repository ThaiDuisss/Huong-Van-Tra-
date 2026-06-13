import { Navigate, Route, Routes } from "react-router-dom";

import { NotFoundPage } from "./NotFoundPage";
import { routePaths } from "../routes/path";
import { SystemPlaceholderPage } from "../features/manufacturing/pages/SystemPlaceholderPage";
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
                element={
                    <Navigate
                        to={routePaths.bomList}
                        replace
                    />
                }
            />

            <Route
                path={routePaths.bomList}
                element={
                    <SystemPlaceholderPage
                        title="Danh sách BOM"
                        description="Màn BOM sẽ được triển khai ở task riêng."
                    />
                }
            />

            <Route
                path={routePaths.productionOrders}
                element={
                    <SystemPlaceholderPage
                        title="Lệnh sản xuất"
                        description="Màn lệnh sản xuất sẽ được triển khai ở task riêng."
                    />
                }
            />

            <Route
                path="*"
                element={<NotFoundPage />}
            />
        </Routes>
    );
}
