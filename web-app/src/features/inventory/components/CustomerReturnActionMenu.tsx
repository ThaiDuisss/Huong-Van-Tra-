import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "@/auth/role-utils";
import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";
import type { OrderReturnListRow } from "../../order/types/order-return.types";
import {
  canInspectCustomerReturn,
} from "../../order/utils/order-return.utils";

export interface CustomerReturnActionMenuProps {
  row: OrderReturnListRow;
  isOpen: boolean;
  isReceiving: boolean;
  onOpenChange: (open: boolean) => void;
  onViewDetail: () => void;
  onReceive: () => void;
  onInspect: () => void;
  onViewInspection?: () => void;
}

function getReturnAvailableActions(
  props: CustomerReturnActionMenuProps,
  canExecuteQc: boolean,
): RowAction[] {
  const { row, onViewDetail, onInspect, onReceive, isReceiving, onViewInspection } = props;
  const actions: RowAction[] = [{ label: "Xem chi tiết", onSelect: () => onViewDetail() }];

  if (row.status === "CREATED") {
    actions.push({
      label: isReceiving ? "Đang nhận..." : "Nhận hàng",
      onSelect: onReceive,
      disabled: isReceiving,
    });
    return actions;
  }

  if (row.status === "COMPLETED" || row.displayStatus === "COMPLETED") {
    if (
      onViewInspection &&
      (row.inspectionStatus === "POSTED" ||
        row.qcStatus === "COMPLETED" ||
        Boolean(row.physicalDone))
    ) {
      actions.push({
        label: "Xem kết quả kiểm định",
        onSelect: onViewInspection,
      });
    }
    return actions;
  }

  if (row.status === "RECEIVED") {
    const isQcCompleted =
      row.qcStatus === "COMPLETED" ||
      (row.remainingQuantity === 0 && row.remainingQcQuantity === 0 && Boolean(row.physicalDone));
    const isInspectionProcessing =
      row.inspectionStatus === "PROCESSING" || (row.qcStatus === "PARTIAL" && row.inspectionStatus !== "POSTED");
    const isInspectionCreated = row.inspectionStatus === "CREATED";
    const isPending = !isQcCompleted && Boolean(
      row.isPendingDisposition ||
        row.displayStatus === "PROCESSING" ||
        row.inventoryProcessingStatus === "PROCESSING",
    );

    if (isQcCompleted) {
      // Case E: Return đã hoàn tất kiểm định toàn bộ -> Xem kết quả kiểm định
      if (onViewInspection) {
        actions.push({
          label: "Xem kết quả kiểm định",
          onSelect: onViewInspection,
        });
      }
    } else if (isInspectionProcessing) {
      // Case D: Inspection đang kiểm định -> Tiếp tục kiểm định
      if (onViewInspection) {
        actions.push({
          label: canExecuteQc ? "Tiếp tục kiểm định" : "Xem phiếu kiểm định kho",
          onSelect: onViewInspection,
        });
      }
    } else if (isInspectionCreated) {
      // Case C: Inspection đã được tạo và còn chờ xử lý -> Mở phiếu kiểm định
      if (onViewInspection) {
        actions.push({
          label: canExecuteQc ? "Mở phiếu kiểm định" : "Xem phiếu kiểm định kho",
          onSelect: onViewInspection,
        });
      }
    } else if (isPending) {
      // Case B: Command PENDING nhưng chưa có inspection -> Không cho gửi command mới
      if (onViewInspection) {
        actions.push({
          label: "Xem phiếu kiểm định kho",
          onSelect: onViewInspection,
        });
      }
    } else if (canExecuteQc && canInspectCustomerReturn(row)) {
      // Case A & F: Chưa có command hoặc kiểm định một phần còn số lượng
      actions.push({
        label: row.inspectionStatus === "POSTED" ? "Tiếp tục kiểm định" : "Bắt đầu kiểm định",
        onSelect: onInspect,
      });
      if (onViewInspection) {
        actions.push({
          label: row.inspectionStatus === "POSTED" ? "Xem kết quả kiểm định" : "Xem phiếu kiểm định kho",
          onSelect: onViewInspection,
        });
      }
    } else if (onViewInspection) {
      actions.push({
        label: row.inspectionStatus === "POSTED" ? "Xem kết quả kiểm định" : "Xem phiếu kiểm định kho",
        onSelect: onViewInspection,
      });
    }
  }

  return actions;
}

export function CustomerReturnActionMenu(props: CustomerReturnActionMenuProps) {
  const { row, isOpen, onOpenChange } = props;
  const { hasAnyRole } = useAuthorization();
  return (
    <RowActionMenu
      actions={getReturnAvailableActions(props, hasAnyRole(CUSTOMER_RETURN_QC_ROLES))}
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      triggerLabel={`Mở thao tác cho phiếu ${row.returnCode ?? "hoàn hàng"}`}
      menuLabel={`Thao tác phiếu ${row.returnCode ?? "hoàn hàng"}`}
    />
  );
}
