import {
    CUSTOMER_QUICK_VIEWS,
    CUSTOMER_SORT_OPTIONS,
    CUSTOMER_STATUS_LABELS
} from "../constants/customer-list.constants";
import type {
    AppliedCustomerFilter,
    CustomerFilterDraft,
    CustomerPrimaryTab,
    CustomerQuickViewId
} from "../types/customer-list.types";
import type {
    CustomerQuery,
    CustomerSort,
    CustomerStatus,
    CustomerTierResponse
} from "../types/customer.types";

export function customerQuickViewParams(
    view: CustomerQuickViewId,
    tiers: CustomerTierResponse[]
) {
    const params = new URLSearchParams();
    if (view === "new") params.set("createdFrom", daysAgoIso(7));
    if (view === "recent") params.set("lastPurchaseFrom", daysAgoDate(30));
    if (view === "vip") {
        tiers
            .filter((tier) =>
                ["GOLD", "PLATINUM", "DIAMOND"].includes(tier.code)
            )
            .forEach((tier) => params.append("vipTierIds", tier.id));
    }
    if (view === "never-purchased") params.set("hasPurchased", "false");
    if (view === "birthday") {
        params.set("birthMonth", String(new Date().getMonth() + 1));
    }
    if (view === "inactive") {
        params.set("lastPurchaseTo", daysAgoDate(90));
        params.set("includeNeverPurchased", "true");
    }
    return params;
}

export function getActiveCustomerQuickView(
    params: URLSearchParams,
    tiers: CustomerTierResponse[]
): CustomerQuickViewId | null {
    const comparable = new URLSearchParams(params);
    comparable.delete("sort");
    return (
        CUSTOMER_QUICK_VIEWS.find(
            (view) =>
                comparable.toString() ===
                customerQuickViewParams(view.id, tiers).toString()
        )?.id ?? null
    );
}

export function appendCustomerFilterDraft(
    params: URLSearchParams,
    draft: CustomerFilterDraft
) {
    if (draft.status) params.set("status", draft.status);
    draft.tierIds.forEach((id) => params.append("tierIds", id));
    if (draft.createdFrom) {
        params.set("createdFrom", toDayBoundaryIso(draft.createdFrom, false));
    }
    if (draft.createdTo) {
        params.set("createdTo", toDayBoundaryIso(draft.createdTo, true));
    }
    if (draft.lastPurchaseFrom) {
        params.set("lastPurchaseFrom", draft.lastPurchaseFrom);
    }
    if (draft.lastPurchaseTo) {
        params.set("lastPurchaseTo", draft.lastPurchaseTo);
    }
    if (draft.includeNeverPurchased) {
        params.set("includeNeverPurchased", "true");
    }
    if (draft.hasPurchased) params.set("hasPurchased", draft.hasPurchased);
    if (draft.birthMonth) params.set("birthMonth", draft.birthMonth);
    if (draft.birthYear) params.set("birthYear", draft.birthYear);
}

export function customerFilterDraftFromParams(
    params: URLSearchParams
): CustomerFilterDraft {
    return {
        status: (params.get("status") as CustomerStatus | null) ?? "",
        tierIds: params.getAll("tierIds"),
        createdFrom: toLocalDateValue(params.get("createdFrom")),
        createdTo: toLocalDateValue(params.get("createdTo")),
        lastPurchaseFrom: params.get("lastPurchaseFrom") ?? "",
        lastPurchaseTo: params.get("lastPurchaseTo") ?? "",
        includeNeverPurchased:
            params.get("includeNeverPurchased") === "true",
        hasPurchased:
            (params.get("hasPurchased") as CustomerFilterDraft["hasPurchased"]) ??
            "",
        birthMonth: params.get("birthMonth") ?? "",
        birthYear: params.get("birthYear") ?? ""
    };
}

export function getAppliedCustomerFilters(
    params: URLSearchParams,
    tiers: CustomerTierResponse[]
): AppliedCustomerFilter[] {
    const labels: AppliedCustomerFilter[] = [];
    const status = params.get("status") as CustomerStatus | null;
    if (params.get("q")) {
        labels.push({ key: "q", label: `Tìm: ${params.get("q")}` });
    }
    if (status) {
        labels.push({
            key: "status",
            label: `Trạng thái: ${CUSTOMER_STATUS_LABELS[status]}`
        });
    }
    addTierLabel(labels, params, tiers, "tierIds", "Hạng");
    addDateLabel(labels, params, "createdFrom", "Tạo từ");
    addDateLabel(labels, params, "createdTo", "Tạo đến");
    addDateLabel(labels, params, "lastPurchaseFrom", "Mua từ");
    addDateLabel(labels, params, "lastPurchaseTo", "Mua đến");
    if (params.get("includeNeverPurchased") === "true") {
        labels.push({
            key: "includeNeverPurchased",
            label: "Gồm khách chưa mua"
        });
    }
    if (params.get("hasPurchased")) {
        labels.push({
            key: "hasPurchased",
            label:
                params.get("hasPurchased") === "true"
                    ? "Đã từng mua"
                    : "Chưa từng mua"
        });
    }
    if (params.get("birthMonth")) {
        labels.push({
            key: "birthMonth",
            label: `Sinh nhật tháng ${params.get("birthMonth")}`
        });
    }
    if (params.get("birthYear")) {
        labels.push({
            key: "birthYear",
            label: `Năm sinh: ${params.get("birthYear")}`
        });
    }
    addTierLabel(labels, params, tiers, "vipTierIds", "Hạng VIP");
    return labels;
}

export function getCustomerSort(params: URLSearchParams): CustomerSort {
    const sort = params.get("sort");
    return CUSTOMER_SORT_OPTIONS.some((option) => option.value === sort)
        ? (sort as CustomerSort)
        : "lastPurchaseDesc";
}

export function toCustomerQuery(
    params: URLSearchParams,
    page?: number,
    size?: number,
    tiers: CustomerTierResponse[] = []
): CustomerQuery {
    const query: CustomerQuery = { sort: getCustomerSort(params) };
    const q = params.get("q")?.trim();
    const status = params.get("status") as CustomerStatus | null;
    const tierIds = params.getAll("tierIds");
    const vipTierIds = params.getAll("vipTierIds");
    if (q) query.q = q;
    if (status) query.status = status;
    if (tierIds.length) query.tierIds = tierIds;
    if (vipTierIds.length) query.vipTierIds = vipTierIds;
    copyStringParam(query, params, "createdFrom");
    copyStringParam(query, params, "createdTo");
    copyStringParam(query, params, "lastPurchaseFrom");
    copyStringParam(query, params, "lastPurchaseTo");
    if (params.has("includeNeverPurchased")) {
        query.includeNeverPurchased =
            params.get("includeNeverPurchased") === "true";
    }
    if (params.has("hasPurchased")) {
        query.hasPurchased = params.get("hasPurchased") === "true";
    }
    if (params.get("birthMonth")) {
        query.birthMonth = Number(params.get("birthMonth"));
    }
    if (params.get("birthYear")) {
        query.birthYear = Number(params.get("birthYear"));
    }
    applyCustomerViewQuery(query, params, tiers);
    if (page !== undefined) query.page = page;
    if (size !== undefined) query.size = size;
    return query;
}

export function formatCustomerRelativeDate(value: string) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const date = new Date(`${value}T00:00:00`);
    const days = Math.round((today.getTime() - date.getTime()) / 86_400_000);
    if (days === 0) return "Hôm nay";
    if (days === 1) return "Hôm qua";
    return `${days} ngày trước`;
}

export function customerTierBadgeStyle(color: string | null) {
    const safeColor = color && /^#[0-9a-f]{6}$/i.test(color) ? color : "#6B7280";
    return { backgroundColor: `${safeColor}18`, color: safeColor };
}

export function customerErrorMessage(error: unknown) {
    return error instanceof Error
        ? error.message
        : "Không thể tải dữ liệu khách hàng. Vui lòng thử lại.";
}

function addTierLabel(
    labels: AppliedCustomerFilter[],
    params: URLSearchParams,
    tiers: CustomerTierResponse[],
    key: "tierIds" | "vipTierIds",
    prefix: string
) {
    const names = params
        .getAll(key)
        .map((id) => tiers.find((tier) => tier.id === id)?.name ?? id);
    if (names.length) {
        labels.push({ key, label: `${prefix}: ${names.join(", ")}` });
    }
}

function addDateLabel(
    labels: AppliedCustomerFilter[],
    params: URLSearchParams,
    key: string,
    prefix: string
) {
    const value = params.get(key);
    if (value) {
        labels.push({
            key,
            label: `${prefix}: ${new Intl.DateTimeFormat("vi-VN").format(
                new Date(value)
            )}`
        });
    }
}

function copyStringParam(
    query: CustomerQuery,
    params: URLSearchParams,
    key:
        | "createdFrom"
        | "createdTo"
        | "lastPurchaseFrom"
        | "lastPurchaseTo"
) {
    const value = params.get(key);
    if (value) query[key] = value;
}

function applyCustomerViewQuery(
    query: CustomerQuery,
    params: URLSearchParams,
    tiers: CustomerTierResponse[]
) {
    const view = (params.get("view") ?? "all") as CustomerPrimaryTab;
    const segment = params.get("segment") ?? "all";
    const today = new Date();

    if (view === "care") {
        query.includeNeverPurchased = true;
        query.lastPurchaseTo = daysAgoDate(segment === "long-time" ? 60 : 30);
        if (segment === "birthday") {
            delete query.lastPurchaseTo;
            query.birthMonth = today.getMonth() + 1;
        }
        if (segment === "vip-drop") {
            query.vipTierIds = tierIdsByCodes(tiers, ["GOLD", "PLATINUM", "DIAMOND"]);
        }
        if (segment === "one-order") query.hasPurchased = true;
    }

    if (view === "loyal") {
        const codesBySegment: Record<string, string[]> = {
            all: ["SILVER", "GOLD", "PLATINUM", "DIAMOND"],
            silver: ["SILVER"],
            gold: ["GOLD"],
            platinum: ["PLATINUM"],
            diamond: ["DIAMOND"]
        };
        query.tierIds = tierIdsByCodes(tiers, codesBySegment[segment] ?? codesBySegment.all);
    }

    if (view === "new") {
        query.createdFrom = daysAgoIso(segment === "today" ? 1 : 30);
        if (segment === "week") query.createdFrom = daysAgoIso(7);
        if (segment === "first-order") query.hasPurchased = true;
        if (segment === "no-order") query.hasPurchased = false;
    }

    if (view === "inactive") {
        query.lastPurchaseTo = daysAgoDate(segment === "30-days" ? 30 : segment === "60-days" ? 60 : 90);
        query.includeNeverPurchased = true;
        if (segment === "never-purchased") {
            delete query.lastPurchaseTo;
            query.hasPurchased = false;
        }
    }

    if (view === "all") {
        if (segment === "has-tier") query.tierIds = tiers.map((tier) => tier.id);
        if (segment === "purchased") query.hasPurchased = true;
        if (segment === "never-purchased") query.hasPurchased = false;
    }
}

function tierIdsByCodes(tiers: CustomerTierResponse[], codes: string[]) {
    return tiers
        .filter((tier) => codes.includes(tier.code))
        .map((tier) => tier.id);
}

function daysAgoDate(days: number) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString().slice(0, 10);
}

function daysAgoIso(days: number) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString();
}

function toLocalDateValue(value: string | null) {
    if (!value) return "";
    const date = new Date(value);
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}

function toDayBoundaryIso(value: string, endOfDay: boolean) {
    const date = new Date(`${value}T${endOfDay ? "23:59:59.999" : "00:00:00"}`);
    return date.toISOString();
}
