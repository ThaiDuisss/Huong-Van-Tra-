import { InventoryTransactionForm } from "../components/InventoryTransactionForm";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";

export function StockAdjustmentPage() {
    return (
        <InventoryTransactionForm
            mode="adjustment"
            onSubmit={(input) =>
                inventoryTransactionApi.adjust({
                    branchId: input.branchId,
                    referenceType: input.referenceType,
                    referenceId: input.referenceId,
                    lines: input.lines.map((line) => ({
                        inventoryItemId: line.item.id,
                        locationId: input.locationId,
                        actualQuantity: Number(line.quantity)
                    }))
                })
            }
        />
    );
}
