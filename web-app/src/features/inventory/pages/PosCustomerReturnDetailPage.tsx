import { Navigate, useLocation, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";

/** Compatibility URL: load the same persisted Order Return used by the QC queue. */
export function PosCustomerReturnDetailPage() {
    const { returnId } = useParams();
    const { search, hash } = useLocation();
    const target = returnId
        ? routePaths.returnDetail.replace(":returnId", encodeURIComponent(returnId))
        : routePaths.returns;
    return <Navigate replace to={`${target}${search}${hash}`} />;
}
