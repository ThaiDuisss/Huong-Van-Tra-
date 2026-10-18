import { maxBundleWeight } from "../constants/custom-bundle.constants";
import {
    bundleActor,
    customBundleAudit,
    customBundleMockData,
    customBundleOptions
} from "../mocks/custom-bundles.mock";
import type {
    BundlePackingInput,
    CustomBundleApi,
    CustomBundleCreateInput,
    CustomBundleDetail,
    CustomBundleListQuery
} from "../types/custom-bundle.types";

const bundleCache = [...customBundleMockData];
const packedOperations = new Set<string>();

function wait<T>(value: T, ms = 360) {
    return new Promise<T>((resolve) => window.setTimeout(() => resolve(value), ms));
}

function clone(bundle: CustomBundleDetail): CustomBundleDetail {
    return { ...bundle, auditEvents: [...bundle.auditEvents], lines: bundle.lines.map((line) => ({ ...line, batchAllocations: line.batchAllocations.map((item) => ({ ...item })) })) };
}

function findBundle(bundleId: string) {
    const bundle = bundleCache.find((item) => item.id === bundleId);
    if (!bundle) throw new Error("Không tìm thấy Custom Tea Bundle.");
    return bundle;
}

function totalWeight(input: CustomBundleCreateInput) {
    return input.lines.reduce((sum, line) => sum + line.quantity, 0);
}

function validateInput(input: CustomBundleCreateInput) {
    if (input.lines.length === 0) throw new Error("Bundle phải có ít nhất một nguyên liệu.");
    if (totalWeight(input) > maxBundleWeight) throw new Error("Tổng khối lượng vượt giới hạn cấu hình.");
    input.lines.forEach((line) => {
        if (line.quantity <= 0) throw new Error("Quantity phải lớn hơn 0.");
        const ingredient = customBundleOptions.ingredients.find((item) => item.id === line.ingredientId);
        if (!ingredient) throw new Error("Không tìm thấy nguyên liệu.");
        if (ingredient.expired || ingredient.blocked) throw new Error(`${ingredient.skuCode} đã hết hạn hoặc bị blocked.`);
        if (line.unit !== ingredient.unit) throw new Error("UOM không tương thích.");
        if (line.quantity > ingredient.branchAvailableQuantity) throw new Error(`${ingredient.skuCode} không đủ available để reserve.`);
    });
}

export const customBundleApi: CustomBundleApi = {
    cancelCustomBundle(bundleId, actorRole) {
        try {
            const bundle = findBundle(bundleId);
            bundle.status = "CANCELLED";
            if (bundle.paymentStatus === "SUCCESS") {
                bundle.paymentStatus = "REFUND_REQUIRED";
                bundle.refundStatus = "Cần quy trình hoàn tiền";
            } else {
                bundle.reservationStatus = "RELEASED";
            }
            bundle.auditEvents = [customBundleAudit("CANCEL", actorRole ?? "ADMIN", "Hủy bundle và cập nhật reservation/refund."), ...bundle.auditEvents];
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    confirmCustomBundlePacking(input: BundlePackingInput) {
        try {
            const bundle = findBundle(input.bundleId);
            if (packedOperations.has(input.operationId) || bundle.status === "PACKED") return wait(clone(bundle));
            if (bundle.paymentStatus !== "SUCCESS") throw new Error("Payment chưa success, không thể confirm packing.");
            if (bundle.reservationStatus === "EXPIRED") throw new Error("Reservation đã hết hạn.");
            input.lines.forEach((lineInput) => {
                const line = bundle.lines.find((item) => item.id === lineInput.lineId);
                if (!line) throw new Error("Không tìm thấy dòng đóng gói.");
                const reserved = line.batchAllocations.reduce((sum, item) => sum + item.reservedQuantity, 0);
                if (lineInput.actualPackedQuantity > reserved) throw new Error("Actual quantity không được vượt reserved.");
                if (lineInput.actualPackedQuantity < line.quantity && !lineInput.reason?.trim()) throw new Error("Actual ít hơn required phải có reason.");
                if (!lineInput.picked) throw new Error("Phải xác nhận đã lấy hàng cho từng nguyên liệu.");
            });
            bundle.status = "PACKED";
            bundle.reservationStatus = "COMMITTED";
            bundle.packagingType = input.packagingType;
            bundle.numberOfPackages = input.numberOfPackages;
            bundle.actualTotalWeight = input.actualTotalWeight;
            bundle.packingNote = input.packingNote;
            bundle.stockDocumentId = `stock-doc-custom-bundle-${bundle.id}`;
            bundle.stockDocumentCode = `PXK-CB-${bundle.code}`;
            bundle.lines = bundle.lines.map((line) => ({
                ...line,
                batchAllocations: line.batchAllocations.map((allocation) => ({
                    ...allocation,
                    committedQuantity: allocation.reservedQuantity
                }))
            }));
            bundle.auditEvents = [customBundleAudit("POST", "ADMIN", "Confirm Packing, commit reservation và tạo PXK CUSTOM_BUNDLE_ISSUE."), ...bundle.auditEvents];
            packedOperations.add(input.operationId);
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    createCustomBundle(input) {
        try {
            validateInput(input);
            const branch = customBundleOptions.locations.find((item) => item.id === input.branchId);
            if (!branch) throw new Error("Vui lòng chọn chi nhánh/kho.");
            const lines = input.lines.map((line, index) => {
                const ingredient = customBundleOptions.ingredients.find((item) => item.id === line.ingredientId);
                if (!ingredient) throw new Error("Không tìm thấy nguyên liệu.");
                return {
                    availableQuantity: ingredient.branchAvailableQuantity,
                    batchAllocations: [],
                    category: ingredient.category,
                    id: `bundle-line-${Date.now()}-${index}`,
                    ingredientId: ingredient.id,
                    ingredientName: ingredient.name,
                    price: ingredient.pricePerGram * line.quantity,
                    quantity: line.quantity,
                    skuCode: ingredient.skuCode,
                    unit: ingredient.unit
                };
            });
            const bundle: CustomBundleDetail = {
                auditEvents: [customBundleAudit("CREATE_DRAFT", "SALES_STAFF", "Tạo Custom Tea Bundle từ POS.")],
                branch,
                bundleName: input.bundleName,
                code: `CB-2026-${String(bundleCache.length + 1).padStart(5, "0")}`,
                createdAt: new Date().toISOString(),
                createdBy: bundleActor("SALES_STAFF"),
                customerName: input.customerName,
                displayName: input.displayName,
                estimatedPrice: lines.reduce((sum, line) => sum + line.price, 0),
                id: `bundle-${Date.now()}`,
                lines,
                orderCode: `ORD-MOCK-${Date.now()}`,
                packagingType: input.packagingType,
                packingNote: input.packingNote,
                paymentStatus: "PENDING",
                requestedPickupAt: input.requestedPickupAt,
                reservationStatus: "NONE",
                status: "DRAFT",
                targetWeight: input.targetWeight,
                totalIngredients: lines.length,
                totalWeight: lines.reduce((sum, line) => sum + line.quantity, 0),
                usagePurpose: input.usagePurpose
            };
            bundleCache.unshift(bundle);
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    getCustomBundle(bundleId) {
        try {
            return wait(clone(findBundle(bundleId)));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    getCustomBundleOptions() {
        return wait(customBundleOptions);
    },

    listCustomBundles(query: CustomBundleListQuery) {
        const filtered = bundleCache
            .filter((bundle) => {
                if (query.tab === "AWAITING_PAYMENT") return ["AWAITING_PAYMENT", "RESERVED"].includes(bundle.status);
                if (query.tab === "FAILED") return ["FAILED", "RESERVATION_FAILED", "CANCELLED"].includes(bundle.status);
                return bundle.status === query.tab;
            })
            .filter((bundle) => bundle.code.toLowerCase().includes(query.code.trim().toLowerCase()))
            .filter((bundle) => (bundle.orderCode ?? "").toLowerCase().includes(query.orderCode.trim().toLowerCase()))
            .filter((bundle) => query.locationId === "ALL" || bundle.branch.id === query.locationId)
            .filter((bundle) => query.status === "ALL" || bundle.status === query.status)
            .filter((bundle) => bundle.customerName.toLowerCase().includes(query.customerName.trim().toLowerCase()));
        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size);
        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    mockPaymentResult(bundleId, success) {
        try {
            const bundle = findBundle(bundleId);
            bundle.paymentStatus = success ? "SUCCESS" : "FAILED";
            bundle.status = success ? "PAID" : "FAILED";
            if (!success) bundle.reservationStatus = "RELEASED";
            bundle.auditEvents = [customBundleAudit(success ? "APPROVE" : "REJECT", "SALES_STAFF", success ? "Payment success mock." : "Payment fail mock, release reservation."), ...bundle.auditEvents];
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    releaseCustomBundleReservation(bundleId) {
        try {
            const bundle = findBundle(bundleId);
            bundle.reservationStatus = "RELEASED";
            bundle.status = "CANCELLED";
            bundle.auditEvents = [customBundleAudit("CANCEL", "SALES_STAFF", "Release reservation mock."), ...bundle.auditEvents];
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    reserveCustomBundle(bundleId) {
        try {
            const bundle = findBundle(bundleId);
            const shortages = bundle.lines.filter((line) => line.quantity > line.availableQuantity);
            if (shortages.length > 0) {
                bundle.status = "RESERVATION_FAILED";
                bundle.reservationStatus = "FAILED";
                bundle.auditEvents = [customBundleAudit("REJECT", "SALES_STAFF", "Reservation failed do thiếu nguyên liệu."), ...bundle.auditEvents];
                return wait(clone(bundle));
            }
            bundle.status = "AWAITING_PAYMENT";
            bundle.reservationStatus = "RESERVED";
            bundle.reservationExpiresAt = new Date(Date.now() + 30 * 60 * 1000).toISOString();
            bundle.lines = bundle.lines.map((line) => ({
                ...line,
                batchAllocations: [{ batchCode: `FEFO-${line.skuCode}`, committedQuantity: 0, expiryDate: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000).toISOString(), id: `alloc-${line.id}`, locationName: bundle.branch.name, reservedQuantity: line.quantity }]
            }));
            bundle.auditEvents = [customBundleAudit("APPROVE", "SALES_STAFF", "Reserve nguyên liệu FEFO trước payment."), ...bundle.auditEvents];
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    startCustomBundlePacking(bundleId, actorRole) {
        try {
            const bundle = findBundle(bundleId);
            if (bundle.paymentStatus !== "SUCCESS") throw new Error("PAID mới cho phép bắt đầu packing.");
            bundle.status = "PACKING";
            bundle.auditEvents = [customBundleAudit("APPROVE", actorRole ?? "ADMIN", "Bắt đầu đóng gói bundle."), ...bundle.auditEvents];
            return wait(clone(bundle));
        } catch (error) {
            return Promise.reject(error);
        }
    },

    updateCustomBundle(bundleId, input) {
        return customBundleApi.createCustomBundle(input).then((created) => {
            const index = bundleCache.findIndex((item) => item.id === bundleId);
            if (index >= 0) {
                bundleCache[index] = { ...created, id: bundleId };
                return clone(bundleCache[index]);
            }
            return created;
        });
    }
};
