import { Navigate, useLocation, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";

/**
 * Open a persisted return in the shared POS list detail modal, including compatibility links.
 */
export function OrderReturnDetailPage() {
    const { returnId } = useParams<{ returnId: string }>();
    const { search, hash } = useLocation();
    const params = new URLSearchParams(search);
    if (returnId) params.set("detail", returnId);
    const target = `${routePaths.returns}${params.size ? `?${params.toString()}` : ""}${hash}`;

    return <Navigate replace to={target} />;
}
