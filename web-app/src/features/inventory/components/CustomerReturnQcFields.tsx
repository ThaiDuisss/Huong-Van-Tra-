import { RETURN_CONDITIONS, RETURN_CONDITION_LABELS, getAllowedCustomerReturnQcResults, getReturnConditionLabel } from "../../order/utils/order-return.utils";
import type { CustomerReturnActualCondition, CustomerReturnInspectionLine, CustomerReturnQcDraft, CustomerReturnQcLocation, CustomerReturnQcResult } from "../types/customer-return-inspection.types";
import { getCustomerReturnQcLocations } from "../utils/customer-return-qc";

const resultLabels: Record<CustomerReturnQcResult, string> = {
    RESTOCK: "Nhập lại kho", DAMAGED: "Hàng hỏng", WRITE_OFF: "Tiêu hủy", QUARANTINE: "Cách ly (lịch sử)"
};

interface Props {
    line: CustomerReturnInspectionLine;
    draft: CustomerReturnQcDraft;
    editable: boolean;
    locations: CustomerReturnQcLocation[];
    onChange: (update: Partial<CustomerReturnQcDraft>) => void;
}

export function CustomerReturnQcFields({ line, draft, editable, locations, onChange }: Props) {
    const declared = line.declaredCondition ?? line.condition;
    const actual = draft.actualCondition;
    const allowedResults = getAllowedCustomerReturnQcResults(actual || null);
    const options: CustomerReturnQcResult[] = ["RESTOCK", "DAMAGED", "WRITE_OFF"];
    if (line.result === "QUARANTINE") options.push("QUARANTINE");
    const eligibleLocations = getCustomerReturnQcLocations(draft.result, locations);
    return <>
        <td className="border-t border-[#edf0f4] px-3 py-3">
            {declared ? getReturnConditionLabel(declared as CustomerReturnActualCondition) : "Chưa có khai báo"}
        </td>
        <td className="border-t border-[#edf0f4] px-3 py-3">
            {editable ? <select aria-label="Tình trạng thực tế" className="h-9 rounded border border-pos-border px-2 text-sm" value={actual} onChange={(event) => onChange({ actualCondition: event.target.value as CustomerReturnActualCondition | "" })}>
                <option value="">-- Chọn tình trạng thực tế --</option>
                {RETURN_CONDITIONS.map((condition) => <option key={condition} value={condition}>{RETURN_CONDITION_LABELS[condition]}</option>)}
            </select> : <span>{actual ? getReturnConditionLabel(actual) : "Chưa ghi nhận"}</span>}
            {actual && declared && actual !== declared ? <p className="mt-1 text-xs text-[#697684]">Khác khai báo ban đầu</p> : null}
        </td>
        <td className="border-t border-[#edf0f4] px-3 py-3">
            {editable ? <select aria-label="Phân loại xử lý" className="h-9 rounded border border-pos-border px-2 text-sm" value={draft.result} onChange={(event) => onChange({ result: event.target.value as CustomerReturnQcResult, destinationLocationId: "" })}>
                {options.map((result) => <option key={result} value={result} disabled={result === "RESTOCK" && !allowedResults.includes("RESTOCK")}>{resultLabels[result]}</option>)}
            </select> : <span>{resultLabels[draft.result]}</span>}
        </td>
        <td className="border-t border-[#edf0f4] px-3 py-3">
            {draft.result === "WRITE_OFF" ? <span className="text-sm text-[#697684]">Không áp dụng</span> : editable ? <select aria-label="Vị trí đích" className="h-9 rounded border border-pos-border px-2 text-sm" value={draft.destinationLocationId} onChange={(event) => onChange({ destinationLocationId: event.target.value })}>
                <option value="">-- Chọn vị trí --</option>
                {eligibleLocations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}
            </select> : <span>{line.destinationLocationName ?? "Chưa xác định"}</span>}
        </td>
        <td className="border-t border-[#edf0f4] px-3 py-3">
            {editable ? <textarea aria-label="Ghi chú kiểm định" className="min-h-16 rounded border border-pos-border p-2 text-sm" maxLength={500} value={draft.note} onChange={(event) => onChange({ note: event.target.value })} /> : <span>{line.note || "Không có ghi chú"}</span>}
        </td>
    </>;
}
