import {
    Gift,
    PackageCheck,
    Percent,
    TicketPercent,
    Truck
} from "lucide-react";

import styles from "./DiscountProgramPage.module.css";
import type {
    DiscountApplicationType,
    DiscountProgramStatus
} from "../../types/discount-program.types";
import {
    applicationTypeLabels,
    discountStatusLabels
} from "../../utils/discount-programs";

const applicationBadgeClassNames: Record<
    DiscountApplicationType,
    string
> = {
    DISCOUNT_CODE: styles.applicationDiscountCode,
    FREE_SHIPPING: styles.applicationFreeShipping,
    BUY_X_GET_Y: styles.applicationBuyXGetY,
    AMOUNT_OFF: styles.applicationAmountOff,
    GIFT: styles.applicationGift
};

const statusBadgeClassNames: Record<DiscountProgramStatus, string> = {
    ACTIVE: styles.statusActive,
    SCHEDULED: styles.statusScheduled,
    INACTIVE: styles.statusInactive
};

const applicationIcons = {
    DISCOUNT_CODE: TicketPercent,
    FREE_SHIPPING: Truck,
    BUY_X_GET_Y: Gift,
    AMOUNT_OFF: Percent,
    GIFT: PackageCheck
} satisfies Record<DiscountApplicationType, typeof TicketPercent>;

interface ApplicationTypeBadgeProps {
    type: DiscountApplicationType;
}

export function ApplicationTypeBadge({
    type
}: ApplicationTypeBadgeProps) {
    const Icon = applicationIcons[type];

    return (
        <span
            className={`${styles.applicationBadge} ${applicationBadgeClassNames[type]}`}
        >
            <Icon size={15} aria-hidden="true" />
            {applicationTypeLabels[type]}
        </span>
    );
}

interface DiscountStatusBadgeProps {
    status: DiscountProgramStatus;
}

export function DiscountStatusBadge({
    status
}: DiscountStatusBadgeProps) {
    return (
        <span
            className={`${styles.statusBadge} ${statusBadgeClassNames[status]}`}
        >
            {discountStatusLabels[status]}
        </span>
    );
}
