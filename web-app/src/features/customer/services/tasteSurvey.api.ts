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
    compatibleSources?: Array<{ sourceVersionCode: string; sourceManifestSha256: string }>;
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
    customerId: string | null;
    originInvoiceId: string;
    taxonomyVersion: string;
    taxonomySha256: string;
    criteria: TasteCriteria;
}

const surveyPath = "/customer/taste-surveys";
const taxonomyPath = "/catalog/taste-taxonomies";

function verifyTaxonomy(taxonomy: TasteTaxonomy, version: string, sha256?: string): TasteTaxonomy {
    if (taxonomy.versionCode !== version || taxonomy.manifest?.versionCode !== version ||
        ![1, 2].includes(taxonomy.manifest?.schemaVersion) ||
        !/^[0-9a-f]{64}$/.test(taxonomy.manifestSha256) ||
        (sha256 !== undefined && taxonomy.manifestSha256 !== sha256) ||
        !Array.isArray(taxonomy.manifest?.attributes) || !taxonomy.manifest.attributes.length) {
        throw new Error("Bộ tiêu chí khẩu vị không hợp lệ.");
    }
    return taxonomy;
}

export function orderedTasteAttributes(taxonomy?: TasteTaxonomy): TasteAttribute[] {
    return (taxonomy?.manifest.attributes ?? []).map((attribute) => ({
        ...attribute,
        values: [...attribute.values].sort((left, right) => left.displayOrder - right.displayOrder),
    })).sort((left, right) => left.displayOrder - right.displayOrder);
}

export function canPrefillTasteSurvey(taxonomy: TasteTaxonomy, version: string, sha256: string): boolean {
    if (taxonomy.versionCode === version && taxonomy.manifestSha256 === sha256) return true;
    return taxonomy.manifest.schemaVersion === 2 && Boolean(taxonomy.compatibleSources?.some((source) =>
        source.sourceVersionCode === version && source.sourceManifestSha256 === sha256));
}

export const tasteSurveyApi = {
    async currentTaxonomy() {
        const taxonomy = await apiClient.get<TasteTaxonomy>(`${taxonomyPath}/current`, { cache: "no-store" });
        if (taxonomy.state !== "PUBLISHED" || !taxonomy.current) {
            throw new Error("Bộ tiêu chí khẩu vị hiện hành không hợp lệ.");
        }
        return verifyTaxonomy(taxonomy, taxonomy.versionCode);
    },
    async taxonomyRelease(version: string, sha256: string, signal?: AbortSignal) {
        const taxonomy = await apiClient.get<TasteTaxonomy>(`${taxonomyPath}/${encodeURIComponent(version)}`, { signal });
        if (taxonomy.state !== "PUBLISHED" && taxonomy.state !== "RETIRED") {
            throw new Error("Bộ tiêu chí gốc chưa được phát hành.");
        }
        return verifyTaxonomy(taxonomy, version, sha256);
    },
    history(customerId: string, page: number) {
        return apiClient.get<PageResponse<TasteSurvey>>(
            `/customer/${encodeURIComponent(customerId)}/taste-surveys`,
            { query: { page, size: 5 } }
        );
    },
    async latest(customerId: string, signal?: AbortSignal) {
        const latest = await apiClient.get<TasteSurvey>(
            `/customer/${encodeURIComponent(customerId)}/taste-surveys/latest`, { signal }
        );
        if (latest.status !== "SUBMITTED" || latest.customerId !== customerId) {
            throw new Error("Khảo sát mới nhất không khớp với khách hàng.");
        }
        return latest;
    },
    detail(surveyId: string, customerId: string) {
        return apiClient.get<TasteSurvey>(`${surveyPath}/${encodeURIComponent(surveyId)}`, {
            query: { customerId }
        });
    },
    async submit(input: TasteSurveySubmission, keys: { create: string; submit: string }) {
        const draft = await apiClient.post<TasteSurvey>(surveyPath, input, {
            headers: { "Idempotency-Key": keys.create }
        });
        if (!draft.id || !draft.sessionId || (draft.customerId ?? null) !== (input.customerId ?? null) ||
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
        if (submitted.status !== "SUBMITTED" || submitted.id !== draft.id || submitted.originInvoiceId !== input.originInvoiceId ||
            (submitted.customerId ?? null) !== (input.customerId ?? null) || submitted.taxonomyVersion !== input.taxonomyVersion ||
            submitted.taxonomySha256 !== input.taxonomySha256 || !submitted.submittedAt) {
            throw new Error("Máy chủ chưa xác nhận khảo sát đã gửi.");
        }
        return submitted;
    }
};
