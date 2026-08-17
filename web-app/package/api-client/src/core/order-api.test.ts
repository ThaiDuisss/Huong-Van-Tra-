import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "../../../../apps/pos-web/src/api/api-client";
import { exportInvoices } from "../../../../apps/pos-web/src/features/order/services/order.api";
import { buildActiveOrderExportParams } from "../../../../apps/pos-web/src/features/order/utils/order-export-filters";

vi.mock("../../../../apps/pos-web/src/api/api-client", () => ({
    apiClient: {
        download: vi.fn()
    }
}));

describe("invoice export API", () => {
    beforeEach(() => {
        vi.mocked(apiClient.download).mockReset();
    });

    it("sends all active invoice filters together", () => {
        exportInvoices(
            buildActiveOrderExportParams({
                fromDate: "2026-07-01",
                keyword: "  sen  ",
                paymentStatus: "PAID",
                sort: "invoicePayment",
                toDate: "2026-07-30"
            })
        );

        expect(apiClient.download).toHaveBeenCalledWith(
            "/orders/invoices/export/excel",
            {
                query: {
                    fromDate: "2026-07-01",
                    keyword: "sen",
                    paymentStatus: "PAID",
                    sort: "invoicePayment",
                    toDate: "2026-07-30"
                }
            }
        );
    });

    it("omits blank and inactive filters", () => {
        exportInvoices(
            buildActiveOrderExportParams({
                keyword: "   ",
                paymentStatus: "ALL",
                status: "ALL"
            })
        );

        expect(apiClient.download).toHaveBeenCalledWith(
            "/orders/invoices/export/excel",
            { query: {} }
        );
    });

    it("sends an empty query for exporting all invoices", () => {
        exportInvoices();

        expect(apiClient.download).toHaveBeenCalledWith(
            "/orders/invoices/export/excel",
            { query: {} }
        );
    });
});
