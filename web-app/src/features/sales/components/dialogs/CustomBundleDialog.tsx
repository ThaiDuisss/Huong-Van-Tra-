import { Leaf } from "lucide-react";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "../../../../components/ui/dialog";
import { CustomBundleBuilder, type CustomBundleInput } from "../custom-bundle/CustomBundleBuilder";

interface CustomBundleDialogProps {
    branchId: string | null;
    customerId?: string | null;
    submitLoading: boolean;
    submitError: string;
    onClose(): void;
    onSubmit(input: CustomBundleInput): void | Promise<unknown>;
}

/** Popup đóng trà theo yêu cầu thông thường (chế độ MANUAL) trên màn POS. */
export function CustomBundleDialog({
    branchId,
    customerId = null,
    submitLoading,
    submitError,
    onClose,
    onSubmit
}: CustomBundleDialogProps) {
    return (
        <Dialog
            open
            onOpenChange={(open) => {
                if (!open && !submitLoading) onClose();
            }}
        >
            <DialogContent className="w-[min(1180px,calc(100vw-32px))]">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2 text-lg">
                        <Leaf aria-hidden="true" className="text-[#159734]" size={20} />
                        Đóng trà theo yêu cầu
                    </DialogTitle>
                    <DialogDescription>
                        Chọn nguyên liệu và khối lượng khách muốn, bấm "Thêm vào đơn" để đưa bundle vào đơn đang bán;
                        kho đóng gói sau khi thu tiền.
                    </DialogDescription>
                </DialogHeader>
                <div className="grid h-[min(680px,calc(90vh-110px))] grid-rows-[minmax(0,1fr)] p-4">
                    <CustomBundleBuilder
                        key={customerId ?? "guest"}
                        branchId={branchId}
                        customerId={customerId}
                        mode="MANUAL"
                        submitLoading={submitLoading}
                        submitError={submitError}
                        onCancel={onClose}
                        onSubmit={onSubmit}
                    />
                </div>
            </DialogContent>
        </Dialog>
    );
}
