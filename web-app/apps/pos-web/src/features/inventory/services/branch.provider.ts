import type { InventoryLocationResponse } from "../types/inventory.types";
import type { InventoryLocationSummary } from "../types/stock-document.types";

export function temporaryBranchesFromLocations(
    locations: InventoryLocationResponse[]
): InventoryLocationSummary[] {
    const branches = new Map<string, InventoryLocationSummary>();

    locations.forEach((location) => {
        const branchId = String(location.branchId);
        if (branches.has(branchId)) return;
        const representative = locations.find(
            (candidate) => String(candidate.branchId) === branchId && candidate.type === "WAREHOUSE"
        );
        branches.set(branchId, {
            branchCode: representative?.code ?? "",
            code: representative?.code ?? "",
            id: branchId,
            isCentral: representative?.type === "WAREHOUSE",
            name: representative?.name
                ? `Chi nhánh ${representative.name}`
                : `Chi nhánh ${branchId.slice(0, 8)}`
        });
    });

    return [...branches.values()];
}
