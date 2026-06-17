import { InventoryTransactionForm } from "../components/InventoryTransactionForm";
import { inventoryTransactionApi } from "../services/inventoryTransaction.api";

export function StockInPage() {
    return (
        <InventoryTransactionForm
            mode="stock-in"
            onSubmit={(input) =>
                inventoryTransactionApi.stockIn({
                    branchId: input.branchId,
                    referenceType: input.referenceType,
                    referenceId: input.referenceId,
                    lines: input.lines.map((line) => ({
                        inventoryItemId: line.item.id,
                        locationId: input.locationId,
                        quantity: Number(line.quantity)
                    }))
                })
            }
        />
    );
}
