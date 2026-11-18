import { createElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
    getOrderReturnAccess,
    isSalePosReturnDetailOverviewOnly
} from "../../../../auth/order-return-access";
import { PERMISSIONS } from "../../../../auth/permissions";
import { ROLES } from "../../../../auth/roles";
import type { OrderReturnListRow } from "../../types/order-return.types";

const mockAuth = {
    roles: [] as string[],
    permissions: undefined as string[] | undefined
};

vi.mock("../../../../components/ui/Overlay", () => ({
    ModalShell: ({ children }: { children: ReactNode }) => createElement("div", null, children)
}));

vi.mock("react-router-dom", () => ({
    useNavigate: () => vi.fn()
}));

vi.mock("../../../../auth/useAuthorization", () => ({
    useAuthorization: () => ({
        roles: mockAuth.roles,
        permissions: mockAuth.permissions,
        permissionsLoaded: true,
        hasRole: vi.fn(),
        hasAnyRole: vi.fn(),
        hasAllRoles: vi.fn(),
        hasPermission: vi.fn(),
        hasAnyPermission: vi.fn(),
        hasAllPermissions: vi.fn()
    })
}));

const sampleRow: OrderReturnListRow = {
    id: "11111111-1111-1111-1111-111111111111",
    returnCode: "RET202611180001",
    orderId: "22222222-2222-2222-2222-222222222222",
    orderCode: "ORD202611180001",
    customerName: "Khách lẻ",
    status: "CREATED",
    displayStatus: "CREATED",
    refundRequired: true,
    items: [
        {
            id: "item-1",
            orderItemId: "oi-1",
            productVariantId: "pv-1",
            productNameSnapshot: "Trà Đinh Ngọc",
            variantNameSnapshot: "Hộp 100g",
            skuSnapshot: "SKU-01",
            quantity: 1,
            reason: "DEFECTIVE",
            condition: "SEALED"
        },
        {
            id: "item-2",
            orderItemId: "oi-2",
            productVariantId: "pv-2",
            productNameSnapshot: "Trà Shan Tuyết",
            variantNameSnapshot: "Hộp 200g",
            skuSnapshot: "SKU-02",
            quantity: 1,
            reason: "WRONG_ITEM",
            condition: "SEALED"
        }
    ]
};

const mockDetailData = {
    returnItem: sampleRow,
    qcSummary: null,
    qcSummaryError: null,
    refundDetail: null,
    timeline: [],
    inspections: [],
    loading: false,
    error: null,
    reload: vi.fn()
};

vi.mock("./useReturnDetailData", () => ({
    useReturnDetailData: (row: OrderReturnListRow) => ({
        ...mockDetailData,
        returnItem: row
    })
}));

import {
    getVisibleReturnDetailTabs,
    resolveReturnDetailTab
} from "./return-detail-tabs";
import { ReturnDetailModal } from "./ReturnDetailModal";

describe("ReturnDetailModal tab visibility & receive authorization by role", () => {
    beforeEach(() => {
        mockAuth.roles = [];
        mockAuth.permissions = undefined;
    });

    it("detects SALE_POS / SALE_STAFF overview-only mode without affecting QC, MANAGER, INVENTORY_STAFF or SUPER_ADMIN", () => {
        expect(isSalePosReturnDetailOverviewOnly([ROLES.SALE_STAFF])).toBe(true);
        expect(isSalePosReturnDetailOverviewOnly(["SALE_STAFF"])).toBe(true);
        expect(isSalePosReturnDetailOverviewOnly(["POS_STAFF"])).toBe(true);
        expect(isSalePosReturnDetailOverviewOnly(["CASHIER"])).toBe(true);
        expect(isSalePosReturnDetailOverviewOnly(["SALE_POS"])).toBe(true);
        expect(isSalePosReturnDetailOverviewOnly(["ROLE_SALE_POS"])).toBe(true);

        expect(isSalePosReturnDetailOverviewOnly([ROLES.QC])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly(["QC_STAFF"])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly([ROLES.SUPER_ADMIN])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly([ROLES.SUPER_ADMIN, ROLES.SALE_STAFF])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly([ROLES.MANAGER])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly([ROLES.INVENTORY_STAFF])).toBe(false);
        expect(isSalePosReturnDetailOverviewOnly(undefined)).toBe(false);
    });

    it("denies receive to SALE_POS even if inventory:receive permission is mistakenly granted, while allowing QC_STAFF and SUPER_ADMIN", () => {
        const saleAccess = getOrderReturnAccess(
            [ROLES.SALE_STAFF],
            [PERMISSIONS.ORDER_VIEW, PERMISSIONS.ORDER_RETURN, PERMISSIONS.INVENTORY_RECEIVE]
        );
        expect(saleAccess.returnDetailOverviewOnly).toBe(true);
        expect(saleAccess.canReceive).toBe(false);
        expect(saleAccess.canCreate).toBe(true);

        const qcAccess = getOrderReturnAccess(
            ["QC_STAFF"],
            [PERMISSIONS.INVENTORY_VIEW, PERMISSIONS.INVENTORY_QC, PERMISSIONS.INVENTORY_ADJUST]
        );
        expect(qcAccess.returnDetailOverviewOnly).toBe(false);
        expect(qcAccess.canReceive).toBe(true);
        expect(qcAccess.canDisposition).toBe(true);
        expect(qcAccess.canRefund).toBe(false);

        const adminAccess = getOrderReturnAccess([ROLES.SUPER_ADMIN], []);
        expect(adminAccess.returnDetailOverviewOnly).toBe(false);
        expect(adminAccess.canReceive).toBe(true);
        expect(adminAccess.canCreate).toBe(true);
        expect(adminAccess.canDisposition).toBe(true);
        expect(adminAccess.canRefund).toBe(true);
    });

    it("restricts tabs & resolves any requested tab back to overview for SALE_POS", () => {
        expect(getVisibleReturnDetailTabs(2, true)).toEqual([{ key: "overview", label: "Tổng quan" }]);
        expect(resolveReturnDetailTab("items", true)).toBe("overview");
        expect(resolveReturnDetailTab("inspection", true)).toBe("overview");
        expect(resolveReturnDetailTab("refund", true)).toBe("overview");

        expect(getVisibleReturnDetailTabs(2, false).map((t) => t.label)).toEqual([
            "Tổng quan",
            "Sản phẩm (2)",
            "Kiểm hàng",
            "Hoàn tiền"
        ]);
        expect(resolveReturnDetailTab("inspection", false)).toBe("inspection");
    });

    it.each([ROLES.SALE_STAFF, "SALE_POS"])("hides Receive and other tabs for %s even with inventory:receive", (role) => {
        mockAuth.roles = [role];
        mockAuth.permissions = [PERMISSIONS.ORDER_VIEW, PERMISSIONS.ORDER_RETURN, PERMISSIONS.INVENTORY_RECEIVE];

        const markup = renderToStaticMarkup(
            createElement(ReturnDetailModal, {
                returnItem: sampleRow,
                allowCancel: true,
                initialTab: "inspection",
                onClose: vi.fn(),
                onToast: vi.fn()
            })
        );

        expect(markup).toContain("RET202611180001");
        expect(markup).not.toContain("Xác nhận nhận hàng");
        expect(markup).not.toContain('role="tablist"');
        expect(markup).not.toContain("Sản phẩm (2)");
        expect(markup).not.toContain(">Kiểm hàng<");
        expect(markup).not.toContain(">Hoàn tiền<");
    });

    it.each(["QC_STAFF", "ROLE_QC", "QC"])("renders all 4 tabs and Receive button for %s on CREATED, but not on RECEIVED", (role) => {
        mockAuth.roles = [role];
        mockAuth.permissions = [
            PERMISSIONS.INVENTORY_VIEW,
            PERMISSIONS.INVENTORY_ADJUST
        ];

        const createdMarkup = renderToStaticMarkup(
            createElement(ReturnDetailModal, {
                returnItem: sampleRow,
                onClose: vi.fn(),
                onToast: vi.fn()
            })
        );

        expect(createdMarkup).toContain('role="tablist"');
        expect(createdMarkup).toContain("Tổng quan");
        expect(createdMarkup).toContain("Sản phẩm (2)");
        expect(createdMarkup).toContain("Kiểm hàng");
        expect(createdMarkup).toContain("Hoàn tiền");
        expect(createdMarkup).toContain("Xác nhận nhận hàng");

        const receivedMarkup = renderToStaticMarkup(
            createElement(ReturnDetailModal, {
                returnItem: { ...sampleRow, status: "RECEIVED", displayStatus: "CREATED" },
                onClose: vi.fn(),
                onToast: vi.fn()
            })
        );
        expect(receivedMarkup).not.toContain("Xác nhận nhận hàng");
    });

    it.each([
        { permissions: undefined },
        { permissions: [] }
    ])("renders Receive for QC on CREATED when permissions are unavailable or empty ($permissions)", ({ permissions }) => {
        mockAuth.roles = ["QC_STAFF"];
        mockAuth.permissions = permissions;

        const markup = renderToStaticMarkup(
            createElement(ReturnDetailModal, {
                returnItem: sampleRow,
                onClose: vi.fn(),
                onToast: vi.fn()
            })
        );

        expect(markup).toContain('role="tablist"');
        expect(markup).toContain("Xác nhận nhận hàng");
    });

    it.each([ROLES.MANAGER, ROLES.INVENTORY_STAFF])("keeps %s eligible to receive without inventory:receive", (role) => {
        expect(getOrderReturnAccess([role], []).canReceive).toBe(true);
    });

    it("does not let an unrelated role receive even with inventory:receive", () => {
        expect(getOrderReturnAccess(["AUDITOR"], [PERMISSIONS.INVENTORY_RECEIVE]).canReceive).toBe(false);
    });

    it("does not grant other return actions to QC with only inventory:view and inventory:adjust", () => {
        const access = getOrderReturnAccess([ROLES.QC], [PERMISSIONS.INVENTORY_VIEW, PERMISSIONS.INVENTORY_ADJUST]);
        expect(access.canReadWarehouse).toBe(true);
        expect(access.canCreate).toBe(false);
        expect(access.canQc).toBe(false);
        expect(access.canDisposition).toBe(false);
        expect(access.canRefund).toBe(false);
    });

    it("preserves all 4 tabs and actions for SUPER_ADMIN", () => {
        mockAuth.roles = [ROLES.SUPER_ADMIN];
        mockAuth.permissions = [];

        const markup = renderToStaticMarkup(
            createElement(ReturnDetailModal, {
                returnItem: sampleRow,
                allowCancel: true,
                onClose: vi.fn(),
                onToast: vi.fn()
            })
        );

        expect(markup).toContain('role="tablist"');
        expect(markup).toContain("Tổng quan");
        expect(markup).toContain("Sản phẩm (2)");
        expect(markup).toContain("Kiểm hàng");
        expect(markup).toContain("Hoàn tiền");
        expect(markup).toContain("Hủy phiếu");
        expect(markup).toContain("Xác nhận nhận hàng");
    });
});
