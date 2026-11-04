package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.dto.request.OrderImportRequest;
import org.hvtra.orderservice.dto.response.OrderImportResponse;
import org.hvtra.orderservice.service.OrderImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderImportController {

    /** Nhập đơn hàng loạt là thao tác quản lý: nhân viên bán hàng không có quyền khi gateway chưa relay permission. */
    private static final Role[] LEGACY_IMPORT_ROLES = {Role.MANAGER, Role.SUPER_ADMIN};

    private final OrderImportService orderImportService;

    @Operation(summary = "nhap don hang tu file Excel (dryRun=true chi kiem tra)")
    @UserAction(value = "ORDER_IMPORT", entityType = "Order")
    @PostMapping("/import")
    public OrderImportResponse importOrders(@Valid @RequestBody OrderImportRequest request) {
        SecurityUtils.requirePermission(OrderController.PERMISSION_ORDER_CREATE, LEGACY_IMPORT_ROLES);
        return orderImportService.importOrders(request);
    }
}
