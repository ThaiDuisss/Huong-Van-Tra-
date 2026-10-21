import type {
    BundleIngredientOption,
    CustomBundleDetail,
    AppRole
} from "../types/custom-bundle.types";
import type { InventoryAuditEvent, InventoryLocationSummary, UserSnapshot } from "../types/stock-document.types";

const now = new Date("2026-08-02T13:00:00+07:00").getTime();
const hours = (value: number) => new Date(now + value * 60 * 60 * 1000).toISOString();

export const bundleUsers: UserSnapshot[] = [
    { id: "bundle-sales", name: "Mai Anh", role: "SALES_STAFF" },
    { id: "bundle-branch", name: "Trần Bảo Ngọc", role: "MANAGER" },
    { id: "bundle-warehouse", name: "Nguyễn Minh Anh", role: "INVENTORY_STAFF" },
    { id: "bundle-admin", name: "Phạm Thu Hương", role: "ADMIN" },
    { id: "bundle-accountant", name: "Lê Hoài Nam", role: "ACCOUNTANT" }
];

export const bundleLocations: InventoryLocationSummary[] = [
    { branchCode: "Q1", code: "BR-Q1", id: "loc-q1", isCentral: false, name: "Chi nhánh Quận 1" },
    { branchCode: "Q7", code: "BR-Q7", id: "loc-q7", isCentral: false, name: "Chi nhánh Quận 7" },
    { branchCode: "CENTRAL", code: "WH-CTR", id: "loc-central", isCentral: true, name: "Kho trung tâm" }
];

export const bundleIngredients: BundleIngredientOption[] = [
    { alternativeSkuId: "ing-olong", blocked: false, branchAvailableQuantity: 850, category: "Trà nền", centralAvailableQuantity: 2400, expired: false, id: "ing-sen", name: "Trà sen Tây Hồ", nearLowStock: false, pricePerGram: 1200, skuCode: "TRA-SEN", unit: "g" },
    { blocked: false, branchAvailableQuantity: 420, category: "Trà nền", centralAvailableQuantity: 1600, expired: false, id: "ing-olong", name: "Trà Ô Long rang", nearLowStock: true, pricePerGram: 900, skuCode: "TRA-OLONG", unit: "g" },
    { blocked: false, branchAvailableQuantity: 120, category: "Hương liệu", centralAvailableQuantity: 600, expired: false, id: "ing-jasmine", name: "Hoa nhài sấy", nearLowStock: true, pricePerGram: 1500, skuCode: "HL-JASMINE", unit: "g" },
    { blocked: true, branchAvailableQuantity: 80, category: "Hương liệu", centralAvailableQuantity: 120, expired: false, id: "ing-blocked", name: "Vỏ quýt blocked", nearLowStock: false, pricePerGram: 700, skuCode: "HL-QUYT-BLOCK", unit: "g" },
    { blocked: false, branchAvailableQuantity: 0, category: "Trà nền", centralAvailableQuantity: 0, expired: true, id: "ing-expired", name: "Trà cũ hết hạn", nearLowStock: false, pricePerGram: 300, skuCode: "TRA-OLD", unit: "g" }
];

export function bundleActor(role: AppRole) {
    return bundleUsers.find((user) => user.role === role) ?? bundleUsers[0];
}

function audit(type: InventoryAuditEvent["type"], role: AppRole, note: string): InventoryAuditEvent {
    const actor = bundleActor(role);
    return { actor, happenedAt: new Date().toISOString(), id: `${type}-${Date.now()}`, note, roleSnapshot: actor.role, type };
}

export const customBundleMockData: CustomBundleDetail[] = [
    {
        auditEvents: [audit("SUBMIT", "SALES_STAFF", "Đã reserve và chờ thanh toán.")],
        branch: bundleLocations[0],
        bundleName: "Set sen nhài riêng",
        code: "CB-2026-00001",
        createdAt: hours(-5),
        createdBy: bundleActor("SALES_STAFF"),
        customerName: "Nguyễn Hải An",
        displayName: "An's Sen Blend",
        estimatedPrice: 225000,
        id: "bundle-1",
        lines: [
            { availableQuantity: 850, batchAllocations: [{ batchCode: "B-SEN-0826", committedQuantity: 0, expiryDate: hours(24 * 90), id: "ba-1", locationName: "Q1", reservedQuantity: 120 }], category: "Trà nền", id: "line-1", ingredientId: "ing-sen", ingredientName: "Trà sen Tây Hồ", price: 144000, quantity: 120, skuCode: "TRA-SEN", unit: "g" },
            { availableQuantity: 120, batchAllocations: [{ batchCode: "B-JAS-0826", committedQuantity: 0, expiryDate: hours(24 * 45), id: "ba-2", locationName: "Q1", reservedQuantity: 54 }], category: "Hương liệu", id: "line-2", ingredientId: "ing-jasmine", ingredientName: "Hoa nhài sấy", price: 81000, quantity: 54, skuCode: "HL-JASMINE", unit: "g" }
        ],
        orderCode: "ORD-2026-2801",
        packagingType: "Hộp giấy kraft",
        packingNote: "Dán nhãn quà tặng.",
        paymentStatus: "PENDING",
        requestedPickupAt: hours(6),
        reservationExpiresAt: hours(1),
        reservationStatus: "RESERVED",
        status: "AWAITING_PAYMENT",
        targetWeight: 180,
        totalIngredients: 2,
        totalWeight: 174,
        usagePurpose: "GIFT"
    },
    {
        auditEvents: [audit("APPROVE", "MANAGER", "Đã thanh toán, chờ đóng gói.")],
        branch: bundleLocations[1],
        code: "CB-2026-00002",
        createdAt: hours(-3),
        createdBy: bundleActor("SALES_STAFF"),
        customerName: "Lê Minh",
        displayName: "Morning Oolong",
        estimatedPrice: 180000,
        id: "bundle-2",
        lines: [
            { availableQuantity: 420, batchAllocations: [{ batchCode: "B-OL-0826", committedQuantity: 0, expiryDate: hours(24 * 60), id: "ba-3", locationName: "Q7", reservedQuantity: 200 }], category: "Trà nền", id: "line-3", ingredientId: "ing-olong", ingredientName: "Trà Ô Long rang", price: 180000, quantity: 200, skuCode: "TRA-OLONG", unit: "g" }
        ],
        orderCode: "ORD-2026-2802",
        packagingType: "Túi zip bạc",
        paymentStatus: "SUCCESS",
        requestedPickupAt: hours(4),
        reservationExpiresAt: hours(2),
        reservationStatus: "RESERVED",
        status: "PAID",
        targetWeight: 200,
        totalIngredients: 1,
        totalWeight: 200,
        usagePurpose: "PERSONAL"
    }
];

export const customBundleOptions = {
    ingredients: bundleIngredients,
    locations: bundleLocations
};

export { audit as customBundleAudit };
