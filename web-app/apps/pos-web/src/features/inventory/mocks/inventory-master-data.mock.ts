import type { InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";

export const inventoryMasterLocations = {
    branchCauGiay: { branchCode: "CG", code: "BR-CG-WH", id: "loc-branch-cau-giay", isCentral: false, name: "Kho chi nhánh Cầu Giấy" },
    branchTayMo: { branchCode: "TM", code: "BR-TM-WH", id: "loc-branch-tay-mo", isCentral: false, name: "Kho chi nhánh Tây Mỗ" },
    central: { branchCode: "HN", code: "WH-HN", id: "loc-central", isCentral: true, name: "Kho trung tâm Hà Nội" },
    centralDamaged: { branchCode: "HN", code: "WH-HN-DMG", id: "loc-central-damaged", isCentral: true, name: "Khu damaged trung tâm" },
    centralQc: { branchCode: "HN", code: "WH-HN-QC", id: "loc-central-qc", isCentral: true, name: "Kho chờ QC trung tâm" },
    centralQuarantine: { branchCode: "HN", code: "WH-HN-QTN", id: "loc-central-quarantine", isCentral: true, name: "Khu quarantine trung tâm" },
    counterCauGiay01: { branchCode: "CG", code: "POS-CG-01", id: "loc-counter-cau-giay-01", isCentral: false, name: "Quầy Cầu Giấy 01" }
} satisfies Record<string, InventoryLocationSummary>;

export const inventoryMasterLocationList = Object.values(inventoryMasterLocations);

export const inventoryMasterMaterials = [
    { category: "Trà nền", id: "mat-olong", name: "Trà Ô Long", skuCode: "MAT-OLONG", unit: "kg", unitCost: 210000 },
    { category: "Hoa ướp", id: "mat-dried-lotus", name: "Hoa sen khô", skuCode: "MAT-LOTUS-DRY", unit: "kg", unitCost: 320000 },
    { category: "Hoa ướp", id: "mat-jasmine", name: "Hoa nhài", skuCode: "MAT-JASMINE", unit: "kg", unitCost: 260000 },
    { category: "Bao bì", id: "mat-filter-bag", name: "Túi lọc", skuCode: "PKG-FILTER-BAG", unit: "cái", unitCost: 800 },
    { category: "Bao bì", id: "mat-paper-box", name: "Hộp giấy", skuCode: "PKG-PAPER-BOX", unit: "cái", unitCost: 4500 },
    { category: "Bao bì", id: "mat-zipper-bag", name: "Túi zipper", skuCode: "PKG-ZIPPER", unit: "cái", unitCost: 1800 }
] as const;

export const inventoryMasterFinishedProducts = [
    { category: "Trà ướp hoa", id: "fg-lotus-100", name: "Trà Sen 100g", skuCode: "FG-TRA-SEN-100", unit: "gói", unitCost: 98000 },
    { category: "Trà ướp hoa", id: "fg-lotus-250", name: "Trà Sen 250g", skuCode: "FG-TRA-SEN-250", unit: "gói", unitCost: 215000 },
    { category: "Trà ướp hoa", id: "fg-jasmine-100", name: "Trà Nhài 100g", skuCode: "FG-TRA-NHAI-100", unit: "gói", unitCost: 86000 }
] as const;

export const inventoryMasterBatches = {
    available: { batchCode: "BATCH-OLONG-AVAILABLE", expiryDate: "2027-03-20T00:00:00.000Z", id: "batch-available", status: "AVAILABLE" },
    damaged: { batchCode: "BATCH-ZIPPER-DAMAGED", expiryDate: "2027-05-12T00:00:00.000Z", id: "batch-damaged", status: "DAMAGED" },
    expired: { batchCode: "BATCH-LOTUS-EXPIRED", expiryDate: "2026-07-30T00:00:00.000Z", id: "batch-expired", status: "EXPIRED" },
    nearExpiry: { batchCode: "BATCH-JASMINE-NEAR", expiryDate: "2026-08-08T00:00:00.000Z", id: "batch-near-expiry", status: "NEAR_EXPIRY" },
    quarantine: { batchCode: "BATCH-FILTER-QTN", expiryDate: "2027-01-15T00:00:00.000Z", id: "batch-quarantine", status: "QUARANTINE" }
} as const;

export const inventoryMasterDocuments = {
    customBundleIssue: { code: "PXK-CB-20260802-001", id: "doc-custom-bundle-issue-1" },
    customerReturnReceipt: { code: "PNK-CRT-20260801-001", id: "doc-customer-return-receipt-1" },
    disposalWriteOff: { code: "PXK-WRITEOFF-DSP-20260801-001", id: "doc-disposal-write-off-1" },
    goodsReceipt: { code: "PNK-GR-20260720-001", id: "doc-goods-receipt-1" },
    productionIssue: { code: "PXK-LSX-20260801-001", id: "doc-production-issue-1" },
    productionReceipt: { code: "PNK-LSX-20260801-001", id: "doc-production-receipt-1" },
    stocktakeAdjustment: { code: "DCK-STK-20260801-001", id: "doc-stocktake-adjustment-1" },
    supplierReturnIssue: { code: "PXK-SRT-20260801-001", id: "doc-supplier-return-issue-1" },
    transferIssue: { code: "PXK-TRF-20260801-001", id: "doc-transfer-issue-1" },
    transferReceipt: { code: "PNK-TRF-20260801-001", id: "doc-transfer-receipt-1" }
} as const;

export const inventoryMasterUsers: Record<string, UserSnapshot> = {
    accountant: { id: "user-accountant", name: "Lan Kế toán", role: "ACCOUNTANT" },
    admin: { id: "user-admin", name: "Admin Hương Vân", role: "ADMIN" },
    branchManager: { id: "user-branch-manager", name: "Minh Quản lý Cầu Giấy", role: "BRANCH_MANAGER" },
    director: { id: "user-director", name: "Hà Giám đốc", role: "DIRECTOR" },
    qc: { id: "user-qc", name: "Vy QC", role: "QC_STAFF" },
    sales: { id: "user-sales", name: "An Bán hàng", role: "SALES_STAFF" },
    warehouse: { id: "user-warehouse", name: "Kho trung tâm Hà Nội", role: "WAREHOUSE" }
};
