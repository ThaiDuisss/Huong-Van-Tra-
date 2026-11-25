import { apiClient } from "@/api/api-client";
import type { PageResponse } from "../../customer/types/customer.types";
import type { TasteTaxonomy } from "../../customer/services/tasteSurvey.api";
import type {
  AssessmentPayload,
  CreateTasteProfileDraft,
  VariantTasteProfileResponse
} from "../types/variantTaste.types";

export interface BomItemDetail {
  id: string;
  ingredient: {
    id: string;
    ingredientCode: string;
    name: string;
    unitName?: string;
    type?: number;
  };
  quantity: number;
  wasteRate?: number;
  unit?: {
    id: string;
    name: string;
  };
}

export const variantTasteApi = {
  currentTaxonomy() {
    return apiClient.get<TasteTaxonomy>("/catalog/taste-taxonomies/current");
  },

  getVariantProfiles(variantId: string, page = 0, size = 20) {
    return apiClient.get<PageResponse<VariantTasteProfileResponse>>(
      `/catalog/product-variants/${encodeURIComponent(variantId)}/taste-profiles`,
      { query: { page, size } }
    );
  },

  getProfileDetail(profileId: string) {
    return apiClient.get<VariantTasteProfileResponse>(
      `/catalog/taste-profiles/${encodeURIComponent(profileId)}`
    );
  },

  createProfile(variantId: string, payload: CreateTasteProfileDraft) {
    return apiClient.post<VariantTasteProfileResponse>(
      `/catalog/product-variants/${encodeURIComponent(variantId)}/taste-profiles`,
      payload
    );
  },

  updateProfile(profileId: string, version: number, payload: AssessmentPayload) {
    return apiClient.patch<VariantTasteProfileResponse>(
      `/catalog/taste-profiles/${encodeURIComponent(profileId)}`,
      payload,
      {
        headers: {
          "If-Match": `"${version}"`
        }
      }
    );
  },

  approveProfile(profileId: string, version: number) {
    return apiClient.post<VariantTasteProfileResponse>(
      `/catalog/taste-profiles/${encodeURIComponent(profileId)}/approve`,
      undefined,
      {
        headers: {
          "If-Match": `"${version}"`
        }
      }
    );
  },

  retireProfile(profileId: string, version: number) {
    return apiClient.post<VariantTasteProfileResponse>(
      `/catalog/taste-profiles/${encodeURIComponent(profileId)}/retire`,
      undefined,
      {
        headers: {
          "If-Match": `"${version}"`
        }
      }
    );
  },

  getVariantBom(variantId: string) {
    return apiClient.get<BomItemDetail[]>(
      `/catalog/product-variants/${encodeURIComponent(variantId)}/bom-items`
    );
  }
};
