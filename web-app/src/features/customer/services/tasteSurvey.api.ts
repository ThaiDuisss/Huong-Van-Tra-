import { apiClient } from "@/api/api-client";
import type { PageResponse } from "../types/customer.types";

export interface TasteAttribute {
    attributeCode: string;
    label: string;
    displayOrder: number;
    selectionKind: "ORDINAL_SINGLE" | "ENUM_SINGLE" | "ENUM_MULTI";
    maxSelections: number;
    allowsNotApplicable?: boolean;
    values: Array<{ valueCode: string; label: string; displayOrder: number }>;
}

export interface TasteTaxonomy {
    versionCode: string;
    manifestSha256: string;
    state: "DRAFT" | "PUBLISHED" | "RETIRED";
    current: boolean;
    manifest: { schemaVersion: number; versionCode: string; attributes: TasteAttribute[] };
}

export interface TasteCriteria {
    hardGroups: Array<{ attribute: string; allowedValues: string[] }>;
    softGroups: Array<{ attribute: string; priorities: Array<{ value: string; weight: number }> }>;
    notApplicableAttributes?: string[];
}

export interface TasteSurvey {
    id: string;
    customerId: string | null;
    sessionId: string | null;
    originInvoiceId: string | null;
    status: "DRAFT" | "SUBMITTED";
    rowVersion: number;
    taxonomyVersion: string;
    taxonomySha256: string;
    criteria: TasteCriteria;
    submittedAt: string | null;
}

interface TasteSurveySubmission {
    customerId: string;
    originInvoiceId: string;
    taxonomyVersion: string;
    taxonomySha256: string;
    criteria: TasteCriteria;
}

const surveyPath = "/customer/taste-surveys";

export const tasteSurveyApi = {
    async currentTaxonomy() {
        const taxonomy = await apiClient.get<TasteTaxonomy>("/catalog/taste-taxonomies/current");
        if (taxonomy.state !== "PUBLISHED" || !taxonomy.current ||
            taxonomy.manifest?.schemaVersion !== 1 ||
            taxonomy.manifest.versionCode !== taxonomy.versionCode ||
            !/^[0-9a-f]{64}$/.test(taxonomy.manifestSha256) ||
            !Array.isArray(taxonomy.manifest.attributes) || !taxonomy.manifest.attributes.length) {
            throw new Error("Bộ tiêu chí khẩu vị hiện hành không hợp lệ.");
        }
        return taxonomy;
    },
    history(customerId: string, page: number) {
        return apiClient.get<PageResponse<TasteSurvey>>(
            `/customer/${encodeURIComponent(customerId)}/taste-surveys`,
            { query: { page, size: 5 } }
        );
    },
    async submit(input: TasteSurveySubmission, keys: { create: string; submit: string }) {
        const draft = await apiClient.post<TasteSurvey>(surveyPath, input, {
            headers: { "Idempotency-Key": keys.create }
        });
        if (!draft.id || !draft.sessionId || draft.customerId !== input.customerId ||
            draft.originInvoiceId !== input.originInvoiceId || draft.taxonomyVersion !== input.taxonomyVersion ||
            draft.taxonomySha256 !== input.taxonomySha256) {
            throw new Error("Bản khảo sát không khớp khách hàng, hóa đơn hoặc bộ tiêu chí.");
        }
        const submitted = await apiClient.post<TasteSurvey>(
            `${surveyPath}/${encodeURIComponent(draft.id)}/submit`,
            undefined,
            {
                headers: {
                    "Idempotency-Key": keys.submit,
                    "If-Match": `"${draft.rowVersion}"`,
                    "X-Survey-Session-Id": draft.sessionId
                }
            }
        );
        if (submitted.status !== "SUBMITTED" || submitted.id !== draft.id ||
            submitted.customerId !== input.customerId || submitted.taxonomyVersion !== input.taxonomyVersion ||
            submitted.taxonomySha256 !== input.taxonomySha256 || !submitted.submittedAt) {
            throw new Error("Máy chủ chưa xác nhận khảo sát đã gửi.");
        }
        return submitted;
    }
};
