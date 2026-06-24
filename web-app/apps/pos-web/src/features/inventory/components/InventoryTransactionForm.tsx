import { Plus, Trash2 } from "lucide-react";
import { useCallback, useState } from "react";
import { Link } from "react-router-dom";

import { ActionButton } from "../../manufacturing/components/shared/ActionButton";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { useInventoryPage, messageOf } from "../hooks/useInventoryResource";
import { inventoryBalanceApi } from "../services/inventoryBalance.api";
import { inventoryLocationApi } from "../services/inventoryLocation.api";
import type {
    EntityId,
    InventoryItemResponse,
    InventoryReferenceType,
    InventoryTransactionResponse
} from "../types/inventory.types";
import {
    inventoryItemTypeLabels,
    referenceTypeLabels
} from "../utils/inventory-labels";
import { InventoryItemSelect } from "./InventoryItemSelect";
import { routePaths } from "../../../routes/path";

interface TransactionLine {
    item: InventoryItemResponse;
    currentQuantity: number;
    quantity: string;
}

interface InventoryTransactionFormProps {
    mode: "stock-in" | "adjustment";
    onSubmit(input: {
        branchId: EntityId;
        referenceType: InventoryReferenceType;
        referenceId: EntityId | null;
        locationId: EntityId;
        lines: TransactionLine[];
    }): Promise<InventoryTransactionResponse>;
}

export function InventoryTransactionForm({
    mode,
    onSubmit
}: InventoryTransactionFormProps) {
    const adjustment = mode === "adjustment";
    const [branchId, _setBranchId] = useState("");
    const [locationId, setLocationId] = useState("");
    const [referenceType, setReferenceType] =
        useState<InventoryReferenceType>(
            adjustment ? "STOCK_COUNT" : "MANUAL"
        );
    const [selectedItem, setSelectedItem] =
        useState<InventoryItemResponse | null>(null);
    const [lines, setLines] = useState<TransactionLine[]>([]);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [result, setResult] =
        useState<InventoryTransactionResponse | null>(null);
    const loadLocations = useCallback(
        () =>
            inventoryLocationApi.list({
                page: 0,
                size: 200,
                branchId: branchId ? branchId : undefined,
                status: "ACTIVE"
            }),
        [branchId]
    );
    const locationResource = useInventoryPage(loadLocations);
    const locations = locationResource.data?.content ?? [];

    async function addItem() {
        if (!selectedItem || !locationId) {
            setError("Vui lòng chọn vị trí và hàng hóa.");
            return;
        }

        if (lines.some((line) => line.item.id === selectedItem.id)) {
            setError("Hàng hóa này đã có trong danh sách.");
            return;
        }

        let currentQuantity = 0;
        if (adjustment) {
            const balances = await inventoryBalanceApi.list({
                page: 0,
                size: 1,
                inventoryItemId: selectedItem.id,
                locationId: locationId,
                branchId: branchId ? branchId : undefined
            });
            currentQuantity = balances.content[0]?.quantityOnHand ?? 0;
        }

        setLines((current) => [
            ...current,
            {
                item: selectedItem,
                currentQuantity,
                quantity: adjustment ? String(currentQuantity) : "1"
            }
        ]);
        setSelectedItem(null);
        setError("");
    }

    async function submit() {
        if (!branchId || !locationId || lines.length === 0) {
            setError("Vui lòng nhập đầy đủ thông tin bắt buộc.");
            return;
        }
        if (
            lines.some((line) =>
                adjustment
                    ? Number(line.quantity) < 0
                    : Number(line.quantity) <= 0
            )
        ) {
            setError(
                adjustment
                    ? "Số lượng kiểm kê thực tế phải lớn hơn hoặc bằng 0."
                    : "Số lượng nhập phải lớn hơn 0."
            );
            return;
        }
        const message = adjustment
            ? "Bạn có chắc chắn muốn điều chỉnh tồn kho?"
            : "Bạn có chắc chắn muốn nhập hàng?";
        if (!window.confirm(message)) {
            return;
        }

        setSubmitting(true);
        setError("");
        try {
            const response = await onSubmit({
                branchId: branchId,
                referenceType,
                referenceId: null,
                locationId: locationId,
                lines
            });
            setResult(response);
            setLines([]);
        } catch (submitError) {
            setError(messageOf(submitError));
        } finally {
            setSubmitting(false);
        }
    }

    const title = adjustment
        ? "Điều chỉnh tồn kho"
        : "Nhập hàng lên kệ";

    return (
        <section className="grid gap-5">
            {/* <Breadcrumbs items={["Quản lý tồn kho", title]} /> */}
            <PageHeader
                title={title}
                description={
                    adjustment
                        ? "Cập nhật tồn kho sau khi kiểm kê thực tế."
                        : "Bổ sung hàng hóa vào kệ, quầy hoặc vị trí lưu trữ."
                }
            />
            <section className="grid gap-5 rounded-2xl bg-white p-5 shadow-pos-card">
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                    {/* <Field label="Mã chi nhánh" value={branchId} type="text" onChange={setBranchId} /> */}
                    <label className="grid gap-2 text-sm font-bold">Chọn vị trí<select value={locationId} onChange={(event) => setLocationId(event.currentTarget.value)} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal"><option value="">Chọn vị trí</option>{locations.map((location) => <option key={location.id} value={location.id}>{location.code} - {location.name}</option>)}</select></label>
                    <label className="grid gap-2 text-sm font-bold">Loại tham chiếu<select value={referenceType} onChange={(event) => setReferenceType(event.currentTarget.value as InventoryReferenceType)} className="h-11 rounded-xl border border-pos-border bg-white px-3 font-normal">{Object.entries(referenceTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                </div>
                <div className="grid gap-3 rounded-2xl border border-pos-border p-4">
                    <h2 className="font-bold">Chọn hàng hóa</h2>
                    <InventoryItemSelect value={selectedItem?.id ?? null} onChange={setSelectedItem} />
                    <button type="button" onClick={() => void addItem()} className="inline-flex h-10 w-fit items-center gap-2 rounded-xl border border-pos-border px-4 text-sm font-bold"><Plus size={16} />Thêm dòng hàng</button>
                </div>
                <div className="overflow-x-auto rounded-2xl border border-pos-border">
                    <table className="min-w-[780px] w-full text-left text-sm">
                        <thead className="bg-neutral-50 text-xs text-gray-600"><tr><th className="px-4 py-3">Mã hàng tồn kho</th><th className="px-4 py-3">Mã tham chiếu</th><th className="px-4 py-3">Loại hàng hóa</th>{adjustment ? <th className="px-4 py-3">Tồn hiện tại</th> : null}<th className="px-4 py-3">{adjustment ? "Số lượng kiểm kê thực tế" : "Số lượng"}</th>{adjustment ? <th className="px-4 py-3">Chênh lệch</th> : null}<th className="px-4 py-3 text-right">Thao tác</th></tr></thead>
                        <tbody>{lines.map((line) => { const difference = Number(line.quantity) - line.currentQuantity; return <tr key={line.item.id}><Cell>#{line.item.id}</Cell><Cell>#{line.item.referenceId}</Cell><Cell>{inventoryItemTypeLabels[line.item.itemType]}</Cell>{adjustment ? <Cell>{line.currentQuantity}</Cell> : null}<Cell><input type="number" min={adjustment ? 0 : 0.001} step="0.001" value={line.quantity} onChange={(event) => setLines((current) => current.map((item) => item.item.id === line.item.id ? { ...item, quantity: event.currentTarget.value } : item))} className="h-9 w-32 rounded-lg border border-pos-border px-2" /></Cell>{adjustment ? <Cell><strong className={difference < 0 ? "text-red-600" : difference > 0 ? "text-emerald-700" : "text-gray-600"}>{difference > 0 ? "+" : ""}{difference}</strong></Cell> : null}<Cell><button type="button" onClick={() => setLines((current) => current.filter((item) => item.item.id !== line.item.id))} className="ml-auto grid size-8 place-items-center rounded-lg text-red-600 hover:bg-red-50" aria-label="Xóa dòng"><Trash2 size={16} /></button></Cell></tr>; })}</tbody>
                    </table>
                    {lines.length === 0 ? <p className="p-8 text-center text-sm text-gray-500">Chưa có hàng hóa trong danh sách.</p> : null}
                </div>
                {error ? <p className="rounded-xl bg-red-50 p-3 text-sm font-bold text-red-700">{error}</p> : null}
                {result ? <div className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800"><strong>{adjustment ? "Điều chỉnh tồn kho thành công" : "Nhập hàng thành công"}</strong><p className="mt-1">Mã giao dịch: {result.transactionCode}</p><Link className="mt-2 inline-block font-bold underline" to={routePaths.inventoryTransactions}>Xem lịch sử biến động tồn kho</Link></div> : null}
                <div className="flex justify-end"><ActionButton variant="primary" onClick={() => void submit()}>{submitting ? "Đang xử lý..." : adjustment ? "Xác nhận điều chỉnh" : "Xác nhận nhập hàng"}</ActionButton></div>
            </section>
        </section>
    );
}


function Cell({ children }: { children: React.ReactNode }) {
    return <td className="border-t border-pos-border px-4 py-4">{children}</td>;
}
