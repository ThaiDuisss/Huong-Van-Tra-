import { AlertCircle, LoaderCircle, ShieldCheck, ShieldOff } from "lucide-react";
import { useState } from "react";

import { Button } from "../../../components/ui/Button";
import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { CUSTOMER_STATUS_LABELS } from "../constants/customer-list.constants";
import { customerApi } from "../services/customer.api";
import type { CustomerResponse } from "../types/customer.types";
import {
    customerErrorMessage,
    customerStatusActionLabel,
    nextCustomerStatus
} from "../utils/customer-list.utils";

export function CustomerStatusDialog({
    customer,
    onCancel,
    onChanged
}: {
    customer: CustomerResponse;
    onCancel: () => void;
    onChanged: (customer: CustomerResponse) => void | Promise<void>;
}) {
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const targetStatus = nextCustomerStatus(customer.status);
    const deactivating = targetStatus !== "ACTIVE";
    const Icon = deactivating ? ShieldOff : ShieldCheck;

    async function changeStatus() {
        setSaving(true);
        setError("");
        try {
            const updated = await customerApi.changeStatus(customer.id, {
                status: targetStatus
            });
            await onChanged(updated);
        } catch (changeError) {
            setError(customerErrorMessage(changeError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <DialogContent className="w-[min(520px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                    <Icon
                        className={deactivating ? "text-[#c4422e]" : "text-[#159734]"}
                        size={18}
                    />
                    {deactivating
                        ? "Ngừng hoạt động khách hàng"
                        : "Kích hoạt lại khách hàng"}
                </DialogTitle>
                <DialogDescription>
                    {deactivating
                        ? "Khách hàng sẽ không còn xuất hiện khi chọn khách tại quầy bán hàng. Hồ sơ, hạng thành viên, điểm và lịch sử mua hàng vẫn được giữ nguyên."
                        : "Khách hàng sẽ xuất hiện trở lại khi chọn khách tại quầy bán hàng."}
                </DialogDescription>
            </DialogHeader>
            <div className="px-6 py-5 text-sm font-semibold text-[#425047]">
                <div className="rounded-xl border border-[#eef2f0] bg-[#f8faf9] p-4">
                    Bạn có chắc muốn chuyển{" "}
                    <strong className="font-black text-[#172317]">
                        {customer.fullName}
                    </strong>{" "}
                    ({customer.customerCode}) từ trạng thái{" "}
                    <strong className="font-black text-[#172317]">
                        {CUSTOMER_STATUS_LABELS[customer.status]}
                    </strong>{" "}
                    sang{" "}
                    <strong className="font-black text-[#172317]">
                        {CUSTOMER_STATUS_LABELS[targetStatus]}
                    </strong>
                    ?
                </div>
                {error && (
                    <div
                        className="mt-4 flex gap-2 rounded-xl border border-[#f1c8c1] bg-[#fff5f3] p-3 text-sm font-bold text-[#a43a2a]"
                        role="alert"
                    >
                        <AlertCircle size={16} className="shrink-0" />
                        {error}
                    </div>
                )}
            </div>
            <DialogFooter>
                <Button disabled={saving} onClick={onCancel}>
                    Hủy
                </Button>
                <Button
                    variant={deactivating ? "danger" : "primary"}
                    disabled={saving}
                    onClick={() => void changeStatus()}
                >
                    {saving && <LoaderCircle className="animate-spin" size={16} />}
                    {saving ? "Đang cập nhật..." : customerStatusActionLabel(customer.status)}
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}
