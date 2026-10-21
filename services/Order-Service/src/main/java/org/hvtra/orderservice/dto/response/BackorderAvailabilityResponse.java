package org.hvtra.orderservice.dto.response;

import org.hvtra.orderservice.client.InventoryClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param ready                đã đủ hàng để giao cho khách
 * @param estimatedAvailableAt ngày dự kiến có hàng (null nếu đã đủ hoặc chưa ước tính được)
 */
public record BackorderAvailabilityResponse(
        UUID orderId,
        boolean ready,
        Instant estimatedAvailableAt,
        List<InventoryClient.AvailabilityLineEstimate> lines
) {
}
