import { stocktakeApprovalThresholds } from "../constants/stocktake.constants";
import {
    stocktakeActor,
    stocktakeAudit,
    stocktakeMockData,
    stocktakeMockOptions
} from "../mocks/stocktakes.mock";
import type {
    StocktakeActionInput,
    StocktakeAdjustmentInput,
    StocktakeApi,
    StocktakeCountInput,
    StocktakeCreateInput,
    StocktakeDetail,
    StocktakeFinalizeInput,
    StocktakeLine,
    StocktakeListQuery,
    StocktakeRejectInput,
    StocktakeRole,
    StocktakeVarianceLevel
} from "../types/stocktake.types";

const stocktakeCache = [...stocktakeMockData];

function wait<T>(value: T, ms = 360) {
    return new Promise<T>((resolve) => {
        window.setTimeout(() => resolve(value), ms);
    });
}

function copy(stocktake: StocktakeDetail): StocktakeDetail {
    return {
        ...stocktake,
        adjustmentRequest: stocktake.adjustmentRequest ? { ...stocktake.adjustmentRequest } : undefined,
        auditEvents: [...stocktake.auditEvents],
        lines: stocktake.lines.map((line) => ({
            ...line,
            rounds: line.rounds.map((round) => ({ ...round }))
        })),
        scope: {
            batchCodes: [...stocktake.scope.batchCodes],
            categoryIds: [...stocktake.scope.categoryIds],
            skuIds: [...stocktake.scope.skuIds]
        }
    };
}

function findStocktake(stocktakeId: string) {
    const stocktake = stocktakeCache.find((item) => item.id === stocktakeId);
    if (!stocktake) throw new Error("Không tìm thấy kỳ kiểm kê.");
    return stocktake;
}

function assertRole(role: StocktakeRole, allowed: StocktakeRole[]) {
    if (!allowed.includes(role)) {
        throw new Error("Vai trò hiện tại không được thao tác nghiệp vụ này.");
    }
}

function summaryOf(stocktake: StocktakeDetail) {
    return {
        code: stocktake.code,
        countedSku: stocktake.countedSku,
        hasVariance: stocktake.hasVariance,
        id: stocktake.id,
        location: stocktake.location,
        owner: stocktake.owner,
        scheduledStartAt: stocktake.scheduledStartAt,
        scopeLabel: stocktake.scopeLabel,
        startedAt: stocktake.startedAt,
        status: stocktake.status,
        totalSku: stocktake.totalSku,
        type: stocktake.type,
        varianceLevel: stocktake.varianceLevel,
        varianceValue: stocktake.varianceValue
    };
}

function varianceLevel(value: number): StocktakeVarianceLevel {
    if (value >= stocktakeApprovalThresholds.large) return "LARGE";
    if (value >= stocktakeApprovalThresholds.medium) return "MEDIUM";
    return "SMALL";
}

function unitCost(line: StocktakeLine) {
    return stocktakeMockOptions.skus.find((item) => item.id === line.skuId)?.unitCost ?? 0;
}

function recalculate(stocktake: StocktakeDetail) {
    stocktake.countedSku = stocktake.lines.filter((line) =>
        line.rounds.some((round) => typeof round.countedQuantity === "number")
    ).length;
    stocktake.lines.forEach((line) => {
        if (typeof line.finalPhysicalQuantity === "number") {
            line.varianceQuantity = line.finalPhysicalQuantity - line.expectedCurrentQuantity;
            line.variancePercent = line.expectedCurrentQuantity === 0 ? 0 : (line.varianceQuantity / line.expectedCurrentQuantity) * 100;
            line.financialImpact = line.varianceQuantity * unitCost(line);
            line.issueFlag = Math.abs(line.varianceQuantity) > 0 || line.rounds.some((round) => round.condition !== "GOOD");
        }
    });
    stocktake.hasVariance = stocktake.lines.some((line) => Math.abs(line.varianceQuantity) > 0);
    stocktake.varianceValue = stocktake.lines.reduce((sum, line) => sum + Math.abs(line.financialImpact), 0);
    stocktake.varianceLevel = varianceLevel(stocktake.varianceValue);
}

function tabDate(value: string, from: string, to: string) {
    const time = new Date(value).getTime();
    if (from && time < new Date(`${from}T00:00:00`).getTime()) return false;
    if (to && time > new Date(`${to}T23:59:59`).getTime()) return false;
    return true;
}

function scopeLabel(input: StocktakeCreateInput) {
    if (input.type === "FULL") return "Toàn kho";
    if (input.type === "CATEGORY") return `${input.categoryIds.length} danh mục`;
    if (input.type === "SKU_SELECTED") return `${input.skuIds.length} SKU được chọn`;
    if (input.type === "BATCH_SELECTED") return `${input.batchCodes.length} batch được chọn`;
    return "Cycle count";
}

function skusForScope(input: StocktakeCreateInput) {
    if (input.type === "FULL" || input.type === "CYCLE_COUNT") return stocktakeMockOptions.skus;
    if (input.type === "CATEGORY") {
        const categoryNames = stocktakeMockOptions.categories.filter((item) => input.categoryIds.includes(item.id)).map((item) => item.name);
        return stocktakeMockOptions.skus.filter((item) => categoryNames.includes(item.category));
    }
    if (input.type === "SKU_SELECTED") return stocktakeMockOptions.skus.filter((item) => input.skuIds.includes(item.id));
    return stocktakeMockOptions.skus.filter((item) => input.batchCodes.includes(item.batchCode));
}

export const stocktakeApi: StocktakeApi = {
    approveStocktakeAdjustment(input: StocktakeActionInput) {
        const stocktake = findStocktake(input.stocktakeId);
        if (!stocktake.adjustmentRequest || stocktake.status !== "PENDING_APPROVAL") {
            return Promise.reject(new Error("Không có yêu cầu điều chỉnh đang chờ duyệt."));
        }
        const creatorRole = stocktake.adjustmentRequest.createdBy.role;
        if (creatorRole === input.actorRole && input.actorRole !== "ADMIN") {
            return Promise.reject(new Error("Người tạo không được tự phê duyệt trong UI simulation."));
        }
        if (stocktake.varianceLevel === "LARGE") {
            assertRole(input.actorRole, ["ADMIN", "DIRECTOR"]);
        } else {
            assertRole(input.actorRole, ["ADMIN", "DIRECTOR", "WAREHOUSE", "BRANCH_MANAGER"]);
        }
        stocktake.adjustmentRequest.status = "APPROVED";
        stocktake.status = "APPROVED";
        stocktake.auditEvents = [stocktakeAudit("APPROVE", input.actorRole, "Duyệt adjustment request kiểm kê."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    createAdjustmentFromStocktake(input: StocktakeAdjustmentInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status !== "RECONCILING") {
            return Promise.reject(new Error("Chỉ tạo adjustment khi đang đối chiếu."));
        }
        const selected = stocktake.lines.filter((line) => input.lineIds.includes(line.id) && Math.abs(line.varianceQuantity) > 0);
        if (selected.length === 0) return Promise.reject(new Error("Cần chọn ít nhất một dòng có chênh lệch."));
        stocktake.lines.forEach((line) => {
            line.adjustmentSelected = input.lineIds.includes(line.id);
        });
        stocktake.adjustmentRequest = {
            createdBy: stocktakeActor(input.actorRole),
            decreaseValue: selected.filter((line) => line.financialImpact < 0).reduce((sum, line) => sum + Math.abs(line.financialImpact), 0),
            evidence: input.evidence,
            id: `adj-${stocktake.id}`,
            increaseValue: selected.filter((line) => line.financialImpact > 0).reduce((sum, line) => sum + line.financialImpact, 0),
            note: input.note,
            reason: input.reason,
            status: "PENDING_APPROVAL"
        };
        stocktake.status = "PENDING_APPROVAL";
        stocktake.auditEvents = [stocktakeAudit("SUBMIT", input.actorRole, "Tạo adjustment request từ variance kiểm kê."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    createStocktake(input: StocktakeCreateInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const location = stocktakeMockOptions.locations.find((item) => item.id === input.locationId);
        const counterRound1 = stocktakeMockOptions.users.find((item) => item.id === input.counterRound1Id);
        const counterRound2 = stocktakeMockOptions.users.find((item) => item.id === input.counterRound2Id);
        if (!location || !counterRound1) return Promise.reject(new Error("Vui lòng chọn location và người đếm vòng 1."));
        if (input.actorRole === "BRANCH_MANAGER" && location.isCentral) {
            return Promise.reject(new Error("Quản lý chi nhánh không được tạo kiểm kê kho trung tâm."));
        }
        const scopedSkus = skusForScope(input);
        if (scopedSkus.length === 0) return Promise.reject(new Error("Phạm vi kiểm kê chưa có SKU phù hợp."));
        const stocktake: StocktakeDetail = {
            auditEvents: [stocktakeAudit("CREATE_DRAFT", input.actorRole, "Tạo kỳ kiểm kê.")],
            blindCount: input.blindCount,
            code: `STK-${new Date().toISOString().slice(0, 10).replaceAll("-", "")}-${String(stocktakeCache.length + 1).padStart(3, "0")}`,
            countedSku: 0,
            counterRound1,
            counterRound2,
            hasVariance: false,
            id: `stocktake-${Date.now()}`,
            lines: scopedSkus.map((sku) => ({
                adjustmentSelected: false,
                batchCode: sku.batchCode,
                bin: sku.bin,
                category: sku.category,
                expectedCurrentQuantity: sku.systemQuantity,
                expiryDate: sku.expiryDate,
                financialImpact: 0,
                id: `st-line-${Date.now()}-${sku.id}`,
                issueFlag: false,
                movementAfterSnapshot: 0,
                rounds: [{ condition: "GOOD", round: 1 }],
                skuCode: sku.skuCode,
                skuId: sku.id,
                skuName: sku.name,
                snapshotQuantity: sku.systemQuantity,
                unit: sku.unit,
                variancePercent: 0,
                varianceQuantity: 0
            })),
            location,
            note: input.note,
            owner: stocktakeActor(input.actorRole),
            scheduledStartAt: input.scheduledStartAt,
            scope: {
                batchCodes: input.batchCodes,
                categoryIds: input.categoryIds,
                skuIds: input.skuIds
            },
            scopeLabel: scopeLabel(input),
            status: "DRAFT",
            totalSku: scopedSkus.length,
            type: input.type,
            varianceLevel: "SMALL",
            varianceValue: 0
        };
        stocktakeCache.unshift(stocktake);
        return wait(copy(stocktake));
    },

    finalizeStocktakeCount(input: StocktakeFinalizeInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER", "DIRECTOR"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (!["COUNTED", "RECONCILING"].includes(stocktake.status)) {
            return Promise.reject(new Error("Chỉ finalize sau khi đã submit vòng đếm."));
        }
        input.lines.forEach((lineInput) => {
            const line = stocktake.lines.find((item) => item.id === lineInput.lineId);
            if (!line) throw new Error("Không tìm thấy dòng kiểm kê.");
            const round1 = line.rounds.find((round) => round.round === 1)?.countedQuantity;
            const round2 = line.rounds.find((round) => round.round === 2)?.countedQuantity;
            if (typeof round1 === "number" && typeof round2 === "number" && round1 !== round2 && !lineInput.reasonNote?.trim()) {
                throw new Error("Hai vòng đếm khác nhau phải nhập lý do chọn final count.");
            }
            line.finalPhysicalQuantity = lineInput.finalPhysicalQuantity;
            line.reasonCode = lineInput.reasonCode;
            line.reasonNote = lineInput.reasonNote;
        });
        recalculate(stocktake);
        stocktake.status = "RECONCILING";
        stocktake.auditEvents = [stocktakeAudit("APPROVE", input.actorRole, "Finalize count và phân loại reason code."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    getStocktake(stocktakeId: string) {
        return wait(copy(findStocktake(stocktakeId)));
    },

    getStocktakeOptions() {
        return wait(stocktakeMockOptions);
    },

    listStocktakes(query: StocktakeListQuery) {
        const filtered = stocktakeCache.filter((stocktake) => {
            const matchesCode = !query.code || stocktake.code.toLowerCase().includes(query.code.toLowerCase());
            const matchesLocation = query.locationId === "ALL" || stocktake.location.id === query.locationId;
            const matchesStatus = query.status === "ALL" || stocktake.status === query.status;
            const matchesType = query.type === "ALL" || stocktake.type === query.type;
            const matchesOwner = query.ownerId === "ALL" || stocktake.owner.id === query.ownerId;
            const matchesVariance = query.hasVariance === "ALL" || (query.hasVariance === "YES" ? stocktake.hasVariance : !stocktake.hasVariance);
            const matchesDate = tabDate(stocktake.startedAt ?? stocktake.scheduledStartAt, query.startedFrom, query.startedTo);
            return matchesCode && matchesLocation && matchesStatus && matchesType && matchesOwner && matchesVariance && matchesDate;
        });
        const start = query.page * query.size;
        const content = filtered.slice(start, start + query.size).map(summaryOf);
        return wait({
            content,
            first: query.page === 0,
            last: start + query.size >= filtered.length,
            page: query.page,
            size: query.size,
            summary: {
                inProgress: stocktakeCache.filter((item) => item.status === "IN_PROGRESS").length,
                largeVariance: stocktakeCache.filter((item) => item.varianceLevel === "LARGE" && item.hasVariance).length,
                pendingApproval: stocktakeCache.filter((item) => item.status === "PENDING_APPROVAL").length,
                postedThisMonth: stocktakeCache.filter((item) => item.status === "POSTED").length,
                waitingReconcile: stocktakeCache.filter((item) => item.status === "COUNTED" || item.status === "RECONCILING").length
            },
            totalElements: filtered.length,
            totalPages: Math.max(1, Math.ceil(filtered.length / query.size))
        });
    },

    postStocktakeAdjustment(input: StocktakeActionInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status !== "APPROVED" || stocktake.adjustmentRequest?.status !== "APPROVED") {
            return Promise.reject(new Error("Chỉ post adjustment đã được duyệt."));
        }
        const selected = stocktake.lines.filter((line) => line.adjustmentSelected && Math.abs(line.varianceQuantity) > 0);
        const hasIncrease = selected.some((line) => line.varianceQuantity > 0);
        const hasDecrease = selected.some((line) => line.varianceQuantity < 0);
        if (hasIncrease) {
            stocktake.adjustmentInDocumentId = `doc-stocktake-in-${stocktake.id}`;
            stocktake.adjustmentInDocumentCode = `DCK-IN-${stocktake.code}`;
        }
        if (hasDecrease) {
            stocktake.adjustmentOutDocumentId = `doc-stocktake-out-${stocktake.id}`;
            stocktake.adjustmentOutDocumentCode = `DCK-OUT-${stocktake.code}`;
        }
        stocktake.status = "POSTED";
        stocktake.adjustmentRequest.status = "POSTED";
        stocktake.auditEvents = [stocktakeAudit("POST", input.actorRole, "Post adjustment, tạo chứng từ ADJUSTMENT_IN/ADJUSTMENT_OUT và khóa kỳ kiểm kê."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    rejectStocktakeAdjustment(input: StocktakeRejectInput) {
        assertRole(input.actorRole, ["ADMIN", "DIRECTOR", "WAREHOUSE", "BRANCH_MANAGER"]);
        if (!input.reason.trim()) return Promise.reject(new Error("Reject bắt buộc nhập lý do."));
        const stocktake = findStocktake(input.stocktakeId);
        if (!stocktake.adjustmentRequest) return Promise.reject(new Error("Không có adjustment request."));
        stocktake.adjustmentRequest.status = "REJECTED";
        stocktake.adjustmentRequest.rejectedReason = input.reason;
        stocktake.status = "RECONCILING";
        stocktake.auditEvents = [stocktakeAudit("REJECT", input.actorRole, input.reason), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    saveStocktakeCount(input: StocktakeCountInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status === "POSTED") return Promise.reject(new Error("Kỳ kiểm kê đã post, không thể sửa."));
        if (input.countedQuantity < 0) return Promise.reject(new Error("Không được nhập số âm."));
        const line = stocktake.lines.find((item) => item.id === input.lineId);
        if (!line) return Promise.reject(new Error("Không tìm thấy dòng kiểm kê."));
        let round = line.rounds.find((item) => item.round === input.round);
        if (!round) {
            round = { condition: input.condition, round: input.round };
            line.rounds.push(round);
        }
        round.countedAt = new Date().toISOString();
        round.countedBy = stocktakeActor(input.actorRole);
        round.countedQuantity = input.countedQuantity;
        round.condition = input.condition;
        round.note = input.note;
        line.issueFlag = input.condition !== "GOOD";
        recalculate(stocktake);
        return wait(copy(stocktake));
    },

    startStocktake(input: StocktakeActionInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status !== "DRAFT") return Promise.reject(new Error("Chỉ start kỳ kiểm kê nháp."));
        stocktake.status = "IN_PROGRESS";
        stocktake.startedAt = new Date().toISOString();
        stocktake.snapshotFrozenAt = stocktake.startedAt;
        stocktake.lines = stocktake.lines.map((line, index) => ({
            ...line,
            movementAfterSnapshot: index % 2 === 0 ? 0 : -1,
            expectedCurrentQuantity: line.snapshotQuantity + (index % 2 === 0 ? 0 : -1)
        }));
        stocktake.auditEvents = [stocktakeAudit("APPROVE", input.actorRole, "Bắt đầu kiểm kê, đóng băng snapshot và ghi nhận movement sau snapshot riêng."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    startStocktakeReconciliation(input: StocktakeActionInput) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status !== "COUNTED") return Promise.reject(new Error("Chỉ đối chiếu sau khi đã đếm xong."));
        stocktake.status = "RECONCILING";
        stocktake.auditEvents = [stocktakeAudit("APPROVE", input.actorRole, "Bắt đầu đối chiếu snapshot, movement after snapshot và final physical count."), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    },

    submitStocktakeRound(input: StocktakeActionInput & { round: 1 | 2 }) {
        assertRole(input.actorRole, ["ADMIN", "WAREHOUSE", "BRANCH_MANAGER"]);
        const stocktake = findStocktake(input.stocktakeId);
        if (stocktake.status !== "IN_PROGRESS") return Promise.reject(new Error("Chỉ submit round khi đang kiểm kê."));
        const incomplete = stocktake.lines.some((line) => !line.rounds.find((round) => round.round === input.round && typeof round.countedQuantity === "number"));
        if (incomplete) return Promise.reject(new Error(`Vòng ${input.round} còn dòng chưa đếm.`));
        if (input.round === 1) {
            const needsRound2 = stocktake.lines.some((line) => {
                const counted = line.rounds.find((round) => round.round === 1)?.countedQuantity ?? 0;
                return Math.abs(counted - line.expectedCurrentQuantity) >= 3;
            });
            if (needsRound2 && stocktake.counterRound2) {
                stocktake.lines.forEach((line) => {
                    if (!line.rounds.some((round) => round.round === 2)) {
                        line.rounds.push({ condition: "GOOD", round: 2 });
                    }
                });
                stocktake.auditEvents = [stocktakeAudit("SUBMIT", input.actorRole, "Variance vượt ngưỡng, yêu cầu count round 2."), ...stocktake.auditEvents];
                return wait(copy(stocktake));
            }
        }
        stocktake.lines.forEach((line) => {
            const round2 = line.rounds.find((round) => round.round === 2 && typeof round.countedQuantity === "number");
            const round1 = line.rounds.find((round) => round.round === 1);
            line.finalPhysicalQuantity = round2?.countedQuantity ?? round1?.countedQuantity;
        });
        recalculate(stocktake);
        stocktake.status = "COUNTED";
        stocktake.auditEvents = [stocktakeAudit("SUBMIT", input.actorRole, `Submit count round ${input.round}.`), ...stocktake.auditEvents];
        return wait(copy(stocktake));
    }
};
