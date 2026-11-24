import { useOrderReturnAccess } from "@/auth/order-return-access";
import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";
import type { OrderReturnListRow } from "../../order/types/order-return.types";

export interface CustomerReturnActionMenuProps {
  row: OrderReturnListRow;
  isOpen: boolean;
  isReceiving: boolean;
  onOpenChange: (open: boolean) => void;
  onViewDetail: () => void;
  onReceive: () => void;
  onInspect: () => void;
}

export function CustomerReturnActionMenu({
  row, isOpen, isReceiving, onOpenChange, onViewDetail, onReceive, onInspect
}: CustomerReturnActionMenuProps) {
  const { canDisposition, canReceive } = useOrderReturnAccess();
  const actions: RowAction[] = [{ label: "Xem chi tiết", onSelect: onViewDetail }];
  if (row.status === "CREATED" && canReceive) {
    actions.push({ label: isReceiving ? "Đang nhận..." : "Nhận hàng", onSelect: onReceive, disabled: isReceiving });
  }
  if (row.status === "RECEIVED" || (row.status === "COMPLETED" && (
    row.inspectionStatus === "POSTED" || row.qcStatus === "COMPLETED" || row.physicalDone
  ))) {
    const completed = row.status === "COMPLETED" || row.qcStatus === "COMPLETED" || row.physicalDone;
    actions.push({
      label: completed ? "Xem kết quả kiểm định" : canDisposition ? "Kiểm định" : "Xem tiến độ kiểm định",
      onSelect: onInspect
    });
  }
  return (
    <RowActionMenu
      actions={actions}
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      triggerLabel={`Mở thao tác cho phiếu ${row.returnCode ?? "hoàn hàng"}`}
      menuLabel={`Thao tác phiếu ${row.returnCode ?? "hoàn hàng"}`}
    />
  );
}
