package org.hvtra.iventoryservice.domain.support;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.EnumSet;
import java.util.Collections;
import java.util.Set;

public final class InventoryLocationSaleability {
    private static final Set<InventoryLocationType> NON_SALEABLE_TYPES = EnumSet.of(
            InventoryLocationType.DAMAGED,
            InventoryLocationType.QUARANTINE
    );

    private InventoryLocationSaleability() {
    }

    public static boolean isSaleable(InventoryLocation location) {
        return location != null
                && location.getStatus() == InventoryStatus.ACTIVE
                && isSaleableType(location.getType());
    }

    public static boolean isSaleableType(InventoryLocationType type) {
        return type != null && !NON_SALEABLE_TYPES.contains(type);
    }

    public static Set<InventoryLocationType> nonSaleableTypes() {
        return Collections.unmodifiableSet(NON_SALEABLE_TYPES);
    }
}
