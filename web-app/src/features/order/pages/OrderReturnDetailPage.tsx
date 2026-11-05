import { Navigate, useParams } from "react-router-dom";

import { routePaths } from "../../../routes/path";

/**
 * Giữ tương thích link cũ `/system/orders/returns/:returnId`:
 * chuyển sang danh sách Hoàn hàng và mở sẵn hộp thoại chi tiết (`?detail=<id>`).
 */
export function OrderReturnDetailPage() {
    const { returnId } = useParams<{ returnId: string }>();
    const target = returnId
        ? `${routePaths.returns}?detail=${encodeURIComponent(returnId)}`
        : routePaths.returns;

    return <Navigate replace to={target} />;
}
