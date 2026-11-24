import { Leaf } from "lucide-react";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "../../../../components/ui/dialog";
import { CustomBundleBuilder, type CustomBundleInput } from "../custom-bundle/CustomBundleBuilder";

interface CustomBundleDialogProps {
    branchId: string | null;
    submitLoading: boolean;
    submitError: string;
    onClose(): void;
    onSubmit(input: CustomBundleInput): void;
}

/** Popup đóng trà theo yêu cầu trên màn POS: chọn nguyên liệu rồi thêm bundle vào đơn đang bán. */
export function CustomBundleDialog({ branchId, submitLoading, submitError, onClose, onSubmit }: CustomBundleDialogProps) {
    return (
        <Dialog
            open
            onOpenChange={(open) => {
                if (!open && !submitLoading) onClose();
            }}
        >
            <DialogContent className="w-[min(1120px,calc(100vw-32px))]">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2 text-lg">
                        <Leaf aria-hidden="true" className="text-[#159734]" size={20} />
                        Đóng trà theo yêu cầu
                    </DialogTitle>
                    <DialogDescription>
                        Chọn nguyên liệu và khối lượng khách muốn, bấm "Thêm vào đơn" để đưa bundle vào đơn đang bán;
                        kho đóng gói sau khi thu tiền.
                        Gợi ý công thức (BOM) chưa sẵn sàng.
                    </DialogDescription>
                </DialogHeader>
                <div className="grid h-[min(620px,calc(90vh-110px))] grid-rows-[minmax(0,1fr)] p-4">
                    <CustomBundleBuilder
                        branchId={branchId}
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
