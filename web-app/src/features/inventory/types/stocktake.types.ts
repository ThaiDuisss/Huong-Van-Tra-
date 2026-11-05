import type {
  InventoryAuditEvent,
  InventoryLocationSummary,
  UserSnapshot,
} from "./stock-document.types";

export type StocktakeScopeType = "FULL" | "LOCATION" | "PARTIAL";
export type StocktakeStatus = "DRAFT" | "COUNTING" | "PENDING_APPROVAL" | "COMPLETED" | "CANCELLED";
export type StocktakeStatusFilter = "ALL" | StocktakeStatus;
export type StocktakeCountType = "FULL_COUNT" | "CYCLE_COUNT" | "SPOT_COUNT";
export type AdjustmentStatus = "DRAFT" | "APPROVED" | "POSTED" | "REJECTED";
export type AdjustmentReferenceType = "STOCK_COUNT" | "MANUAL";
export type AdjustmentDocumentType = "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";

export interface InventoryBatchSummary {
  id: string;
  batchCode: string;
  expiryDate: string | null;
}

export interface StocktakeLine {
  id: string;
  inventoryItemId: string;
  inventoryItemCode: string;
  inventoryItemName: string;
  locationId: string;
  locationName: string;
  inventoryBatchId: string | null;
  batchCode: string | null;
  expiryDate: string | null;
  snapshotQuantity: number;
  movementQuantity: number;
  expectedQuantity: number;
  snapshotUnitCost: number;
  counted: boolean;
  countedQuantity: number | null;
  varianceQuantity: number | null;
  varianceValue: number;
  goodQuantity: number;
  damagedQuantity: number;
  expiredQuantity: number;
  quarantinedQuantity: number;
  reasonCode: string | null;
  note: string | null;
  unit: string;
}

export interface StocktakeSummary {
  id: string;
  countCode: string;
  branch: InventoryLocationSummary;
  scopeType: StocktakeScopeType;
  countType: StocktakeCountType;
  blindCount: boolean;
  scopeLabel: string;
  status: StocktakeStatus;
  startedAt: string | null;
  submittedAt: string | null;
  completedAt: string | null;
  returnReason: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  totalLines: number;
  countedLines: number;
  varianceLines: number;
  createdBy: UserSnapshot;
  approvedBy: UserSnapshot | null;
  approvedAt: string | null;
  note: string | null;
  adjustmentDocument: AdjustmentDocumentSummary | null;
}

export interface StocktakeDetail extends StocktakeSummary {
  approvedBy: UserSnapshot | null;
  approvedAt: string | null;
  adjustmentDocument: AdjustmentDocumentSummary | null;
  lines: StocktakeLine[];
  auditEvents: InventoryAuditEvent[];
  locationIds: string[];
  inventoryItemIds: string[];
  assignments: StocktakeAssignment[];
}

export interface StocktakeAssignment {
  id: string;
  userId: string;
  role: "COUNTER" | "SUPERVISOR";
  status: "ASSIGNED" | "DONE";
  locationId: string | null;
}

export interface AdjustmentDocumentSummary {
  id: string;
  documentCode: string;
  documentType: AdjustmentDocumentType;
  status: AdjustmentStatus;
  referenceType: AdjustmentReferenceType;
  referenceId: string | null;
  approvedBy: UserSnapshot | null;
  approvedAt: string | null;
  postedAt: string | null;
}

export interface AdjustmentListItem {
  id: string;
  documentCode: string;
  documentType: AdjustmentDocumentType;
  status: AdjustmentStatus;
  branchId: string;
  referenceType: AdjustmentReferenceType;
  referenceId: string | null;
  reasonCode: string | null;
  note: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  createdAt: string;
  createdBy: string | null;
  lines: Array<{
    id: string;
    adjustmentQuantity: number;
    inventoryItemId: string;
    locationId: string | null;
  }>;
}

export interface AdjustmentListQuery {
  page: number;
  size: number;
  branchId?: string;
  status?: AdjustmentStatus;
  keyword?: string;
}

export interface StocktakeListQuery {
  page: number;
  size: number;
  branchId: "ALL" | string;
  status: StocktakeStatusFilter;
  scopeType: "ALL" | StocktakeScopeType;
  keyword: string;
  sort: StocktakeSort;
}

export type StocktakeSort = "createdAt,desc" | "createdAt,asc" | "countCode,asc" | "countCode,desc";

export interface StocktakeListResult {
  content: StocktakeSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  summary: {
    draft: number;
    counting: number;
    pendingApproval: number;
    completed: number;
  };
}

export interface StocktakeOptions {
  branches: InventoryLocationSummary[];
  locations: Array<{ id: string; name: string; branchId: string }>;
  items: Array<{
    id: string;
    itemCode: string;
    itemName: string;
    trackingBatch: boolean;
    unit: string;
    hasBatches: boolean;
  }>;
  batches: InventoryBatchSummary[];
  users: UserSnapshot[];
}

export interface StocktakeCreateInput {
  branchId: string;
  countType?: StocktakeCountType;
  blindCount?: boolean;
  scopeType: StocktakeScopeType;
  locationIds?: string[];
  inventoryItemIds?: string[];
  note?: string;
}

export interface StocktakeActionInput {
  stocktakeId: string;
}

export interface StocktakeReturnInput extends StocktakeActionInput {
  lineIds: string[];
  /** Bỏ trống để giữ nguyên người đếm đã phân công. */
  assignedUserId?: string;
  reason: string;
}

export interface StocktakeCountsInput extends StocktakeActionInput {
  lines: Array<{
    lineId: string;
    countedQuantity: number;
    reasonCode?: string;
    note?: string;
  }>;
}
