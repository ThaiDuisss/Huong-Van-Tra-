import type { ReactNode } from "react";

import { NativeSelect, TextArea } from "@/components/ui/Field";

import {
    RETURN_CONDITIONS,
    RETURN_CONDITION_LABELS,
    getAllowedCustomerReturnQcResults,
    getReturnConditionLabel
} from "../../order/utils/order-return.utils";
import type {
    CustomerReturnActualCondition,
    CustomerReturnInspectionLine,
    CustomerReturnQcDraft,
    CustomerReturnQcLocation,
    CustomerReturnQcResult
} from "../types/customer-return-inspection.types";
import { getCustomerReturnQcLocations } from "../utils/customer-return-qc";

const QC_RESULT_LABELS: Record<CustomerReturnQcResult, string> = {
    RESTOCK: "Nhập lại kho",
    DAMAGED: "Hàng hỏng",
    WRITE_OFF: "Tiêu hủy",
    QUARANTINE: "Cách ly (lịch sử)"
};

interface Props {
    line: CustomerReturnInspectionLine;
    draft: CustomerReturnQcDraft;
    editable: boolean;
    locations: CustomerReturnQcLocation[];
    error?: string | null;
    onChange: (update: Partial<CustomerReturnQcDraft>) => void;
}

/** Form kiểm hàng cho một dòng (bố cục dọc, dùng ở màn chi tiết phiếu kiểm định). */
export function CustomerReturnQcForm({ line, draft, editable, locations, error, onChange }: Props) {
    const declared = line.declaredCondition ?? line.condition;
    const actual = draft.actualCondition;
    const allowedResults = getAllowedCustomerReturnQcResults(actual || null);
    const options: CustomerReturnQcResult[] = ["RESTOCK", "DAMAGED", "WRITE_OFF"];
    if (line.result === "QUARANTINE") options.push("QUARANTINE");
    const eligibleLocations = getCustomerReturnQcLocations(draft.result, locations);
    const idPrefix = `qc-${line.id}`;

    return (
        <div className="grid gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Tình trạng khai báo">
                    <p className="text-sm font-black text-[#172317]">
                        {declared ? getReturnConditionLabel(declared as CustomerReturnActualCondition) : "Chưa có khai báo"}
                    </p>
                </Field>
                <Field hint={actual && declared && actual !== declared ? "Khác khai báo ban đầu" : undefined} htmlFor={`${idPrefix}-actual`} label="Tình trạng thực tế">
                    {editable ? (
                        <NativeSelect
                            id={`${idPrefix}-actual`}
                            onChange={(event) => onChange({ actualCondition: event.target.value as CustomerReturnActualCondition | "" })}
                            value={actual}
                        >
                            <option value="">-- Chọn tình trạng thực tế --</option>
                            {RETURN_CONDITIONS.map((condition) => (
                                <option key={condition} value={condition}>{RETURN_CONDITION_LABELS[condition]}</option>
                            ))}
                        </NativeSelect>
                    ) : (
                        <p className="text-sm font-black text-[#172317]">{actual ? getReturnConditionLabel(actual) : "Chưa ghi nhận"}</p>
                    )}
                </Field>
                <Field htmlFor={`${idPrefix}-result`} label="Phân loại xử lý">
                    {editable ? (
                        <NativeSelect
                            id={`${idPrefix}-result`}
                            onChange={(event) => onChange({ result: event.target.value as CustomerReturnQcResult, destinationLocationId: "" })}
                            value={draft.result}
                        >
                            {options.map((result) => (
                                <option disabled={result === "RESTOCK" && !allowedResults.includes("RESTOCK")} key={result} value={result}>
                                    {QC_RESULT_LABELS[result]}
                                </option>
                            ))}
                        </NativeSelect>
                    ) : (
                        <p className="text-sm font-black text-[#172317]">{QC_RESULT_LABELS[draft.result]}</p>
                    )}
                </Field>
                <Field htmlFor={`${idPrefix}-location`} label="Vị trí đích">
                    {draft.result === "WRITE_OFF" ? (
                        <p className="text-sm font-semibold text-[#697684]">Không nhập kho (tiêu hủy)</p>
                    ) : editable ? (
                        <NativeSelect
                            id={`${idPrefix}-location`}
                            onChange={(event) => onChange({ destinationLocationId: event.target.value })}
                            value={draft.destinationLocationId}
                        >
                            <option value="">-- Chọn vị trí --</option>
                            {eligibleLocations.map((location) => (
                                <option key={location.id} value={location.id}>{location.name}</option>
                            ))}
                        </NativeSelect>
                    ) : (
                        <p className="text-sm font-black text-[#172317]">{line.destinationLocationName ?? "Chưa xác định"}</p>
                    )}
                </Field>
            </div>
            <Field htmlFor={`${idPrefix}-note`} label="Ghi chú kiểm hàng">
                {editable ? (
                    <TextArea
                        id={`${idPrefix}-note`}
                        maxLength={500}
                        onChange={(event) => onChange({ note: event.target.value })}
                        placeholder="Mô tả tình trạng thực tế nếu cần"
                        value={draft.note}
                    />
                ) : (
                    <p className="text-sm text-[#172317]">{line.note || "Không có ghi chú"}</p>
                )}
            </Field>
            {editable && error ? (
                <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800" role="alert">{error}</p>
            ) : null}
        </div>
    );
}

function Field({ children, hint, htmlFor, label }: { children: ReactNode; hint?: string; htmlFor?: string; label: string }) {
    return (
        <div className="grid content-start gap-1.5">
            <label className="text-xs font-black uppercase text-[#697684]" htmlFor={htmlFor}>{label}</label>
            {children}
            {hint ? <p className="text-xs font-semibold text-amber-700">{hint}</p> : null}
        </div>
    );
}
