package org.hvtra.iventoryservice.customerreturn.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerReturnInspectionLineResponse(
        UUID id,
        UUID returnItemId,
        UUID orderItemId,
        UUID productVariantId,
        UUID inventoryItemId,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        CustomerReturnQcResult result,
        UUID destinationLocationId,
        String destinationLocationName,
        String note,
        Boolean posted,
        UUID transactionId,
        InventoryTransactionType transactionType,
        UUID disposalRequestId,
        String disposalRequestCode,
        DisposalRequestStatus disposalStatus,
        String declaredCondition,
        String returnReason,
        CustomerReturnActualCondition actualCondition
) {
    public CustomerReturnInspectionLineResponse(
            UUID id,
            UUID returnItemId,
            UUID orderItemId,
            UUID productVariantId,
            UUID inventoryItemId,
            String itemCode,
            String itemName,
            BigDecimal quantity,
            CustomerReturnQcResult result,
            UUID destinationLocationId,
            String destinationLocationName,
            String note,
            Boolean posted,
            UUID transactionId,
            UUID disposalRequestId,
            String disposalRequestCode,
            DisposalRequestStatus disposalStatus,
            String condition,
            String returnReason,
            CustomerReturnActualCondition actualCondition
    ) {
        this(
                id,
                returnItemId,
                orderItemId,
                productVariantId,
                inventoryItemId,
                itemCode,
                itemName,
                quantity,
                result,
                destinationLocationId,
                destinationLocationName,
                note,
                posted,
                transactionId,
                transactionType(result),
                disposalRequestId,
                disposalRequestCode,
                disposalStatus,
                condition,
                returnReason,
                actualCondition
        );
    }

    public CustomerReturnInspectionLineResponse(
            UUID id,
            UUID returnItemId,
            UUID orderItemId,
            UUID productVariantId,
            UUID inventoryItemId,
            String itemCode,
            String itemName,
            BigDecimal quantity,
            CustomerReturnQcResult result,
            UUID destinationLocationId,
            String destinationLocationName,
            String note,
            Boolean posted,
            UUID transactionId,
            CustomerReturnActualCondition actualCondition
    ) {
        this(id, returnItemId, orderItemId, productVariantId, inventoryItemId, itemCode, itemName, quantity, result, destinationLocationId, destinationLocationName, note, posted, transactionId, null, null, null, null, null, actualCondition);
    }

    /** Compatibility alias; this is the historical declaration, never the QC observation. */
    @JsonProperty("condition")
    public String condition() {
        return declaredCondition;
    }

    private static InventoryTransactionType transactionType(CustomerReturnQcResult result) {
        return result == CustomerReturnQcResult.WRITE_OFF
                ? InventoryTransactionType.WRITE_OFF
                : InventoryTransactionType.CUSTOMER_RETURN_RECEIPT;
    }
}
