import { useCallback, useState } from "react";

import { useInventoryPage } from "../hooks/useInventoryResource";
import { inventoryItemApi } from "../services/inventoryItem.api";
import type {
    InventoryItemResponse,
    InventoryItemType
} from "../types/inventory.types";
import { inventoryItemTypeLabels } from "../utils/inventory-labels";

export function InventoryItemSelect({
    value,
    onChange
}: {
    value: number | null;
    onChange(item: InventoryItemResponse | null): void;
}) {
    const [referenceId, setReferenceId] = useState("");
    const [itemType, setItemType] = useState<"ALL" | InventoryItemType>(
        "ALL"
    );
    const load = useCallback(
        () =>
            inventoryItemApi.list({
                page: 0,
                size: 100,
                referenceId: referenceId
                    ? Number(referenceId)
                    : undefined,
                itemType: itemType === "ALL" ? undefined : itemType,
                status: "ACTIVE"
            }),
        [itemType, referenceId]
    );
    const resource = useInventoryPage(load);
    const items = resource.data?.content ?? [];

    return (
        <div className="grid gap-2">
            <div className="grid gap-2 sm:grid-cols-2">
                <input
                    type="number"
                    value={referenceId}
                    onChange={(event) =>
                        setReferenceId(event.currentTarget.value)
                    }
                    placeholder="Tìm theo mã tham chiếu"
                    className="h-11 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700"
                />
                <select
                    value={itemType}
                    onChange={(event) =>
                        setItemType(
                            event.currentTarget.value as
                                | "ALL"
                                | InventoryItemType
                        )
                    }
                    className="h-11 rounded-xl border border-pos-border bg-white px-3 text-sm"
                >
                    <option value="ALL">Tất cả loại hàng hóa</option>
                    {Object.entries(inventoryItemTypeLabels).map(
                        ([type, label]) => (
                            <option key={type} value={type}>
                                {label}
                            </option>
                        )
                    )}
                </select>
            </div>
            <select
                value={value ?? ""}
                onChange={(event) => {
                    const selected = items.find(
                        (item) => item.id === Number(event.currentTarget.value)
                    );
                    onChange(selected ?? null);
                }}
                className="h-11 rounded-xl border border-pos-border bg-white px-3 text-sm"
            >
                <option value="">
                    {resource.loading
                        ? "Đang tải dữ liệu..."
                        : "Chọn hàng hóa"}
                </option>
                {items.map((item) => (
                    <option key={item.id} value={item.id}>
                        #{item.id} - {inventoryItemTypeLabels[item.itemType]} -
                        tham chiếu #{item.referenceId}
                    </option>
                ))}
            </select>
        </div>
    );
}
