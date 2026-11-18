import { hasAnyRole } from "./auth.utils";
import { PERMISSIONS } from "./permissions";
import { CUSTOMER_RETURN_QC_ROLES, INVENTORY_OPERATION_ROLES, POS_WORKSPACE_ROLES } from "./role-utils";
import { ROLES } from "./roles";
import { useAuthorization } from "./useAuthorization";

const RETURN_DETAIL_FULL_TAB_ROLES = [
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.QC,
    ROLES.INVENTORY_STAFF
] as const;

const SALE_POS_ROLES = [ROLES.SALE_STAFF, "SALE_POS"] as const;
const LEGACY_RECEIVE_ROLES = INVENTORY_OPERATION_ROLES.filter((role) => role !== ROLES.QC);

/** SALE_POS (ROLE_SALE_STAFF / alias POS_STAFF / SALE_POS) chỉ xem tab Tổng quan trong popup Chi tiết phiếu hoàn. */
export function isSalePosReturnDetailOverviewOnly(roles: readonly string[] | undefined): boolean {
    return hasAnyRole(roles, SALE_POS_ROLES) && !hasAnyRole(roles, RETURN_DETAIL_FULL_TAB_ROLES);
}

/** Apply role and permission checks; QC receipt requires a known permission grant. */
export function getOrderReturnAccess(roles: readonly string[] | undefined, permissions: readonly string[] | undefined) {
    const action = (allowedRoles: readonly string[], permission: string) =>
        hasAnyRole(roles, [ROLES.SUPER_ADMIN]) ||
        (hasAnyRole(roles, allowedRoles) && (permissions === undefined || permissions.includes(permission)));
    const canReadSales = action(POS_WORKSPACE_ROLES, PERMISSIONS.ORDER_VIEW);
    const canReadWarehouse = action(INVENTORY_OPERATION_ROLES, PERMISSIONS.INVENTORY_VIEW);
    const canReadQc = action(POS_WORKSPACE_ROLES, PERMISSIONS.INVENTORY_VIEW) || canReadWarehouse;
    const canQc = action(CUSTOMER_RETURN_QC_ROLES, PERMISSIONS.INVENTORY_QC);
    return {
        canReadSales,
        canReadWarehouse,
        canRead: canReadSales || canReadWarehouse,
        canReadQc,
        canCreate: action(POS_WORKSPACE_ROLES, PERMISSIONS.ORDER_RETURN),
        canReceive: action(
            permissions === undefined ? LEGACY_RECEIVE_ROLES : INVENTORY_OPERATION_ROLES,
            PERMISSIONS.INVENTORY_RECEIVE
        ),
        canQc,
        canDisposition: canQc && action(CUSTOMER_RETURN_QC_ROLES, PERMISSIONS.INVENTORY_ADJUST),
        canRefund: action(POS_WORKSPACE_ROLES, PERMISSIONS.ORDER_REFUND),
        returnDetailOverviewOnly: isSalePosReturnDetailOverviewOnly(roles),
    };
}

export function useOrderReturnAccess() {
    const { roles, permissions } = useAuthorization();
    return getOrderReturnAccess(roles, permissions);
}
