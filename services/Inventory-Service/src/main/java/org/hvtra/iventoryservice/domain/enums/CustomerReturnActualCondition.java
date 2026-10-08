package org.hvtra.iventoryservice.domain.enums;

/** Physical condition recorded by QC, independent of the original Order declaration. */
public enum CustomerReturnActualCondition {
    SEALED,
    OPENED,
    DAMAGED,
    SPOILED,
    USED
}
