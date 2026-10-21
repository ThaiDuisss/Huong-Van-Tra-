package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.response.BackorderAvailabilityResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.service.OrderBackorderService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderBackorderController {

    private static final String UUID_PATH = "{orderId:[0-9a-fA-F\\-]{36}}";

    private final OrderBackorderService orderBackorderService;

    @Operation(summary = "danh sach don cho hang (da thu tien, chua giao)")
    @GetMapping("/backorders")
    public Page<OrderResponse> getWaitingOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderBackorderService.getWaitingOrders(keyword, page, size);
    }

    @Operation(summary = "kiem tra don cho hang da du hang de giao chua")
    @PostMapping("/" + UUID_PATH + "/backorder/check")
    public BackorderAvailabilityResponse checkAvailability(@PathVariable UUID orderId) {
        return orderBackorderService.checkAvailability(orderId);
    }

    @Operation(summary = "giao hang cho don cho hang: tru ton, thu phan con lai, hoan tat don")
    @UserAction(value = "ORDER_BACKORDER_FULFILL", entityType = "Order", entityId = "#orderId")
    @PostMapping("/" + UUID_PATH + "/backorder/fulfill")
    public OrderResponse fulfill(
            @PathVariable UUID orderId,
            @RequestBody(required = false) FulfillBackorderRequest request
    ) {
        return orderBackorderService.fulfill(orderId, request);
    }
}
