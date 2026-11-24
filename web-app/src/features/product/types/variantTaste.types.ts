export type VariantTasteProfileStatus = "DRAFT" | "APPROVED" | "STALE" | "RETIRED";

export interface PreparationProtocol {
  description?: string;
  targetUsage?: string;
  servingTemperature?: string;
  steepTimeSeconds?: number;
  waterTemperatureCelsius?: number;
  teaToWaterRatio?: string;
}

export interface AssessmentPayload {
  preparationCode: string;
  preparationProtocol: PreparationProtocol;
  sensoryValues: Record<string, string[]>;
  sourceMethod: string;
}

export interface CreateTasteProfileDraft {
  taxonomyVersion: string;
  assessment: AssessmentPayload;
}

export interface VariantTasteProfileResponse {
  id: string;
  variantId: string;
  taxonomyVersionId: string;
  taxonomyVersion: string;
  taxonomySha256: string;
  profileRevision: number;
  compositionRevision: number;
  compositionSha256: string;
  basisSnapshot: unknown;
  preparationCode: string;
  preparationProtocol: PreparationProtocol;
  preparationSha256: string;
  sensoryValues: Record<string, string[]>;
  sourceMethod: string;
  assessedBy?: string;
  assessedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
  profileSha256: string;
  status: VariantTasteProfileStatus;
  rankable: boolean;
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
}

export const PREPARATION_CODE_OPTIONS = [
  { code: "GIFT", value: "GIFT", label: "Quà biếu / Quà tặng", desc: "Hộp quà, set quà biếu cao cấp, bao bì sang trọng", description: "Hộp quà, set quà biếu cao cấp, bao bì sang trọng" },
  { code: "DAILY", value: "DAILY", label: "Thưởng thức hàng ngày", desc: "Uống trà thư giãn, thanh lọc cơ thể tại nhà", description: "Uống trà thư giãn, thanh lọc cơ thể tại nhà" },
  { code: "HOT_BREW", value: "HOT_BREW", label: "Pha nóng truyền thống", desc: "Ấm chén đất nung/gốm sứ, nước sôi 85-95°C", description: "Ấm chén đất nung/gốm sứ, nước sôi 85-95°C" },
  { code: "COLD_BREW", value: "COLD_BREW", label: "Ủ lạnh / Cold Brew", desc: "Ủ trong tủ lạnh 6-12 tiếng, vị thanh ngọt mát", description: "Ủ trong tủ lạnh 6-12 tiếng, vị thanh ngọt mát" },
  { code: "EVENT_TEA", value: "EVENT_TEA", label: "Tiệc trà / Tiếp khách", desc: "Phục vụ phòng khách, hội nghị, tiệc sang trọng", description: "Phục vụ phòng khách, hội nghị, tiệc sang trọng" },
] as const;
