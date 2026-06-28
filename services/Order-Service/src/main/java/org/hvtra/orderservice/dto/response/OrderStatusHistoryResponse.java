package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderStatusHistoryResponse {
    private UUID id;
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
    private String oldStatusLabel;
    private String newStatusLabel;
    private String note;
    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
