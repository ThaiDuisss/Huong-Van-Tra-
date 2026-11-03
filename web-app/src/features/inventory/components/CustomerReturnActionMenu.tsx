import { CUSTOMER_RETURN_QC_ROLES, useAuthorization } from "@/auth/role-utils";
import { RowActionMenu, type RowAction } from "@/components/shared/RowActionMenu";
import type { OrderReturnListRow } from "../../order/types/order-return.types";
import {
  canInspectCustomerReturn,
  getCustomerReturnQcProgressStatus,
} from "../../order/utils/order-return.utils";

interface CustomerReturnActionMenuProps {
  row: OrderReturnListRow;
  isOpen: boolean;
  isReceiving: boolean;
  onOpenChange: (open: boolean) => void;
  onViewDetail: () => void;
  onReceive: () => void;
  onInspect: () => void;
}

function getReturnAvailableActions(
  props: CustomerReturnActionMenuProps,
  canExecuteQc: boolean,
): RowAction[] {
  const { row, onViewDetail, onInspect, onReceive, isReceiving } = props;
  const actions: RowAction[] = [{ label: "Xem chi tiết", onSelect: () => onViewDetail() }];

  if (row.status === "CREATED") {
    actions.push({
      label: isReceiving ? "Đang nhận..." : "Nhận hàng",
      onSelect: onReceive,
      disabled: isReceiving,
    });
  } else if (canExecuteQc && canInspectCustomerReturn(row)) {
    // Reuse the existing eligibility rule, including remaining quantities after
    // a failed disposition. This opens the current form, not a new retry/reset.
    actions.push({
      label:
        getCustomerReturnQcProgressStatus(row).status === "PROCESSING"
          ? "Tiếp tục kiểm định"
          : "Kiểm định hàng",
      onSelect: onInspect,
    });
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
