import type { CustomerReturnInspectionLine, CustomerReturnQcDraft, CustomerReturnQcLocation, CustomerReturnQcResult } from "../types/customer-return-inspection.types";

export function createCustomerReturnQcDraft(line: CustomerReturnInspectionLine): CustomerReturnQcDraft {
    return {
        actualCondition: line.actualCondition ?? "",
        result: line.result,
        destinationLocationId: line.destinationLocationId ?? "",
        note: line.note ?? ""
    };
}

export function getCustomerReturnQcLocations(result: CustomerReturnQcResult, locations: CustomerReturnQcLocation[]) {
    if (result === "WRITE_OFF") return [];
    if (result === "DAMAGED" || result === "QUARANTINE") return locations.filter((location) => location.type === result);
    return locations.filter((location) => location.type !== "DAMAGED" && location.type !== "QUARANTINE");
}

export function getCustomerReturnQcError(line: CustomerReturnInspectionLine, draft: CustomerReturnQcDraft, locations: CustomerReturnQcLocation[]) {
    if (!draft.actualCondition) return "Vui lòng chọn tình trạng thực tế sau khi kiểm tra hàng.";
    if (draft.result === "RESTOCK" && draft.actualCondition !== "SEALED") {
        return "Chỉ được nhập lại kho khi tình trạng thực tế còn nguyên seal.";
    }
    if (draft.result === "QUARANTINE" && line.result !== "QUARANTINE") return "Không tạo mới phân loại cách ly.";
    if (draft.result !== "WRITE_OFF" && !getCustomerReturnQcLocations(draft.result, locations).some((location) => location.id === draft.destinationLocationId)) {
        return "Vui lòng chọn vị trí kho phù hợp với phân loại xử lý.";
    }
    return null;
}
