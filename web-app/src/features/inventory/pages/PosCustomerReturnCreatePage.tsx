import { Navigate, useLocation } from "react-router-dom";

import { routePaths } from "../../../routes/path";

/** Compatibility URL: the real Order Return wizard owns all POS return creation. */
export function PosCustomerReturnCreatePage() {
    const { search, hash } = useLocation();
    return <Navigate replace to={`${routePaths.returnCreate}${search}${hash}`} />;
}
