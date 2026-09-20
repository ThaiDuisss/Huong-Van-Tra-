package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InTransitSummaryResponse(
        long totalInTransitShipments,
        BigDecimal totalInTransitQuantity,
        BigDecimal totalInTransitValue,
        List<InTransitShipmentDetail> shipments
) {
    public record InTransitShipmentDetail(
            UUID documentId,
            String documentCode,
            String sourceLocationName,
            String destinationLocationName,
            BigDecimal totalQuantity,
            BigDecimal estimatedValue,
            Instant createdAt
    ) {}
}
