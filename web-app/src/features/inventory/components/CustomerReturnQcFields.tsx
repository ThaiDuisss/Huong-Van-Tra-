import { NativeSelect, TextArea } from "@/components/ui/Field";
import { TableCell } from "@/components/ui/Table";

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
        <TableCell className="border-t border-[#edf0f4]">
            {declared ? getReturnConditionLabel(declared as CustomerReturnActualCondition) : "Chưa có khai báo"}
        </TableCell>
        <TableCell className="border-t border-[#edf0f4]">
            {editable ? <NativeSelect aria-label="Tình trạng thực tế" value={actual} onChange={(event) => onChange({ actualCondition: event.target.value as CustomerReturnActualCondition | "" })}>
                <option value="">-- Chọn tình trạng thực tế --</option>
                {RETURN_CONDITIONS.map((condition) => <option key={condition} value={condition}>{RETURN_CONDITION_LABELS[condition]}</option>)}
            </NativeSelect> : <span>{actual ? getReturnConditionLabel(actual) : "Chưa ghi nhận"}</span>}
            {actual && declared && actual !== declared ? <p className="mt-1 text-xs text-[#697684]">Khác khai báo ban đầu</p> : null}
        </TableCell>
        <TableCell className="border-t border-[#edf0f4]">
            {editable ? <NativeSelect aria-label="Phân loại xử lý" value={draft.result} onChange={(event) => onChange({ result: event.target.value as CustomerReturnQcResult, destinationLocationId: "" })}>
                {options.map((result) => <option key={result} value={result} disabled={result === "RESTOCK" && !allowedResults.includes("RESTOCK")}>{resultLabels[result]}</option>)}
            </NativeSelect> : <span>{resultLabels[draft.result]}</span>}
        </TableCell>
        <TableCell className="border-t border-[#edf0f4]">
            {draft.result === "WRITE_OFF" ? <span className="text-sm text-[#697684]">Không áp dụng</span> : editable ? <NativeSelect aria-label="Vị trí đích" value={draft.destinationLocationId} onChange={(event) => onChange({ destinationLocationId: event.target.value })}>
                <option value="">-- Chọn vị trí --</option>
                {eligibleLocations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}
            </NativeSelect> : <span>{line.destinationLocationName ?? "Chưa xác định"}</span>}
        </TableCell>
        <TableCell className="border-t border-[#edf0f4]">
            {editable ? <TextArea aria-label="Ghi chú kiểm định" maxLength={500} value={draft.note} onChange={(event) => onChange({ note: event.target.value })} /> : <span>{line.note || "Không có ghi chú"}</span>}
        </TableCell>
    </>;
}
