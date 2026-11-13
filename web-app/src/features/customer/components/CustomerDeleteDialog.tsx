import { AlertCircle, LoaderCircle, Trash2 } from "lucide-react";
import { useState } from "react";

import { Button } from "../../../components/ui/Button";
import {
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../../components/ui/dialog";
import { customerApi } from "../services/customer.api";
import type { CustomerResponse } from "../types/customer.types";
import { customerErrorMessage } from "../utils/customer-list.utils";

export function CustomerDeleteDialog({
    customer,
    onCancel,
    onDeleted
}: {
    customer: CustomerResponse;
    onCancel: () => void;
    onDeleted: () => void | Promise<void>;
}) {
    const [deleting, setDeleting] = useState(false);
    const [error, setError] = useState("");

    async function removeCustomer() {
        setDeleting(true);
        setError("");
        try {
            await customerApi.remove(customer.id);
            await onDeleted();
        } catch (deleteError) {
            setError(customerErrorMessage(deleteError));
        } finally {
            setDeleting(false);
        }
    }

    return (
        <DialogContent className="w-[min(520px,calc(100vw-32px))]">
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                    <Trash2 className="text-[#c4422e]" size={18} />
                    Xóa khách hàng
                </DialogTitle>
                <DialogDescription>
                    Khách hàng sẽ bị ẩn khỏi hệ thống nhưng dữ liệu liên quan
                    vẫn được giữ lại.
                </DialogDescription>
            </DialogHeader>
            <div className="px-6 py-5 text-sm font-semibold text-[#425047]">
                <div className="rounded-xl border border-[#eef2f0] bg-[#f8faf9] p-4">
                    Bạn có chắc muốn xóa{" "}
                    <strong className="font-black text-[#172317]">
                        {customer.fullName}
                    </strong>{" "}
                    ({customer.customerCode})?
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
                <Button disabled={deleting} onClick={onCancel}>
                    Hủy
                </Button>
                <Button
                    variant="danger"
                    disabled={deleting}
                    onClick={() => void removeCustomer()}
                >
                    {deleting && (
                        <LoaderCircle className="animate-spin" size={16} />
                    )}
                    {deleting ? "Đang xóa..." : "Xóa khách hàng"}
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}
