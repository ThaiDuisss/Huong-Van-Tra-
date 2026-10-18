import { Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { storeShelfApi } from "../services/storeShelf.api";
import { inventoryItemApi } from "../services/inventoryItem.api";
import { productVariantApi } from "../../product/services/productVariant.api";
import type { ProductVariant } from "../../product/types/product.types";
import type {
    EntityId,
    InventoryItemResponse,
    InventoryLocationResponse,
    InventoryReferenceType,
    InventoryTransactionResponse,
    InventoryItemType
} from "../types/inventory.types";
import { inventoryItemTypeLabels } from "../utils/inventory-labels";
import { routePaths } from "../../../routes/path";

interface TransactionLine {
    item: InventoryItemResponse;
    sku: string;
    productName: string;
    beforeQuantity: number;
    changeQuantity: string;
    afterQuantity: number;
}

interface StoreInventoryFormProps {
    mode: "stock-in" | "adjustment";
    onSuccess(): void;
    onSubmit(input: {
        shelfId: EntityId;
        referenceType: InventoryReferenceType;
        referenceId: EntityId | null;
        lines: {
            item: InventoryItemResponse;
            quantity: string;
        }[];
    }): Promise<InventoryTransactionResponse>;
}

export function StoreInventoryForm({
    mode,
    onSuccess,
    onSubmit
}: StoreInventoryFormProps) {
    const adjustment = mode === "adjustment";
    const [shelfId, setShelfId] = useState("");
    const [referenceId, setReferenceId] = useState("");
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [result, setResult] = useState<InventoryTransactionResponse | null>(null);

    // Shelves and Variants Metadata
    const [shelves, setShelves] = useState<InventoryLocationResponse[]>([]);
    const [inventoryItems, setInventoryItems] = useState<InventoryItemResponse[]>([]);
    const [variantsMap, setVariantsMap] = useState<Record<string, ProductVariant>>({});
    
    // Select Product search states
    const [selectedItem, setSelectedItem] = useState<InventoryItemResponse | null>(null);
    const [searchQuery, setSearchQuery] = useState("");
    const [itemType, setItemType] = useState<"ALL" | InventoryItemType>("ALL");
    const [lines, setLines] = useState<TransactionLine[]>([]);

    // 1. Load active shelves of current store and products metadata
    useEffect(() => {
        async function loadMetadata() {
            try {
                // Load SHELF locations via store shelf API (already filters to type=SHELF of first warehouse)
                const shelfRes = await storeShelfApi.list({ page: 0, size: 200, status: "ACTIVE" });
                const activeShelves = shelfRes.content ?? [];
                setShelves(activeShelves);

                // Auto-select first active shelf
                if (activeShelves.length > 0) {
                    setShelfId(String(activeShelves[0].id));
                }

                // Fetch inventory items
                const itemsRes = await inventoryItemApi.list({ page: 0, size: 1000, status: "ACTIVE" });
                setInventoryItems(itemsRes.content ?? []);

                // Fetch variants to construct map
                const varRes = await productVariantApi.list({ page: 0, size: 1000 });
                const varMap: Record<string, ProductVariant> = {};
                (varRes.content ?? []).forEach(v => {
                    varMap[String(v.id)] = v;
                });
                setVariantsMap(varMap);
            } catch (err) {
                console.error("Error loading StoreInventoryForm metadata:", err);
            }
        }
        loadMetadata();
    }, [adjustment]);



    // Client-side search items
    const filteredSearchItems = useMemo(() => {
        return inventoryItems.filter(item => {
            if (itemType !== "ALL" && item.itemType !== itemType) {
                return false;
            }
            const variant = variantsMap[String(item.referenceId)];
            const sku = (variant?.sku || "").toLowerCase();
            const prodName = (variant?.productName || "").toLowerCase();
            const varName = (variant?.variantName || "").toLowerCase();
            const query = searchQuery.toLowerCase();
            return (
                sku.includes(query) ||
                prodName.includes(query) ||
                varName.includes(query) ||
                String(item.id).includes(query)
            );
        });
    }, [inventoryItems, searchQuery, itemType, variantsMap]);

    // Add selected item to grid
    const handleAddItem = async () => {
        if (!selectedItem || !shelfId) {
            setError("Vui lòng chọn kệ và hàng hóa.");
            return;
        }

        if (lines.some(l => l.item.id === selectedItem.id)) {
            setError("Hàng hóa này đã có trong danh sách.");
            return;
        }

        try {
            // Fetch current quantity at the selected shelf
            const balances = await inventoryBalanceApi.list({
                page: 0,
                size: 1,
                inventoryItemId: selectedItem.id,
                locationId: shelfId,
            });
            const beforeQuantity = balances.content[0]?.quantityOnHand ?? 0;

            const variant = variantsMap[String(selectedItem.referenceId)];
            const sku = variant?.sku || `#${selectedItem.id}`;
            const name = variant
                ? `${variant.productName} (${variant.variantName})`
                : `Mã tham chiếu #${selectedItem.referenceId}`;

            const newLine: TransactionLine = {
                item: selectedItem,
                sku,
                productName: name,
                beforeQuantity,
                changeQuantity: adjustment ? String(beforeQuantity) : "1",
                afterQuantity: adjustment ? beforeQuantity : beforeQuantity + 1
            };

            setLines(curr => [...curr, newLine]);
            setSelectedItem(null);
            setSearchQuery("");
            setError("");
        } catch (err) {
            setError("Không thể lấy thông tin tồn kho hiện tại.");
        }
    };

    // Update quantity input for an item
    const handleLineQuantityChange = (itemId: EntityId, val: string) => {
        setLines(curr => curr.map(line => {
            if (line.item.id !== itemId) return line;

            const numVal = Number(val);
            if (isNaN(numVal)) {
                return { ...line, changeQuantity: val };
            }

            let afterQty = line.beforeQuantity;
            if (adjustment) {
                afterQty = numVal;
            } else {
                afterQty = line.beforeQuantity + numVal;
            }

            return {
                ...line,
                changeQuantity: val,
                afterQuantity: afterQty
            };
        }));
    };

    // Submit transaction
    const handleSubmit = async () => {
        if (submitting) return;
        if (!shelfId) {
            setError("Vui lòng chọn kệ.");
            return;
        }
        if (lines.length === 0) {
            setError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
            return;
        }

        if (adjustment && !referenceId) {
            setError("Vui lòng chọn lý do điều chỉnh.");
            return;
        }

        // Validate values
        const hasInvalidQty = lines.some(line => {
            const num = Number(line.changeQuantity);
            return isNaN(num) || (adjustment ? num < 0 : num <= 0);
        });

        if (hasInvalidQty) {
            setError(
                adjustment
                    ? "Số lượng kiểm kê thực tế phải lớn hơn hoặc bằng 0."
                    : "Số lượng nhập phải lớn hơn 0."
            );
            return;
        }

        const selectedShelf = shelves.find(s => String(s.id) === shelfId);
        const confirmMsg = adjustment
            ? `Bạn có chắc chắn muốn điều chỉnh tồn kho tại kệ "${selectedShelf?.name}"?`
            : `Bạn có chắc chắn muốn nhập hàng vào kệ "${selectedShelf?.name}"?`;
        if (!window.confirm(confirmMsg)) return;

        setSubmitting(true);
        setError("");
        try {
            const res = await onSubmit({
                shelfId,
                referenceType: adjustment ? "STOCK_COUNT" : "STOCK_TRANSFER",
                referenceId: referenceId || null,
                lines: lines.map(line => ({
                    item: line.item,
                    quantity: line.changeQuantity
                }))
            });

            setResult(res);
            setLines([]);
            onSuccess(); // Refresh current list view
        } catch (err: any) {
            setError(err.message || "Giao dịch không thành công.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <section className="grid gap-5">
            <section className="grid gap-5 rounded-2xl bg-white p-5 shadow-pos-card">
                <div className="col-span-full rounded-xl bg-neutral-50 p-4 border border-pos-border flex flex-col gap-2 text-sm">
                    {/* Shelf selector */}
                    <div className="flex flex-col gap-1.5 max-w-xs">
                        <label className="text-sm font-bold text-gray-700">
                            Kệ hàng <span className="text-red-500">*</span>
                        </label>
                        <select
                            value={shelfId}
                            onChange={(e) => {
                                setShelfId(e.currentTarget.value);
                                setLines([]); // Reset lines when shelf changes
                                setError("");
                            }}
                            className="h-10 rounded-xl border border-pos-border bg-white px-3 font-normal outline-none focus:border-hvt-green-700"
                        >
                            <option value="">-- Chọn kệ --</option>
                            {shelves.map(s => (
                                <option key={s.id} value={String(s.id)}>{s.name}</option>
                            ))}
                        </select>
                    </div>

                    {adjustment ? (
                        <div className="flex flex-col gap-1.5 mt-1 max-w-xs">
                            <label className="text-sm font-bold text-gray-700">
                                Lý do điều chỉnh
                            </label>
                            <select
                                value={referenceId}
                                onChange={(e) => setReferenceId(e.currentTarget.value)}
                                className="h-10 rounded-xl border border-pos-border bg-white px-3 font-normal outline-none focus:border-hvt-green-700"
                            >
                                <option value="">-- Chọn lý do --</option>
                                <option value="Hỏng">Hỏng</option>
                                <option value="Mất">Mất</option>
                                <option value="Kiểm kê lệch">Kiểm kê lệch</option>
                                <option value="Nhập nhầm">Nhập nhầm</option>
                            </select>
                        </div>
                    ) : (
                        <div>
                            <span className="font-bold text-gray-500">Loại tham chiếu: </span>
                            <span className="font-semibold text-gray-800">Chuyển tồn kho</span>
                        </div>
                    )}
                </div>

                {/* Choose items */}
                <div className="grid gap-3 rounded-2xl border border-pos-border p-4">
                    <h2 className="font-bold">Chọn hàng hóa</h2>
                    
                    <div className="grid gap-2">
                        <div className="grid gap-2 sm:grid-cols-3">
                            <input
                                type="text"
                                placeholder="Gõ tìm nhanh SKU hoặc tên sản phẩm..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="h-11 rounded-xl border border-pos-border px-3 text-sm outline-none focus:border-hvt-green-700"
                            />
                            <select
                                value={itemType}
                                onChange={(e) => setItemType(e.currentTarget.value as "ALL" | InventoryItemType)}
                                className="h-11 rounded-xl border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700"
                            >
                                <option value="ALL">Tất cả loại hàng hóa</option>
                                {Object.entries(inventoryItemTypeLabels).map(([type, label]) => (
                                    <option key={type} value={type}>
                                        {label}
                                    </option>
                                ))}
                            </select>
                            <select
                                value={selectedItem?.id ?? ""}
                                onChange={(e) => {
                                    const selected = inventoryItems.find(item => String(item.id) === e.currentTarget.value);
                                    setSelectedItem(selected ?? null);
                                }}
                                className="h-11 rounded-xl border border-pos-border bg-white px-3 text-sm outline-none focus:border-hvt-green-700"
                            >
                                <option value="">Chọn hàng hóa</option>
                                {filteredSearchItems.map(item => {
                                    const variant = variantsMap[String(item.referenceId)];
                                    const sku = variant?.sku || "";
                                    const name = variant
                                        ? `${variant.productName} (${variant.variantName})`
                                        : `Mã tham chiếu #${item.referenceId}`;
                                    return (
                                        <option key={item.id} value={item.id}>
                                            {sku ? `${sku} - ${name}` : `#${item.id}`}
                                        </option>
                                    );
                                })}
                            </select>
                        </div>
                    </div>

                    <button
                        type="button"
                        onClick={handleAddItem}
                        className="inline-flex h-10 w-fit items-center gap-2 rounded-xl border border-pos-border px-4 text-sm font-bold"
                    >
                        <Plus size={16} />
                        Thêm dòng hàng
                    </button>
                </div>

                {/* Table of Lines */}
                <div className="overflow-x-auto rounded-2xl border border-pos-border">
                    <table className="min-w-[780px] w-full text-left text-sm">
                        <thead className="bg-neutral-50 text-xs text-gray-600">
                            <tr>
                                <th className="px-4 py-3">SKU</th>
                                <th className="px-4 py-3">Tên sản phẩm</th>
                                <th className="px-4 py-3">Loại hàng hóa</th>
                                <th className="px-4 py-3">Tồn trước</th>
                                <th className="px-4 py-3">
                                    {adjustment ? "Số lượng kiểm thực tế" : "Số lượng nhập"}
                                </th>
                                <th className="px-4 py-3">Tồn sau</th>
                                {adjustment ? <th className="px-4 py-3">Chênh lệch</th> : null}
                                <th className="px-4 py-3 text-right">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody>
                            {lines.map((line) => {
                                const changeQty = Number(line.changeQuantity) || 0;
                                const difference = adjustment ? changeQty - line.beforeQuantity : changeQty;
                                return (
                                    <tr key={line.item.id} className="hover:bg-neutral-50/50">
                                        <Cell>{line.sku}</Cell>
                                        <Cell>{line.productName}</Cell>
                                        <Cell>{inventoryItemTypeLabels[line.item.itemType]}</Cell>
                                        <Cell>{line.beforeQuantity}</Cell>
                                        <Cell>
                                            <input
                                                type="number"
                                                min={adjustment ? 0 : 1}
                                                value={line.changeQuantity}
                                                onChange={(e) => handleLineQuantityChange(line.item.id, e.target.value)}
                                                className="h-9 w-32 rounded-lg border border-pos-border px-2 text-sm outline-none focus:border-hvt-green-700"
                                            />
                                        </Cell>
                                        <Cell><strong>{line.afterQuantity}</strong></Cell>
                                        {adjustment ? (
                                            <Cell>
                                                <strong
                                                    className={
                                                        difference < 0
                                                            ? "text-red-600"
                                                            : difference > 0
                                                            ? "text-emerald-700"
                                                            : "text-gray-600"
                                                    }
                                                >
                                                    {difference > 0 ? "+" : ""}
                                                    {difference}
                                                </strong>
                                            </Cell>
                                        ) : null}
                                        <Cell>
                                            <button
                                                type="button"
                                                onClick={() => setLines(curr => curr.filter(l => l.item.id !== line.item.id))}
                                                className="ml-auto grid size-8 place-items-center rounded-lg text-red-600 hover:bg-red-50"
                                                aria-label="Xóa dòng"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </Cell>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {lines.length === 0 ? (
                        <p className="p-8 text-center text-sm text-gray-500">Chưa có sản phẩm nào được chọn.</p>
                    ) : null}
                </div>

                {error ? (
                    <p className="rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{error}</p>
                ) : null}

                {result ? (
                    <div className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">
                        <strong>
                            {adjustment
                                ? "Điều chỉnh tồn kho thành công"
                                : "Nhập hàng vào kệ thành công"}
                        </strong>
                        <p className="mt-1">Mã giao dịch: {result.transactionCode}</p>
                        <Link
                            className="mt-2 inline-block font-bold underline"
                            to={routePaths.inventoryTransactions}
                        >
                            Xem lịch sử biến động tồn kho
                        </Link>
                    </div>
                ) : null}

                <div className="flex justify-end">
                    <ActionButton variant="primary" onClick={handleSubmit}>
                        {submitting ? "Đang xử lý..." : adjustment ? "Xác nhận điều chỉnh" : "Xác nhận nhập hàng"}
                    </ActionButton>
                </div>
            </section>
        </section>
    );
}

function Cell({ children }: { children: React.ReactNode }) {
    return <td className="border-t border-pos-border px-4 py-4">{children}</td>;
}
