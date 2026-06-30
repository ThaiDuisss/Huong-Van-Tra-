import { Globe2, UserRound } from "lucide-react";

import styles from "../discount-programs/DiscountProgramPage.module.css";
import type {
    DiscountCodeScope,
    DiscountCodeStatus
} from "../../types/discount-code.types";
import {
    discountCodeScopeLabels,
    discountCodeStatusLabels
} from "../../utils/discount-codes";

const statusClassNames: Record<DiscountCodeStatus, string> = {
    ACTIVE: styles.statusActive,
    USED: styles.statusUsed,
    SCHEDULED: styles.statusScheduled,
    EXPIRED: styles.statusExpired
};

const scopeClassNames: Record<DiscountCodeScope, string> = {
    PUBLIC: styles.scopePublic,
    PERSONAL: styles.scopePersonal
};

const scopeIcons = {
    PUBLIC: Globe2,
    PERSONAL: UserRound
} satisfies Record<DiscountCodeScope, typeof Globe2>;

export function DiscountCodeStatusBadge({
    status
}: {
    status: DiscountCodeStatus;
}) {
    return (
        <span
            className={`${styles.statusBadge} ${statusClassNames[status]}`}
        >
            {discountCodeStatusLabels[status]}
        </span>
    );
}

export function DiscountCodeScopeBadge({
    scope
}: {
    scope: DiscountCodeScope;
}) {
    const Icon = scopeIcons[scope];

    return (
        <span
            className={`${styles.applicationBadge} ${scopeClassNames[scope]}`}
        >
            <Icon size={15} aria-hidden="true" />
            {discountCodeScopeLabels[scope]}
        </span>
    );
}
